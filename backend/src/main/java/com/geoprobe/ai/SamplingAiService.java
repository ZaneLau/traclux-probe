package com.geoprobe.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.ConversationTurn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SamplingAiService {

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_TIMEOUT = 3;
    private static final String SAMPLING_PROVIDER = "volcengine";
    private static final String RESPONSE_MODE_REALISTIC = "realistic";
    private static final String RESPONSE_MODE_COMPACT = "compact";
    private static final String DEFAULT_REALISTIC_SYSTEM_INSTRUCTION = "你是一个真实咨询助手，只输出最终回答，不展示思维过程。请基于用户问题给出自然、可执行的建议，可包含推荐理由、适配人群/场景、关键比较点和注意事项；允许分点或分段，不要刻意压缩字数。";
    private static final String DEFAULT_COMPACT_SYSTEM_INSTRUCTION = "你是一个咨询问答助手，只输出最终回答，不展示思维过程。请用一个自然段回答，控制在120字以内。";

    private final ObjectMapper objectMapper;
    private final ModelCapabilityRegistry modelCapabilityRegistry;
    private final VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${llm.volcengine.api-key:dummy-api-key}")
    private String apiKey;

    @Value("${llm.volcengine.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    @Value("${llm.volcengine.model:doubao-seed-1.8}")
    private String model;

    @Value("${llm.volcengine.endpoint-id:}")
    private String endpointId;

    @Value("${app.engine.sampling.model-timeout-seconds:150}")
    private long timeoutSeconds = 150L;

    @Value("${app.engine.sampling.model-max-completion-tokens:4096}")
    private int maxCompletionTokens = 4096;

    @Value("${app.engine.sampling.disable-reasoning:true}")
    private boolean disableReasoning = true;

    @Value("${app.engine.sampling.response-mode:realistic}")
    private String responseMode;

    @Value("${app.engine.sampling.system-instruction:}")
    private String systemInstructionOverride;

    @Value("${app.engine.sampling.system-instruction-realistic:" + DEFAULT_REALISTIC_SYSTEM_INSTRUCTION + "}")
    private String systemInstructionRealistic;

    @Value("${app.engine.sampling.system-instruction-compact:" + DEFAULT_COMPACT_SYSTEM_INSTRUCTION + "}")
    private String systemInstructionCompact;

    public SamplingAiResult generate(String diagnosisId, String promptText) {
        return generate(diagnosisId, List.of(ConversationTurn.user(promptText)));
    }

    public SamplingAiResult generate(String diagnosisId, List<ConversationTurn> conversation) {
        List<ConversationTurn> normalizedConversation = normalizeConversation(conversation);
        if (normalizedConversation.isEmpty()) {
            return SamplingAiResult.failed(STATUS_FAILED, 0, "INVALID_INPUT", resolveModelName(), "采样 prompt 为空");
        }

        long startNanos = System.nanoTime();
        try {
            volcengineRewardRunAdmissionService.requireAllowed(SAMPLING_PROVIDER, resolveModelName());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveCompletionsUrl()))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(normalizedConversation)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int latencyMs = elapsedMillis(startNanos);

            if (response.statusCode() / 100 != 2) {
                return SamplingAiResult.failed(
                        STATUS_FAILED,
                        latencyMs,
                        "HTTP_" + response.statusCode(),
                        resolveModelName(),
                        truncate("采样 HTTP " + response.statusCode() + ": " + SensitiveDataSanitizer.sanitize(response.body()), 500)
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                return SamplingAiResult.failed(
                        STATUS_FAILED,
                        latencyMs,
                        "API_ERROR",
                        resolveModelName(),
                        truncate(SensitiveDataSanitizer.sanitize(root.get("error").toString()), 500)
                );
            }

            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");

            String content = asText(message.get("content"));
            String finishReason = normalizeFinishReason(asText(choice.get("finish_reason")));
            String resolvedModel = StringUtils.hasText(asText(root.get("model"))) ? asText(root.get("model")) : resolveModelName();

            JsonNode usage = root.path("usage");
            Integer promptTokens = asInt(usage.get("prompt_tokens"));
            Integer completionTokens = asInt(usage.get("completion_tokens"));
            Integer totalTokens = asInt(usage.get("total_tokens"));
            Integer reasoningTokens = asInt(usage.path("completion_tokens_details").get("reasoning_tokens"));

            return SamplingAiResult.success(
                    content,
                    content.length(),
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    reasoningTokens,
                    latencyMs,
                    finishReason,
                    resolvedModel
            );
        } catch (java.net.http.HttpTimeoutException ex) {
            return SamplingAiResult.failed(STATUS_TIMEOUT, elapsedMillis(startNanos), "TIMEOUT", resolveModelName(), "采样请求超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SamplingAiResult.failed(STATUS_FAILED, elapsedMillis(startNanos), "INTERRUPTED", resolveModelName(), "采样请求被中断");
        } catch (IOException ex) {
            return SamplingAiResult.failed(
                    STATUS_FAILED,
                    elapsedMillis(startNanos),
                    "IO_ERROR",
                    resolveModelName(),
                    truncate(SensitiveDataSanitizer.sanitize(ex.getMessage()), 300)
            );
        } catch (Exception ex) {
            return SamplingAiResult.failed(
                    STATUS_FAILED,
                    elapsedMillis(startNanos),
                    "UNEXPECTED_ERROR",
                    resolveModelName(),
                    truncate(SensitiveDataSanitizer.sanitize(ex.getMessage()), 300)
            );
        }
    }

    private String buildRequestBody(List<ConversationTurn> conversation) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        ModelCapability capability = modelCapabilityRegistry.resolve(SAMPLING_PROVIDER, resolveModelName());

        payload.put("model", resolveModelName());
        payload.put("messages", buildMessages(conversation));
        payload.put("temperature", 0.85d);
        if (capability.supportsMaxCompletionTokens()) {
            payload.put("max_completion_tokens", resolveMaxCompletionTokens());
        }
        payload.put("stream", false);
        if (disableReasoning && capability.supportsThinkingToggle()) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        return objectMapper.writeValueAsString(payload);
    }

    private List<Map<String, String>> buildMessages(List<ConversationTurn> conversation) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", resolveSystemInstruction()));
        for (ConversationTurn turn : conversation) {
            if (turn == null || !StringUtils.hasText(turn.content())) {
                continue;
            }
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        return messages;
    }

    private List<ConversationTurn> normalizeConversation(List<ConversationTurn> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return List.of();
        }
        List<ConversationTurn> normalized = conversation.stream()
                .filter(turn -> turn != null && StringUtils.hasText(turn.content()))
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }

    private String resolveCompletionsUrl() {
        String trimmedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmedBaseUrl + "/chat/completions";
    }

    private String resolveModelName() {
        return StringUtils.hasText(endpointId) ? endpointId : model;
    }

    private long resolveTimeoutSeconds() {
        return Math.max(30L, timeoutSeconds);
    }

    private int resolveMaxCompletionTokens() {
        return Math.max(32, maxCompletionTokens);
    }

    private String resolveSystemInstruction() {
        if (StringUtils.hasText(systemInstructionOverride)) {
            return systemInstructionOverride.trim();
        }
        if (RESPONSE_MODE_COMPACT.equalsIgnoreCase(responseMode)) {
            return StringUtils.hasText(systemInstructionCompact)
                    ? systemInstructionCompact.trim()
                    : DEFAULT_COMPACT_SYSTEM_INSTRUCTION;
        }
        return StringUtils.hasText(systemInstructionRealistic)
                ? systemInstructionRealistic.trim()
                : DEFAULT_REALISTIC_SYSTEM_INSTRUCTION;
    }

    private Integer asInt(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return null;
        }
        return node.intValue();
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String normalizeFinishReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "UNKNOWN";
        }
        return reason.trim().toUpperCase(Locale.ROOT);
    }

    private int elapsedMillis(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String truncate(String message, int maxLen) {
        if (!StringUtils.hasText(message)) {
            return "Unknown error";
        }
        String trimmed = message.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }
}

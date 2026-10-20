package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.LlmCostCalculator;
import com.geoprobe.common.LlmManualCallLogger;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.entity.LlmCallLog;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class OpenAiCompatibleAdapter implements ModelAdapter {

    private static final String ERROR_CODE_ACCOUNT_OVERDUE = "UPSTREAM_PROVIDER_ACCOUNT_OVERDUE";
    private static final Pattern THINK_PATTERN = Pattern.compile("(?s)<think>(.*?)</think>");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[[^\\]]+\\]\\((https?://[^)\\s]+)\\)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");

    private final String providerCode;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModelId;
    private final Duration timeout;
    private final Double temperature;
    private final Integer maxCompletionTokens;
    private final boolean supportsThinkingToggle;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Consumer<LlmCallLog> llmCallLogSink;
    private final LlmTokenBudgetService llmTokenBudgetService;

    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_TIMEOUT = 3;

    public OpenAiCompatibleAdapter(String providerCode,
                                   String apiKey,
                                   String baseUrl,
                                   String defaultModelId,
                                   Duration timeout,
                                   Double temperature,
                                   Integer maxCompletionTokens,
                                   boolean supportsThinkingToggle,
                                   ObjectMapper objectMapper) {
        this(
                providerCode,
                apiKey,
                baseUrl,
                defaultModelId,
                timeout,
                temperature,
                maxCompletionTokens,
                supportsThinkingToggle,
                objectMapper,
                null,
                null
        );
    }

    public OpenAiCompatibleAdapter(String providerCode,
                                   String apiKey,
                                   String baseUrl,
                                   String defaultModelId,
                                   Duration timeout,
                                   Double temperature,
                                   Integer maxCompletionTokens,
                                   boolean supportsThinkingToggle,
                                   ObjectMapper objectMapper,
                                   Consumer<LlmCallLog> llmCallLogSink) {
        this(
                providerCode,
                apiKey,
                baseUrl,
                defaultModelId,
                timeout,
                temperature,
                maxCompletionTokens,
                supportsThinkingToggle,
                objectMapper,
                llmCallLogSink,
                null
        );
    }

    public OpenAiCompatibleAdapter(String providerCode,
                                   String apiKey,
                                   String baseUrl,
                                   String defaultModelId,
                                   Duration timeout,
                                   Double temperature,
                                   Integer maxCompletionTokens,
                                   boolean supportsThinkingToggle,
                                   ObjectMapper objectMapper,
                                   Consumer<LlmCallLog> llmCallLogSink,
                                   LlmTokenBudgetService llmTokenBudgetService) {
        this.providerCode = normalizeProvider(providerCode);
        this.apiKey = normalizeOptional(apiKey);
        this.baseUrl = normalizeOptional(baseUrl);
        this.defaultModelId = normalizeOptional(defaultModelId);
        this.timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
        this.supportsThinkingToggle = supportsThinkingToggle;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.llmCallLogSink = llmCallLogSink;
        this.llmTokenBudgetService = llmTokenBudgetService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
        Objects.requireNonNull(probeInput, "probeInput 不能为空");
        Objects.requireNonNull(dispatchControl, "dispatchControl 不能为空");
        if (dispatchControl.realtimeSearchEnabled()) {
            throw new ProviderDispatchException(
                    "REALTIME_SEARCH_UNSUPPORTED",
                    providerCode + " 当前未接入实时联网搜索"
            );
        }
        validateConfigured();
        String modelId = resolveModelId(dispatchControl.targetModelId());
        long startedAt = System.nanoTime();
        LlmTokenBudgetService.BudgetLease budgetLease = null;
        try {
            budgetLease = llmTokenBudgetService == null
                    ? LlmTokenBudgetService.BudgetLease.noop()
                    : llmTokenBudgetService.reserveOrThrow(providerCode, modelId);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveCompletionsUrl()))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                            buildRequestPayload(modelId, probeInput.renderedPrompt(), dispatchControl.disableReasoning())
                    )))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            if (response.statusCode() / 100 != 2) {
                String responseBody = response.body();
                throw new ProviderDispatchException(
                        resolveProviderErrorCode(response.statusCode(), responseBody),
                        providerCode + " 返回非 2xx: " + response.statusCode() + ", body=" + summarizeBody(responseBody)
                );
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                String errorPayload = root.get("error").toString();
                throw new ProviderDispatchException(
                        resolveProviderErrorCode(200, errorPayload),
                        providerCode + " 返回错误: " + SensitiveDataSanitizer.preview(errorPayload, 180)
                );
            }
            RawResponse rawResponse = parseChatCompletionResponse(root, modelId, latencyMs);
            if (!StringUtils.hasText(rawResponse.fullText())) {
                String suffix = StringUtils.hasText(rawResponse.thinkContent())
                        ? ", reasoning=" + SensitiveDataSanitizer.preview(rawResponse.thinkContent(), 120)
                        : "";
                throw new ProviderDispatchException(
                        "EMPTY_FINAL_RESPONSE",
                        providerCode + " 返回空正文，可能未产出最终答案" + suffix,
                        rawResponse
                );
            }
            if (llmTokenBudgetService != null) {
                llmTokenBudgetService.commit(budgetLease, rawResponse.totalTokenCount());
            }
            recordCallLogSuccess(rawResponse.modelId(), rawResponse);
            return rawResponse;
        } catch (LlmTokenBudgetExceededException ex) {
            recordCallLogFailure(modelId, null, startedAt, ex);
            throw ex;
        } catch (ProviderDispatchException ex) {
            reconcileBudgetFailure(budgetLease, ex.rawResponse());
            recordCallLogFailure(modelId, ex.rawResponse(), startedAt, ex);
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            ProviderDispatchException wrapped = new ProviderDispatchException("UPSTREAM_PROVIDER_TIMEOUT", providerCode + " 调用超时", ex);
            reconcileBudgetFailure(budgetLease, null);
            recordCallLogFailure(modelId, null, startedAt, wrapped);
            throw wrapped;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ProviderDispatchException wrapped = new ProviderDispatchException("UPSTREAM_PROVIDER_INTERRUPTED", providerCode + " 调用被中断", ex);
            reconcileBudgetFailure(budgetLease, null);
            recordCallLogFailure(modelId, null, startedAt, wrapped);
            throw wrapped;
        } catch (IOException ex) {
            ProviderDispatchException wrapped = new ProviderDispatchException(
                    "UPSTREAM_PROVIDER_IO_ERROR",
                    providerCode + " 调用失败: " + summarizeException(ex),
                    ex
            );
            reconcileBudgetFailure(budgetLease, null);
            recordCallLogFailure(modelId, null, startedAt, wrapped);
            throw wrapped;
        } catch (RuntimeException ex) {
            ProviderDispatchException wrapped = new ProviderDispatchException(
                    "UPSTREAM_PROVIDER_ERROR",
                    providerCode + " 调用失败: " + summarizeException(ex),
                    ex
            );
            reconcileBudgetFailure(budgetLease, null);
            recordCallLogFailure(modelId, null, startedAt, wrapped);
            throw wrapped;
        }
    }

    Map<String, Object> buildRequestPayload(String modelId, String promptText, boolean disableReasoning) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelId);
        payload.put("messages", List.of(Map.of("role", "user", "content", promptText)));
        if (temperature != null) {
            payload.put("temperature", temperature);
        }
        if (maxCompletionTokens != null && maxCompletionTokens > 0) {
            payload.put("max_completion_tokens", maxCompletionTokens);
        }
        payload.put("stream", false);
        if (disableReasoning && supportsThinkingToggle) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        return payload;
    }

    RawResponse parseChatCompletionResponse(JsonNode root, String modelId, long latencyMs) {
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        ParsedMessageContent parsedMessageContent = parseMessageContent(message);
        String fullText = parsedMessageContent.finalText();
        String reasoningContent = normalize(asText(message.get("reasoning_content")));
        String finishReason = normalize(asText(choice.get("finish_reason")));
        JsonNode usage = root.path("usage");
        int promptTokenCount = asNonNegativeInt(usage.get("prompt_tokens"));
        int completionTokenCount = asNonNegativeInt(usage.get("completion_tokens"));
        int totalTokenCount = asNonNegativeInt(usage.get("total_tokens"));
        String resolvedModelId = StringUtils.hasText(asText(root.get("model")))
                ? asText(root.get("model")).trim()
                : modelId;
        return new RawResponse(
                fullText,
                StringUtils.hasText(reasoningContent)
                        ? reasoningContent
                        : StringUtils.hasText(parsedMessageContent.reasoningText())
                        ? parsedMessageContent.reasoningText()
                        : extractThinkContent(fullText),
                extractCitations(fullText),
                promptTokenCount,
                completionTokenCount,
                totalTokenCount,
                Math.max(0L, latencyMs),
                isLengthStopped(finishReason),
                false,
                false,
                false,
                List.of(),
                0,
                List.of(),
                resolvedModelId,
                "",
                List.of()
        );
    }

    private boolean isLengthStopped(String finishReason) {
        if (!StringUtils.hasText(finishReason)) {
            return false;
        }
        String normalized = finishReason.trim().toLowerCase(Locale.ROOT);
        return "length".equals(normalized) || "max_tokens".equals(normalized);
    }

    private void validateConfigured() {
        if (!StringUtils.hasText(apiKey) || "dummy-api-key".equals(apiKey)) {
            throw new ProviderDispatchException("TARGET_PROVIDER_UNCONFIGURED", providerCode + " apiKey 未配置");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new ProviderDispatchException("TARGET_PROVIDER_UNCONFIGURED", providerCode + " baseUrl 未配置");
        }
    }

    private String resolveModelId(String requestedModelId) {
        if (StringUtils.hasText(requestedModelId)) {
            return requestedModelId.trim();
        }
        if (StringUtils.hasText(defaultModelId)) {
            return defaultModelId;
        }
        throw new ProviderDispatchException("TARGET_MODEL_UNSPECIFIED", providerCode + " 缺少默认 modelId");
    }

    private String resolveCompletionsUrl() {
        String trimmedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmedBaseUrl + "/chat/completions";
    }

    private ParsedMessageContent parseMessageContent(JsonNode message) {
        if (message == null || message.isMissingNode() || message.isNull()) {
            return ParsedMessageContent.empty();
        }
        JsonNode contentNode = message.get("content");
        if (contentNode == null || contentNode.isNull()) {
            return ParsedMessageContent.empty();
        }
        if (contentNode.isTextual()) {
            return new ParsedMessageContent(normalize(contentNode.asText("")), "");
        }
        if (!contentNode.isArray()) {
            return new ParsedMessageContent(normalize(contentNode.asText("")), "");
        }
        StringBuilder finalTextBuilder = new StringBuilder();
        StringBuilder fallbackTextBuilder = new StringBuilder();
        StringBuilder reasoningTextBuilder = new StringBuilder();
        for (JsonNode item : contentNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                appendLine(fallbackTextBuilder, item.asText(""));
                continue;
            }
            String type = normalizeContentPartType(item);
            String text = extractContentPartText(item);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (isReasoningContentPart(type)) {
                appendLine(reasoningTextBuilder, text);
                continue;
            }
            if (isFinalAnswerContentPart(type)) {
                appendLine(finalTextBuilder, text);
                continue;
            }
            appendLine(fallbackTextBuilder, text);
        }
        String resolvedFinalText = StringUtils.hasText(finalTextBuilder.toString())
                ? finalTextBuilder.toString()
                : fallbackTextBuilder.toString();
        return new ParsedMessageContent(
                normalize(resolvedFinalText),
                normalize(reasoningTextBuilder.toString())
        );
    }

    private String normalizeContentPartType(JsonNode item) {
        if (item == null || item.isNull()) {
            return "";
        }
        String type = asText(item.get("type"));
        return StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";
    }

    private boolean isFinalAnswerContentPart(String type) {
        return switch (type) {
            case "text", "output_text", "final_text", "assistant_text", "answer" -> true;
            default -> false;
        };
    }

    private boolean isReasoningContentPart(String type) {
        return switch (type) {
            case "reasoning", "thinking", "reasoning_text", "reasoning_content", "analysis" -> true;
            default -> false;
        };
    }

    private String extractContentPartText(JsonNode item) {
        if (item == null || item.isNull()) {
            return "";
        }
        if (item.isTextual()) {
            return normalize(item.asText(""));
        }
        if (item.has("text")) {
            return extractTextValue(item.get("text"));
        }
        if (item.has("content")) {
            return extractTextValue(item.get("content"));
        }
        if (item.has("value")) {
            return extractTextValue(item.get("value"));
        }
        return normalize(item.asText(""));
    }

    private String extractTextValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return normalize(node.asText(""));
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : node) {
                appendLine(builder, extractTextValue(item));
            }
            return normalize(builder.toString());
        }
        if (node.isObject()) {
            if (node.has("value")) {
                return extractTextValue(node.get("value"));
            }
            if (node.has("text")) {
                return extractTextValue(node.get("text"));
            }
        }
        return normalize(node.asText(""));
    }

    private void appendLine(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    private void recordCallLogSuccess(String modelId, RawResponse rawResponse) {
        recordCallLog(
                modelId,
                rawResponse == null ? null : rawResponse.promptTokenCount(),
                rawResponse == null ? null : rawResponse.completionTokenCount(),
                rawResponse == null ? null : toLatencyMillis(rawResponse.latencyMs()),
                STATUS_SUCCESS,
                null
        );
    }

    private void recordCallLogFailure(String modelId,
                                      RawResponse rawResponse,
                                      long startedAt,
                                      Throwable error) {
        Integer latencyMs = rawResponse != null
                ? toLatencyMillis(rawResponse.latencyMs())
                : toLatencyMillis((System.nanoTime() - startedAt) / 1_000_000L);
        recordCallLog(
                modelId,
                rawResponse == null ? null : rawResponse.promptTokenCount(),
                rawResponse == null ? null : rawResponse.completionTokenCount(),
                latencyMs,
                isTimeout(error) ? STATUS_TIMEOUT : STATUS_FAILED,
                summarizeFailure(error)
        );
    }

    private void recordCallLog(String modelId,
                               Integer inputTokens,
                               Integer outputTokens,
                               Integer latencyMs,
                               int status,
                               String errorMessage) {
        String resolvedModelId = StringUtils.hasText(modelId) ? modelId.trim() : defaultModelId;
        LlmManualCallLogger.record(
                llmCallLogSink,
                providerCode,
                resolvedModelId,
                inputTokens,
                outputTokens,
                latencyMs,
                status,
                errorMessage
        );
    }

    private void reconcileBudgetFailure(LlmTokenBudgetService.BudgetLease budgetLease, RawResponse rawResponse) {
        if (llmTokenBudgetService == null || budgetLease == null || budgetLease.noOp()) {
            return;
        }
        Integer totalTokens = rawResponse == null ? null : rawResponse.totalTokenCount();
        if (totalTokens != null && totalTokens > 0) {
            llmTokenBudgetService.commit(budgetLease, totalTokens);
            return;
        }
        llmTokenBudgetService.release(budgetLease);
    }

    private Integer toLatencyMillis(long latencyMs) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, latencyMs));
    }

    private boolean isTimeout(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            if (cursor instanceof ProviderDispatchException providerDispatchException
                    && "UPSTREAM_PROVIDER_TIMEOUT".equalsIgnoreCase(providerDispatchException.errorCode())) {
                return true;
            }
            String message = cursor.getMessage();
            if (StringUtils.hasText(message) && message.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String summarizeFailure(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        if (!StringUtils.hasText(message) && error.getCause() != null) {
            message = error.getCause().getMessage();
        }
        return StringUtils.hasText(message)
                ? SensitiveDataSanitizer.sanitize(message.trim())
                : error.getClass().getSimpleName();
    }

    private int asNonNegativeInt(JsonNode node) {
        if (node == null || node.isNull() || !node.canConvertToInt()) {
            return 0;
        }
        return Math.max(0, node.intValue());
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private List<String> extractCitations(String fullText) {
        if (!StringUtils.hasText(fullText)) {
            return List.of();
        }
        java.util.LinkedHashSet<String> citations = new java.util.LinkedHashSet<>();
        Matcher markdownMatcher = MARKDOWN_LINK_PATTERN.matcher(fullText);
        while (markdownMatcher.find()) {
            citations.add(markdownMatcher.group(1).trim());
        }
        Matcher urlMatcher = URL_PATTERN.matcher(fullText);
        while (urlMatcher.find()) {
            citations.add(urlMatcher.group().trim());
        }
        return List.copyOf(citations);
    }

    private String extractThinkContent(String fullText) {
        if (!StringUtils.hasText(fullText)) {
            return "";
        }
        Matcher matcher = THINK_PATTERN.matcher(fullText);
        if (!matcher.find()) {
            return "";
        }
        return normalize(matcher.group(1));
    }

    private String summarizeBody(String body) {
        return SensitiveDataSanitizer.preview(body, 180);
    }

    private String resolveProviderErrorCode(int statusCode, String body) {
        if (looksLikeAccountOverdue(statusCode, body)) {
            return ERROR_CODE_ACCOUNT_OVERDUE;
        }
        return "UPSTREAM_PROVIDER_ERROR";
    }

    private boolean looksLikeAccountOverdue(int statusCode, String body) {
        if (statusCode > 0 && statusCode != 200 && statusCode != 402 && statusCode != 403) {
            return false;
        }
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("accountoverdueerror")
                || normalized.contains("overdue balance");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim();
    }

    private String normalizeProvider(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException("providerCode 不能为空");
        }
        return providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String summarizeException(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return throwable == null ? "UNKNOWN_ERROR" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage().trim();
    }

    private record ParsedMessageContent(String finalText, String reasoningText) {
        private static ParsedMessageContent empty() {
            return new ParsedMessageContent("", "");
        }
    }
}

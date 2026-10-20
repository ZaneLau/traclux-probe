package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.impl.PromptAssetBackedProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetLoader;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import com.geoprobe.ai.v42.support.V42SecretAssetSettings;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeStatus;
import com.geoprobe.dto.engine.v42.RealizationConstraints;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.util.StringUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Manual canary runner for validating Node2 prompt patches against real rendering outputs.
 * This tool intentionally stays outside the default test suite and is executed explicitly via
 * maven exec so we can call the real creative model without pulling up the whole Spring app.
 */
public final class W7Node2PromptPatchCanaryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Path DEFAULT_ENV_FILE = Path.of("../.env.local");
    private static final String DEFAULT_PROMPT_ASSET_DIR = "file:../.private/prompt-assets";
    private static final String DEFAULT_ARK_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-v3.2";
    private static final double DEFAULT_NODE2_RENDER_TEMPERATURE = 0.2d;

    private static final Map<String, List<String>> DIMENSION_VARIABLES = Map.of(
            "D1", List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下"),
            "D2", List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到"),
            "D3", List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "就想更有个性", "主要图个开心"),
            "D4", List.of("脑子已经很累了", "有点累但还行", "头脑清醒能分析"),
            "D5", List.of("马上就得用", "还有几天缓冲", "不急慢慢看"),
            "D6", List.of("直接搜个答案", "先随便刷一刷", "打算跟AI聊聊")
    );

    private W7Node2PromptPatchCanaryTool() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Map<String, String> env = loadEnv(parsed.envFile());
        VolcengineRewardQuotaGate.requireLiveExperimentAllowance(
                "W7Node2PromptPatchCanaryTool",
                resolveQuotaModelLabel(env)
        );
        ChatLanguageModel creativeModel = buildCreativeModel(env);
        ProbeRenderingAiService aiService = new PromptAssetBackedProbeRenderingAiService(
                creativeModel,
                new V42PromptExecutionSupport(new V42PromptAssetLoader(
                        new V42SecretAssetSettings(parsed.promptAssetDir(), true)
                ))
        );
        ProbeRenderingService renderingService = new ProbeRenderingService(aiService, new ThreeLayerMappingEvaluator());
        ProbeSimulationPlanningService planningService = new ProbeSimulationPlanningService();

        List<Map<String, Object>> rows = readJsonl(parsed.input());
        Files.createDirectories(parsed.output().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(parsed.output(), StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> output = rerenderRow(row, renderingService, planningService, env);
                writer.write(MAPPER.writeValueAsString(output));
                writer.newLine();
                writer.flush();
            }
        }
        Map<String, Object> summary = summarize(readJsonl(parsed.output()));
        if (parsed.summaryOutput() != null) {
            Files.createDirectories(parsed.summaryOutput().getParent());
            Files.writeString(
                    parsed.summaryOutput(),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary),
                    StandardCharsets.UTF_8
            );
        }
        assertSummaryPasses(summary);
    }

    private static Map<String, Object> rerenderRow(Map<String, Object> row,
                                                   ProbeRenderingService renderingService,
                                                   ProbeSimulationPlanningService planningService,
                                                   Map<String, String> env) {
        String brand = requiredString(row, "brand");
        String category = requiredString(row, "category");
        List<String> competitors = stringList(row.get("competitors"));
        Map<String, String> abstractTags = normalizeStateVector(mapOf(row.get("state_vector")));
        Map<String, String> translatedTags = translatedTags(abstractTags);

        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedAnchors =
                V42CategoryAnchorResolver.fromRequest(category, List.of());
        EngineTaskContextPackage contextPackage = new EngineTaskContextPackage(
                brand,
                category,
                competitors,
                resolvedAnchors.anchors(),
                List.of(),
                resolvedAnchors.fallbackAnchorMode(),
                null,
                List.of(),
                List.of(),
                competitors,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );

        CategoryVariablePool pool = new CategoryVariablePool(
                brand,
                category,
                competitors,
                resolvedAnchors.anchors(),
                dimension("D1"),
                dimension("D2"),
                dimension("D3"),
                dimension("D4"),
                dimension("D5"),
                dimension("D6"),
                contextPackage
        );

        BucketType bucketType = BucketType.valueOf(requiredString(row, "bucket_type").trim().toUpperCase(Locale.ROOT));
        RealizationPlan realizationPlan = planningService.plan(
                bucketType,
                abstractTags,
                translatedTags,
                null,
                null,
                contextPackage,
                W7Node2TopicProjectionHeuristics.deriveTopicProjection(stringValue(row.get("probe_text")))
        );
        realizationPlan = augmentSourceContractHints(realizationPlan, stringValue(row.get("probe_text")), category);

        ProbeBlueprint blueprint = new ProbeBlueprint(
                requiredString(row, "probe_id"),
                bucketType,
                abstractTags,
                translatedTags,
                stringValue(row.get("probe_text")),
                ProbeStatus.PENDING,
                realizationPlan
        );

        RenderedProbe renderedProbe = renderingService.renderOne(
                "w7-v2-patch-canary",
                pool,
                List.of(),
                blueprint
        );

        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("probe_id", requiredString(row, "probe_id"));
        output.put("probe_ref", stringValue(row.get("probe_ref")));
        output.put("brand", brand);
        output.put("category", category);
        output.put("competitors", competitors);
        output.put("bucket_type", bucketType.name());
        output.put("probe_type", stringValue(row.get("probe_type")));
        output.put("state_vector", abstractTags);
        output.put("translated_tags", translatedTags);
        output.put("category_anchors", resolvedAnchors.anchors());
        output.put("category_anchor_source", resolvedAnchors.source());
        output.put("old_probe_text", stringValue(row.get("probe_text")));
        output.put("target_effect_entries", row.getOrDefault("target_effect_entries", List.of()));
        output.put("old_triggered_rules_json", row.get("triggered_rules_json"));
        output.put("new_probe_text", renderedProbe.renderedPrompt());
        output.put("new_render_reason", renderedProbe.renderReason());
        output.put("new_render_source", renderedProbe.renderSource());
        output.put("new_render_family", renderedProbe.renderFamily());
        output.put("new_fallback_used", renderedProbe.fallbackUsed());
        output.put("new_triggered_rules_json", renderedProbe.triggeredRulesJson());
        output.put("creative_model", resolveCreativeModelLabel(env));
        return output;
    }

    private static RealizationPlan augmentSourceContractHints(RealizationPlan realizationPlan,
                                                              String sourceProbeText,
                                                              String category) {
        if (realizationPlan == null || !StringUtils.hasText(sourceProbeText)) {
            return realizationPlan;
        }
        String sourceCategoryAnchor = extractSourceCategoryAnchor(sourceProbeText, category);
        String sourceOfficialObject = extractSourceOfficialObject(sourceProbeText);
        if (!StringUtils.hasText(sourceOfficialObject) && !StringUtils.hasText(sourceCategoryAnchor)) {
            return realizationPlan;
        }
        RealizationConstraints constraints = realizationPlan.realizationConstraints();
        List<String> softHints = new ArrayList<>(constraints.softSemanticHints());
        if (StringUtils.hasText(sourceOfficialObject)) {
            String hint = "SOURCE_OFFICIAL_OBJECT:" + sourceOfficialObject;
            if (!softHints.contains(hint)) {
                softHints.add(hint);
            }
        }
        if (StringUtils.hasText(sourceCategoryAnchor)) {
            String hint = "SOURCE_CATEGORY_ANCHOR:" + sourceCategoryAnchor;
            if (!softHints.contains(hint)) {
                softHints.add(hint);
            }
        }
        return new RealizationPlan(
                realizationPlan.simulationIntentProfile(),
                new RealizationConstraints(
                        constraints.brandExposurePolicy(),
                        constraints.mustExpressSemantics(),
                        softHints,
                        constraints.anchorPolicy(),
                        constraints.lengthPolicy(),
                        constraints.questionFormPolicy(),
                        constraints.fallbackFamily()
                ),
                realizationPlan.realizationSeed(),
                realizationPlan.debugSignature()
        );
    }

    private static String extractSourceOfficialObject(String sourceProbeText) {
        if (!StringUtils.hasText(sourceProbeText)) {
            return null;
        }
        String trimmed = sourceProbeText.trim();
        if (trimmed.contains("官网信息")) {
            return "官网信息";
        }
        if (trimmed.contains("官方信息")) {
            return "官方信息";
        }
        if (trimmed.contains("可信来源")) {
            return "可信来源";
        }
        if (trimmed.contains("来源信息")) {
            return "来源信息";
        }
        if (trimmed.contains("官网")) {
            return "官网";
        }
        if (trimmed.contains("官方")) {
            return "官方";
        }
        if (trimmed.contains("来源")) {
            return "来源";
        }
        return null;
    }

    private static String extractSourceCategoryAnchor(String sourceProbeText, String category) {
        if (!StringUtils.hasText(sourceProbeText) || !StringUtils.hasText(category)) {
            return null;
        }
        String trimmed = sourceProbeText.trim();
        return trimmed.contains(category.trim()) ? category.trim() : null;
    }

    private static DimensionVariables dimension(String dimensionId) {
        return new DimensionVariables(
                dimensionId,
                CssmHiAbstractStates.DIMENSION_NAMES.get(dimensionId),
                CssmHiAbstractStates.ABSTRACT_CODE_MAP.get(dimensionId),
                DIMENSION_VARIABLES.get(dimensionId)
        );
    }

    private static Map<String, String> translatedTags(Map<String, String> abstractTags) {
        LinkedHashMap<String, String> translated = new LinkedHashMap<>();
        for (String dimensionId : CssmHiAbstractStates.DIMENSION_IDS) {
            List<String> codes = CssmHiAbstractStates.ABSTRACT_CODE_MAP.get(dimensionId);
            List<String> variables = DIMENSION_VARIABLES.get(dimensionId);
            String code = abstractTags.get(dimensionId);
            int index = codes.indexOf(code);
            if (index < 0 || index >= variables.size()) {
                translated.put(dimensionId, code);
                continue;
            }
            translated.put(dimensionId, variables.get(index));
        }
        return translated;
    }

    private static Map<String, String> normalizeStateVector(Map<String, Object> raw) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (String dimensionId : CssmHiAbstractStates.DIMENSION_IDS) {
            String lowerKey = dimensionId.toLowerCase(Locale.ROOT);
            Object value = raw.containsKey(dimensionId) ? raw.get(dimensionId) : raw.get(lowerKey);
            if (!StringUtils.hasText(stringValue(value))) {
                throw new IllegalArgumentException("state_vector 缺少维度: " + dimensionId);
            }
            normalized.put(dimensionId, stringValue(value).trim());
        }
        return normalized;
    }

    private static ChatLanguageModel buildCreativeModel(Map<String, String> env) {
        String apiKey = firstNonBlank(
                System.getenv("V42_ARK_API_KEY"),
                env.get("V42_ARK_API_KEY"),
                System.getenv("VOLCENGINE_API_KEY"),
                env.get("VOLCENGINE_API_KEY")
        );
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未找到 Ark/Volcengine API key。");
        }
        String baseUrl = firstNonBlank(
                System.getenv("DEEPSEEK_BASE_URL"),
                env.get("DEEPSEEK_BASE_URL"),
                System.getenv("VOLCENGINE_BASE_URL"),
                env.get("VOLCENGINE_BASE_URL"),
                DEFAULT_ARK_BASE_URL
        );
        String modelName = resolveCreativeModelLabel(env);
        double temperature = resolveNode2RenderTemperature(env);
        return OpenAiChatModel.builder()
                .apiKey(apiKey.trim())
                .baseUrl(baseUrl.trim())
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(0)
                .build();
    }

    private static double resolveNode2RenderTemperature(Map<String, String> env) {
        String raw = firstNonBlank(
                System.getenv("V42_NODE2_RENDER_TEMPERATURE"),
                env.get("V42_NODE2_RENDER_TEMPERATURE"),
                System.getenv("APP_ENGINE_NODE2_PROBE_RENDERING_MODEL_TEMPERATURE"),
                env.get("APP_ENGINE_NODE2_PROBE_RENDERING_MODEL_TEMPERATURE")
        );
        if (!StringUtils.hasText(raw)) {
            return DEFAULT_NODE2_RENDER_TEMPERATURE;
        }
        try {
            return Math.max(0.0d, Math.min(1.0d, Double.parseDouble(raw.trim())));
        } catch (NumberFormatException ex) {
            return DEFAULT_NODE2_RENDER_TEMPERATURE;
        }
    }

    private static String resolveCreativeModelLabel(Map<String, String> env) {
        return firstNonBlank(
                System.getenv("V42_DEEPSEEK32_ENDPOINT_ID"),
                env.get("V42_DEEPSEEK32_ENDPOINT_ID"),
                System.getenv("DEEPSEEK_ENDPOINT_ID"),
                env.get("DEEPSEEK_ENDPOINT_ID"),
                System.getenv("DEEPSEEK_MODEL"),
                env.get("DEEPSEEK_MODEL"),
                DEFAULT_DEEPSEEK_MODEL
        ).trim();
    }

    private static String resolveQuotaModelLabel(Map<String, String> env) {
        return firstNonBlank(
                System.getenv("DEEPSEEK_MODEL"),
                env.get("DEEPSEEK_MODEL"),
                DEFAULT_DEEPSEEK_MODEL
        ).trim();
    }

    private static List<Map<String, Object>> readJsonl(Path path) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            rows.add(MAPPER.readValue(line, new TypeReference<>() {
            }));
        }
        return rows;
    }

    private static Map<String, String> loadEnv(Path path) throws IOException {
        LinkedHashMap<String, String> env = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return env;
        }
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            int idx = line.indexOf('=');
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            value = stripQuotes(value);
            env.put(key, value);
        }
        return env;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            String text = stringValue(item);
            if (StringUtils.hasText(text)) {
                result.add(text.trim());
            }
        }
        return List.copyOf(result);
    }

    private static String requiredString(Map<String, Object> row, String key) {
        String value = stringValue(row.get(key));
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("缺少字段: " + key);
        }
        return value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private static Map<String, Object> summarize(List<Map<String, Object>> rows) {
        LinkedHashMap<String, Integer> promptCounts = new LinkedHashMap<>();
        Map<String, Integer> bucketCounts = new TreeMap<>();
        int sampleCount = 0;
        int fallbackCount = 0;
        for (Map<String, Object> row : rows) {
            sampleCount++;
            String prompt = stringValue(row.get("new_probe_text")).trim();
            if (!prompt.isEmpty()) {
                promptCounts.merge(prompt, 1, Integer::sum);
            }
            String bucketType = stringValue(row.get("bucket_type")).trim();
            if (!bucketType.isEmpty()) {
                bucketCounts.merge(bucketType, 1, Integer::sum);
            }
            Object fallback = row.get("new_fallback_used");
            if (Boolean.TRUE.equals(fallback) || "true".equalsIgnoreCase(String.valueOf(fallback))) {
                fallbackCount++;
            }
        }
        int duplicatePromptCount = 0;
        int maxPromptRepeatCount = 0;
        for (Integer count : promptCounts.values()) {
            if (count != null && count > 1) {
                duplicatePromptCount += count - 1;
            }
            if (count != null && count > maxPromptRepeatCount) {
                maxPromptRepeatCount = count;
            }
        }
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("sampleCount", sampleCount);
        summary.put("fallbackCount", fallbackCount);
        summary.put("duplicatePromptCount", duplicatePromptCount);
        summary.put("maxPromptRepeatCount", maxPromptRepeatCount);
        summary.put("bucketCounts", bucketCounts);
        return summary;
    }

    private static void assertSummaryPasses(Map<String, Object> summary) {
        int fallbackCount = asInt(summary.get("fallbackCount"));
        if (fallbackCount > 0) {
            throw new IllegalStateException("Node2 live canary 失败：fallbackCount=" + fallbackCount);
        }
    }

    private static int asInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private record Args(Path input, Path output, Path summaryOutput, Path envFile, String promptAssetDir) {

        private static Args parse(String[] args) {
            Path input = null;
            Path output = null;
            Path summaryOutput = null;
            Path envFile = DEFAULT_ENV_FILE;
            String promptAssetDir = DEFAULT_PROMPT_ASSET_DIR;
            for (int index = 0; index < args.length; index++) {
                String arg = Objects.requireNonNullElse(args[index], "").trim();
                switch (arg) {
                    case "--input" -> input = Path.of(requireValue(args, ++index, "--input"));
                    case "--output" -> output = Path.of(requireValue(args, ++index, "--output"));
                    case "--summary-output" -> summaryOutput = Path.of(requireValue(args, ++index, "--summary-output"));
                    case "--env-file" -> envFile = Path.of(requireValue(args, ++index, "--env-file"));
                    case "--prompt-asset-dir" -> promptAssetDir = requireValue(args, ++index, "--prompt-asset-dir");
                    default -> throw new IllegalArgumentException("未知参数: " + arg);
                }
            }
            if (input == null || output == null) {
                throw new IllegalArgumentException("必须提供 --input 和 --output");
            }
            return new Args(input, output, summaryOutput, envFile, promptAssetDir);
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length || !StringUtils.hasText(args[index])) {
                throw new IllegalArgumentException(flag + " 缺少值");
            }
            return args[index];
        }
    }
}

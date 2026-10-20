package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetAware;
import com.geoprobe.ai.v42.support.V42PromptAssetDescriptor;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
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

public final class W7Node2PromptVersionCompareTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> GENERIC_COMPETITOR_PHRASES = List.of("这几个牌子", "这几家", "这几个里面", "这几个里");
    private static final List<String> OFFICIAL_INTENT_TOKENS = List.of("官网", "官方", "可信来源", "来源");
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

    private W7Node2PromptVersionCompareTool() {
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Map<String, String> env = loadEnv(parsed.envFile());
        VolcengineRewardQuotaGate.requireLiveExperimentAllowance(
                "W7Node2PromptVersionCompareTool",
                resolveQuotaModelLabel(env)
        );
        ChatLanguageModel creativeModel = buildCreativeModel(env);
        V42PromptAssetLoader promptAssetLoader = new V42PromptAssetLoader(
                new V42SecretAssetSettings(parsed.promptAssetDir(), true)
        );
        V42PromptExecutionSupport promptExecutionSupport = new V42PromptExecutionSupport(promptAssetLoader);
        ProbeRenderingService baselineService = new ProbeRenderingService(
                new VersionPinnedProbeRenderingAiService(creativeModel, promptExecutionSupport, parsed.baseVersion()),
                new ThreeLayerMappingEvaluator()
        );
        ProbeRenderingService candidateService = new ProbeRenderingService(
                new VersionPinnedProbeRenderingAiService(creativeModel, promptExecutionSupport, parsed.candidateVersion()),
                new ThreeLayerMappingEvaluator()
        );
        ProbeSimulationPlanningService planningService = new ProbeSimulationPlanningService();
        List<Map<String, Object>> rows = readJsonl(parsed.input());
        List<ComparisonRow> comparisons = new ArrayList<>();
        Files.createDirectories(parsed.output().getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(parsed.output(), StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                ComparisonRow comparison = compareRow(
                        row,
                        baselineService,
                        candidateService,
                        planningService
                );
                comparisons.add(comparison);
                writer.write(MAPPER.writeValueAsString(comparison.toMap()));
                writer.newLine();
            }
        }
        if (parsed.summaryOutput() != null) {
            Files.createDirectories(parsed.summaryOutput().getParent());
            Files.writeString(
                    parsed.summaryOutput(),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summarize(comparisons).toMap()),
                    StandardCharsets.UTF_8
            );
        }
        CompareSummary summary = summarize(comparisons);
        validateSummary(summary, parsed);
    }

    static CompareSummary summarize(List<ComparisonRow> rows) {
        int total = rows == null ? 0 : rows.size();
        int promptChangedCount = 0;
        int baselineFallbackCount = 0;
        int candidateFallbackCount = 0;
        int baselineRepairCount = 0;
        int candidateRepairCount = 0;
        int fallbackImprovedCount = 0;
        int fallbackRegressedCount = 0;
        int repairImprovedCount = 0;
        int repairRegressedCount = 0;
        int sourceExplicitCompetitorCount = 0;
        int baselineExplicitCompetitorRetainedCount = 0;
        int candidateExplicitCompetitorRetainedCount = 0;
        int baselineGenericCompetitorPhraseCount = 0;
        int candidateGenericCompetitorPhraseCount = 0;
        int sourceOfficialIntentCount = 0;
        int baselineOfficialIntentRetainedCount = 0;
        int candidateOfficialIntentRetainedCount = 0;
        LinkedHashMap<String, Integer> baselineRuleCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> candidateRuleCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> baselinePromptCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> candidatePromptCounts = new LinkedHashMap<>();
        for (ComparisonRow row : rows == null ? List.<ComparisonRow>of() : rows) {
            if (row == null) {
                continue;
            }
            if (!Objects.equals(row.baseline().renderedPrompt(), row.candidate().renderedPrompt())) {
                promptChangedCount++;
            }
            if (row.baseline().fallbackUsed()) {
                baselineFallbackCount++;
            }
            if (row.candidate().fallbackUsed()) {
                candidateFallbackCount++;
            }
            if (row.baseline().repairUsed()) {
                baselineRepairCount++;
            }
            if (row.candidate().repairUsed()) {
                candidateRepairCount++;
            }
            if (row.baseline().fallbackUsed() && !row.candidate().fallbackUsed()) {
                fallbackImprovedCount++;
            } else if (!row.baseline().fallbackUsed() && row.candidate().fallbackUsed()) {
                fallbackRegressedCount++;
            }
            if (row.baseline().repairUsed() && !row.candidate().repairUsed()) {
                repairImprovedCount++;
            } else if (!row.baseline().repairUsed() && row.candidate().repairUsed()) {
                repairRegressedCount++;
            }
            if (row.isPromptAssetComparable()) {
                if (row.sourceHasExplicitCompetitorMention()) {
                    sourceExplicitCompetitorCount++;
                }
                if (row.baselineRetainsExplicitCompetitor()) {
                    baselineExplicitCompetitorRetainedCount++;
                }
                if (row.candidateRetainsExplicitCompetitor()) {
                    candidateExplicitCompetitorRetainedCount++;
                }
                if (row.baselineUsesGenericCompetitorPhrase()) {
                    baselineGenericCompetitorPhraseCount++;
                }
                if (row.candidateUsesGenericCompetitorPhrase()) {
                    candidateGenericCompetitorPhraseCount++;
                }
                if (row.sourceHasOfficialIntent()) {
                    sourceOfficialIntentCount++;
                }
                if (row.baselineRetainsOfficialIntent()) {
                    baselineOfficialIntentRetainedCount++;
                }
                if (row.candidateRetainsOfficialIntent()) {
                    candidateOfficialIntentRetainedCount++;
                }
                mergePromptCount(baselinePromptCounts, row.baseline().renderedPrompt());
                mergePromptCount(candidatePromptCounts, row.candidate().renderedPrompt());
            }
            mergeRuleCount(baselineRuleCounts, row.baseline().keyRule());
            mergeRuleCount(candidateRuleCounts, row.candidate().keyRule());
        }
        return new CompareSummary(
                total,
                promptChangedCount,
                baselineFallbackCount,
                candidateFallbackCount,
                baselineRepairCount,
                candidateRepairCount,
                fallbackImprovedCount,
                fallbackRegressedCount,
                repairImprovedCount,
                repairRegressedCount,
                sourceExplicitCompetitorCount,
                baselineExplicitCompetitorRetainedCount,
                candidateExplicitCompetitorRetainedCount,
                baselineGenericCompetitorPhraseCount,
                candidateGenericCompetitorPhraseCount,
                sourceOfficialIntentCount,
                baselineOfficialIntentRetainedCount,
                candidateOfficialIntentRetainedCount,
                countDuplicatePrompts(baselinePromptCounts),
                countDuplicatePrompts(candidatePromptCounts),
                maxPromptRepeatCount(baselinePromptCounts),
                maxPromptRepeatCount(candidatePromptCounts),
                Map.copyOf(baselineRuleCounts),
                Map.copyOf(candidateRuleCounts)
        );
    }

    private static void validateSummary(CompareSummary summary, Args args) {
        if (args.maxCandidateFallbackCount() != null
                && summary.candidateFallbackCount() > args.maxCandidateFallbackCount()) {
            throw new IllegalStateException("候选版本 fallback 超门限: "
                    + summary.candidateFallbackCount() + " > " + args.maxCandidateFallbackCount());
        }
        if (args.maxCandidateRepairCount() != null
                && summary.candidateRepairCount() > args.maxCandidateRepairCount()) {
            throw new IllegalStateException("候选版本 repair 超门限: "
                    + summary.candidateRepairCount() + " > " + args.maxCandidateRepairCount());
        }
    }

    private static void mergeRuleCount(Map<String, Integer> counts, String rule) {
        if (!StringUtils.hasText(rule)) {
            return;
        }
        counts.merge(rule.trim(), 1, Integer::sum);
    }

    private static void mergePromptCount(Map<String, Integer> counts, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return;
        }
        counts.merge(prompt.trim(), 1, Integer::sum);
    }

    private static int countDuplicatePrompts(Map<String, Integer> counts) {
        int duplicates = 0;
        for (Integer value : counts.values()) {
            if (value != null && value > 1) {
                duplicates += value - 1;
            }
        }
        return duplicates;
    }

    private static int maxPromptRepeatCount(Map<String, Integer> counts) {
        int max = 0;
        for (Integer value : counts.values()) {
            if (value != null && value > max) {
                max = value;
            }
        }
        return max;
    }

    private static ComparisonRow compareRow(Map<String, Object> row,
                                            ProbeRenderingService baselineService,
                                            ProbeRenderingService candidateService,
                                            ProbeSimulationPlanningService planningService) {
        RenderContext context = buildRenderContext(row, planningService);
        RenderedProbe baseline = baselineService.renderOne("w7-node2-compare-baseline", context.pool(), List.of(), context.blueprint());
        RenderedProbe candidate = candidateService.renderOne("w7-node2-compare-candidate", context.pool(), List.of(), context.blueprint());
        return new ComparisonRow(
                requiredString(row, "probe_id"),
                context.brand(),
                context.category(),
                context.bucketType().name(),
                stringList(row.get("competitors")),
                textOrNull(row.get("probe_text")),
                snapshot(baseline),
                snapshot(candidate)
        );
    }

    private static RenderSnapshot snapshot(RenderedProbe renderedProbe) {
        Map<String, Object> rules = renderedProbe.triggeredRulesJson() == null ? Map.of() : renderedProbe.triggeredRulesJson();
        Map<String, Object> gateDecisionInput = rules.get("gateDecisionInput") instanceof Map<?, ?> map
                ? toStringObjectMap(map)
                : Map.of();
        String repairRule = textOrNull(rules.get("validationRepairApplied"));
        String fallbackRule = textOrNull(rules.get("fallbackTriggerRule"));
        String mechanicalHeuristicId = textOrNull(gateDecisionInput.get("mechanicalHeuristicId"));
        String awkwardHeuristicId = textOrNull(gateDecisionInput.get("awkwardHeuristicId"));
        return new RenderSnapshot(
                renderedProbe.renderedPrompt(),
                renderedProbe.renderReason(),
                renderedProbe.renderSource(),
                renderedProbe.renderFamily(),
                renderedProbe.fallbackUsed(),
                StringUtils.hasText(repairRule),
                repairRule,
                fallbackRule,
                mechanicalHeuristicId,
                awkwardHeuristicId,
                textOrNull(rules.get("promptAssetVersion"))
        );
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static RenderContext buildRenderContext(Map<String, Object> row,
                                                    ProbeSimulationPlanningService planningService) {
        String brand = requiredString(row, "brand");
        String category = requiredString(row, "category");
        List<String> competitors = stringList(row.get("competitors"));
        String sourceProbeText = textOrNull(row.get("probe_text"));
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
                W7Node2TopicProjectionHeuristics.deriveTopicProjection(sourceProbeText)
        );
        realizationPlan = augmentSourceContractHints(realizationPlan, sourceProbeText, category);
        ProbeBlueprint blueprint = new ProbeBlueprint(
                requiredString(row, "probe_id"),
                bucketType,
                abstractTags,
                translatedTags,
                sourceProbeText,
                ProbeStatus.PENDING,
                realizationPlan
        );
        return new RenderContext(brand, category, bucketType, pool, blueprint);
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
            translated.put(dimensionId, index < 0 || index >= variables.size() ? code : variables.get(index));
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
        String modelName = firstNonBlank(
                System.getenv("V42_DEEPSEEK32_ENDPOINT_ID"),
                env.get("V42_DEEPSEEK32_ENDPOINT_ID"),
                System.getenv("DEEPSEEK_ENDPOINT_ID"),
                env.get("DEEPSEEK_ENDPOINT_ID"),
                System.getenv("DEEPSEEK_MODEL"),
                env.get("DEEPSEEK_MODEL"),
                DEFAULT_DEEPSEEK_MODEL
        ).trim();
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

    private static String resolveQuotaModelLabel(Map<String, String> env) {
        return firstNonBlank(
                System.getenv("DEEPSEEK_MODEL"),
                env.get("DEEPSEEK_MODEL"),
                DEFAULT_DEEPSEEK_MODEL
        ).trim();
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
            env.put(line.substring(0, idx).trim(), stripQuotes(line.substring(idx + 1).trim()));
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
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
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

    private static String textOrNull(Object value) {
        String text = stringValue(value).trim();
        return text.isEmpty() ? null : text;
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

    public record RenderSnapshot(
            String renderedPrompt,
            String renderReason,
            String renderSource,
            String renderFamily,
            boolean fallbackUsed,
            boolean repairUsed,
            String repairRule,
            String fallbackRule,
            String mechanicalHeuristicId,
            String awkwardHeuristicId,
            String promptAssetVersion
    ) {
        String keyRule() {
            if (StringUtils.hasText(repairRule)) {
                return repairRule;
            }
            if (StringUtils.hasText(fallbackRule)) {
                return fallbackRule;
            }
            if (StringUtils.hasText(mechanicalHeuristicId)) {
                return mechanicalHeuristicId;
            }
            if (StringUtils.hasText(awkwardHeuristicId)) {
                return awkwardHeuristicId;
            }
            return null;
        }

        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("renderedPrompt", renderedPrompt);
            payload.put("renderReason", renderReason);
            payload.put("renderSource", renderSource);
            payload.put("renderFamily", renderFamily);
            payload.put("fallbackUsed", fallbackUsed);
            payload.put("repairUsed", repairUsed);
            payload.put("repairRule", repairRule == null ? "" : repairRule);
            payload.put("fallbackRule", fallbackRule == null ? "" : fallbackRule);
            payload.put("mechanicalHeuristicId", mechanicalHeuristicId == null ? "" : mechanicalHeuristicId);
            payload.put("awkwardHeuristicId", awkwardHeuristicId == null ? "" : awkwardHeuristicId);
            payload.put("promptAssetVersion", promptAssetVersion == null ? "" : promptAssetVersion);
            return Map.copyOf(payload);
        }
    }

    public record ComparisonRow(
            String probeId,
            String brand,
            String category,
            String bucketType,
            List<String> competitors,
            String sourceProbeText,
            RenderSnapshot baseline,
            RenderSnapshot candidate
    ) {
        public ComparisonRow {
            competitors = competitors == null ? List.of() : List.copyOf(competitors);
            sourceProbeText = sourceProbeText == null ? "" : sourceProbeText;
        }

        boolean isPromptAssetComparable() {
            return StringUtils.hasText(baseline.promptAssetVersion()) || StringUtils.hasText(candidate.promptAssetVersion());
        }

        boolean sourceHasExplicitCompetitorMention() {
            return containsAnyCompetitor(sourceProbeText);
        }

        boolean baselineRetainsExplicitCompetitor() {
            return sourceHasExplicitCompetitorMention() && containsAnyCompetitor(baseline.renderedPrompt());
        }

        boolean candidateRetainsExplicitCompetitor() {
            return sourceHasExplicitCompetitorMention() && containsAnyCompetitor(candidate.renderedPrompt());
        }

        boolean baselineUsesGenericCompetitorPhrase() {
            return containsGenericCompetitorPhrase(baseline.renderedPrompt());
        }

        boolean candidateUsesGenericCompetitorPhrase() {
            return containsGenericCompetitorPhrase(candidate.renderedPrompt());
        }

        boolean sourceHasOfficialIntent() {
            return W7Node2TopicProjectionHeuristics.hasOfficialIntent(sourceProbeText);
        }

        boolean baselineRetainsOfficialIntent() {
            return sourceHasOfficialIntent() && containsAnyToken(baseline.renderedPrompt(), OFFICIAL_INTENT_TOKENS);
        }

        boolean candidateRetainsOfficialIntent() {
            return sourceHasOfficialIntent() && containsAnyToken(candidate.renderedPrompt(), OFFICIAL_INTENT_TOKENS);
        }

        private boolean containsAnyCompetitor(String content) {
            if (!StringUtils.hasText(content)) {
                return false;
            }
            for (String competitor : competitors) {
                if (StringUtils.hasText(competitor) && content.contains(competitor.trim())) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsGenericCompetitorPhrase(String content) {
            return containsAnyToken(content, GENERIC_COMPETITOR_PHRASES);
        }

        private boolean containsAnyToken(String content, List<String> tokens) {
            if (!StringUtils.hasText(content)) {
                return false;
            }
            for (String token : tokens) {
                if (StringUtils.hasText(token) && content.contains(token)) {
                    return true;
                }
            }
            return false;
        }

        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("probeId", probeId);
            payload.put("brand", brand);
            payload.put("category", category);
            payload.put("bucketType", bucketType);
            payload.put("competitors", competitors);
            payload.put("sourceProbeText", sourceProbeText);
            payload.put("promptChanged", !Objects.equals(baseline.renderedPrompt(), candidate.renderedPrompt()));
            payload.put("baseline", baseline.toMap());
            payload.put("candidate", candidate.toMap());
            return payload;
        }
    }

    public record CompareSummary(
            int sampleCount,
            int promptChangedCount,
            int baselineFallbackCount,
            int candidateFallbackCount,
            int baselineRepairCount,
            int candidateRepairCount,
            int fallbackImprovedCount,
            int fallbackRegressedCount,
            int repairImprovedCount,
            int repairRegressedCount,
            int sourceExplicitCompetitorCount,
            int baselineExplicitCompetitorRetainedCount,
            int candidateExplicitCompetitorRetainedCount,
            int baselineGenericCompetitorPhraseCount,
            int candidateGenericCompetitorPhraseCount,
            int sourceOfficialIntentCount,
            int baselineOfficialIntentRetainedCount,
            int candidateOfficialIntentRetainedCount,
            int baselineDuplicatePromptCount,
            int candidateDuplicatePromptCount,
            int baselineMaxPromptRepeatCount,
            int candidateMaxPromptRepeatCount,
            Map<String, Integer> baselineRuleCounts,
            Map<String, Integer> candidateRuleCounts
    ) {
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("sampleCount", sampleCount);
            payload.put("promptChangedCount", promptChangedCount);
            payload.put("baselineFallbackCount", baselineFallbackCount);
            payload.put("candidateFallbackCount", candidateFallbackCount);
            payload.put("baselineRepairCount", baselineRepairCount);
            payload.put("candidateRepairCount", candidateRepairCount);
            payload.put("fallbackImprovedCount", fallbackImprovedCount);
            payload.put("fallbackRegressedCount", fallbackRegressedCount);
            payload.put("repairImprovedCount", repairImprovedCount);
            payload.put("repairRegressedCount", repairRegressedCount);
            payload.put("sourceExplicitCompetitorCount", sourceExplicitCompetitorCount);
            payload.put("baselineExplicitCompetitorRetainedCount", baselineExplicitCompetitorRetainedCount);
            payload.put("candidateExplicitCompetitorRetainedCount", candidateExplicitCompetitorRetainedCount);
            payload.put("baselineGenericCompetitorPhraseCount", baselineGenericCompetitorPhraseCount);
            payload.put("candidateGenericCompetitorPhraseCount", candidateGenericCompetitorPhraseCount);
            payload.put("sourceOfficialIntentCount", sourceOfficialIntentCount);
            payload.put("baselineOfficialIntentRetainedCount", baselineOfficialIntentRetainedCount);
            payload.put("candidateOfficialIntentRetainedCount", candidateOfficialIntentRetainedCount);
            payload.put("baselineDuplicatePromptCount", baselineDuplicatePromptCount);
            payload.put("candidateDuplicatePromptCount", candidateDuplicatePromptCount);
            payload.put("baselineMaxPromptRepeatCount", baselineMaxPromptRepeatCount);
            payload.put("candidateMaxPromptRepeatCount", candidateMaxPromptRepeatCount);
            payload.put("baselineRuleCounts", baselineRuleCounts);
            payload.put("candidateRuleCounts", candidateRuleCounts);
            return Map.copyOf(payload);
        }
    }

    private record RenderContext(
            String brand,
            String category,
            BucketType bucketType,
            CategoryVariablePool pool,
            ProbeBlueprint blueprint
    ) {
    }

    private record Args(
            Path input,
            Path output,
            Path summaryOutput,
            Path envFile,
            String promptAssetDir,
            String baseVersion,
            String candidateVersion,
            Integer maxCandidateFallbackCount,
            Integer maxCandidateRepairCount
    ) {

        private static Args parse(String[] args) {
            Path input = null;
            Path output = null;
            Path summaryOutput = null;
            Path envFile = DEFAULT_ENV_FILE;
            String promptAssetDir = DEFAULT_PROMPT_ASSET_DIR;
            String baseVersion = null;
            String candidateVersion = null;
            Integer maxCandidateFallbackCount = null;
            Integer maxCandidateRepairCount = null;
            for (int index = 0; index < args.length; index++) {
                String arg = Objects.requireNonNullElse(args[index], "").trim();
                switch (arg) {
                    case "--input" -> input = Path.of(requireValue(args, ++index, "--input"));
                    case "--output" -> output = Path.of(requireValue(args, ++index, "--output"));
                    case "--summary-output" -> summaryOutput = Path.of(requireValue(args, ++index, "--summary-output"));
                    case "--env-file" -> envFile = Path.of(requireValue(args, ++index, "--env-file"));
                    case "--prompt-asset-dir" -> promptAssetDir = requireValue(args, ++index, "--prompt-asset-dir");
                    case "--base-version" -> baseVersion = requireValue(args, ++index, "--base-version");
                    case "--candidate-version" -> candidateVersion = requireValue(args, ++index, "--candidate-version");
                    case "--max-candidate-fallback-count" ->
                            maxCandidateFallbackCount = Integer.parseInt(requireValue(args, ++index, "--max-candidate-fallback-count"));
                    case "--max-candidate-repair-count" ->
                            maxCandidateRepairCount = Integer.parseInt(requireValue(args, ++index, "--max-candidate-repair-count"));
                    default -> throw new IllegalArgumentException("未知参数: " + arg);
                }
            }
            if (input == null || output == null || !StringUtils.hasText(baseVersion)) {
                throw new IllegalArgumentException("必须提供 --input、--output、--base-version");
            }
            return new Args(
                    input,
                    output,
                    summaryOutput,
                    envFile,
                    promptAssetDir,
                    baseVersion,
                    candidateVersion,
                    maxCandidateFallbackCount,
                    maxCandidateRepairCount
            );
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length || !StringUtils.hasText(args[index])) {
                throw new IllegalArgumentException(flag + " 缺少值");
            }
            return args[index];
        }
    }

    private static final class VersionPinnedProbeRenderingAiService implements ProbeRenderingAiService, V42PromptAssetAware {

        private final ChatLanguageModel model;
        private final V42PromptExecutionSupport support;
        private final String version;

        private VersionPinnedProbeRenderingAiService(ChatLanguageModel model,
                                                     V42PromptExecutionSupport support,
                                                     String version) {
            this.model = model;
            this.support = support;
            this.version = StringUtils.hasText(version) ? version.trim() : null;
        }

        @Override
        public String renderProbe(String brandName,
                                  String category,
                                  String competitors,
                                  String categoryAnchors,
                                  String brandInjectionPolicy,
                                  String simulationProfile,
                                  String mustSemantics,
                                  String softHints,
                                  String behaviorRules) {
            Map<String, String> variables = Map.of(
                    "brandName", normalize(brandName),
                    "category", normalize(category),
                    "competitors", normalize(competitors),
                    "categoryAnchors", normalize(categoryAnchors),
                    "brandInjectionPolicy", normalize(brandInjectionPolicy),
                    "simulationProfile", normalize(simulationProfile),
                    "mustSemantics", normalize(mustSemantics),
                    "softHints", normalize(softHints),
                    "behaviorRules", normalize(behaviorRules)
            );
            if (version == null) {
                return support.execute(model, V42PromptAssetKey.PROBE_RENDERING, variables);
            }
            return support.executeVersion(model, V42PromptAssetKey.PROBE_RENDERING, version, variables);
        }

        @Override
        public V42PromptAssetDescriptor describePromptAsset(V42PromptAssetKey assetKey) {
            if (version == null) {
                return support.describe(assetKey);
            }
            return support.describeVersion(assetKey, version);
        }

        private String normalize(String value) {
            return value == null ? "" : value;
        }
    }
}

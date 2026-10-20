package com.geoprobe.service.shared;

import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LightConeV43TopicStateProjectionSupport {

    public static final String PROJECTION_VERSION = "light_cone_topic_projection_v4_3_r3a";
    public static final String PROJECTION_HINT_KEY = "topicStateProjections";
    public static final String PROJECTION_SUMMARY_HINT_KEY = "topicStateProjectionSummary";
    public static final String SELECTION_MODE = "topic_quota_weighted_selector_r3b";

    private static final List<String> DIMENSION_IDS = List.of("D1", "D2", "D3", "D4", "D5", "D6");

    private static final List<String> BRAND_CHOICE_KEYWORDS = List.of(
            "选择", "推荐", "对比", "比较", "哪个好", "值不值", "值得买", "替代", "先看谁", "怎么选"
    );
    private static final List<String> RISK_OBJECTION_KEYWORDS = List.of(
            "风险", "劝退", "问题", "避坑", "翻车", "争议", "异议", "缺点", "投诉", "不稳", "靠谱吗",
            "靠谱", "噱头", "掉连"
    );
    private static final List<String> USAGE_SCENARIO_KEYWORDS = List.of(
            "场景", "适合", "家用", "办公室", "租房", "宿舍", "通勤", "送礼", "长期用", "日常"
    );
    private static final List<String> QUALITY_EVIDENCE_KEYWORDS = List.of(
            "材质", "质量", "耐用", "安装", "成分", "口感", "做工", "效果", "参数", "性能", "证据",
            "官网", "第三方", "承重", "口碑", "连接", "稳定", "延迟", "掉连", "切换", "双模", "无线",
            "dpi", "实用", "调节", "功能", "防水", "静音"
    );
    private static final List<String> QUALITY_EVIDENCE_DECISIVE_KEYWORDS = List.of(
            "官网", "第三方", "承重", "材质", "做工", "证据", "参数", "性能", "耐用", "连接", "稳定",
            "延迟", "掉连", "切换", "dpi", "实用", "防水", "静音"
    );
    private static final int TOPIC_DESCRIPTOR_KEYWORD_WEIGHT = 3;
    private static final int PROMPT_KEYWORD_WEIGHT = 1;
    private static final int DECISIVE_EVIDENCE_BONUS_WEIGHT = 2;

    private LightConeV43TopicStateProjectionSupport() {
    }

    public static ProjectionEnvelope buildDraftProjectionEnvelope(List<BrandSpaceTopicPayload> topics,
                                                                  List<BrandSpacePromptPayload> prompts,
                                                                  String lifecycleStage) {
        return buildProjectionEnvelope(buildDraftSeeds(topics, prompts), lifecycleStage);
    }

    public static ProjectionEnvelope buildConfigProjectionEnvelope(BrandSpaceConfigView configView,
                                                                   String lifecycleStage) {
        return buildProjectionEnvelope(buildConfigSeeds(configView), lifecycleStage);
    }

    public static Map<String, Object> extractExtensibleSignalPayload(Map<String, Object> businessHints) {
        ProjectionRuntimeContext runtimeContext = resolveRuntimeContext(businessHints);
        if (!runtimeContext.active()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                PROJECTION_HINT_KEY,
                runtimeContext.projections().stream().map(TopicStateProjection::toMap).toList()
        );
        payload.put(PROJECTION_SUMMARY_HINT_KEY, runtimeContext.summary());
        return Map.copyOf(payload);
    }

    public static ProjectionRuntimeContext resolveRuntimeContext(Map<String, Object> signalsOrHints) {
        Map<String, Object> root = readObjectMap(signalsOrHints);
        Map<String, Object> nestedHints = readObjectMap(root.get("frontDeskBusinessHints"));

        List<TopicStateProjection> projections = readTopicStateProjections(root.get(PROJECTION_HINT_KEY));
        if (projections.isEmpty()) {
            projections = readTopicStateProjections(nestedHints.get(PROJECTION_HINT_KEY));
        }
        if (projections.isEmpty()) {
            return ProjectionRuntimeContext.inactive();
        }

        Map<String, Object> summary = readObjectMap(root.get(PROJECTION_SUMMARY_HINT_KEY));
        if (summary.isEmpty()) {
            summary = readObjectMap(nestedHints.get(PROJECTION_SUMMARY_HINT_KEY));
        }
        if (summary.isEmpty()) {
            summary = buildSummary(projections, null, sumEnabledPromptCount(projections));
        }
        return new ProjectionRuntimeContext(
                List.copyOf(projections),
                immutableObjectMap(summary)
        );
    }

    private static ProjectionEnvelope buildProjectionEnvelope(List<TopicSeed> seeds, String lifecycleStage) {
        if (seeds.isEmpty()) {
            return ProjectionEnvelope.empty();
        }
        List<TopicStateProjection> projections = new ArrayList<>();
        int enabledPromptCount = 0;
        for (TopicSeed seed : seeds) {
            if (!seed.enabled()) {
                continue;
            }
            TopicStateProjection projection = buildProjection(seed, lifecycleStage);
            projections.add(projection);
            enabledPromptCount += seed.enabledPromptCount();
        }
        if (projections.isEmpty()) {
            return ProjectionEnvelope.empty();
        }
        return new ProjectionEnvelope(
                projections.stream().map(TopicStateProjection::toMap).toList(),
                buildSummary(projections, lifecycleStage, enabledPromptCount)
        );
    }

    private static TopicStateProjection buildProjection(TopicSeed seed, String lifecycleStage) {
        KeywordDecision decision = resolveKeywordDecision(seed);
        String sourceType = normalizeSourceType(seed.source());
        String topicId = stableTopicId(seed.rawTopicId(), seed.topicLabel(), sourceType);
        return new TopicStateProjection(
                topicId,
                PROJECTION_VERSION,
                seed.topicLabel(),
                decision.problemSpaceBucket(),
                buildDimensionWeights(decision.problemSpaceBucket()),
                buildStateBias(decision.problemSpaceBucket(), decision.matchedKeywords()),
                Math.max(1, Math.min(3, seed.enabledPromptCount() <= 0 ? 1 : seed.enabledPromptCount())),
                Math.max(1, Math.min(2, seed.enabledPromptCount() <= 0 ? 1 : seed.enabledPromptCount())),
                resolveFreshnessBoost(sourceType, lifecycleStage),
                resolveCoverageDebtBoost(seed.enabledPromptCount(), lifecycleStage),
                sourceType,
                buildTopicGroundedSignals(seed, decision, sourceType),
                buildProjectionTrace(seed, decision, lifecycleStage)
        );
    }

    private static Map<String, Double> buildDimensionWeights(String problemSpaceBucket) {
        LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        switch (problemSpaceBucket) {
            case "brand_choice" -> {
                weights.put("D1", 0.60d);
                weights.put("D2", 0.90d);
                weights.put("D3", 0.75d);
                weights.put("D4", 0.45d);
                weights.put("D5", 0.70d);
                weights.put("D6", 0.55d);
            }
            case "risk_objection" -> {
                weights.put("D1", 0.85d);
                weights.put("D2", 0.60d);
                weights.put("D3", 0.65d);
                weights.put("D4", 0.50d);
                weights.put("D5", 0.70d);
                weights.put("D6", 0.70d);
            }
            case "usage_scenario" -> {
                weights.put("D1", 0.45d);
                weights.put("D2", 0.75d);
                weights.put("D3", 0.70d);
                weights.put("D4", 0.40d);
                weights.put("D5", 0.55d);
                weights.put("D6", 0.55d);
            }
            case "quality_evidence" -> {
                weights.put("D1", 0.80d);
                weights.put("D2", 0.55d);
                weights.put("D3", 0.60d);
                weights.put("D4", 0.45d);
                weights.put("D5", 0.45d);
                weights.put("D6", 0.80d);
            }
            default -> {
                weights.put("D1", 0.55d);
                weights.put("D2", 0.60d);
                weights.put("D3", 0.55d);
                weights.put("D4", 0.45d);
                weights.put("D5", 0.50d);
                weights.put("D6", 0.55d);
            }
        }
        return Map.copyOf(weights);
    }

    private static List<StateBiasRule> buildStateBias(String problemSpaceBucket, List<String> matchedKeywords) {
        List<StateBiasRule> rules = new ArrayList<>();
        switch (problemSpaceBucket) {
            case "brand_choice" -> {
                rules.add(rule(Map.of("D2", "resource_acquisition"), 0.60d, "品牌选择类 topic 优先资源获取态"));
                rules.add(rule(Map.of("D5", "anticipatory_constraint"), 0.35d, "品牌选择通常发生在短期比较窗口"));
                rules.add(rule(Map.of("D6", "recognition"), 0.20d, "默认兼容搜索式入口"));
            }
            case "risk_objection" -> {
                rules.add(rule(Map.of("D1", "verification"), 0.70d, "风险异议类 topic 优先核验态"));
                rules.add(rule(Map.of("D5", "reflective"), 0.35d, "风险判断更偏复核与回看"));
                rules.add(rule(Map.of("D6", "specification"), 0.30d, "风险判断优先结构化提问"));
                rules.add(rule(Map.of("D4", "severely_depleted"), -0.20d, "高风险核验默认抑制极度耗竭态"));
            }
            case "usage_scenario" -> {
                rules.add(rule(Map.of("D2", "instructional_active"), 0.50d, "场景适配类 topic 优先行动性问法"));
                rules.add(rule(Map.of("D3", "subsistence_protection"), 0.30d, "场景匹配优先稳妥可用"));
                rules.add(rule(Map.of("D6", "recognition"), 0.20d, "场景问法默认兼容搜索式入口"));
            }
            case "quality_evidence" -> {
                rules.add(rule(Map.of("D1", "verification"), 0.60d, "质量证据类 topic 优先核验态"));
                rules.add(rule(Map.of("D6", "specification"), 0.45d, "质量证据类 topic 优先结构化提问"));
                rules.add(rule(Map.of("D5", "reflective"), 0.20d, "质量判断默认偏复核"));
            }
            default -> {
                rules.add(rule(Map.of("D1", "exploration"), 0.35d, "泛可见性 topic 默认先探索"));
                rules.add(rule(Map.of("D6", "recognition"), 0.20d, "泛可见性 topic 默认兼容搜索式入口"));
            }
        }
        if (matchedKeywords.stream().anyMatch(keyword -> keyword.contains("对比") || keyword.contains("比较") || keyword.contains("哪个好"))) {
            rules.add(rule(Map.of("D2", "resource_acquisition", "D6", "specification"), 0.25d, "对比类表述优先直接比较"));
        }
        return List.copyOf(rules);
    }

    private static StateBiasRule rule(Map<String, String> matchTags, double biasScore, String reason) {
        return new StateBiasRule(matchTags, biasScore, reason);
    }

    private static Map<String, Object> buildProjectionTrace(TopicSeed seed,
                                                            KeywordDecision decision,
                                                            String lifecycleStage) {
        LinkedHashMap<String, Object> trace = new LinkedHashMap<>();
        trace.put("derivationMode", "heuristic_topic_compiler_r3a");
        if (StringUtils.hasText(seed.rawTopicId())) {
            trace.put("rawTopicId", seed.rawTopicId());
        }
        trace.put("enabledPromptCount", seed.enabledPromptCount());
        trace.put("linkedPromptRefs", seed.promptRefs());
        trace.put("samplePromptTexts", seed.enabledPromptTexts().stream().limit(3).toList());
        trace.put("matchedKeywords", decision.matchedKeywords());
        if (StringUtils.hasText(lifecycleStage)) {
            trace.put("lifecycleStage", lifecycleStage.trim());
        }
        if (StringUtils.hasText(seed.description())) {
            trace.put("topicDescription", seed.description());
        }
        return immutableObjectMap(trace);
    }

    private static List<GroundedContextSignal> buildTopicGroundedSignals(TopicSeed seed,
                                                                         KeywordDecision decision,
                                                                         String sourceType) {
        LinkedHashMap<String, GroundedContextSignal> signals = new LinkedHashMap<>();
        String topicLabel = defaultString(normalizeOptional(seed.topicLabel()), "未命名 topic");
        String topicDescription = normalizeOptional(seed.description());
        String topicDetailCue = normalizeTopicDescriptionCue(topicDescription);
        String compiledSource = "topic_projection_compiler";
        String evidence = "topic_contract";
        Map<String, Object> metadata = Map.of(
                "topicLabel", topicLabel,
                "problemSpaceBucket", decision.problemSpaceBucket(),
                "sourceType", normalizeSourceType(sourceType)
        );

        addTopicSignal(signals, new GroundedContextSignal(
                MinusOneContextSlots.DESCRIPTIVE_CUE,
                topicLabel,
                compiledSource,
                evidence,
                0.85d,
                List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                metadata
        ));
        if (StringUtils.hasText(topicDetailCue) && !topicLabel.equals(topicDetailCue)) {
            addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.DESCRIPTIVE_CUE,
                    topicDetailCue,
                    compiledSource,
                    evidence,
                    0.83d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
        }

        switch (decision.problemSpaceBucket()) {
            case "usage_scenario" -> addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.USAGE_SCENARIO,
                    topicLabel,
                    compiledSource,
                    evidence,
                    0.90d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
            case "quality_evidence" -> addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.BRAND_EVIDENCE,
                    firstNonBlank(topicDetailCue, topicDescription, topicLabel),
                    compiledSource,
                    evidence,
                    0.90d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
            default -> {
            }
        }

        addTopicSignal(signals, new GroundedContextSignal(
                MinusOneContextSlots.DECISION_FACTOR,
                topicLabel,
                compiledSource,
                evidence,
                0.88d,
                List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                metadata
        ));
        if (StringUtils.hasText(topicDetailCue) && !topicLabel.equals(topicDetailCue)) {
            addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.DECISION_FACTOR,
                    topicDetailCue,
                    compiledSource,
                    evidence,
                    0.86d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
        }

        if (containsAny(topicLabel + "\n" + defaultString(topicDescription), USAGE_SCENARIO_KEYWORDS)) {
            addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.USAGE_SCENARIO,
                    topicLabel,
                    compiledSource,
                    evidence,
                    0.82d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
        }
        if (containsAny(topicLabel + "\n" + defaultString(topicDescription), QUALITY_EVIDENCE_DECISIVE_KEYWORDS)) {
            addTopicSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.BRAND_EVIDENCE,
                    firstNonBlank(topicDetailCue, topicDescription, topicLabel),
                    compiledSource,
                    evidence,
                    0.80d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY),
                    metadata
            ));
        }
        return signals.isEmpty() ? List.of() : List.copyOf(signals.values());
    }

    private static String normalizeTopicDescriptionCue(String topicDescription) {
        String normalized = normalizeOptional(topicDescription);
        if (normalized == null) {
            return null;
        }
        String compact = normalized
                .replaceFirst("^处理", "")
                .replaceFirst("[。！？；，、]+$", "")
                .trim();
        return StringUtils.hasText(compact) ? compact : null;
    }

    private static void addTopicSignal(Map<String, GroundedContextSignal> collector, GroundedContextSignal signal) {
        if (collector == null || signal == null || !StringUtils.hasText(signal.value())) {
            return;
        }
        collector.putIfAbsent(signal.slot() + "|" + signal.value(), signal);
    }

    private static boolean containsAny(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static double resolveFreshnessBoost(String sourceType, String lifecycleStage) {
        double base = switch (sourceType) {
            case "user_added" -> 0.75d;
            case "user_confirmed" -> 0.65d;
            default -> 0.40d;
        };
        if ("draft_preview".equals(normalizeOptional(lifecycleStage))
                || "create_initial_run".equals(normalizeOptional(lifecycleStage))) {
            base += 0.10d;
        }
        return clamp(base, 0.0d, 1.0d);
    }

    private static double resolveCoverageDebtBoost(int enabledPromptCount, String lifecycleStage) {
        double base = enabledPromptCount >= 2 ? 0.35d : 0.25d;
        if ("active_scheduled_run".equals(normalizeOptional(lifecycleStage))) {
            base += 0.05d;
        }
        return clamp(base, 0.0d, 1.0d);
    }

    private static KeywordDecision resolveKeywordDecision(TopicSeed seed) {
        String descriptorText = (seed.topicLabel() + "\n" + defaultString(seed.description())).toLowerCase(Locale.ROOT);
        String promptText = String.join("\n", seed.enabledPromptTexts()).toLowerCase(Locale.ROOT);
        String keywordText = (descriptorText + "\n" + promptText).trim();

        List<String> brandChoiceHits = collectKeywordHits(keywordText, BRAND_CHOICE_KEYWORDS);
        List<String> riskHits = collectKeywordHits(keywordText, RISK_OBJECTION_KEYWORDS);
        List<String> scenarioHits = collectKeywordHits(keywordText, USAGE_SCENARIO_KEYWORDS);
        List<String> qualityHits = collectKeywordHits(keywordText, QUALITY_EVIDENCE_KEYWORDS);

        int brandChoiceScore = scoreBucket(
                collectKeywordHits(descriptorText, BRAND_CHOICE_KEYWORDS),
                collectKeywordHits(promptText, BRAND_CHOICE_KEYWORDS)
        );
        int riskScore = scoreBucket(
                collectKeywordHits(descriptorText, RISK_OBJECTION_KEYWORDS),
                collectKeywordHits(promptText, RISK_OBJECTION_KEYWORDS)
        );
        int scenarioScore = scoreBucket(
                collectKeywordHits(descriptorText, USAGE_SCENARIO_KEYWORDS),
                collectKeywordHits(promptText, USAGE_SCENARIO_KEYWORDS)
        );
        List<String> qualityDescriptorHits = collectKeywordHits(descriptorText, QUALITY_EVIDENCE_KEYWORDS);
        List<String> qualityPromptHits = collectKeywordHits(promptText, QUALITY_EVIDENCE_KEYWORDS);
        List<String> decisiveEvidenceHits = collectKeywordHits(keywordText, QUALITY_EVIDENCE_DECISIVE_KEYWORDS);
        int qualityScore = scoreBucket(qualityDescriptorHits, qualityPromptHits)
                + decisiveEvidenceHits.size() * DECISIVE_EVIDENCE_BONUS_WEIGHT;

        if (qualityScore > 0
                && !decisiveEvidenceHits.isEmpty()
                && qualityScore >= brandChoiceScore
                && qualityScore >= scenarioScore
                && qualityScore >= riskScore) {
            return new KeywordDecision("quality_evidence", qualityHits);
        }
        if (riskScore > 0 && riskScore >= brandChoiceScore && riskScore >= scenarioScore && riskScore >= qualityScore) {
            return new KeywordDecision("risk_objection", riskHits);
        }
        if (brandChoiceScore > 0 && brandChoiceScore >= scenarioScore && brandChoiceScore >= qualityScore) {
            return new KeywordDecision("brand_choice", brandChoiceHits);
        }
        if (scenarioScore > 0 && scenarioScore >= qualityScore) {
            return new KeywordDecision("usage_scenario", scenarioHits);
        }
        if (qualityScore > 0) {
            return new KeywordDecision("quality_evidence", qualityHits);
        }
        return new KeywordDecision("general_visibility", List.of());
    }

    private static int scoreBucket(List<String> descriptorHits, List<String> promptHits) {
        int descriptorScore = descriptorHits == null ? 0 : descriptorHits.size() * TOPIC_DESCRIPTOR_KEYWORD_WEIGHT;
        int promptScore = promptHits == null ? 0 : promptHits.size() * PROMPT_KEYWORD_WEIGHT;
        return descriptorScore + promptScore;
    }

    private static List<String> collectKeywordHits(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> hits = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                hits.add(keyword);
            }
        }
        return List.copyOf(hits);
    }

    private static Map<String, Object> buildSummary(List<TopicStateProjection> projections,
                                                    String lifecycleStage,
                                                    int enabledPromptCount) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectionVersion", PROJECTION_VERSION);
        summary.put("selectionMode", SELECTION_MODE);
        summary.put("activeTopicCount", projections.size());
        summary.put("canonicalTopicLabels", projections.stream().map(TopicStateProjection::canonicalTopicLabel).toList());
        summary.put("problemSpaceBuckets", projections.stream()
                .map(TopicStateProjection::problemSpaceBucket)
                .distinct()
                .toList());
        summary.put("sourceTypeCounts", countSourceTypes(projections));
        summary.put("enabledPromptCount", Math.max(0, enabledPromptCount));
        if (StringUtils.hasText(lifecycleStage)) {
            summary.put("lifecycleStage", lifecycleStage.trim());
        }
        return Map.copyOf(summary);
    }

    private static Map<String, Integer> countSourceTypes(List<TopicStateProjection> projections) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (TopicStateProjection projection : projections) {
            counts.merge(projection.sourceType(), 1, Integer::sum);
        }
        return counts.isEmpty() ? Map.of() : Map.copyOf(counts);
    }

    private static int sumEnabledPromptCount(List<TopicStateProjection> projections) {
        return projections.stream().map(TopicStateProjection::coverageFloor).reduce(0, Integer::sum);
    }

    private static List<TopicSeed> buildDraftSeeds(List<BrandSpaceTopicPayload> topics,
                                                   List<BrandSpacePromptPayload> prompts) {
        Map<String, List<String>> promptTextsByTopic = new LinkedHashMap<>();
        Map<String, List<String>> promptRefsByTopic = new LinkedHashMap<>();
        if (prompts != null) {
            for (BrandSpacePromptPayload prompt : prompts) {
                if (prompt == null || !Boolean.TRUE.equals(prompt.getEnabled()) || !StringUtils.hasText(prompt.getText())) {
                    continue;
                }
                String topicKey = normalizeOptional(prompt.getTopicTempId());
                if (topicKey == null) {
                    continue;
                }
                promptTextsByTopic.computeIfAbsent(topicKey, ignored -> new ArrayList<>()).add(prompt.getText().trim());
                String ref = normalizeOptional(prompt.getTempId());
                if (ref != null) {
                    promptRefsByTopic.computeIfAbsent(topicKey, ignored -> new ArrayList<>()).add(ref);
                }
            }
        }
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<TopicSeed> seeds = new ArrayList<>();
        for (BrandSpaceTopicPayload topic : topics) {
            if (topic == null || !Boolean.TRUE.equals(topic.getEnabled()) || !StringUtils.hasText(topic.getName())) {
                continue;
            }
            String topicKey = normalizeOptional(topic.getTempId());
            List<String> promptTexts = topicKey == null
                    ? List.of()
                    : List.copyOf(promptTextsByTopic.getOrDefault(topicKey, List.of()));
            List<String> promptRefs = topicKey == null
                    ? List.of()
                    : List.copyOf(promptRefsByTopic.getOrDefault(topicKey, List.of()));
            seeds.add(new TopicSeed(
                    topicKey,
                    topic.getName().trim(),
                    normalizeOptional(topic.getDescription()),
                    normalizeOptional(topic.getSource()),
                    true,
                    promptTexts.size(),
                    promptTexts,
                    promptRefs
            ));
        }
        return List.copyOf(seeds);
    }

    private static List<TopicSeed> buildConfigSeeds(BrandSpaceConfigView configView) {
        if (configView == null || configView.getTopics() == null || configView.getTopics().isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> promptTextsByTopic = new LinkedHashMap<>();
        Map<String, List<String>> promptRefsByTopic = new LinkedHashMap<>();
        if (configView.getPrompts() != null) {
            for (BrandSpaceConfigView.PromptView prompt : configView.getPrompts()) {
                if (prompt == null || !Boolean.TRUE.equals(prompt.getEnabled()) || !StringUtils.hasText(prompt.getText())) {
                    continue;
                }
                String topicKey = normalizeOptional(prompt.getTopicId());
                if (topicKey == null) {
                    continue;
                }
                promptTextsByTopic.computeIfAbsent(topicKey, ignored -> new ArrayList<>()).add(prompt.getText().trim());
                String promptId = normalizeOptional(prompt.getPromptId());
                if (promptId != null) {
                    promptRefsByTopic.computeIfAbsent(topicKey, ignored -> new ArrayList<>()).add(promptId);
                }
            }
        }
        List<TopicSeed> seeds = new ArrayList<>();
        for (BrandSpaceConfigView.TopicView topic : configView.getTopics()) {
            if (topic == null || !Boolean.TRUE.equals(topic.getEnabled()) || !StringUtils.hasText(topic.getName())) {
                continue;
            }
            String topicKey = normalizeOptional(topic.getTopicId());
            List<String> promptTexts = topicKey == null
                    ? List.of()
                    : List.copyOf(promptTextsByTopic.getOrDefault(topicKey, List.of()));
            List<String> promptRefs = topicKey == null
                    ? List.of()
                    : List.copyOf(promptRefsByTopic.getOrDefault(topicKey, List.of()));
            seeds.add(new TopicSeed(
                    topicKey,
                    topic.getName().trim(),
                    normalizeOptional(topic.getDescription()),
                    normalizeOptional(topic.getSource()),
                    true,
                    promptTexts.size(),
                    promptTexts,
                    promptRefs
            ));
        }
        return List.copyOf(seeds);
    }

    private static List<TopicStateProjection> readTopicStateProjections(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<TopicStateProjection> projections = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> projectionMap = readObjectMap(item);
            if (projectionMap.isEmpty()) {
                continue;
            }
            projections.add(new TopicStateProjection(
                    stableTopicId(
                            coerceText(projectionMap.get("topicId")),
                            coerceText(projectionMap.get("canonicalTopicLabel")),
                            normalizeSourceType(coerceText(projectionMap.get("sourceType")))
                    ),
                    defaultString(coerceText(projectionMap.get("projectionVersion")), PROJECTION_VERSION),
                    defaultString(coerceText(projectionMap.get("canonicalTopicLabel")), "未命名 topic"),
                    defaultString(coerceText(projectionMap.get("problemSpaceBucket")), "general_visibility"),
                    readDimensionWeights(projectionMap.get("dimensionWeights")),
                    readStateBiasRules(projectionMap.get("stateBias")),
                    positiveInt(projectionMap.get("coverageFloor"), 1),
                    Math.max(1, positiveInt(projectionMap.get("topicQuotaFloor"), 1)),
                    clamp(asDouble(projectionMap.get("topicFreshnessBoost"), 0.0d), 0.0d, 1.0d),
                    clamp(asDouble(projectionMap.get("stateCoverageDebtBoost"), 0.0d), 0.0d, 1.0d),
                    normalizeSourceType(coerceText(projectionMap.get("sourceType"))),
                    readGroundedSignals(projectionMap.get("topicGroundedSignals")),
                    readObjectMap(projectionMap.get("projectionTrace"))
            ));
        }
        return projections.isEmpty() ? List.of() : List.copyOf(projections);
    }

    private static List<GroundedContextSignal> readGroundedSignals(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<GroundedContextSignal> signals = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> signalMap = readObjectMap(item);
            if (signalMap.isEmpty()) {
                continue;
            }
            String slot = normalizeOptional(coerceText(signalMap.get("slot")));
            String value = normalizeOptional(coerceText(signalMap.get("value")));
            if (!StringUtils.hasText(slot) || !StringUtils.hasText(value)) {
                continue;
            }
            signals.add(new GroundedContextSignal(
                    slot,
                    value,
                    normalizeOptional(coerceText(signalMap.get("source"))),
                    normalizeOptional(coerceText(signalMap.get("evidence"))),
                    signalMap.get("confidence") == null ? null : clamp(asDouble(signalMap.get("confidence"), 0.0d), 0.0d, 1.0d),
                    readStringList(signalMap.get("useFor")),
                    readObjectMap(signalMap.get("metadata"))
            ));
        }
        return signals.isEmpty() ? List.of() : List.copyOf(signals);
    }

    private static Map<String, Double> readDimensionWeights(Object raw) {
        Map<String, Object> map = readObjectMap(raw);
        LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
        for (String dimensionId : DIMENSION_IDS) {
            normalized.put(dimensionId, clamp(asDouble(map.get(dimensionId), 0.0d), 0.0d, 1.0d));
        }
        return Map.copyOf(normalized);
    }

    private static List<StateBiasRule> readStateBiasRules(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        List<StateBiasRule> rules = new ArrayList<>();
        for (Object item : rawList) {
            Map<String, Object> ruleMap = readObjectMap(item);
            if (ruleMap.isEmpty()) {
                continue;
            }
            rules.add(new StateBiasRule(
                    readStringMap(ruleMap.get("matchTags")),
                    clamp(asDouble(ruleMap.get("biasScore"), 0.0d), -1.0d, 1.0d),
                    defaultString(coerceText(ruleMap.get("reason")), "topic_state_projection_rule")
            ));
        }
        return rules.isEmpty() ? List.of() : List.copyOf(rules);
    }

    private static Map<String, String> readStringMap(Object raw) {
        Map<String, Object> map = readObjectMap(raw);
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String value = coerceText(entry.getValue());
            if (StringUtils.hasText(value)) {
                normalized.put(entry.getKey(), value.trim());
            }
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : rawList) {
            String value = normalizeOptional(coerceText(item));
            if (StringUtils.hasText(value)) {
                values.add(value);
            }
        }
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private static String stableTopicId(String rawTopicId, String canonicalTopicLabel, String sourceType) {
        String sourceKey = normalizeOptional(rawTopicId);
        if (sourceKey == null) {
            sourceKey = defaultString(normalizeOptional(canonicalTopicLabel), "topic") + "|" + defaultString(sourceType, "system_generated");
        }
        return UUID.nameUUIDFromBytes(("light-cone-topic|" + sourceKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String normalizeSourceType(String source) {
        String normalized = normalizeOptional(source);
        if (normalized == null) {
            return "system_generated";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "manual", "user_added" -> "user_added";
            case "confirmed", "frontend_confirmed", "user_confirmed" -> "user_confirmed";
            default -> "system_generated";
        };
    }

    private static Map<String, Object> readObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !StringUtils.hasText(key) || entry.getValue() == null) {
                continue;
            }
            normalized.put(key.trim(), entry.getValue());
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                normalized.put(key.trim(), value);
            }
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static double asDouble(Object raw, double fallback) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int positiveInt(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Math.max(0, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String coerceText(Object raw) {
        return raw instanceof String text && StringUtils.hasText(text) ? text.trim() : null;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    public record ProjectionEnvelope(
            List<Map<String, Object>> projections,
            Map<String, Object> summary
    ) {

        public ProjectionEnvelope {
            projections = projections == null || projections.isEmpty() ? List.of() : List.copyOf(projections);
            summary = summary == null || summary.isEmpty() ? Map.of() : Map.copyOf(summary);
        }

        public static ProjectionEnvelope empty() {
            return new ProjectionEnvelope(List.of(), Map.of());
        }

        public boolean active() {
            return !projections.isEmpty();
        }
    }

    public record ProjectionRuntimeContext(
            List<TopicStateProjection> projections,
            Map<String, Object> summary
    ) {

        public ProjectionRuntimeContext {
            projections = projections == null || projections.isEmpty() ? List.of() : List.copyOf(projections);
            summary = summary == null || summary.isEmpty() ? Map.of() : Map.copyOf(summary);
        }

        public static ProjectionRuntimeContext inactive() {
            return new ProjectionRuntimeContext(List.of(), Map.of());
        }

        public boolean active() {
            return !projections.isEmpty();
        }

        public List<String> canonicalTopicLabels() {
            return projections.stream().map(TopicStateProjection::canonicalTopicLabel).toList();
        }

        public String selectionMode() {
            String mode = summary.get("selectionMode") instanceof String text ? text.trim() : null;
            return StringUtils.hasText(mode) ? mode : SELECTION_MODE;
        }

        public TopicStateProjection projectionByLabel(String topicLabel) {
            String normalized = normalizeOptional(topicLabel);
            if (normalized == null) {
                return null;
            }
            return projections.stream()
                    .filter(projection -> normalized.equals(projection.canonicalTopicLabel()))
                    .findFirst()
                    .orElse(null);
        }
    }

    public record TopicStateProjection(
            String topicId,
            String projectionVersion,
            String canonicalTopicLabel,
            String problemSpaceBucket,
            Map<String, Double> dimensionWeights,
            List<StateBiasRule> stateBias,
            int coverageFloor,
            int topicQuotaFloor,
            double topicFreshnessBoost,
            double stateCoverageDebtBoost,
            String sourceType,
            List<GroundedContextSignal> topicGroundedSignals,
            Map<String, Object> projectionTrace
    ) {

        public TopicStateProjection {
            topicId = defaultString(normalizeOptional(topicId), stableTopicId(null, canonicalTopicLabel, sourceType));
            projectionVersion = defaultString(normalizeOptional(projectionVersion), PROJECTION_VERSION);
            canonicalTopicLabel = defaultString(normalizeOptional(canonicalTopicLabel), "未命名 topic");
            problemSpaceBucket = defaultString(normalizeOptional(problemSpaceBucket), "general_visibility");
            dimensionWeights = normalizeDimensionWeights(dimensionWeights);
            stateBias = stateBias == null || stateBias.isEmpty() ? List.of() : List.copyOf(stateBias);
            coverageFloor = Math.max(0, coverageFloor);
            topicQuotaFloor = Math.max(1, topicQuotaFloor);
            topicFreshnessBoost = clamp(topicFreshnessBoost, 0.0d, 1.0d);
            stateCoverageDebtBoost = clamp(stateCoverageDebtBoost, 0.0d, 1.0d);
            sourceType = normalizeSourceType(sourceType);
            topicGroundedSignals = topicGroundedSignals == null || topicGroundedSignals.isEmpty()
                    ? List.of()
                    : List.copyOf(topicGroundedSignals);
            projectionTrace = immutableObjectMap(projectionTrace);
        }

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("topicId", topicId);
            map.put("projectionVersion", projectionVersion);
            map.put("canonicalTopicLabel", canonicalTopicLabel);
            map.put("problemSpaceBucket", problemSpaceBucket);
            map.put("dimensionWeights", dimensionWeights);
            map.put("stateBias", stateBias.stream().map(StateBiasRule::toMap).toList());
            map.put("coverageFloor", coverageFloor);
            map.put("topicQuotaFloor", topicQuotaFloor);
            map.put("topicFreshnessBoost", topicFreshnessBoost);
            map.put("stateCoverageDebtBoost", stateCoverageDebtBoost);
            map.put("sourceType", sourceType);
            map.put("topicGroundedSignals", topicGroundedSignals.stream()
                    .map(TopicStateProjection::signalToMap)
                    .toList());
            map.put("projectionTrace", projectionTrace);
            return Map.copyOf(map);
        }

        private static Map<String, Object> signalToMap(GroundedContextSignal signal) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("slot", signal.slot());
            map.put("value", signal.value());
            if (StringUtils.hasText(signal.source())) {
                map.put("source", signal.source());
            }
            if (StringUtils.hasText(signal.evidence())) {
                map.put("evidence", signal.evidence());
            }
            if (signal.confidence() != null) {
                map.put("confidence", signal.confidence());
            }
            if (signal.useFor() != null && !signal.useFor().isEmpty()) {
                map.put("useFor", signal.useFor());
            }
            if (signal.metadata() != null && !signal.metadata().isEmpty()) {
                map.put("metadata", signal.metadata());
            }
            return Map.copyOf(map);
        }

        private static Map<String, Double> normalizeDimensionWeights(Map<String, Double> rawWeights) {
            LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
            for (String dimensionId : DIMENSION_IDS) {
                double value = rawWeights == null ? 0.0d : rawWeights.getOrDefault(dimensionId, 0.0d);
                normalized.put(dimensionId, clamp(value, 0.0d, 1.0d));
            }
            return Map.copyOf(normalized);
        }
    }

    public record StateBiasRule(
            Map<String, String> matchTags,
            double biasScore,
            String reason
    ) {

        public StateBiasRule {
            matchTags = matchTags == null || matchTags.isEmpty() ? Map.of() : Map.copyOf(matchTags);
            biasScore = clamp(biasScore, -1.0d, 1.0d);
            reason = defaultString(normalizeOptional(reason), "topic_state_projection_rule");
        }

        public boolean matches(Map<String, String> candidateTags) {
            if (matchTags.isEmpty() || candidateTags == null || candidateTags.isEmpty()) {
                return false;
            }
            for (Map.Entry<String, String> entry : matchTags.entrySet()) {
                if (!entry.getValue().equals(candidateTags.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("matchTags", matchTags);
            map.put("biasScore", biasScore);
            map.put("reason", reason);
            return Map.copyOf(map);
        }
    }

    private record TopicSeed(
            String rawTopicId,
            String topicLabel,
            String description,
            String source,
            boolean enabled,
            int enabledPromptCount,
            List<String> enabledPromptTexts,
            List<String> promptRefs
    ) {
        private TopicSeed {
            rawTopicId = normalizeOptional(rawTopicId);
            topicLabel = defaultString(normalizeOptional(topicLabel), "未命名 topic");
            description = normalizeOptional(description);
            source = normalizeOptional(source);
            enabledPromptTexts = enabledPromptTexts == null || enabledPromptTexts.isEmpty()
                    ? List.of()
                    : List.copyOf(enabledPromptTexts);
            promptRefs = promptRefs == null || promptRefs.isEmpty()
                    ? List.of()
                    : List.copyOf(promptRefs);
            enabledPromptCount = Math.max(0, enabledPromptCount);
        }
    }

    private record KeywordDecision(
            String problemSpaceBucket,
            List<String> matchedKeywords
    ) {
        private KeywordDecision {
            problemSpaceBucket = defaultString(normalizeOptional(problemSpaceBucket), "general_visibility");
            matchedKeywords = matchedKeywords == null || matchedKeywords.isEmpty() ? List.of() : List.copyOf(matchedKeywords);
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "";
    }
}

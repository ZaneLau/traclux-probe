package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.CommercialInsightAiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service("v42InsightGenerationService")
@RequiredArgsConstructor
public class InsightGenerationService {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_TEMPLATE_FALLBACK = "TEMPLATE_FALLBACK";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern JSON_CODE_FENCE_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final Pattern ACTIONABLE_PREFIX = Pattern.compile(
            "^(必须|立即|立刻|马上|全面|优先|重新|重写|重做|启动|发起|重构|强化|补齐|围绕|审查|优化|拆解|生产|分发|调整|建立|改写|扩展|统一).+"
    );
    private static final List<String> WEAK_RECOMMENDATION_WORDS = List.of(
            "建议", "可能", "或许", "也许", "可以考虑", "尽量"
    );
    private static final List<String> WEAK_RECOMMENDATION_PREFIXES = List.of(
            "建议先", "建议", "可以考虑先", "可以考虑", "可以先", "尽量先", "尽量", "最好先", "最好"
    );

    private final CommercialInsightAiService commercialInsightAiService;
    private final ObjectMapper objectMapper;

    public CommercialInsights generate(Node5AKpiResult node5AKpiResult) {
        return generate(null, node5AKpiResult);
    }

    public CommercialInsights generate(String diagnosisId, Node5AKpiResult node5AKpiResult) {
        Objects.requireNonNull(node5AKpiResult, "Node5AKpiResult 不能为空");
        KpiSummary summary = node5AKpiResult.kpiSummary();
        String summaryJson = toJson(summary);
        String focusPoints = buildFocusPoints(summary);
        String rawJson = null;
        try {
            LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_CREATIVE, PROVIDER_DEEPSEEK));
            rawJson = commercialInsightAiService.generateInsights(summaryJson, focusPoints);
            log.debug("V4.2 Node 5B raw response. diagnosisId={}, snapshot={}",
                    diagnosisId, snapshot(rawJson));
            return parseAndValidate(rawJson, summary);
        } catch (RuntimeException ex) {
            String fallbackReasonCode = V42CreativeDegradationReasons.resolve(ex);
            log.warn("V4.2 Node 5B deterministic fallback engaged. diagnosisId={}, reason={}, rawSnapshot={}",
                    diagnosisId, fallbackReasonCode, snapshot(rawJson), ex);
            return buildDeterministicFallback(summary, fallbackReasonCode);
        } finally {
            LlmCallContext.clear();
        }
    }

    private CommercialInsights parseAndValidate(String rawJson, KpiSummary summary) {
        CommercialInsights insights = parse(rawJson, summary);
        validateLlmInsights(insights, summary);
        return insights;
    }

    private CommercialInsights parse(String rawJson, KpiSummary summary) {
        try {
            JsonNode payload = objectMapper.readTree(normalizeJsonPayload(rawJson));
            JsonNode topThreeInsightsNode = payload.get("topThreeInsights");
            if (topThreeInsightsNode == null || !topThreeInsightsNode.isArray()) {
                throw new IllegalStateException("Node 5B LLM 输出缺少 topThreeInsights 数组");
            }
            CommercialInsights template = buildDeterministicFallback(summary, V42CreativeDegradationReasons.OUTPUT_INVALID);
            List<Double> knownMetrics = collectKnownMetrics(summary);
            List<InsightItem> topThreeInsights = new ArrayList<>();
            int index = 0;
            for (; index < topThreeInsightsNode.size() && index < 3; index++) {
                JsonNode itemNode = topThreeInsightsNode.get(index);
                InsightItem fallbackItem = template.topThreeInsights().get(index);
                topThreeInsights.add(buildInsightItem(itemNode, fallbackItem, knownMetrics));
            }
            while (index < 3) {
                topThreeInsights.add(template.topThreeInsights().get(index));
                index++;
            }
            String headlineInsight = firstText(payload.path("headlineInsight").asText(null), template.headlineInsight());
            return new CommercialInsights(headlineInsight, topThreeInsights, SOURCE_LLM);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Node 5B LLM 输出 JSON 解析失败", ex);
        }
    }

    private InsightItem buildInsightItem(JsonNode itemNode,
                                         InsightItem fallbackItem,
                                         List<Double> knownMetrics) {
        String title = firstText(readOptionalText(itemNode, "title"), fallbackItem.title());
        String evidence = repairEvidence(
                firstText(readOptionalText(itemNode, "evidence"), fallbackItem.evidence()),
                fallbackItem.evidence(),
                knownMetrics
        );
        String recommendation = normalizeRecommendation(
                firstText(readOptionalText(itemNode, "recommendation"), fallbackItem.recommendation())
        );
        try {
            return new InsightItem(title, evidence, recommendation);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("recommendation")) {
                return new InsightItem(title, evidence, forceActionableRecommendation(recommendation));
            }
            throw ex;
        }
    }

    private String readOptionalText(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        return field == null ? null : field.asText(null);
    }

    private String normalizeRecommendation(String rawRecommendation) {
        if (!hasText(rawRecommendation)) {
            throw new IllegalStateException("Node 5B recommendation 不能为空");
        }
        String normalized = rawRecommendation.trim();
        if (ACTIONABLE_PREFIX.matcher(normalized).matches()) {
            return normalized;
        }
        String stripped = stripWeakRecommendationPrefix(normalized);
        if (ACTIONABLE_PREFIX.matcher(stripped).matches()) {
            return stripped;
        }
        if (!normalized.equals(stripped)) {
            return "立即" + stripped;
        }
        return forceActionableRecommendation(normalized);
    }

    private String stripWeakRecommendationPrefix(String recommendation) {
        String normalized = recommendation == null ? "" : recommendation.trim();
        for (String prefix : WEAK_RECOMMENDATION_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                String stripped = normalized.substring(prefix.length()).trim();
                return stripped.isEmpty() ? normalized : stripped;
            }
        }
        return normalized;
    }

    private String forceActionableRecommendation(String recommendation) {
        String normalized = recommendation == null ? "" : recommendation.trim();
        if (ACTIONABLE_PREFIX.matcher(normalized).matches()) {
            return normalized;
        }
        String stripped = stripWeakRecommendationPrefix(normalized);
        for (String weakWord : WEAK_RECOMMENDATION_WORDS) {
            stripped = stripped.replace(weakWord, "");
        }
        stripped = stripped.trim();
        if (stripped.isEmpty()) {
            return "立即补齐品牌结论与证据表达。";
        }
        if (ACTIONABLE_PREFIX.matcher(stripped).matches()) {
            return stripped;
        }
        return "立即" + stripped;
    }

    private String repairEvidence(String evidence, String fallbackEvidence, List<Double> knownMetrics) {
        String normalized = firstText(evidence, fallbackEvidence);
        if (containsKnownMetric(normalized, knownMetrics)) {
            return normalized;
        }
        if (!hasText(fallbackEvidence) || normalized.contains(fallbackEvidence)) {
            return normalized;
        }
        return normalized + " 关键指标：" + fallbackEvidence;
    }

    private String firstText(String preferred, String fallback) {
        if (hasText(preferred)) {
            return preferred.trim();
        }
        if (hasText(fallback)) {
            return fallback.trim();
        }
        throw new IllegalStateException("Node 5B 文本字段为空");
    }

    private String toJson(KpiSummary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Node 5B prompt JSON 序列化失败", ex);
        }
    }

    private void validateLlmInsights(CommercialInsights insights, KpiSummary summary) {
        if (!SOURCE_LLM.equals(insights.insightSource())) {
            throw new IllegalStateException("LLM 输出的 insightSource 非法");
        }
        List<Double> knownMetrics = collectKnownMetrics(summary);
        for (InsightItem item : insights.topThreeInsights()) {
            if (!containsKnownMetric(item.evidence(), knownMetrics)) {
                throw new IllegalStateException("evidence 未引用已知 KPI 数值");
            }
        }
    }

    private List<Double> collectKnownMetrics(KpiSummary summary) {
        LinkedHashSet<Double> metrics = new LinkedHashSet<>();
        addRateMetrics(metrics, summary.spontaneousMentionRate());
        addRateMetrics(metrics, summary.confirmationResponseRate());
        addRateMetrics(metrics, summary.overallMentionRate());
        addRateMetrics(metrics, summary.recommendRate());
        addRateMetrics(metrics, summary.positiveRate());
        metrics.add(scale2(summary.stabilityScore()));
        metrics.add((double) summary.effectiveSampleSize());

        addBucketMetrics(metrics, summary.orthogonalKpi());
        addBucketMetrics(metrics, summary.paretoKpi());
        addBucketMetrics(metrics, summary.adversarialKpi());
        addLengthMetrics(metrics, summary.lengthStratifiedKpi().shortBucket());
        addLengthMetrics(metrics, summary.lengthStratifiedKpi().mediumBucket());
        addLengthMetrics(metrics, summary.lengthStratifiedKpi().longBucket());
        for (Double value : summary.dimensionPenetration().values()) {
            addRateMetrics(metrics, value);
        }
        for (Double value : summary.dimensionRadarScores().values()) {
            addRateMetrics(metrics, value);
        }
        for (CompetitorKpi competitorKpi : summary.competitorKpis().values()) {
            metrics.add((double) competitorKpi.mentionCount());
            addRateMetrics(metrics, competitorKpi.responseShare());
            addRateMetrics(metrics, competitorKpi.positiveRate());
            addRateMetrics(metrics, competitorKpi.averageStrength());
        }
        return List.copyOf(metrics);
    }

    private void addBucketMetrics(Set<Double> metrics, BucketKpi bucketKpi) {
        metrics.add((double) bucketKpi.totalProbes());
        metrics.add((double) bucketKpi.effectiveSampleSize());
        addRateMetrics(metrics, bucketKpi.spontaneousMentionRate());
        addRateMetrics(metrics, bucketKpi.confirmationResponseRate());
        addRateMetrics(metrics, bucketKpi.overallMentionRate());
        addRateMetrics(metrics, bucketKpi.recommendRate());
        addRateMetrics(metrics, bucketKpi.positiveRate());
    }

    private void addLengthMetrics(Set<Double> metrics, LengthBucketKpi lengthBucketKpi) {
        metrics.add((double) lengthBucketKpi.totalProbes());
        metrics.add((double) lengthBucketKpi.openDiscoveryCount());
        metrics.add((double) lengthBucketKpi.brandAssertiveCount());
        addRateMetrics(metrics, lengthBucketKpi.spontaneousMentionRate());
        addRateMetrics(metrics, lengthBucketKpi.confirmationResponseRate());
        metrics.add(scale2(lengthBucketKpi.avgBrandsMentioned()));
    }

    private void addRateMetrics(Set<Double> metrics, double value) {
        metrics.add(scale2(value * 100.0d));
        metrics.add(scale4(value));
    }

    private boolean containsKnownMetric(String evidence, List<Double> knownMetrics) {
        List<Double> evidenceMetrics = extractNumericSignals(evidence);
        if (evidenceMetrics.isEmpty()) {
            return false;
        }
        for (Double candidate : evidenceMetrics) {
            for (Double knownMetric : knownMetrics) {
                if (approximatelyMatches(candidate, knownMetric)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String buildFocusPoints(KpiSummary summary) {
        DimensionWeakness dimensionWeakness = resolveDimensionWeakness(summary);
        BucketWeakness bucketWeakness = resolveBucketWeakness(summary);
        LengthWeakness lengthWeakness = resolveLengthWeakness(summary);
        CompetitorPressure competitorPressure = resolveCompetitorPressure(summary);

        List<String> focusLines = new ArrayList<>();
        focusLines.add("- 总体提及: spontaneousMentionRate=%s, confirmationResponseRate=%s, overallMentionRate=%s, stabilityScore=%s"
                .formatted(
                        formatPercent(summary.spontaneousMentionRate()),
                        formatPercent(summary.confirmationResponseRate()),
                        formatPercent(summary.overallMentionRate()),
                        formatScore(summary.stabilityScore())
                ));
        focusLines.add("- 结构敏感维度: %s=%s; 最弱切片=%s=%s"
                .formatted(
                        dimensionWeakness.dimensionName(),
                        formatPercent(dimensionWeakness.radarScore()),
                        dimensionWeakness.sliceLabel(),
                        formatPercent(dimensionWeakness.sliceRate())
                ));
        focusLines.add("- 最差桶: %s %s=%s; effectiveSampleSize=%d"
                .formatted(
                        bucketWeakness.bucketLabel(),
                        bucketWeakness.metricLabel(),
                        formatPercent(bucketWeakness.focusRate()),
                        bucketWeakness.effectiveSampleSize()
                ));
        focusLines.add("- 最差长度层: %s combinedRate=%s; spontaneousMentionRate=%s; confirmationResponseRate=%s"
                .formatted(
                        lengthWeakness.lengthLabel(),
                        formatPercent(lengthWeakness.combinedRate()),
                        formatPercent(lengthWeakness.bucket().spontaneousMentionRate()),
                        formatPercent(lengthWeakness.bucket().confirmationResponseRate())
                ));
        if (competitorPressure != null) {
            focusLines.add("- 竞品压制: %s responseShare=%s; positiveRate=%s; averageStrength=%s"
                    .formatted(
                            competitorPressure.competitorName(),
                            formatPercent(competitorPressure.kpi().responseShare()),
                            formatPercent(competitorPressure.kpi().positiveRate()),
                            formatPercent(competitorPressure.kpi().averageStrength())
                    ));
        }
        return String.join("\n", focusLines);
    }

    private CommercialInsights buildDeterministicFallback(KpiSummary summary, String fallbackReasonCode) {
        DimensionWeakness dimensionWeakness = resolveDimensionWeakness(summary);
        BucketWeakness bucketWeakness = resolveBucketWeakness(summary);
        LengthWeakness lengthWeakness = resolveLengthWeakness(summary);
        CompetitorPressure competitorPressure = resolveCompetitorPressure(summary);

        return new CommercialInsights(
                buildHeadline(dimensionWeakness, bucketWeakness),
                List.of(
                        buildDimensionInsight(dimensionWeakness),
                        buildBucketInsight(bucketWeakness),
                        shouldUseCompetitorPressure(summary, competitorPressure)
                                ? buildCompetitorInsight(summary, competitorPressure)
                                : buildLengthInsight(lengthWeakness)
                ),
                SOURCE_TEMPLATE_FALLBACK,
                fallbackReasonCode
        );
    }

    private String buildHeadline(DimensionWeakness dimensionWeakness, BucketWeakness bucketWeakness) {
        return "您的品牌在%s维度出现明显结构失衡，%s的%s只剩%s，AI 已经不会稳定把您放进首选。"
                .formatted(
                        dimensionWeakness.dimensionName(),
                        bucketWeakness.bucketLabel(),
                        bucketWeakness.metricLabel(),
                        formatPercent(bucketWeakness.focusRate())
                );
    }

    private InsightItem buildDimensionInsight(DimensionWeakness weakness) {
        return new InsightItem(
                weakness.dimensionName() + "维度已经形成结构性脆弱点",
                "%s 结构对比度达到 %s，其中 %s 切片穿透率只有 %s，说明这个维度内部的长尾场景正在明显拉低品牌可见度。"
                        .formatted(
                                weakness.dimensionId(),
                                formatPercent(weakness.radarScore()),
                                weakness.sliceLabel(),
                                formatPercent(weakness.sliceRate())
                        ),
                "必须立即围绕 %s 维度重写品牌论据，把最低穿透切片对应的首选证据前置到回答开头。"
                        .formatted(weakness.dimensionName())
        );
    }

    private InsightItem buildBucketInsight(BucketWeakness weakness) {
        return new InsightItem(
                weakness.bucketLabel() + "正在成为推荐链路的断点",
                "%s 的 %s只有 %s，有效样本 %d 条，这个桶位一旦被问到，品牌就会从比较池里直接掉队。"
                        .formatted(
                                weakness.bucketLabel(),
                                weakness.metricLabel(),
                                formatPercent(weakness.focusRate()),
                                weakness.effectiveSampleSize()
                        ),
                "必须立即重做 %s 的回答模板，把品牌名、比较结论和可验证证据压进前两句。"
                        .formatted(weakness.bucketLabel())
        );
    }

    private InsightItem buildLengthInsight(LengthWeakness weakness) {
        return new InsightItem(
                weakness.lengthLabel() + "回答场景正在放大失血",
                "%s 的 spontaneousMentionRate 只有 %s，confirmationResponseRate 只有 %s，combinedRate 只有 %s，回答一变长品牌就滑出首选链路。"
                        .formatted(
                                weakness.lengthLabel(),
                                formatPercent(weakness.bucket().spontaneousMentionRate()),
                                formatPercent(weakness.bucket().confirmationResponseRate()),
                                formatPercent(weakness.combinedRate())
                        ),
                "必须立即重写 %s 的长文回答结构，把品牌结论、核心证据和比较优势前置到前两屏。"
                        .formatted(weakness.lengthLabel())
        );
    }

    private InsightItem buildCompetitorInsight(KpiSummary summary, CompetitorPressure competitorPressure) {
        return new InsightItem(
                competitorPressure.competitorName() + "正在抢走比较位定义权",
                "%s 的 responseShare 达到 %s，positiveRate 达到 %s，而您的 overallMentionRate 只有 %s，比较池已经先被竞品占住。"
                        .formatted(
                                competitorPressure.competitorName(),
                                formatPercent(competitorPressure.kpi().responseShare()),
                                formatPercent(competitorPressure.kpi().positiveRate()),
                                formatPercent(summary.overallMentionRate())
                        ),
                "必须立即围绕 %s 抢走的比较问题补齐对比证据和首选结论，不能再让竞品先定义购买标准。"
                        .formatted(competitorPressure.competitorName())
        );
    }

    private boolean shouldUseCompetitorPressure(KpiSummary summary, CompetitorPressure competitorPressure) {
        return competitorPressure != null
                && competitorPressure.kpi().responseShare() >= 0.25d
                && competitorPressure.kpi().responseShare() >= summary.overallMentionRate();
    }

    private DimensionWeakness resolveDimensionWeakness(KpiSummary summary) {
        Map.Entry<String, Double> weakestDimension = summary.dimensionRadarScores().entrySet().stream()
                .max(Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .orElse(Map.entry("D1", 0.0d));
        String dimensionId = weakestDimension.getKey();
        Map.Entry<String, Double> weakestSlice = summary.dimensionPenetration().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(dimensionId + "_"))
                .min(Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .orElse(Map.entry(dimensionId + "_no_slice_data", weakestDimension.getValue()));
        return new DimensionWeakness(
                dimensionId,
                CssmHiAbstractStates.DIMENSION_NAMES.getOrDefault(dimensionId, dimensionId),
                weakestDimension.getValue(),
                sliceLabel(weakestSlice.getKey()),
                weakestSlice.getValue()
        );
    }

    private BucketWeakness resolveBucketWeakness(KpiSummary summary) {
        return List.of(summary.orthogonalKpi(), summary.paretoKpi(), summary.adversarialKpi()).stream()
                .map(this::toBucketWeakness)
                .min(Comparator.comparingDouble(BucketWeakness::focusRate)
                        .thenComparing(BucketWeakness::bucketLabel))
                .orElseGet(() -> toBucketWeakness(summary.orthogonalKpi()));
    }

    private BucketWeakness toBucketWeakness(BucketKpi bucketKpi) {
        if (bucketKpi.bucketType() == BucketType.ORTHOGONAL) {
            return new BucketWeakness(
                    bucketLabel(bucketKpi.bucketType()),
                    "spontaneousMentionRate",
                    bucketKpi.spontaneousMentionRate(),
                    bucketKpi.effectiveSampleSize()
            );
        }
        return new BucketWeakness(
                bucketLabel(bucketKpi.bucketType()),
                "confirmationResponseRate",
                bucketKpi.confirmationResponseRate(),
                bucketKpi.effectiveSampleSize()
        );
    }

    private LengthWeakness resolveLengthWeakness(KpiSummary summary) {
        return List.of(
                        summary.lengthStratifiedKpi().shortBucket(),
                        summary.lengthStratifiedKpi().mediumBucket(),
                        summary.lengthStratifiedKpi().longBucket()
                ).stream()
                .map(this::toLengthWeakness)
                .min(Comparator.comparingDouble(LengthWeakness::combinedRate)
                        .thenComparing(LengthWeakness::lengthLabel))
                .orElseGet(() -> toLengthWeakness(summary.lengthStratifiedKpi().shortBucket()));
    }

    private LengthWeakness toLengthWeakness(LengthBucketKpi bucket) {
        double totalRate = 0.0d;
        int rateCount = 0;
        if (bucket.openDiscoveryCount() > 0) {
            totalRate += bucket.spontaneousMentionRate();
            rateCount++;
        }
        if (bucket.brandAssertiveCount() > 0) {
            totalRate += bucket.confirmationResponseRate();
            rateCount++;
        }
        double combinedRate = rateCount == 0 ? 0.0d : scale2(totalRate / rateCount);
        return new LengthWeakness(lengthLabel(bucket.lengthLabel()), bucket, combinedRate);
    }

    private CompetitorPressure resolveCompetitorPressure(KpiSummary summary) {
        return summary.competitorKpis().values().stream()
                .max(Comparator.comparingDouble(CompetitorKpi::responseShare)
                        .thenComparingDouble(CompetitorKpi::positiveRate)
                        .thenComparingDouble(CompetitorKpi::averageStrength)
                        .thenComparing(CompetitorKpi::competitorName))
                .map(CompetitorPressure::new)
                .orElse(null);
    }

    private String sliceLabel(String sliceKey) {
        int separator = sliceKey.indexOf('_');
        return separator >= 0 ? sliceKey.substring(separator + 1) : sliceKey;
    }

    private String bucketLabel(BucketType bucketType) {
        return switch (bucketType) {
            case ORTHOGONAL -> "正交桶";
            case PARETO -> "帕累托桶";
            case ADVERSARIAL -> "对抗桶";
        };
    }

    private String lengthLabel(String lengthLabel) {
        return switch (lengthLabel) {
            case "Short" -> "短回答";
            case "Medium" -> "中等长度回答";
            case "Long" -> "长回答";
            default -> lengthLabel;
        };
    }

    private String formatPercent(double value) {
        return BigDecimal.valueOf(value * 100.0d)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }

    private String formatScore(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private List<Double> extractNumericSignals(String text) {
        Matcher matcher = NUMERIC_PATTERN.matcher(text);
        List<Double> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group()));
        }
        return values;
    }

    private boolean approximatelyMatches(double candidate, double knownMetric) {
        double tolerance = knownMetric >= 1.0d ? 0.2d : 0.02d;
        return Math.abs(candidate - knownMetric) <= tolerance;
    }

    private double scale2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String snapshot(String rawJson) {
        if (rawJson == null) {
            return "<null>";
        }
        return SensitiveDataSanitizer.preview(rawJson, 160);
    }

    private double scale4(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String normalizeJsonPayload(String rawJson) {
        if (!hasText(rawJson)) {
            throw new IllegalStateException("Node 5B LLM 输出为空");
        }
        String trimmed = rawJson.trim();
        Matcher fenced = JSON_CODE_FENCE_PATTERN.matcher(trimmed);
        if (fenced.find()) {
            return fenced.group(1);
        }

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record DimensionWeakness(
            String dimensionId,
            String dimensionName,
            double radarScore,
            String sliceLabel,
            double sliceRate
    ) {
    }

    private record BucketWeakness(
            String bucketLabel,
            String metricLabel,
            double focusRate,
            int effectiveSampleSize
    ) {
    }

    private record LengthWeakness(
            String lengthLabel,
            LengthBucketKpi bucket,
            double combinedRate
    ) {
    }

    private record CompetitorPressure(CompetitorKpi kpi) {

        private String competitorName() {
            return kpi.competitorName();
        }
    }
}

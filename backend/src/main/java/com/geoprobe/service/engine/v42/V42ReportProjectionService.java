package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import com.geoprobe.dto.engine.v42.SearchEvidence;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class V42ReportProjectionService {

    private static final String SEO_NOTE = "V4.2 当前只验证 AI 能见度主链，不再内置 SEO 对比诊断。";

    private final ObjectMapper objectMapper;
    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy;

    public V42ReportProjectionService(ObjectMapper objectMapper,
                                      V42TargetProbeCountPolicy v42TargetProbeCountPolicy) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        this.v42TargetProbeCountPolicy = Objects.requireNonNull(v42TargetProbeCountPolicy, "v42TargetProbeCountPolicy 不能为空");
    }

    public Map<String, Object> buildReportData(Diagnosis diagnosis,
                                               V42DiagnosisResult result,
                                               DiagnosisAliasConfigData aliasConfig,
                                               Map<String, Object> existingMeta) {
        Objects.requireNonNull(diagnosis, "diagnosis 不能为空");
        Objects.requireNonNull(result, "result 不能为空");

        V42DiagnosisResult.Task task = result.task();
        V42DiagnosisResult.Report report = result.report();
        KpiSummary summary = report.kpiSummary();
        CommercialInsights insights = report.commercialInsights();
        DashboardCharts charts = report.dashboardCharts();

        Map<String, Object> meta = new LinkedHashMap<>();
        if (existingMeta != null) {
            meta.putAll(existingMeta);
        }
        String reportEngineVersion = report.engineVersion();
        meta.remove("engineVersions");
        meta.put("reportVersion", reportEngineVersion);
        meta.put("engineVersion", reportEngineVersion);
        meta.put("engineComponents", V42EngineManifest.components());
        meta.put("taskId", task.taskId());
        meta.put("reportId", report.reportId());
        meta.put("modelProvider", report.modelProvider());
        meta.put("sampleSize", report.sampleSize());
        int expectedSamples = resolveExpectedSamples(existingMeta);
        int generatedSamples = report.sampleSize();
        int missingSamples = Math.max(0, expectedSamples - generatedSamples);
        Map<String, Object> executionIntegrity = buildExecutionIntegrity(task);
        boolean executionDegraded = V42ExecutionIntegrityView.degraded(task.executionSignals());
        meta.put("generatedSamples", generatedSamples);
        meta.put("expectedSamples", expectedSamples);
        meta.put("missingSamples", missingSamples);
        meta.put("samplingVariantShortfall", missingSamples > 0);
        meta.put("methodology", report.methodology());
        meta.put("disclaimer", report.disclaimer());
        meta.put("usageCost", buildUsageCostStats(task));
        meta.put("executionIntegrity", executionIntegrity);
        meta.put("node2RuleCatalogVersion", Node2RuleCatalog.VERSION);
        meta.put("displayNameMappings", convertToMapList(report.displayNameMappings()));
        if (aliasConfig != null) {
            meta.put("brandAliases", aliasConfig.brandAliases());
            meta.put("competitorAliases", aliasConfig.competitorAliases());
        }

        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("diagnosisId", diagnosis.getId());
        reportData.put("brandName", diagnosis.getBrandName());
        reportData.put("category", diagnosis.getCategory());
        reportData.put("task", convertToMap(task));
        reportData.put("report", convertToMap(report));
        reportData.put("executiveSummary", buildExecutiveSummary(report, task));
        reportData.put("seoVsGeo", buildSeoVsGeo(summary));
        reportData.put("clusterAnalysis", buildClusterAnalysis(summary));
        reportData.put("samplingStats", buildSamplingStats(task));
        reportData.put("samplingDegraded", task.failedProbes() > 0L);
        reportData.put("executionDegraded", executionDegraded);
        reportData.put("cleanRun", !executionDegraded && task.failedProbes() <= 0L);
        reportData.put("executionIntegrity", executionIntegrity);
        reportData.put("probeRuntimeTruth", buildProbeRuntimeTruth(task.probeTrace()));
        reportData.put("node2RuleCatalog", buildNode2RuleCatalog(task.probeTrace()));
        reportData.put("evidenceChain", buildEvidenceChain(task.probeTrace()));
        reportData.put("competitorRanking", buildCompetitorRanking(charts, summary));
        reportData.put("negativeSignals", buildNegativeSignals(summary, report.sampleSize()));
        reportData.put("associatedKeywords", buildAssociatedKeywords(summary, report.sampleSize()));
        reportData.put("actionItems", buildActionItems(insights));
        reportData.put("meta", meta);
        reportData.put("brandPerception", buildBrandPerception(summary, insights, report.sampleSize()));
        reportData.put("semanticDistanceMap", buildSemanticDistanceMap(charts));
        reportData.put("clusterBreakdown", buildClusterBreakdown(summary, report.sampleSize()));
        reportData.put("competitorRadar", buildCompetitorRadar(diagnosis.getBrandName(), charts, summary));
        reportData.put("sourceAttribution", buildSourceAttribution(task, insights));
        reportData.put("kpiInterpretation", buildKpiInterpretation(summary));
        reportData.put("requestSummary", buildRequestSummary(diagnosis));
        reportData.put("heroSummary", buildHeroSummary(summary, insights, task, report.sampleSize()));
        return reportData;
    }

    private int resolveExpectedSamples(Map<String, Object> existingMeta) {
        if (existingMeta == null || existingMeta.isEmpty()) {
            throw new IllegalStateException("V4.2 缺少报告 meta，无法投影前台任务单探针数");
        }
        return v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(existingMeta, "v42_report_projection");
    }

    public boolean supports(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return false;
        }
        Map<String, Object> reportData = convertToMap(diagnosis.getReportData());
        Map<String, Object> meta = asMap(reportData.get("meta"));
        if (V42EngineVersionSupport.isSupported(asText(meta.get("engineVersion")))) {
            return true;
        }
        Map<String, Object> report = asMap(reportData.get("report"));
        return V42EngineVersionSupport.isSupported(asText(report.get("engineVersion")));
    }

    public V42DiagnosisResult.Task readTask(Diagnosis diagnosis) {
        if (!supports(diagnosis)) {
            return null;
        }
        Map<String, Object> reportData = convertToMap(diagnosis.getReportData());
        Object rawTask = reportData.get("task");
        if (rawTask == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(rawTask, V42DiagnosisResult.Task.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public V42DiagnosisResult.Report readReport(Diagnosis diagnosis) {
        if (!supports(diagnosis)) {
            return null;
        }
        Map<String, Object> reportData = convertToMap(diagnosis.getReportData());
        Object rawReport = reportData.get("report");
        if (rawReport == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(rawReport, V42DiagnosisResult.Report.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Map<String, Object> buildSummaryKpiMirror(V42DiagnosisResult result) {
        Objects.requireNonNull(result, "result 不能为空");
        return asMap(buildExecutiveSummary(result.report(), result.task()).get("kpis"));
    }

    private Map<String, Object> buildExecutiveSummary(V42DiagnosisResult.Report report,
                                                      V42DiagnosisResult.Task task) {
        KpiSummary summary = report.kpiSummary();
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("l1MentionRate", round4(summary.overallMentionRate()));
        kpis.put("l2RecommendRate", round4(summary.recommendRate()));
        kpis.put("l3Top3Rate", round4(summary.spontaneousMentionRate()));
        kpis.put("spontaneousMentionRate", round4(summary.spontaneousMentionRate()));
        kpis.put("confirmationResponseRate", round4(summary.confirmationResponseRate()));
        kpis.put("overallMentionRate", round4(summary.overallMentionRate()));
        kpis.put("recommendRate", round4(summary.recommendRate()));
        kpis.put("positiveRate", round4(summary.positiveRate()));
        kpis.put("stabilityScore", round4(normalizeStability(summary.stabilityScore())));
        kpis.put("mentionedCount", toCount(summary.overallMentionRate(), report.sampleSize()));
        kpis.put("recommendedCount", toCount(summary.recommendRate(), report.sampleSize()));
        kpis.put("top3Count", toCount(summary.spontaneousMentionRate(), report.sampleSize()));
        kpis.put("totalSuccessRuns", (int) task.successfulProbes());

        Map<String, Object> summaryBlock = new LinkedHashMap<>();
        summaryBlock.put("headline", report.commercialInsights().headlineInsight());
        summaryBlock.put("kpis", kpis);
        summaryBlock.put("stabilityScore", round4(normalizeStability(summary.stabilityScore())));
        summaryBlock.put("methodology", report.methodology());
        return summaryBlock;
    }

    private Map<String, Object> buildSeoVsGeo(KpiSummary summary) {
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("baiduRank", null);
        seo.put("aiRecommendRate", round4(summary.recommendRate()));
        seo.put("source", "UNAVAILABLE");
        seo.put("gapType", "UNAVAILABLE");
        seo.put("insight", SEO_NOTE);
        seo.put("queryKeyword", null);
        seo.put("errorMessage", null);
        seo.put("status", "unavailable");
        seo.put("stateNote", SEO_NOTE);
        return seo;
    }

    private List<Map<String, Object>> buildClusterAnalysis(KpiSummary summary) {
        return List.of(
                bucketCluster("orthogonal", "正交探测", summary.orthogonalKpi()),
                bucketCluster("pareto", "帕累托场景", summary.paretoKpi()),
                bucketCluster("adversarial", "对抗验证", summary.adversarialKpi())
        );
    }

    private Map<String, Object> bucketCluster(String clusterId, String clusterName, BucketKpi bucketKpi) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clusterId", clusterId);
        item.put("clusterName", clusterName);
        item.put("mentionRate", round4(bucketKpi.overallMentionRate()));
        item.put("recommendRate", round4(bucketKpi.recommendRate()));
        item.put("top3Rate", round4(bucketKpi.spontaneousMentionRate()));
        item.put("sampleCount", bucketKpi.effectiveSampleSize());
        item.put("blindSpot", bucketKpi.recommendRate() < 0.3d);
        item.put("description", clusterName + " 的样本用于验证品牌在不同问题结构里的表现稳定度。");
        return item;
    }

    private Map<String, Object> buildSamplingStats(V42DiagnosisResult.Task task) {
        int totalSamples = task.sampleSize();
        int successSamples = (int) task.successfulProbes();
        int failedSamples = (int) task.failedProbes();
        RealtimeSearchSummary searchSummary = task.realtimeSearchSummary();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSamples", totalSamples);
        stats.put("successSamples", successSamples);
        stats.put("failedSamples", failedSamples);
        stats.put("parsedSamples", successSamples);
        stats.put("parseCoverage", round4(totalSamples == 0 ? 0.0d : successSamples * 1.0d / totalSamples));
        stats.put("successRate", round4(totalSamples == 0 ? 0.0d : successSamples * 1.0d / totalSamples));
        stats.put("modelProvider", task.modelProvider());
        Map<String, Object> realtimeSearch = new LinkedHashMap<>();
        realtimeSearch.put("enabled", searchSummary.enabled());
        realtimeSearch.put("provider", searchSummary.provider());
        realtimeSearch.put("requestedProbeCount", searchSummary.requestedProbeCount());
        realtimeSearch.put("satisfiedProbeCount", searchSummary.satisfiedProbeCount());
        realtimeSearch.put("evidenceSuccessRate", round4(searchSummary.evidenceSuccessRate()));
        realtimeSearch.put("fallbackProbeCount", searchSummary.fallbackProbeCount());
        realtimeSearch.put("backfillRoundsUsed", searchSummary.backfillRoundsUsed());
        realtimeSearch.put("recoveredProbeCount", searchSummary.recoveredProbeCount());
        realtimeSearch.put("qualityGatePassed", searchSummary.qualityGatePassed());
        realtimeSearch.put("unsatisfiedProbeIds", searchSummary.unsatisfiedProbeIds());
        stats.put("realtimeSearch", realtimeSearch);
        stats.put("usageCost", buildUsageCostStats(task));
        return stats;
    }

    private Map<String, Object> buildUsageCostStats(V42DiagnosisResult.Task task) {
        LinkedHashMap<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokenTotal", task.usageCostSummary().promptTokenTotal());
        usage.put("completionTokenTotal", task.usageCostSummary().completionTokenTotal());
        usage.put("totalTokenTotal", task.usageCostSummary().totalTokenTotal());
        usage.put("searchCallTotal", task.usageCostSummary().searchCallTotal());
        usage.put("modelCostYuan", task.usageCostSummary().modelCostYuan());
        usage.put("searchPluginCostYuan", task.usageCostSummary().searchPluginCostYuan());
        usage.put("totalCostYuan", task.usageCostSummary().totalCostYuan());
        usage.put("currency", "CNY");
        return usage;
    }

    private Map<String, Object> buildExecutionIntegrity(V42DiagnosisResult.Task task) {
        return V42ExecutionIntegrityView.toMap(task.executionSignals(), this::convertToMapList);
    }

    private List<Map<String, Object>> buildCompetitorRanking(DashboardCharts charts, KpiSummary summary) {
        List<Map<String, Object>> ranking = new ArrayList<>();
        List<DashboardCharts.ScatterPoint> points = charts.scatter().points().stream()
                .filter(point -> !point.targetBrand())
                .sorted(Comparator.comparingDouble(DashboardCharts.ScatterPoint::recommendRate).reversed())
                .toList();
        int rank = 1;
        for (DashboardCharts.ScatterPoint point : points) {
            CompetitorKpi competitorKpi = summary.competitorKpis().get(point.brandName());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            item.put("brand", point.brandName());
            item.put("mentionRate", round4(point.candidateShare()));
            item.put("recommendRate", round4(point.recommendRate()));
            item.put("positiveRate", round4(competitorKpi == null ? 0.0d : competitorKpi.positiveRate()));
            item.put("mentionCount", competitorKpi == null ? point.mentionCount() : competitorKpi.mentionCount());
            ranking.add(item);
        }
        return ranking;
    }

    private List<Map<String, Object>> buildNegativeSignals(KpiSummary summary, int sampleSize) {
        return summary.dimensionRadarScores().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(entry -> Map.<String, Object>of(
                        "signal", dimensionDisplay(entry.getKey()) + " 维度偏弱",
                        "count", toCount(1.0d - entry.getValue(), sampleSize)
                ))
                .toList();
    }

    private List<Map<String, Object>> buildAssociatedKeywords(KpiSummary summary, int sampleSize) {
        return summary.dimensionPenetration().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(entry -> Map.<String, Object>of(
                        "keyword", dimensionDisplay(entry.getKey()),
                        "count", toCount(entry.getValue(), sampleSize)
                ))
                .toList();
    }

    private Map<String, Object> buildActionItems(CommercialInsights insights) {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("p0", List.of(insights.topThreeInsights().get(0).recommendation()));
        items.put("p1", List.of(insights.topThreeInsights().get(1).recommendation()));
        items.put("p2", List.of(insights.topThreeInsights().get(2).recommendation()));
        return items;
    }

    private Map<String, Object> buildBrandPerception(KpiSummary summary,
                                                     CommercialInsights insights,
                                                     int sampleSize) {
        double positiveRate = summary.positiveRate();
        List<Map.Entry<String, Double>> topDimensions = summary.dimensionRadarScores().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMentions", toCount(summary.overallMentionRate(), sampleSize));
        result.put("positivePercent", round4(positiveRate));
        result.put("negativePercent", 0.0d);
        result.put("neutralPercent", round4(Math.max(0.0d, 1.0d - positiveRate)));
        result.put("topTags", topDimensions.stream()
                .map(entry -> Map.of("tag", dimensionDisplay(entry.getKey()), "count", toCount(entry.getValue(), sampleSize)))
                .toList());
        result.put("topPros", insights.topThreeInsights().stream().map(InsightItem::title).limit(2).toList());
        result.put("topCons", summary.dimensionRadarScores().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(2)
                .map(entry -> dimensionDisplay(entry.getKey()) + " 偏弱")
                .toList());
        result.put("oneLineSummary", insights.headlineInsight());
        return result;
    }

    private Map<String, Object> buildSemanticDistanceMap(DashboardCharts charts) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brands", charts.scatter().points().stream()
                .map(point -> Map.of(
                        "brandName", point.brandName(),
                        "distance", round4(point.brandDistance()),
                        "recommendRate", round4(point.recommendRate()),
                        "isTargetBrand", point.targetBrand()
                ))
                .toList());
        result.put("clusters", List.of());
        result.put("includedClusterCount", charts.scatter().points().size());
        result.put("insufficientClusterCount", 0);
        return result;
    }

    private Map<String, Object> buildClusterBreakdown(KpiSummary summary, int sampleSize) {
        List<Map<String, Object>> clusters = List.of(
                buildClusterDetail("orthogonal", "正交探测", summary.orthogonalKpi(), sampleSize),
                buildClusterDetail("pareto", "帕累托场景", summary.paretoKpi(), sampleSize),
                buildClusterDetail("adversarial", "对抗验证", summary.adversarialKpi(), sampleSize)
        );
        long coveredClusters = clusters.stream()
                .filter(item -> asBoolean(item.get("blindSpot")) == Boolean.FALSE)
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalClusters", clusters.size());
        result.put("coveredClusters", (int) coveredClusters);
        result.put("coverageRate", round4(clusters.isEmpty() ? 0.0d : coveredClusters * 1.0d / clusters.size()));
        result.put("clusters", clusters);
        return result;
    }

    private Map<String, Object> buildClusterDetail(String clusterId, String clusterName, BucketKpi bucketKpi, int sampleSize) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clusterId", clusterId);
        item.put("clusterName", clusterName);
        item.put("description", clusterName + " 用来验证品牌在该类问题结构中的穿透与防守表现。");
        item.put("eeatDimension", null);
        item.put("involvementLevel", null);
        item.put("weight", round4(sampleSize == 0 ? 0.0d : bucketKpi.effectiveSampleSize() * 1.0d / sampleSize));
        item.put("mentionRate", round4(bucketKpi.overallMentionRate()));
        item.put("recommendRate", round4(bucketKpi.recommendRate()));
        item.put("top3Rate", round4(bucketKpi.spontaneousMentionRate()));
        item.put("sampleCount", bucketKpi.effectiveSampleSize());
        item.put("blindSpot", bucketKpi.recommendRate() < 0.3d);
        item.put("samplePrompts", List.of());
        return item;
    }

    private Map<String, Object> buildCompetitorRadar(String targetBrand,
                                                     DashboardCharts charts,
                                                     KpiSummary summary) {
        List<Map<String, Object>> brands = new ArrayList<>();
        for (DashboardCharts.ScatterPoint point : charts.scatter().points()) {
            CompetitorKpi competitorKpi = summary.competitorKpis().get(point.brandName());
            boolean target = point.targetBrand();
            brands.add(Map.of(
                    "brandName", point.brandName(),
                    "isTargetBrand", target,
                    "mentionRate", round4(point.candidateShare()),
                    "recommendRate", round4(point.recommendRate()),
                    "top3Rate", round4(target ? summary.spontaneousMentionRate() : point.candidateShare()),
                    "positivePercent", round4(target ? summary.positiveRate() : competitorKpi == null ? 0.0d : competitorKpi.positiveRate()),
                    "stabilityScore", round4(target ? normalizeStability(summary.stabilityScore()) : competitorKpi == null ? 0.0d : competitorKpi.averageStrength())
            ));
        }
        Map.Entry<String, Double> weakest = summary.dimensionRadarScores().entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brands", brands);
        result.put("weakestDimension", weakest == null ? null : weakest.getKey());
        result.put("weakDimensionCount", (int) summary.dimensionRadarScores().values().stream().filter(value -> value < 0.4d).count());
        return result;
    }

    private List<Map<String, Object>> buildEvidenceChain(List<V42DiagnosisResult.ProbeTrace> probeTrace) {
        if (probeTrace == null || probeTrace.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> evidenceChain = new ArrayList<>();
        for (V42DiagnosisResult.ProbeTrace trace : probeTrace) {
            if (trace == null || trace.searchEvidence() == null || trace.searchEvidence().isEmpty()) {
                continue;
            }
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (SearchEvidence item : trace.searchEvidence()) {
                if (item == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceType", normalizeOptional(item.sourceType()));
                row.put("provider", normalizeOptional(item.provider()));
                row.put("url", normalizeOptional(item.url()));
                row.put("domain", domainOf(item.url()));
                row.put("title", normalizeOptional(item.title()));
                row.put("snippet", normalizeOptional(item.snippet()));
                row.put("query", normalizeOptional(item.query()));
                row.put("publishTime", normalizeOptional(item.publishTime()));
                row.put("retrievedAt", normalizeOptional(item.retrievedAt()));
                evidence.add(row);
            }
            if (evidence.isEmpty()) {
                continue;
            }
            LinkedHashSet<String> querySet = new LinkedHashSet<>();
            if (trace.searchQueries() != null) {
                for (String query : trace.searchQueries()) {
                    if (StringUtils.hasText(query)) {
                        querySet.add(query.trim());
                    }
                }
            }
            Map<String, Object> chainRow = new LinkedHashMap<>();
            chainRow.put("probeId", trace.probeId());
            chainRow.put("renderedPrompt", trace.renderedPrompt());
            chainRow.put("renderSource", asText(trace.triggeredRulesJson().get("renderSource")));
            chainRow.put("renderReason", asText(trace.triggeredRulesJson().get("renderReason")));
            chainRow.put("promptAssetVersion", asText(trace.triggeredRulesJson().get("promptAssetVersion")));
            chainRow.put("responseText", trace.responseText());
            chainRow.put("searchRequested", trace.searchRequested());
            chainRow.put("searchSatisfied", trace.searchSatisfied());
            chainRow.put("searchFallbackFlag", trace.searchFallbackFlag());
            chainRow.put("searchCallCount", trace.searchCallCount());
            chainRow.put("searchQueries", List.copyOf(querySet));
            chainRow.put("evidence", evidence);
            evidenceChain.add(chainRow);
        }
        return evidenceChain;
    }

    private List<Map<String, Object>> buildProbeRuntimeTruth(List<V42DiagnosisResult.ProbeTrace> probeTrace) {
        if (probeTrace == null || probeTrace.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (V42DiagnosisResult.ProbeTrace trace : probeTrace) {
            if (trace == null) {
                continue;
            }
            Map<String, Object> rules = trace.triggeredRulesJson() == null ? Map.of() : trace.triggeredRulesJson();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("probeId", trace.probeId());
            row.put("renderedPrompt", trace.renderedPrompt());
            row.put("renderFamily", normalizeOptional(trace.renderFamily()));
            row.put("renderSource", asText(rules.get("renderSource")));
            row.put("renderReason", asText(rules.get("renderReason")));
            row.put("fallbackUsed", rules.get("fallbackUsed"));
            row.put("failureReason", normalizeOptional(trace.failureReason()));
            row.put("validationFailedChecks", normalizeStringList(rules.get("validationFailedChecks")));
            row.put("validationFailedRuleIds", normalizeStringList(rules.get("validationFailedRuleIds")));
            row.put("behaviorRuleIds", normalizeStringList(rules.get("behaviorRuleIds")));
            row.put("validationRejectedCandidate", asText(rules.get("validationRejectedCandidate")));
            row.put("validationRepairApplied", asText(rules.get("validationRepairApplied")));
            row.put("repairUsed", StringUtils.hasText(asText(rules.get("validationRepairApplied"))));
            row.put("gateDecisionInput", normalizeObjectMap(rules.get("gateDecisionInput")));
            row.put("fallbackTriggerRule", asText(rules.get("fallbackTriggerRule")));
            row.put("fallbackFamilyRuleId", asText(rules.get("fallbackFamilyRuleId")));
            row.put("promptAssetKey", asText(rules.get("promptAssetKey")));
            row.put("promptAssetVersion", asText(rules.get("promptAssetVersion")));
            row.put("promptAssetSource", asText(rules.get("promptAssetSource")));
            row.put("promptAssetSha256", asText(rules.get("promptAssetSha256")));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildNode2RuleCatalog(List<V42DiagnosisResult.ProbeTrace> probeTrace) {
        if (probeTrace == null || probeTrace.isEmpty()) {
            return Node2RuleCatalog.snapshot(List.of());
        }
        LinkedHashSet<String> ruleIds = new LinkedHashSet<>();
        for (V42DiagnosisResult.ProbeTrace trace : probeTrace) {
            if (trace == null || trace.triggeredRulesJson() == null || trace.triggeredRulesJson().isEmpty()) {
                continue;
            }
            Map<String, Object> rules = trace.triggeredRulesJson();
            appendRuleIds(ruleIds, rules.get("must"));
            appendRuleIds(ruleIds, rules.get("should"));
            appendRuleIds(ruleIds, rules.get("validationFailedRuleIds"));
            appendRuleIds(ruleIds, rules.get("behaviorRuleIds"));
            appendRuleIds(ruleIds, rules.get("fallbackTriggerRule"));
            appendRuleIds(ruleIds, rules.get("fallbackFamilyRuleId"));
        }
        return Node2RuleCatalog.snapshot(ruleIds);
    }

    private Map<String, Object> buildSourceAttribution(V42DiagnosisResult.Task task, CommercialInsights insights) {
        List<SearchEvidence> allEvidence = new ArrayList<>();
        for (V42DiagnosisResult.ProbeTrace trace : task.probeTrace()) {
            if (trace != null && trace.searchEvidence() != null) {
                allEvidence.addAll(trace.searchEvidence());
            }
        }
        if (allEvidence.isEmpty()) {
            String sourceName = "LLM".equalsIgnoreCase(insights.insightSource()) ? "LLM 洞察" : "模板回退";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categories", List.of(Map.of("name", sourceName, "count", 0, "percent", 0.0d)));
            result.put("dominantSource", null);
            result.put("dominantPercent", 0.0d);
            result.put("topDomains", List.of());
            result.put("evidenceCount", 0);
            return result;
        }
        Map<String, Integer> counter = new LinkedHashMap<>();
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        for (SearchEvidence evidence : allEvidence) {
            if (evidence == null) {
                continue;
            }
            String category = evidenceCategoryName(evidence);
            counter.merge(category, 1, Integer::sum);
            String domain = domainOf(evidence.url());
            if (domain != null) {
                domains.add(domain);
            }
        }
        int total = counter.values().stream().mapToInt(Integer::intValue).sum();
        List<Map<String, Object>> categories = counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> Map.<String, Object>of(
                        "name", entry.getKey(),
                        "count", entry.getValue(),
                        "percent", round4(total == 0 ? 0.0d : entry.getValue() * 1.0d / total)
                ))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("dominantSource", categories.isEmpty() ? null : categories.getFirst().get("name"));
        result.put("dominantPercent", categories.isEmpty() ? 0.0d : categories.getFirst().get("percent"));
        result.put("topDomains", domains.stream().limit(10).toList());
        result.put("evidenceCount", total);
        return result;
    }

    private String evidenceCategoryName(SearchEvidence evidence) {
        if (evidence == null) {
            return "未知来源";
        }
        String sourceType = normalizeOptional(evidence.sourceType());
        if (sourceType != null) {
            return sourceType;
        }
        String provider = normalizeOptional(evidence.provider());
        return provider == null ? "未知来源" : provider;
    }

    private String domainOf(String url) {
        String normalized = normalizeOptional(url);
        if (normalized == null) {
            return null;
        }
        String withoutProtocol = normalized.replaceFirst("^https?://", "");
        int slash = withoutProtocol.indexOf('/');
        String host = slash >= 0 ? withoutProtocol.substring(0, slash) : withoutProtocol;
        return normalizeOptional(host);
    }

    private Map<String, String> buildKpiInterpretation(KpiSummary summary) {
        Map<String, String> interpretation = new LinkedHashMap<>();
        interpretation.put("mentionRate", "总体提及率 " + percent(summary.overallMentionRate()) + "，代表品牌进入候选池的广度。");
        interpretation.put("recommendRate", "推荐率 " + percent(summary.recommendRate()) + "，代表品牌被明确选择的能力。");
        interpretation.put("stability", "稳定度 " + score(summary.stabilityScore()) + "，代表跨探针表现的一致性。");
        interpretation.put("spontaneousMentionRate", "AI 首选率 " + percent(summary.spontaneousMentionRate()) + "，代表在隐藏品牌条件下被主动提到的强度。");
        interpretation.put("confirmationResponseRate", "AI 确认率 " + percent(summary.confirmationResponseRate()) + "，代表品牌已显式出现时是否仍被正面确认。");
        return interpretation;
    }

    private Map<String, Object> buildRequestSummary(Diagnosis diagnosis) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brandName", diagnosis.getBrandName());
        result.put("category", diagnosis.getCategory());
        result.put("website", diagnosis.getBrandWebsite());
        result.put("websiteDisplay", StringUtils.hasText(diagnosis.getBrandWebsite()) ? diagnosis.getBrandWebsite() : "未提供官网");
        result.put("competitors", diagnosis.getCompetitorsInput() == null ? List.of() : diagnosis.getCompetitorsInput());
        result.put("supplementalBrief", diagnosis.getUserPrompt());
        result.put("supplementalBriefDisplay", StringUtils.hasText(diagnosis.getUserPrompt()) ? diagnosis.getUserPrompt() : "未填写额外关注");
        result.put("edition", diagnosis.getEdition());
        result.put("createdAt", diagnosis.getCreatedAt() == null ? null : diagnosis.getCreatedAt().toString());
        return result;
    }

    private Map<String, Object> buildHeroSummary(KpiSummary summary,
                                                 CommercialInsights insights,
                                                 V42DiagnosisResult.Task task,
                                                 int sampleSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headline", insights.headlineInsight());
        result.put("verdict", buildVerdict(summary));
        result.put("topOpportunity", insights.topThreeInsights().get(0).recommendation());
        result.put("topRisk", summary.dimensionRadarScores().entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(entry -> dimensionDisplay(entry.getKey()) + " 维度仍是当前主要短板")
                .orElse("当前仍需继续放大品牌确定性"));
        result.put("kpis", List.of(
                heroKpi("overall-mention-rate", "总体提及率", percent(summary.overallMentionRate()),
                        toCount(summary.overallMentionRate(), sampleSize) + " / " + sampleSize + " probes"),
                heroKpi("recommend-rate", "推荐率", percent(summary.recommendRate()),
                        toCount(summary.recommendRate(), sampleSize) + " / " + sampleSize + " probes"),
                heroKpi("spontaneous-mention-rate", "AI 首选率", percent(summary.spontaneousMentionRate()),
                        toCount(summary.spontaneousMentionRate(), sampleSize) + " / " + sampleSize + " probes"),
                heroKpi("stability", "稳定度", score(summary.stabilityScore()),
                        task.successfulProbes() + " 条有效采样")
        ));
        return result;
    }

    private Map<String, Object> heroKpi(String key, String label, String value, String context) {
        return Map.of(
                "key", key,
                "label", label,
                "value", value,
                "context", context,
                "availability", "ready"
        );
    }

    private String buildVerdict(KpiSummary summary) {
        if (summary.recommendRate() >= 0.5d && summary.spontaneousMentionRate() >= 0.3d) {
            return "品牌已经形成较强 AI 首选信号，但还可以继续扩大稳定优势。";
        }
        if (summary.overallMentionRate() >= 0.3d || summary.recommendRate() >= 0.2d) {
            return "品牌已经进入 AI 候选池，但还需要把被提到推进到被优先选择。";
        }
        return "当前仍以偶发露出为主，核心价值信号还没有稳定打透。";
    }

    private Map<String, Object> convertToMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            Map<String, Object> mapped = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
            });
            return mapped == null ? Map.of() : mapped;
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> convertToMapList(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> mapped = objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {
            });
            return mapped == null ? List.of() : mapped;
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
            raw.forEach((key, item) -> mapped.put(String.valueOf(key), item));
            return mapped;
        }
        return Map.of();
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text);
        }
        return Boolean.FALSE;
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                String text = normalizeOptional(String.valueOf(item));
                if (text != null) {
                    normalized.add(text);
                }
            }
        }
        return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
    }

    private void appendRuleIds(LinkedHashSet<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    String text = normalizeOptional(String.valueOf(item));
                    if (text != null) {
                        target.add(text);
                    }
                }
            }
            return;
        }
        String text = normalizeOptional(String.valueOf(value));
        if (text != null) {
            target.add(text);
        }
    }

    private Map<String, Object> normalizeObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int toCount(double rate, int sampleSize) {
        return (int) Math.round(Math.max(0.0d, Math.min(1.0d, rate)) * Math.max(0, sampleSize));
    }

    private double normalizeStability(double stabilityScore) {
        return Math.max(0.0d, Math.min(1.0d, stabilityScore / 100.0d));
    }

    private String dimensionDisplay(String key) {
        return switch (key) {
            case "D1" -> "认知异常";
            case "D2" -> "目标导向";
            case "D3" -> "价值驱动";
            case "D4" -> "认知负荷";
            case "D5" -> "时间约束";
            case "D6" -> "交互模态";
            default -> key;
        };
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private String percent(double value) {
        return String.format("%.0f%%", Math.max(0.0d, Math.min(1.0d, value)) * 100.0d);
    }

    private String score(double value) {
        return String.format("%.0f/100", Math.max(0.0d, Math.min(1.0d, value / 100.0d)) * 100.0d);
    }

}

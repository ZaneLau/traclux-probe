package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.geoprobe.dto.response.ClusterDetail;
import com.geoprobe.dto.response.ReportResponse;
import com.geoprobe.dto.workbench.WorkbenchAnalysisContextRequest;
import com.geoprobe.dto.workbench.WorkbenchAnalysisContextView;
import com.geoprobe.dto.workbench.WorkbenchBrandPerformanceView;
import com.geoprobe.dto.workbench.WorkbenchBrandIdentityView;
import com.geoprobe.dto.workbench.WorkbenchCitationSourcesView;
import com.geoprobe.dto.workbench.WorkbenchDataAvailabilityView;
import com.geoprobe.dto.workbench.WorkbenchOverviewView;
import com.geoprobe.dto.workbench.WorkbenchProblemAnalysisView;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.DiagnosisTaskCompetitor;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetMetricRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetProblemRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetSourceRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetHistorySyncService;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskCompetitorMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkbenchProjectionService {

    private static final int TASK_STATUS_PENDING = 0;
    private static final String METRIC_TOPIC = "topic";
    private static final String METRIC_SAMPLE_SIZE = "kpi.sample_size";
    private static final String METRIC_VISIBILITY_SCORE = "visibility.score";
    private static final String METRIC_VISIBILITY_MENTION_RATE = "visibility.mention_rate";
    private static final String METRIC_VISIBILITY_RECOMMEND_RATE = "visibility.recommend_rate";
    private static final String METRIC_VISIBILITY_TOP3_RATE = "visibility.top3_rate";
    private static final String METRIC_VISIBILITY_AVERAGE_POSITION = "visibility.average_position";
    private static final String METRIC_VISIBILITY_MENTION_COUNT = "visibility.mention_count";
    private static final int TASK_STATUS_RUNNING = 1;
    private static final int TASK_STATUS_COMPLETED = 2;
    private static final int TASK_STATUS_FAILED = 3;
    private static final int TASK_STATUS_CANCELLED = 4;
    private static final int TASK_STATUS_QUEUED = 5;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String ROUTE_ENGINE = "/engine";
    private static final String ROUTE_PROBE_ANALYSIS = "/probe-analysis";
    private static final String ROUTE_SOURCES = "/sources";
    private static final String SOURCE_TYPE_EDITORIAL = "editorial";
    private static final String SOURCE_TYPE_CORPORATE = "corporate";
    private static final String SOURCE_TYPE_UGC = "ugc";
    private static final String SOURCE_TYPE_REFERENCE = "reference";
    private static final String SOURCE_TYPE_OWNED = "you";
    private static final String PROBLEM_SPACE_BRAND_AWARENESS = "brand-awareness";
    private static final String PROBLEM_SPACE_CAPABILITY_VALIDATION = "capability-validation";
    private static final String PROBLEM_SPACE_SCENARIO_FIT = "scenario-fit";
    private static final String PROBLEM_SPACE_RISK_OBJECTION = "risk-objection";
    private static final String PROBLEM_SPACE_COMPETITIVE_DECISION = "competitive-decision";
    private static final List<String> PROBLEM_SPACE_BUCKET_ORDER = List.of(
            PROBLEM_SPACE_BRAND_AWARENESS,
            PROBLEM_SPACE_CAPABILITY_VALIDATION,
            PROBLEM_SPACE_SCENARIO_FIT,
            PROBLEM_SPACE_RISK_OBJECTION,
            PROBLEM_SPACE_COMPETITIVE_DECISION
    );
    private static final Map<String, ProblemSpaceBucketDefinition> PROBLEM_SPACE_BUCKET_DEFINITIONS = Map.ofEntries(
            Map.entry(PROBLEM_SPACE_BRAND_AWARENESS, new ProblemSpaceBucketDefinition(
                    PROBLEM_SPACE_BRAND_AWARENESS,
                    "品牌认知",
                    "用户先确认品牌是谁、定位如何、值不值得进入候选池。"
            )),
            Map.entry(PROBLEM_SPACE_CAPABILITY_VALIDATION, new ProblemSpaceBucketDefinition(
                    PROBLEM_SPACE_CAPABILITY_VALIDATION,
                    "能力确认",
                    "用户在确认产品性能、功能、核心能力到底能不能打。"
            )),
            Map.entry(PROBLEM_SPACE_SCENARIO_FIT, new ProblemSpaceBucketDefinition(
                    PROBLEM_SPACE_SCENARIO_FIT,
                    "场景适配",
                    "用户在判断它是否适合自己的安装条件、使用环境和具体场景。"
            )),
            Map.entry(PROBLEM_SPACE_RISK_OBJECTION, new ProblemSpaceBucketDefinition(
                    PROBLEM_SPACE_RISK_OBJECTION,
                    "风险异议",
                    "用户在提前排雷，验证质量、售后、稳定性与负面风险。"
            )),
            Map.entry(PROBLEM_SPACE_COMPETITIVE_DECISION, new ProblemSpaceBucketDefinition(
                    PROBLEM_SPACE_COMPETITIVE_DECISION,
                    "竞品决策",
                    "用户已经进入选择阶段，开始比较价格、替代关系和最终推荐。"
            ))
    );
    private static final Map<String, String> SOURCE_DISPLAY_NAME_BY_SUFFIX = Map.ofEntries(
            Map.entry("autohome.com.cn", "汽车之家"),
            Map.entry("pcauto.com.cn", "太平洋汽车"),
            Map.entry("sina.cn", "新浪新闻"),
            Map.entry("sina.com.cn", "新浪新闻"),
            Map.entry("cnr.cn", "央广网"),
            Map.entry("baike.baidu.com", "百度百科"),
            Map.entry("zhihu.com", "知乎"),
            Map.entry("xiaohongshu.com", "小红书"),
            Map.entry("weibo.com", "微博"),
            Map.entry("bilibili.com", "哔哩哔哩"),
            Map.entry("smzdm.com", "什么值得买")
    );
    private static final List<Map.Entry<String, String>> SOURCE_TYPE_BY_DOMAIN_SUFFIX = List.of(
            Map.entry("baike.baidu.com", SOURCE_TYPE_REFERENCE),
            Map.entry("mofcom.gov.cn", SOURCE_TYPE_REFERENCE),
            Map.entry("ceva.org.cn", SOURCE_TYPE_REFERENCE),
            Map.entry("cnpp.cn", SOURCE_TYPE_REFERENCE),
            Map.entry("gov.cn", SOURCE_TYPE_REFERENCE),
            Map.entry("org.cn", SOURCE_TYPE_REFERENCE),
            Map.entry("zhihu.com", SOURCE_TYPE_UGC),
            Map.entry("xiaohongshu.com", SOURCE_TYPE_UGC),
            Map.entry("weibo.com", SOURCE_TYPE_UGC),
            Map.entry("bilibili.com", SOURCE_TYPE_UGC),
            Map.entry("xueqiu.com", SOURCE_TYPE_UGC),
            Map.entry("smzdm.com", SOURCE_TYPE_UGC),
            Map.entry("autohome.com.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("pcauto.com.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("dongchedi.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("bitauto.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("yiche.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("sina.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("sina.com.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("cnr.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("qq.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("sohu.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("stockstar.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("people.com.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("china.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("donews.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("geeknev.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("elecfans.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("hexun.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("vzkoo.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("zqrb.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("bjd.com.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("ctdsb.net", SOURCE_TYPE_EDITORIAL),
            Map.entry("cri.cn", SOURCE_TYPE_EDITORIAL),
            Map.entry("maiche.com", SOURCE_TYPE_EDITORIAL),
            Map.entry("bianews.com", SOURCE_TYPE_EDITORIAL)
    );
    private static final List<String> OWNED_DOMAIN_FAMILY_SUFFIXES = List.of(
            "international",
            "global",
            "motors",
            "energy",
            "group",
            "world",
            "china",
            "auto",
            "tech",
            "home",
            "life",
            "intl",
            "corp",
            "ev",
            "cn"
    );
    private static final List<String> OVERVIEW_COMPETITOR_METRIC_PRIORITY = List.of(
            "visibilityScore",
            "recommendRate",
            "shareOfVoice",
            "mentionRate",
            "positiveRate"
    );
    private static final Set<String> COUNTRY_CODE_SECOND_LEVEL_SUFFIXES = Set.of(
            "ac",
            "co",
            "com",
            "edu",
            "gov",
            "mil",
            "net",
            "org"
    );

    private final ServiceAccessService serviceAccessService;
    private final BrandScopeMapper brandScopeMapper;
    private final BrandMapper brandMapper;
    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisTaskCompetitorMapper diagnosisTaskCompetitorMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final KnowledgeAssetProperties knowledgeAssetProperties;
    private final ObjectProvider<KnowledgeAssetHistorySyncService> knowledgeAssetHistorySyncServiceProvider;
    private final ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetEvidenceRepository> knowledgeAssetEvidenceRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetMetricRepository> knowledgeAssetMetricRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetProblemRepository> knowledgeAssetProblemRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetSourceRepository> knowledgeAssetSourceRepositoryProvider;
    public WorkbenchOverviewView getOverview(String userId, String brandSpaceId) {
        return getOverview(userId, brandSpaceId, WorkbenchAnalysisContextRequest.defaults());
    }

    public WorkbenchOverviewView getOverview(String userId, String brandSpaceId, WorkbenchAnalysisContextRequest analysisRequest) {
        BrandSpaceContext context = loadContext(userId, brandSpaceId);
        BrandSpaceContext sourceContext = context;
        WorkbenchAnalysisContextView analysisContext = buildAnalysisContext(analysisRequest, "overview", Map.of());
        boolean aggregateWindow = shouldAggregateWindow(analysisContext);
        KnowledgeWorkbenchSelection knowledgeSelection = resolveKnowledgeWorkbenchSelection(context, analysisContext);
        List<DiagnosisReportRecord> scopedReports;
        List<DiagnosisReportRecord> recentReports;
        if (knowledgeSelection != null) {
            scopedReports = knowledgeSelection.windowReports();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = knowledgeSelection.filteredReports();
            context = contextWithCurrentReport(context, recentReports.isEmpty() ? null : recentReports.getFirst(), true);
            markKnowledgeWorkbenchAnalysisContext(analysisContext, knowledgeSelection.selection(), "overview");
        } else if (shouldEnforceKnowledgeWorkbenchRead()) {
            scopedReports = List.of();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = List.of();
            context = contextWithCurrentReport(context, null, true);
            markStrictKnowledgeWorkbenchAnalysisContext(analysisContext, "overview");
        } else {
            scopedReports = filterReportsByTimeContext(
                    loadRecentReports(context, resolveRecentReportLimit(analysisContext, 8)),
                    analysisContext
            );
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = filterReportsByPlatforms(scopedReports, analysisContext);
            context = contextWithCurrentReport(context, selectCurrentReport(context, recentReports, analysisContext), aggregateWindow);
        }
        ReportResponse latestReport = toReportResponse(context.latestReport());
        List<DiagnosisReportRecord> comparisonReports = knowledgeSelection == null
                ? List.of()
                : buildKnowledgeComparisonReports(knowledgeSelection, analysisContext);
        List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints = buildTrendPoints(context, recentReports, analysisContext);
        WorkbenchCitationSourcesView citationSourcesView = buildCitationSourcesInternal(context, recentReports, analysisContext, aggregateWindow);
        List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows = aggregateWindow
                ? knowledgeSelection == null
                ? buildWindowCompetitorRows(context, recentReports)
                : buildKnowledgeWindowCompetitorRows(context, recentReports, comparisonReports)
                : buildCompetitorRows(context, latestReport);
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = aggregateWindow
                ? buildWindowModelPerformanceRows(context, recentReports)
                : buildModelPerformanceRows(context, recentReports);
        List<WorkbenchOverviewView.PriorityItemView> priorities = buildPriorityItems(latestReport);
        WorkbenchOverviewView.CurrentPerformanceView currentPerformance = aggregateWindow
                ? buildWindowCurrentPerformance(context, latestReport, recentReports, competitorRows)
                : buildCurrentPerformance(context, latestReport, competitorRows);
        modelRows = normalizeModelPerformanceRows(modelRows, currentPerformance);
        WorkbenchOverviewView.TrendView trend = buildTrendView(trendPoints);
        WorkbenchDataAvailabilityView availability = buildAvailability(
                context,
                context.latestReport() != null,
                collectOverviewMissingFields(currentPerformance, competitorRows, citationSourcesView, modelRows, priorities),
                analysisContext
        );
        boolean knowledgeWindowEmpty = shouldUseKnowledgeWindowEmptyAvailability(knowledgeSelection, recentReports);
        if (knowledgeWindowEmpty) {
            availability = buildKnowledgeWindowEmptyAvailability(sourceContext, analysisContext);
        }
        List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries =
                buildWorkbenchTopicSummaries(context, latestReport, recentReports, analysisContext, knowledgeSelection);
        WorkbenchBrandPerformanceView.SummaryMetricView overviewBrandPerformanceSummary = buildWindowBrandPerformanceSummary(
                context,
                latestReport,
                recentReports,
                competitorRows,
                topicSummaries
        );
        WorkbenchBrandPerformanceView overviewBrandPerformanceBasis = WorkbenchBrandPerformanceView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context.latestReport() == null ? null : context.latestReport().getId())
                .lastGeneratedAt(formatDateTime(context.latestReport() == null ? null : context.latestReport().getGeneratedAt()))
                .analysisContext(analysisContext)
                .dataAvailability(availability)
                .summary(overviewBrandPerformanceSummary)
                .competitorRows(competitorRows)
                .modelPerformance(modelRows)
                .trendPoints(trendPoints)
                .topicSummaries(topicSummaries)
                .build();
        WorkbenchBrandPerformanceView overviewBrandPerformanceView = overviewBrandPerformanceBasis;
        WorkbenchCitationSourcesView overviewCitationSourcesView = citationSourcesView;
        List<WorkbenchBrandPerformanceView.CompetitorRowView> overviewCompetitorRows =
                overviewBrandPerformanceView == null ? competitorRows : overviewBrandPerformanceView.getCompetitorRows();
        List<WorkbenchBrandPerformanceView.TrendPointView> overviewTrendPoints =
                overviewBrandPerformanceView == null
                        || overviewBrandPerformanceView.getTrendPoints() == null
                        || overviewBrandPerformanceView.getTrendPoints().isEmpty()
                        ? trendPoints
                        : overviewBrandPerformanceView.getTrendPoints();
        WorkbenchOverviewView.CurrentPerformanceView overviewCurrentPerformance = overviewBrandPerformanceView == null
                ? currentPerformance
                : buildOverviewCurrentPerformanceFromBrandPerformance(
                overviewBrandPerformanceView.getSummary(),
                overviewCompetitorRows,
                currentPerformance
        );
        overviewCurrentPerformance = applyFirstBatchMetricTruthToCurrentPerformance(
                context,
                analysisContext,
                overviewCurrentPerformance
        );
        WorkbenchOverviewView.TrendView overviewTrend = buildTrendView(overviewTrendPoints);
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> overviewModelRows =
                overviewBrandPerformanceView == null
                        || overviewBrandPerformanceView.getModelPerformance() == null
                        || overviewBrandPerformanceView.getModelPerformance().isEmpty()
                        ? modelRows
                        : overviewBrandPerformanceView.getModelPerformance();
        overviewModelRows = normalizeModelPerformanceRows(overviewModelRows, overviewCurrentPerformance);
        List<WorkbenchOverviewView.CompetitorSummaryView> overviewCompetitorSummaryRows =
                toOverviewCompetitorRows(overviewCompetitorRows);
        List<WorkbenchOverviewView.PriorityItemView> resolvedPriorities = knowledgeWindowEmpty
                ? List.of()
                : knowledgeSelection == null
                ? priorities
                : buildKnowledgePriorityItems(overviewBrandPerformanceView, overviewCitationSourcesView);
        WorkbenchOverviewView.LatestInsightView fallbackLatestInsight = buildLatestInsight(context, latestReport);
        WorkbenchOverviewView.LatestInsightView latestInsight = knowledgeWindowEmpty
                ? null
                : knowledgeSelection == null
                ? fallbackLatestInsight
                : buildKnowledgeLatestInsight(
                overviewBrandPerformanceView,
                overviewCitationSourcesView,
                resolvedPriorities,
                fallbackLatestInsight
        );
        WorkbenchDataAvailabilityView overviewAvailability = reconcileOverviewAvailabilityAfterPreferredRead(
                availability,
                overviewCurrentPerformance,
                overviewCompetitorRows,
                overviewCitationSourcesView,
                overviewModelRows
        );

        return WorkbenchOverviewView.builder()
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context.latestReport() == null ? null : context.latestReport().getId())
                .lastGeneratedAt(formatDateTime(context.latestReport() == null ? null : context.latestReport().getGeneratedAt()))
                .analysisContext(analysisContext)
                .dataAvailability(overviewAvailability)
                .cards(buildOverviewCards(overviewCurrentPerformance, overviewTrend, resolvedPriorities, overviewCompetitorSummaryRows, overviewCitationSourcesView, overviewModelRows))
                .currentPerformance(overviewCurrentPerformance)
                .latestInsight(latestInsight)
                .trend(overviewTrend)
                .priorities(resolvedPriorities)
                .competitorRows(overviewCompetitorSummaryRows)
                .sourceRows(toOverviewSourceRows(overviewCitationSourcesView))
                .modelPerformance(toOverviewModelRows(overviewModelRows))
                .detailRoutes(Map.of(
                        "engine", ROUTE_ENGINE,
                        "probe_analysis", ROUTE_PROBE_ANALYSIS,
                        "sources", ROUTE_SOURCES
                ))
                .build();
    }

    public WorkbenchBrandPerformanceView getBrandPerformance(String userId, String brandSpaceId) {
        return getBrandPerformance(userId, brandSpaceId, WorkbenchAnalysisContextRequest.defaults());
    }

    public WorkbenchBrandPerformanceView getBrandPerformance(String userId,
                                                             String brandSpaceId,
                                                             WorkbenchAnalysisContextRequest analysisRequest) {
        BrandSpaceContext context = loadContext(userId, brandSpaceId);
        BrandSpaceContext sourceContext = context;
        WorkbenchAnalysisContextView analysisContext = buildAnalysisContext(analysisRequest, "brand-performance", Map.of());
        boolean aggregateWindow = shouldAggregateWindow(analysisContext);
        KnowledgeWorkbenchSelection knowledgeSelection = resolveKnowledgeWorkbenchSelection(context, analysisContext);
        List<DiagnosisReportRecord> scopedReports;
        List<DiagnosisReportRecord> recentReports;
        if (knowledgeSelection != null) {
            scopedReports = knowledgeSelection.windowReports();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = knowledgeSelection.filteredReports();
            context = contextWithCurrentReport(context, recentReports.isEmpty() ? null : recentReports.getFirst(), true);
            markKnowledgeWorkbenchAnalysisContext(analysisContext, knowledgeSelection.selection(), "brand-performance");
        } else if (shouldEnforceKnowledgeWorkbenchRead()) {
            scopedReports = List.of();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = List.of();
            context = contextWithCurrentReport(context, null, true);
            markStrictKnowledgeWorkbenchAnalysisContext(analysisContext, "brand-performance");
        } else {
            scopedReports = filterReportsByTimeContext(
                    loadRecentReports(context, resolveRecentReportLimit(analysisContext, 12)),
                    analysisContext
            );
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = filterReportsByPlatforms(scopedReports, analysisContext);
            context = contextWithCurrentReport(context, selectCurrentReport(context, recentReports, analysisContext), aggregateWindow);
        }
        DiagnosisReportRecord latestReportRecord = context.latestReport();
        DiagnosisReportRecord previousReportRecord = recentReports.size() > 1
                ? hydrateReportPayload(context.scope().getId(), recentReports.get(1))
                : null;
        ReportResponse latestReport = toReportResponse(latestReportRecord);
        ReportResponse previousReport = toReportResponse(previousReportRecord);
        Map<String, Double> currentAveragePositionByBrand = loadAveragePositionByBrand(
                latestReportRecord == null ? null : latestReportRecord.getDiagnosisTaskId()
        );
        Map<String, Double> previousAveragePositionByBrand = loadAveragePositionByBrand(
                previousReportRecord == null ? null : previousReportRecord.getDiagnosisTaskId()
        );
        Map<String, Double> currentTop3RateByBrand = loadTop3RateByBrand(
                latestReportRecord == null ? null : latestReportRecord.getDiagnosisTaskId(),
                latestReportRecord == null ? null : latestReportRecord.getSampleSize()
        );
        Map<String, Double> previousTop3RateByBrand = loadTop3RateByBrand(
                previousReportRecord == null ? null : previousReportRecord.getDiagnosisTaskId(),
                previousReportRecord == null ? null : previousReportRecord.getSampleSize()
        );
        List<DiagnosisReportRecord> comparisonReports = knowledgeSelection == null
                ? List.of()
                : buildKnowledgeComparisonReports(knowledgeSelection, analysisContext);
        List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows = aggregateWindow
                ? knowledgeSelection == null
                ? buildWindowCompetitorRows(context, recentReports)
                : buildKnowledgeWindowCompetitorRows(context, recentReports, comparisonReports)
                : buildCompetitorRows(
                context,
                latestReportRecord,
                previousReportRecord,
                latestReport,
                previousReport,
                currentAveragePositionByBrand,
                previousAveragePositionByBrand,
                currentTop3RateByBrand,
                previousTop3RateByBrand
        );
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows = aggregateWindow
                ? buildWindowModelPerformanceRows(context, recentReports)
                : buildModelPerformanceRows(context, recentReports);
        List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints = buildTrendPoints(context, recentReports, analysisContext);
        List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries =
                buildWorkbenchTopicSummaries(context, latestReport, recentReports, analysisContext, knowledgeSelection);
        WorkbenchBrandPerformanceView.SummaryMetricView summary = aggregateWindow
                ? buildWindowBrandPerformanceSummary(context, latestReport, recentReports, competitorRows, topicSummaries)
                : buildBrandPerformanceSummary(
                context,
                latestReportRecord,
                previousReportRecord,
                latestReport,
                competitorRows,
                currentAveragePositionByBrand,
                previousAveragePositionByBrand,
                currentTop3RateByBrand,
                topicSummaries
        );
        modelRows = normalizeModelPerformanceRows(modelRows, summary);
        WorkbenchDataAvailabilityView availability = buildAvailability(
                context,
                latestReportRecord != null,
                collectBrandPerformanceMissingFields(summary, competitorRows, modelRows, trendPoints, topicSummaries),
                analysisContext
        );
        boolean knowledgeWindowEmpty = shouldUseKnowledgeWindowEmptyAvailability(knowledgeSelection, recentReports);
        if (knowledgeWindowEmpty) {
            availability = buildKnowledgeWindowEmptyAvailability(sourceContext, analysisContext);
        }

        WorkbenchBrandPerformanceView view = WorkbenchBrandPerformanceView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context.latestReport() == null ? null : context.latestReport().getId())
                .lastGeneratedAt(formatDateTime(context.latestReport() == null ? null : context.latestReport().getGeneratedAt()))
                .analysisContext(analysisContext)
                .dataAvailability(availability)
                .summary(summary)
                .competitorRows(competitorRows)
                .modelPerformance(modelRows)
                .trendPoints(trendPoints)
                .topicSummaries(topicSummaries)
                .build();
        WorkbenchBrandPerformanceView resolvedView = view;
        if (resolvedView != null) {
            resolvedView.setModelPerformance(normalizeModelPerformanceRows(
                    resolvedView.getModelPerformance(),
                    resolvedView.getSummary()
            ));
            resolvedView.setDataAvailability(reconcileBrandPerformanceAvailabilityAfterPreferredRead(
                    resolvedView.getDataAvailability(),
                    resolvedView.getSummary(),
                    resolvedView.getCompetitorRows(),
                    resolvedView.getModelPerformance(),
                    resolvedView.getTrendPoints(),
                    resolvedView.getTopicSummaries()
            ));
        }
        return resolvedView;
    }

    public WorkbenchCitationSourcesView getCitationSources(String userId, String brandSpaceId) {
        return getCitationSources(userId, brandSpaceId, WorkbenchAnalysisContextRequest.defaults());
    }

    public WorkbenchCitationSourcesView getCitationSources(String userId,
                                                           String brandSpaceId,
                                                           WorkbenchAnalysisContextRequest analysisRequest) {
        BrandSpaceContext context = loadContext(userId, brandSpaceId);
        BrandSpaceContext sourceContext = context;
        WorkbenchAnalysisContextView analysisContext = buildAnalysisContext(analysisRequest, "citation-sources", Map.of());
        boolean aggregateWindow = shouldAggregateWindow(analysisContext);
        KnowledgeWorkbenchSelection knowledgeSelection = resolveKnowledgeWorkbenchSelection(context, analysisContext);
        List<DiagnosisReportRecord> scopedReports;
        List<DiagnosisReportRecord> recentReports;
        if (knowledgeSelection != null) {
            scopedReports = knowledgeSelection.windowReports();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = knowledgeSelection.filteredReports();
            context = contextWithCurrentReport(context, recentReports.isEmpty() ? null : recentReports.getFirst(), true);
            markKnowledgeWorkbenchAnalysisContext(analysisContext, knowledgeSelection.selection(), "citation-sources");
        } else if (shouldEnforceKnowledgeWorkbenchRead()) {
            scopedReports = List.of();
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = List.of();
            context = contextWithCurrentReport(context, null, true);
            markStrictKnowledgeWorkbenchAnalysisContext(analysisContext, "citation-sources");
        } else {
            scopedReports = filterReportsByTimeContext(
                    loadRecentReports(context, resolveRecentReportLimit(analysisContext, 8)),
                    analysisContext
            );
            populateAnalysisContextPlatforms(analysisContext, scopedReports);
            recentReports = filterReportsByPlatforms(scopedReports, analysisContext);
            context = contextWithCurrentReport(context, selectCurrentReport(context, recentReports, analysisContext), aggregateWindow);
        }
        WorkbenchCitationSourcesView view = buildCitationSourcesInternal(context, recentReports, analysisContext, aggregateWindow);
        boolean knowledgeWindowEmpty = shouldUseKnowledgeWindowEmptyAvailability(knowledgeSelection, recentReports);
        if (knowledgeWindowEmpty) {
            view.setDataAvailability(buildKnowledgeWindowEmptyAvailability(sourceContext, analysisContext));
        }
        view.setAnalysisContext(analysisContext);
        WorkbenchCitationSourcesView resolvedView = view;
        if (resolvedView != null) {
            resolvedView.setDataAvailability(reconcileCitationSourcesAvailabilityAfterPreferredRead(
                    resolvedView.getDataAvailability(),
                    resolvedView.getSummary(),
                    resolvedView.getCategorySlices(),
                    resolvedView.getDomainRows(),
                    resolvedView.getUrlRows()
            ));
        }
        return resolvedView;
    }

    public WorkbenchProblemAnalysisView getProblemAnalysis(String userId, String brandSpaceId) {
        return getProblemAnalysis(userId, brandSpaceId, null, null, WorkbenchAnalysisContextRequest.defaults());
    }

    public WorkbenchProblemAnalysisView getProblemAnalysis(String userId,
                                                           String brandSpaceId,
                                                           List<String> topicNames,
                                                           List<String> problemSpaceBuckets) {
        return getProblemAnalysis(userId, brandSpaceId, topicNames, problemSpaceBuckets, WorkbenchAnalysisContextRequest.defaults());
    }

    public WorkbenchProblemAnalysisView getProblemAnalysis(String userId,
                                                           String brandSpaceId,
                                                           List<String> topicNames,
                                                           List<String> problemSpaceBuckets,
                                                           WorkbenchAnalysisContextRequest analysisRequest) {
        BrandSpaceContext context = loadContext(userId, brandSpaceId);
        BrandSpaceContext sourceContext = context;
        ProblemAnalysisScope scope = ProblemAnalysisScope.of(topicNames, problemSpaceBuckets);
        WorkbenchAnalysisContextView analysisContext = buildAnalysisContext(analysisRequest, "problem-analysis",
                Map.of(
                        "topicNames", topicNames == null ? List.of() : topicNames,
                        "problemSpaceBuckets", problemSpaceBuckets == null ? List.of() : problemSpaceBuckets
                ));
        WorkbenchProblemAnalysisView formalView = tryBuildFormalProblemAnalysisView(
                context,
                scope,
                topicNames,
                problemSpaceBuckets,
                analysisContext
        );
        if (formalView != null) {
            formalView.setDataAvailability(reconcileProblemAnalysisAvailabilityAfterPreferredRead(
                    formalView.getDataAvailability(),
                    formalView.getSummary(),
                    formalView.getTopicRows(),
                    formalView.getPromptRows(),
                    formalView.getProblemClusters(),
                    formalView.getProbeRows(),
                    formalView.getAttemptRows(),
                    formalView.getEvidenceRows()
            ));
            return formalView;
        }
        if (shouldEnforceKnowledgeWorkbenchRead()) {
            return buildStrictKnowledgeProblemAnalysisEmptyView(sourceContext, scope, analysisContext);
        }
        boolean aggregateWindow = shouldAggregateWindow(analysisContext);
        List<DiagnosisReportRecord> scopedReports = filterReportsByTimeContext(
                loadRecentReports(context, resolveRecentReportLimit(analysisContext, 8)),
                analysisContext
        );
        populateAnalysisContextPlatforms(analysisContext, scopedReports);
        List<DiagnosisReportRecord> recentReports = scopedReports;
        context = contextWithCurrentReport(context, selectCurrentReport(context, recentReports, analysisContext), aggregateWindow);
        DiagnosisReportRecord latestReportRecord = context.latestReport();
        ReportResponse latestReport = toReportResponse(latestReportRecord);
        ProblemAnalysisFrame currentFrame = aggregateWindow
                ? aggregateProblemAnalysisFrames(context, recentReports)
                : buildProblemAnalysisFrame(context, latestReportRecord, latestReport);
        DiagnosisReportRecord previousReportRecord = recentReports.size() > 1
                ? hydrateReportPayload(context.scope().getId(), recentReports.get(1))
                : null;
        ProblemAnalysisFrame previousFrame = previousReportRecord == null
                ? ProblemAnalysisFrame.empty()
                : buildProblemAnalysisFrame(
                context,
                previousReportRecord,
                toReportResponse(previousReportRecord)
        );
        populateProblemAnalysisPlatforms(analysisContext, currentFrame, previousFrame);
        ProblemAnalysisFrame filteredCurrentFrame = applyProblemAnalysisAnalysisContext(currentFrame, analysisContext);
        ProblemAnalysisFrame filteredPreviousFrame = applyProblemAnalysisAnalysisContext(previousFrame, analysisContext);
        ProblemAnalysisFrame scopedCurrentFrame = applyProblemAnalysisScope(filteredCurrentFrame, scope);
        ProblemAnalysisFrame scopedPreviousFrame = applyProblemAnalysisScope(filteredPreviousFrame, scope);

        int rawAnswerCount = (int) scopedCurrentFrame.probeRows().stream()
                .filter(row -> StringUtils.hasText(row.getRawAnswer()))
                .count();
        int linkedProbeCount = (int) scopedCurrentFrame.probeRows().stream()
                .filter(this::hasPromptOrTopicLink)
                .count();
        int linkedEvidenceCount = (int) scopedCurrentFrame.evidenceRows().stream()
                .filter(this::hasPromptOrTopicLink)
                .count();
        int linkedPromptCount = (int) scopedCurrentFrame.promptRows().stream()
                .filter(row -> safeInt(row.getProbeCount()) > 0 || safeInt(row.getEvidenceCount()) > 0 || Boolean.TRUE.equals(row.getHasRawAnswer()))
                .count();

        boolean explicitFilterEmpty = hasExplicitProjectionFilter(analysisContext)
                && isProblemAnalysisFilterEmpty(scopedCurrentFrame);
        WorkbenchDataAvailabilityView availability = explicitFilterEmpty
                ? buildFilteredWindowEmptyAvailability(context)
                : refineProblemAnalysisAvailability(
                buildAvailability(
                        context,
                        latestReportRecord != null,
                        collectProblemAnalysisMissingFields(
                                scopedCurrentFrame.topicRows(),
                                scopedCurrentFrame.promptRows(),
                                scopedCurrentFrame.problemClusters(),
                                scopedCurrentFrame.probeRows(),
                                scopedCurrentFrame.attemptRows(),
                                scopedCurrentFrame.evidenceRows(),
                                rawAnswerCount,
                                linkedPromptCount,
                                linkedProbeCount,
                                linkedEvidenceCount
                        ),
                        analysisContext
                ),
                rawAnswerCount
        );

        WorkbenchProblemAnalysisView view = WorkbenchProblemAnalysisView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context.latestReport() == null ? null : context.latestReport().getId())
                .lastGeneratedAt(formatDateTime(context.latestReport() == null ? null : context.latestReport().getGeneratedAt()))
                .analysisContext(analysisContext)
                .dataAvailability(availability)
                .summary(WorkbenchProblemAnalysisView.SummaryView.builder()
                        .topicCount(scopedCurrentFrame.topicRows().size())
                        .promptCount(scopedCurrentFrame.promptRows().size())
                        .enabledPromptCount((int) scopedCurrentFrame.promptRows().stream().filter(row -> Boolean.TRUE.equals(row.getEnabled())).count())
                        .problemClusterCount(scopedCurrentFrame.problemClusters().size())
                        .probeCount(scopedCurrentFrame.probeRows().size())
                        .attemptCount(scopedCurrentFrame.attemptRows().size())
                        .linkedProbeCount(linkedProbeCount)
                        .evidenceCount(scopedCurrentFrame.evidenceRows().size())
                        .linkedEvidenceCount(linkedEvidenceCount)
                        .rawAnswerCount(rawAnswerCount)
                        .linkedPromptCount(linkedPromptCount)
                        .build())
                .catalog(buildProblemAnalysisCatalog(filteredCurrentFrame))
                .topicRows(scopedCurrentFrame.topicRows())
                .promptRows(scopedCurrentFrame.promptRows())
                .problemClusters(scopedCurrentFrame.problemClusters())
                .probeRows(scopedCurrentFrame.probeRows())
                .evidenceRows(scopedCurrentFrame.evidenceRows())
                .attemptRows(scopedCurrentFrame.attemptRows())
                .rich(buildProblemAnalysisRich(context, recentReports, scopedCurrentFrame, scopedPreviousFrame, scope))
                .build();
        WorkbenchProblemAnalysisView resolvedView = view;
        if (resolvedView != null) {
            resolvedView.setDataAvailability(reconcileProblemAnalysisAvailabilityAfterPreferredRead(
                    resolvedView.getDataAvailability(),
                    resolvedView.getSummary(),
                    resolvedView.getTopicRows(),
                    resolvedView.getPromptRows(),
                    resolvedView.getProblemClusters(),
                    resolvedView.getProbeRows(),
                    resolvedView.getAttemptRows(),
                    resolvedView.getEvidenceRows()
            ));
        }
        return resolvedView;
    }

    private WorkbenchProblemAnalysisView tryBuildFormalProblemAnalysisView(BrandSpaceContext context,
                                                                           ProblemAnalysisScope scope,
                                                                           List<String> topicNames,
                                                                           List<String> problemSpaceBuckets,
                                                                           WorkbenchAnalysisContextView analysisContext) {
        ensureKnowledgeHistoryReady(context == null || context.scope() == null ? null : context.scope().getId());
        KnowledgeAssetRunRepository runRepository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (runRepository == null || context == null || context.scope() == null || !StringUtils.hasText(context.scope().getId())) {
            return null;
        }
        KnowledgeAssetRunRepository.RunDetailView latestKnowledgeRun =
                runRepository.findLatestRunDetailByScope(context.scope().getId());
        if (latestKnowledgeRun == null) {
            return null;
        }
        KnowledgeRunWindowSelection selection = resolveKnowledgeRunWindowSelection(
                runRepository,
                context.scope().getId(),
                analysisContext
        );
        if (selection == null) {
            return null;
        }
        ProblemAnalysisFrame currentFrame = buildKnowledgeProblemAnalysisFrame(context, selection.currentRuns());
        ProblemAnalysisFrame previousFrame = buildKnowledgeProblemAnalysisFrame(context, selection.previousRuns());
        populateProblemAnalysisPlatforms(analysisContext, currentFrame, previousFrame);
        markKnowledgeProblemAnalysisContext(analysisContext, selection);

        ProblemAnalysisFrame filteredCurrentFrame = applyProblemAnalysisAnalysisContext(currentFrame, analysisContext);
        ProblemAnalysisFrame filteredPreviousFrame = applyProblemAnalysisAnalysisContext(previousFrame, analysisContext);
        ProblemAnalysisFrame scopedCurrentFrame = applyProblemAnalysisScope(filteredCurrentFrame, scope);
        ProblemAnalysisFrame scopedPreviousFrame = applyProblemAnalysisScope(filteredPreviousFrame, scope);

        int rawAnswerCount = (int) scopedCurrentFrame.probeRows().stream()
                .filter(row -> StringUtils.hasText(row.getRawAnswer()))
                .count();
        int linkedProbeCount = (int) scopedCurrentFrame.probeRows().stream()
                .filter(this::hasPromptOrTopicLink)
                .count();
        int linkedEvidenceCount = (int) scopedCurrentFrame.evidenceRows().stream()
                .filter(this::hasPromptOrTopicLink)
                .count();
        int linkedPromptCount = (int) scopedCurrentFrame.promptRows().stream()
                .filter(row -> safeInt(row.getProbeCount()) > 0 || safeInt(row.getEvidenceCount()) > 0 || Boolean.TRUE.equals(row.getHasRawAnswer()))
                .count();

        boolean explicitFilterEmpty = hasExplicitProjectionFilter(analysisContext)
                && isProblemAnalysisFilterEmpty(scopedCurrentFrame);
        WorkbenchDataAvailabilityView availability = explicitFilterEmpty
                ? buildFilteredWindowEmptyAvailability(context)
                : refineProblemAnalysisAvailability(
                buildAvailability(
                        context,
                        !selection.currentRuns().isEmpty(),
                        collectProblemAnalysisMissingFields(
                                scopedCurrentFrame.topicRows(),
                                scopedCurrentFrame.promptRows(),
                                scopedCurrentFrame.problemClusters(),
                                scopedCurrentFrame.probeRows(),
                                scopedCurrentFrame.attemptRows(),
                                scopedCurrentFrame.evidenceRows(),
                                rawAnswerCount,
                                linkedPromptCount,
                                linkedProbeCount,
                                linkedEvidenceCount
                        ),
                        analysisContext
                ),
                rawAnswerCount
        );

        KnowledgeAssetRunRepository.RunDetailView latestWindowRun = selection.currentRuns().isEmpty()
                ? latestKnowledgeRun
                : selection.currentRuns().getFirst();
        WorkbenchProblemAnalysisView view = WorkbenchProblemAnalysisView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(firstText(
                        latestWindowRun == null ? null : latestWindowRun.sourceTaskId(),
                        latestWindowRun == null ? null : latestWindowRun.runId(),
                        context.latestTask() == null ? null : context.latestTask().getId()
                ))
                .latestReportId(firstText(
                        latestWindowRun == null ? null : latestWindowRun.sourceReportId(),
                        context.latestReport() == null ? null : context.latestReport().getId()
                ))
                .lastGeneratedAt(formatDateTime(latestWindowRun == null ? null : toLocalDateTime(latestWindowRun.observedAt())))
                .analysisContext(analysisContext)
                .dataAvailability(availability)
                .summary(WorkbenchProblemAnalysisView.SummaryView.builder()
                        .topicCount(scopedCurrentFrame.topicRows().size())
                        .promptCount(scopedCurrentFrame.promptRows().size())
                        .enabledPromptCount((int) scopedCurrentFrame.promptRows().stream().filter(row -> Boolean.TRUE.equals(row.getEnabled())).count())
                        .problemClusterCount(scopedCurrentFrame.problemClusters().size())
                        .probeCount(scopedCurrentFrame.probeRows().size())
                        .attemptCount(scopedCurrentFrame.attemptRows().size())
                        .linkedProbeCount(linkedProbeCount)
                        .evidenceCount(scopedCurrentFrame.evidenceRows().size())
                        .linkedEvidenceCount(linkedEvidenceCount)
                        .rawAnswerCount(rawAnswerCount)
                        .linkedPromptCount(linkedPromptCount)
                        .build())
                .catalog(buildProblemAnalysisCatalog(filteredCurrentFrame))
                .topicRows(scopedCurrentFrame.topicRows())
                .promptRows(scopedCurrentFrame.promptRows())
                .problemClusters(scopedCurrentFrame.problemClusters())
                .probeRows(scopedCurrentFrame.probeRows())
                .evidenceRows(scopedCurrentFrame.evidenceRows())
                .attemptRows(scopedCurrentFrame.attemptRows())
                .rich(buildKnowledgeProblemAnalysisRich(context, selection.currentRuns(), scopedCurrentFrame, scopedPreviousFrame, scope))
                .build();
        WorkbenchProblemAnalysisView resolvedView = view;
        if (resolvedView != null) {
            resolvedView.setAnalysisContext(analysisContext);
        }
        return resolvedView;
    }

    private KnowledgeWorkbenchSelection resolveKnowledgeWorkbenchSelection(BrandSpaceContext context,
                                                                           WorkbenchAnalysisContextView analysisContext) {
        ensureKnowledgeHistoryReady(context == null || context.scope() == null ? null : context.scope().getId());
        KnowledgeAssetRunRepository runRepository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (runRepository == null || context == null || context.scope() == null || !StringUtils.hasText(context.scope().getId())) {
            return null;
        }
        KnowledgeRunWindowSelection selection = resolveKnowledgeRunWindowSelection(
                runRepository,
                context.scope().getId(),
                analysisContext
        );
        if (selection == null) {
            return null;
        }
        List<DiagnosisReportRecord> windowReports = selection.currentRuns().stream()
                .map(this::synthesizeReportFromKnowledgeRun)
                .filter(Objects::nonNull)
                .toList();
        List<DiagnosisReportRecord> filteredReports = filterReportsByPlatforms(windowReports, analysisContext);
        return new KnowledgeWorkbenchSelection(selection, windowReports, filteredReports);
    }

    private void markKnowledgeWorkbenchAnalysisContext(WorkbenchAnalysisContextView analysisContext,
                                                       KnowledgeRunWindowSelection selection,
                                                       String channel) {
        if (analysisContext == null || selection == null) {
            return;
        }
        List<String> notes = new ArrayList<>();
        notes.add("当前结果先按自然时间窗口查询知识运行，不再依赖最近 N 次报告近似裁剪。");
        switch (defaultIfBlank(channel, "")) {
            case "citation-sources" ->
                    notes.add("来源结构、域名、URL 与趋势优先来自知识资产引用证据，而不是旧报告摘要回放。");
            case "brand-performance" ->
                    notes.add("品牌表现优先基于知识运行窗口内的快照与指标结果回投，不再直接读取业务库旧报告。");
            default ->
                    notes.add("总览优先复用知识运行窗口内的品牌表现与引用来源结果，不再回到旧报告近似窗口。");
        }
        if (analysisContext.getPlatforms() != null && !analysisContext.getPlatforms().isEmpty()) {
            notes.add("当前平台筛选在知识运行窗口内按统一平台分类生效。");
        }
        if (selection.currentRuns().isEmpty()) {
            notes.add("当前窗口未命中知识运行，已返回正式空态。");
        }
        analysisContext.setTruthSource(switch (defaultIfBlank(channel, "")) {
            case "citation-sources" -> "configuration+knowledge_window_query+knowledge_evidence";
            case "brand-performance" -> "configuration+knowledge_window_query+knowledge_snapshot";
            default -> "configuration+knowledge_window_query+knowledge_projection";
        });
        analysisContext.setSourceWindowKey(defaultIfBlank(analysisContext.getWindowKey(), "custom"));
        analysisContext.setNotes(List.copyOf(notes));
    }

    private void markStrictKnowledgeWorkbenchAnalysisContext(WorkbenchAnalysisContextView analysisContext,
                                                             String channel) {
        if (analysisContext == null) {
            return;
        }
        List<String> notes = new ArrayList<>();
        notes.add("当前结果严格走 5.0 知识窗口主链路，未命中知识运行时直接返回正式空态。");
        if (analysisContext.getPlatforms() != null && !analysisContext.getPlatforms().isEmpty()) {
            notes.add("当前平台筛选按统一平台分类生效，不再按单个模型名称做隐式兜底。");
        }
        analysisContext.setTruthSource(switch (defaultIfBlank(channel, "")) {
            case "citation-sources" -> "configuration+knowledge_window_query+knowledge_evidence";
            case "brand-performance" -> "configuration+knowledge_window_query+knowledge_snapshot";
            case "problem-analysis" -> "configuration+knowledge_window_query+knowledge_evidence";
            default -> "configuration+knowledge_window_query+knowledge_projection";
        });
        analysisContext.setSourceWindowKey(defaultIfBlank(analysisContext.getWindowKey(), "custom"));
        analysisContext.setNotes(List.copyOf(notes));
    }

    private WorkbenchAnalysisContextView buildAnalysisContext(WorkbenchAnalysisContextRequest request,
                                                              String channel,
                                                              Map<String, List<String>> channelFilters) {
        WorkbenchAnalysisContextRequest resolvedRequest = request == null
                ? WorkbenchAnalysisContextRequest.defaults()
                : request;
        String preset = normalizeAnalysisPreset(resolvedRequest.timePreset());
        LocalDate today = LocalDate.now();
        LocalDate from = parseDate(resolvedRequest.dateFrom());
        LocalDate to = parseDate(resolvedRequest.dateTo());
        if (from == null || to == null) {
            LocalDate[] presetRange = resolvePresetRange(preset, today);
            from = from == null ? presetRange[0] : from;
            to = to == null ? presetRange[1] : to;
        }
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }
        String windowKey = resolveWindowKey(preset, from, to, today);
        String normalizedComparison = normalizeComparison(resolvedRequest.comparison());
        String normalizedFrequency = normalizeFrequency(resolvedRequest.frequency());
        List<String> notes = new ArrayList<>();
        notes.add("用户侧时间筛选只使用自然时间窗口，不再回退到最近 N 次报告近似裁剪。");
        notes.add("对比口径仅支持上一自然时间窗口或不对比。");
        notes.add("频次口径仅支持按天或按周聚合。");
        return WorkbenchAnalysisContextView.builder()
                .timePreset(preset)
                .dateFrom(from == null ? null : from.toString())
                .dateTo(to == null ? null : to.toString())
                .windowKey(windowKey)
                .comparison(normalizedComparison)
                .frequency(normalizedFrequency)
                .platforms(normalizedList(resolvedRequest.platforms()))
                .channelFilters(channelFilters == null ? Map.of() : channelFilters)
                .supportedDimensions(supportedDimensionsForChannel(channel))
                .samplePolicy("natural_time_window_only;knowledge_window_primary")
                .truthSource("configuration+knowledge_window_query")
                .sourceWindowKey(windowKey)
                .notes(List.copyOf(notes))
                .build();
    }

    private void populateAnalysisContextPlatforms(WorkbenchAnalysisContextView analysisContext,
                                                  List<DiagnosisReportRecord> reports) {
        if (analysisContext == null) {
            return;
        }
        analysisContext.setAvailablePlatforms(resolveAvailablePlatforms(reports));
    }

    private void populateProblemAnalysisPlatforms(WorkbenchAnalysisContextView analysisContext,
                                                  ProblemAnalysisFrame... frames) {
        if (analysisContext == null) {
            return;
        }
        LinkedHashSet<String> available = new LinkedHashSet<>();
        if (analysisContext.getAvailablePlatforms() != null) {
            analysisContext.getAvailablePlatforms().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(available::add);
        }
        if (frames != null) {
            for (ProblemAnalysisFrame frame : frames) {
                if (frame == null) {
                    continue;
                }
                frame.attemptRows().stream()
                        .map(row -> resolvePlatformLabel(row.getModel(), row.getProvider()))
                        .filter(platform -> StringUtils.hasText(platform) && !"未记录平台".equals(platform))
                        .forEach(available::add);
                frame.probeRows().stream()
                        .map(row -> resolvePlatformLabel(row.getModel(), null))
                        .filter(platform -> StringUtils.hasText(platform) && !"未记录平台".equals(platform))
                        .forEach(available::add);
            }
        }
        analysisContext.setAvailablePlatforms(List.copyOf(available));
    }

    private DiagnosisReportRecord selectCurrentReport(BrandSpaceContext context,
                                                      List<DiagnosisReportRecord> reports,
                                                      WorkbenchAnalysisContextView analysisContext) {
        if (reports != null && !reports.isEmpty()) {
            return reports.getFirst();
        }
        if (hasExplicitProjectionFilter(analysisContext)) {
            return null;
        }
        return context == null ? null : context.latestReport();
    }

    private BrandSpaceContext contextWithCurrentReport(BrandSpaceContext context,
                                                       DiagnosisReportRecord currentReport,
                                                       boolean clearWhenMissing) {
        if (context == null) {
            return null;
        }
        if (currentReport == null) {
            if (!clearWhenMissing || context.latestReport() == null) {
                return context;
            }
            return new BrandSpaceContext(
                    context.userId(),
                    context.organizationId(),
                    context.scope(),
                    context.brand(),
                    context.latestTask(),
                    null
            );
        }
        if (Objects.equals(context.latestReport(), currentReport)) {
            return context;
        }
        return new BrandSpaceContext(
                context.userId(),
                context.organizationId(),
                context.scope(),
                context.brand(),
                context.latestTask(),
                currentReport
        );
    }

    private List<DiagnosisReportRecord> filterReportsByTimeContext(List<DiagnosisReportRecord> reports,
                                                                   WorkbenchAnalysisContextView analysisContext) {
        if (reports == null || reports.isEmpty() || analysisContext == null) {
            return reports == null ? List.of() : reports;
        }
        LocalDate from = parseDate(analysisContext.getDateFrom());
        LocalDate to = parseDate(analysisContext.getDateTo());
        if (from == null || to == null) {
            return reports;
        }
        LocalDateTime fromAt = from.atStartOfDay();
        LocalDateTime toAt = to.plusDays(1).atStartOfDay();
        List<DiagnosisReportRecord> filtered = reports.stream()
                .filter(Objects::nonNull)
                .filter(report -> report.getGeneratedAt() != null)
                .filter(report -> !report.getGeneratedAt().isBefore(fromAt) && report.getGeneratedAt().isBefore(toAt))
                .toList();
        return filtered;
    }

    private List<DiagnosisReportRecord> filterReportsByPlatforms(List<DiagnosisReportRecord> reports,
                                                                 WorkbenchAnalysisContextView analysisContext) {
        if (reports == null || reports.isEmpty() || analysisContext == null) {
            return reports == null ? List.of() : reports;
        }
        Set<String> selectedPlatforms = normalizedSelectionSet(analysisContext.getPlatforms());
        if (selectedPlatforms.isEmpty()) {
            return reports;
        }
        List<DiagnosisReportRecord> filtered = reports.stream()
                .filter(Objects::nonNull)
                .filter(report -> matchesPlatformSelection(report == null ? null : report.getTargetModelProduct(), null, selectedPlatforms))
                .toList();
        return filtered.isEmpty() ? List.of() : filtered;
    }

    private Set<String> normalizedSelectionSet(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null || values.isEmpty()) {
            return normalized;
        }
        for (String value : values) {
            String normalizedValue = normalizeSelectionValue(value);
            if (StringUtils.hasText(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private String normalizeSelectionValue(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> resolveAvailablePlatforms(List<DiagnosisReportRecord> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> available = new LinkedHashSet<>();
        for (DiagnosisReportRecord report : reports) {
            String label = resolvePlatformLabel(report == null ? null : report.getTargetModelProduct(), null);
            if (StringUtils.hasText(label) && !"未记录平台".equals(label)) {
                available.add(label);
            }
        }
        return List.copyOf(available);
    }

    private boolean shouldUsePreferredProjection(WorkbenchAnalysisContextView analysisContext) {
        if (hasExplicitProjectionFilter(analysisContext) || analysisContext == null) {
            return false;
        }
        String truthSource = defaultIfBlank(analysisContext.getTruthSource(), "");
        return truthSource.contains("knowledge_window_query");
    }

    private boolean shouldUsePreferredProjection(WorkbenchAnalysisContextView analysisContext,
                                                 boolean suppressPreferredProjection) {
        if (suppressPreferredProjection) {
            return false;
        }
        return shouldUsePreferredProjection(analysisContext);
    }

    private boolean shouldAggregateWindow(WorkbenchAnalysisContextView analysisContext) {
        return analysisContext != null;
    }

    private int resolveRecentReportLimit(WorkbenchAnalysisContextView analysisContext, int defaultLimit) {
        String windowKey = defaultIfBlank(analysisContext == null ? null : analysisContext.getWindowKey(), "last_14_days");
        return switch (windowKey) {
            case "day" -> Math.max(defaultLimit, 8);
            case "last_7_days" -> Math.max(defaultLimit, 16);
            case "last_14_days" -> Math.max(defaultLimit, 32);
            case "last_30_days", "custom" -> Math.max(defaultLimit, 64);
            default -> defaultLimit;
        };
    }

    private boolean hasExplicitProjectionFilter(WorkbenchAnalysisContextView analysisContext) {
        if (analysisContext == null) {
            return false;
        }
        if (analysisContext.getPlatforms() != null && !analysisContext.getPlatforms().isEmpty()) {
            return true;
        }
        String windowKey = defaultIfBlank(analysisContext.getWindowKey(), "last_14_days");
        return !"last_14_days".equals(windowKey);
    }

    private boolean shouldEnforceKnowledgeWorkbenchRead() {
        if (knowledgeAssetProperties == null || !knowledgeAssetProperties.isEnabled()) {
            return false;
        }
        KnowledgeAssetProperties.Projection projection = knowledgeAssetProperties.getProjection();
        return projection != null
                && projection.isStrictWorkbenchReadEnabled();
    }

    private boolean shouldUseKnowledgeWindowEmptyAvailability(KnowledgeWorkbenchSelection selection,
                                                              List<DiagnosisReportRecord> recentReports) {
        if (!shouldEnforceKnowledgeWorkbenchRead()) {
            return false;
        }
        if (selection == null) {
            return true;
        }
        return recentReports == null || recentReports.isEmpty();
    }

    private WorkbenchDataAvailabilityView buildKnowledgeWindowEmptyAvailability(BrandSpaceContext context,
                                                                                WorkbenchAnalysisContextView analysisContext) {
        if (hasExplicitProjectionFilter(analysisContext)) {
            return buildFilteredWindowEmptyAvailability(context);
        }
        DiagnosisTask latestTask = context == null ? null : context.latestTask();
        DiagnosisReportRecord latestReport = context == null ? null : context.latestReport();
        boolean running = isGenerating(latestTask == null ? null : latestTask.getStatusCode());
        String status;
        String message;
        if (latestReport == null && running) {
            status = "generating";
            message = "首轮结果生成中，请稍后回来查看";
        } else if (latestReport != null && running) {
            status = "partial";
            message = "已有上一轮结果，当前新一轮生成中";
        } else {
            status = "empty";
            message = "当前时间范围内还没有可展示结果";
        }
        return WorkbenchDataAvailabilityView.builder()
                .status(status)
                .message(message)
                .reasonCode("knowledge_window_empty")
                .hasCompletedReport(latestReport != null)
                .hasRunningRun(running)
                .latestRunId(latestTask == null ? null : latestTask.getId())
                .latestReportId(latestReport == null ? null : latestReport.getId())
                .missingFields(List.of())
                .missingFieldDetails(Map.of())
                .build();
    }

    private String normalizeComparison(String rawComparison) {
        String normalized = normalizeText(rawComparison);
        if (!StringUtils.hasText(normalized)) {
            return "previous_period";
        }
        normalized = normalized.replace('-', '_');
        return switch (normalized) {
            case "previous_period", "none" -> normalized;
            default -> "previous_period";
        };
    }

    private String normalizeFrequency(String rawFrequency) {
        String normalized = normalizeText(rawFrequency);
        return "week".equals(normalized) ? "week" : "day";
    }

    private String normalizeAnalysisPreset(String rawPreset) {
        String normalizedText = normalizeText(rawPreset);
        String normalized = normalizedText == null ? "" : normalizedText.replace("-", "_");
        if (!StringUtils.hasText(normalized)) {
            return "last_14_days";
        }
        return switch (normalized) {
            case "today", "yesterday", "last_7_days", "last_14_days", "last_30_days", "custom", "day" -> normalized;
            default -> "last_14_days";
        };
    }

    private LocalDate[] resolvePresetRange(String preset, LocalDate today) {
        return switch (defaultIfBlank(preset, "last_14_days")) {
            case "today", "day" -> new LocalDate[]{today, today};
            case "yesterday" -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
            case "last_7_days" -> new LocalDate[]{today.minusDays(6), today};
            case "last_30_days" -> new LocalDate[]{today.minusDays(29), today};
            default -> new LocalDate[]{today.minusDays(13), today};
        };
    }

    private String resolveWindowKey(String preset, LocalDate from, LocalDate to, LocalDate today) {
        if ("last_7_days".equals(preset) || "last_14_days".equals(preset) || "last_30_days".equals(preset)) {
            return preset;
        }
        if (from == null || to == null || today == null) {
            return "custom";
        }
        if (from.equals(today.minusDays(6)) && to.equals(today)) {
            return "last_7_days";
        }
        if (from.equals(today.minusDays(13)) && to.equals(today)) {
            return "last_14_days";
        }
        if (from.equals(today.minusDays(29)) && to.equals(today)) {
            return "last_30_days";
        }
        return from.equals(to) ? "day" : "custom";
    }

    private LocalDate parseDate(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> supportedDimensionsForChannel(String channel) {
        return switch (defaultIfBlank(channel, "")) {
            case "problem-analysis" -> List.of("timeRange", "platforms", "comparison", "frequency", "topicNames", "problemSpaceBuckets");
            case "citation-sources" -> List.of("timeRange", "platforms", "comparison", "frequency", "sourceTypes", "domains");
            case "brand-performance" -> List.of("timeRange", "platforms", "comparison", "frequency", "competitors", "metrics");
            default -> List.of("timeRange", "platforms", "comparison", "frequency", "metrics");
        };
    }

    private WorkbenchCitationSourcesView buildCitationSourcesInternal(BrandSpaceContext context,
                                                                     List<DiagnosisReportRecord> recentReports,
                                                                     WorkbenchAnalysisContextView analysisContext,
                                                                     boolean aggregateWindow) {
        List<CitationEvidenceSnapshot> snapshots = buildCitationEvidenceSnapshots(recentReports);
        CitationEvidenceBasis basis = selectCitationEvidenceBasis(snapshots, aggregateWindow);
        DiagnosisReportRecord currentReport = basis.currentReport();
        List<ProbeEvidenceRecord> currentEvidence = basis.currentEvidence();
        List<ProbeEvidenceRecord> previousEvidence = basis.previousEvidence();
        SourceAggregation aggregation = buildSourceAggregation(context, currentEvidence, previousEvidence);
        List<WorkbenchCitationSourcesView.TrendPointView> trendPoints = buildCitationTrendPoints(context, snapshots, analysisContext);
        WorkbenchDataAvailabilityView availability = buildAvailability(
                context,
                currentReport != null,
                aggregation.missingFields(),
                analysisContext
        );

        return WorkbenchCitationSourcesView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(firstText(
                        currentReport == null ? null : currentReport.getDiagnosisTaskId(),
                        context.latestTask() == null ? null : context.latestTask().getId()
                ))
                .latestReportId(firstText(
                        currentReport == null ? null : currentReport.getId(),
                        context.latestReport() == null ? null : context.latestReport().getId()
                ))
                .lastGeneratedAt(formatDateTime(firstNonNull(
                        currentReport == null ? null : currentReport.getGeneratedAt(),
                        context.latestReport() == null ? null : context.latestReport().getGeneratedAt()
                )))
                .dataAvailability(availability)
                .summary(aggregation.summary())
                .categorySlices(aggregation.categorySlices())
                .domainRows(aggregation.domainRows())
                .urlRows(aggregation.urlRows())
                .trendPoints(trendPoints)
                .ownership(aggregation.ownership())
                .domainDetails(aggregation.domainDetails())
                .build();
    }

    private List<CitationEvidenceSnapshot> buildCitationEvidenceSnapshots(List<DiagnosisReportRecord> recentReports) {
        if (recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        return recentReports.stream()
                .map(report -> new CitationEvidenceSnapshot(report, loadCitationEvidence(report)))
                .toList();
    }

    private CitationEvidenceBasis selectCitationEvidenceBasis(List<CitationEvidenceSnapshot> snapshots,
                                                              boolean aggregateWindow) {
        if (snapshots == null || snapshots.isEmpty()) {
            return new CitationEvidenceBasis(null, List.of(), null, List.of());
        }
        if (aggregateWindow) {
            List<CitationEvidenceSnapshot> nonEmpty = snapshots.stream()
                    .filter(snapshot -> snapshot != null && snapshot.report() != null && !snapshot.evidence().isEmpty())
                    .toList();
            if (nonEmpty.isEmpty()) {
                return new CitationEvidenceBasis(snapshots.getFirst().report(), List.of(), null, List.of());
            }
            List<ProbeEvidenceRecord> currentEvidence = new ArrayList<>();
            for (CitationEvidenceSnapshot snapshot : nonEmpty) {
                currentEvidence.addAll(snapshot.evidence());
            }
            CitationEvidenceSnapshot latest = nonEmpty.getFirst();
            CitationEvidenceSnapshot previous = nonEmpty.size() > 1 ? nonEmpty.get(1) : null;
            return new CitationEvidenceBasis(
                    latest.report(),
                    currentEvidence,
                    previous == null ? null : previous.report(),
                    previous == null ? List.of() : previous.evidence()
            );
        }
        int currentIndex = 0;
        for (int index = 0; index < snapshots.size(); index++) {
            if (!snapshots.get(index).evidence().isEmpty()) {
                currentIndex = index;
                break;
            }
        }
        CitationEvidenceSnapshot current = snapshots.get(currentIndex);
        CitationEvidenceSnapshot previous = null;
        for (int index = currentIndex + 1; index < snapshots.size(); index++) {
            CitationEvidenceSnapshot candidate = snapshots.get(index);
            if (!candidate.evidence().isEmpty()) {
                previous = candidate;
                break;
            }
        }
        return new CitationEvidenceBasis(
                current.report(),
                current.evidence(),
                previous == null ? null : previous.report(),
                previous == null ? List.of() : previous.evidence()
        );
    }

    private WorkbenchBrandIdentityView buildBrandIdentity(BrandSpaceContext context) {
        return WorkbenchBrandIdentityView.builder()
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .build();
    }

    private List<WorkbenchProblemAnalysisView.TopicRowView> buildProblemAnalysisTopicRows(BrandSpaceContext context,
                                                                                           ReportResponse latestReport) {
        return buildTopicSummaries(context, latestReport).stream()
                .map(item -> WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId(item.getTopicId())
                        .topicName(item.getTopicName())
                        .problemSpaceBucket(null)
                        .seqNo(item.getSeqNo())
                        .enabled(item.getEnabled())
                        .promptCount(item.getPromptCount())
                        .enabledPromptCount(item.getEnabledPromptCount())
                        .signalCount(item.getSignalCount())
                        .sampleQuestions(item.getSampleQuestions())
                        .build())
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.PromptRowView> buildProblemAnalysisPromptRows(BrandSpaceContext context) {
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, context.scope().getId())
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        Map<String, BrandSpaceTopic> topicById = new LinkedHashMap<>();
        for (BrandSpaceTopic topic : topics) {
            topicById.put(topic.getId(), topic);
        }

        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, context.scope().getId())
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt));

        return prompts.stream()
                .map(prompt -> {
                    BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
                    return WorkbenchProblemAnalysisView.PromptRowView.builder()
                            .promptId(prompt.getId())
                            .topicId(prompt.getTopicId())
                            .topicName(topic == null ? null : topic.getName())
                            .problemSpaceBucket(null)
                            .seqNo(prompt.getSeqNo())
                            .promptText(prompt.getPromptText())
                            .enabled(intEquals(prompt.getStatusCode(), 1))
                            .probeCount(0)
                            .evidenceCount(0)
                            .hasRawAnswer(false)
                            .latestProbeStatus(null)
                            .build();
                })
                .toList();
    }

    private Map<String, PromptLookup> buildPromptLookupByText(List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        Map<String, PromptLookup> lookup = new LinkedHashMap<>();
        if (promptRows == null || promptRows.isEmpty()) {
            return lookup;
        }
        for (WorkbenchProblemAnalysisView.PromptRowView row : promptRows) {
            String key = normalizeBrandKey(row.getPromptText());
            if (StringUtils.hasText(key)) {
                lookup.put(key, new PromptLookup(
                        row.getPromptId(),
                        row.getTopicId(),
                        row.getTopicName(),
                        row.getProblemSpaceBucket(),
                        row.getPromptText()
                ));
            }
        }
        return lookup;
    }

    private DiagnosisTask resolveTaskForRun(BrandSpaceContext context, String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        DiagnosisTask persisted = diagnosisTaskMapper.selectById(runId.trim());
        if (persisted != null) {
            return persisted;
        }
        if (context != null
                && context.latestTask() != null
                && Objects.equals(normalizeText(context.latestTask().getId()), normalizeText(runId))) {
            return context.latestTask();
        }
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return null;
        }
        return synthesizeTaskFromKnowledgeRun(repository.findRunDetailByRunId(runId.trim()));
    }

    private RunPromptBridge buildRunPromptBridge(DiagnosisTask runTask,
                                                 List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        if (runTask == null || runTask.getTaskOrderJson() == null) {
            return RunPromptBridge.empty();
        }
        BrandSpaceSchedulingService.ExecutionPlan executionPlan =
                BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(runTask.getTaskOrderJson());
        Map<String, PromptLookup> configuredLookupByText = buildPromptLookupByText(promptRows);
        boolean hasExecutionPromptPlan = executionPlan != null
                && executionPlan.executionPrompts() != null
                && !executionPlan.executionPrompts().isEmpty();
        if (hasExecutionPromptPlan) {
            List<PromptLookup> orderedLookups = new ArrayList<>();
            for (BrandSpaceSchedulingService.ExecutionPromptPlan executionPrompt : executionPlan.executionPrompts()) {
                if (executionPrompt == null) {
                    continue;
                }
                PromptLookup lookup = resolvePromptLookup(Map.of(
                        "promptId", executionPrompt.promptId(),
                        "topicId", executionPrompt.topicId(),
                        "topicName", executionPrompt.topicName(),
                        "promptText", executionPrompt.promptText()
                ), promptRows, configuredLookupByText);
                if (lookup != null) {
                    orderedLookups.add(lookup);
                }
            }
            if (!orderedLookups.isEmpty()) {
                return new RunPromptBridge(List.copyOf(orderedLookups), runTask.getTargetProbeCount());
            }
            return RunPromptBridge.empty();
        }
        return RunPromptBridge.empty();
    }

    private PromptLookup resolvePromptLookup(Map<String, Object> bridgeRecord,
                                             List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                             Map<String, PromptLookup> promptLookupByText) {
        if (bridgeRecord == null || bridgeRecord.isEmpty()) {
            return null;
        }
        String promptId = firstText(
                normalizeText(asText(bridgeRecord.get("promptId"))),
                normalizeText(asText(bridgeRecord.get("promptTempId")))
        );
        String topicId = firstText(
                normalizeText(asText(bridgeRecord.get("topicId"))),
                normalizeText(asText(bridgeRecord.get("topicTempId")))
        );
        String topicName = normalizeText(asText(bridgeRecord.get("topicName")));
        String problemSpaceBucket = normalizeText(asText(bridgeRecord.get("problemSpaceBucket")));
        String promptText = normalizeText(asText(bridgeRecord.get("promptText")));
        PromptLookup configured = firstNonNull(
                resolvePromptLookupById(promptId, promptRows),
                resolvePromptLookup(promptText, promptRows, promptLookupByText)
        );
        if (configured == null
                && !StringUtils.hasText(promptId)
                && !StringUtils.hasText(topicId)
                && !StringUtils.hasText(topicName)
                && !StringUtils.hasText(promptText)) {
            return null;
        }
        return new PromptLookup(
                firstText(promptId, configured == null ? null : configured.promptId()),
                firstText(topicId, configured == null ? null : configured.topicId()),
                firstText(topicName, configured == null ? null : configured.topicName()),
                firstText(problemSpaceBucket, configured == null ? null : configured.problemSpaceBucket()),
                firstText(promptText, configured == null ? null : configured.promptText())
        );
    }

    private Map<String, Integer> buildEvidenceCountByProbeId(List<ProbeEvidenceRecord> evidenceRecords,
                                                             List<ProbeDispatchAttempt> dispatchAttempts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        if (evidenceRecords != null) {
            for (ProbeEvidenceRecord evidence : evidenceRecords) {
                String probeId = normalizeText(evidence.getProbeId());
                if (!StringUtils.hasText(probeId)) {
                    continue;
                }
                counts.merge(probeId, 1, Integer::sum);
                seenKeys.add(buildEvidenceIdentityKey(
                        evidence.getDispatchAttemptId(),
                        evidence.getProbeId(),
                        evidence.getUrl()
                ));
            }
        }
        if (dispatchAttempts == null || dispatchAttempts.isEmpty()) {
            return counts;
        }
        for (ProbeDispatchAttempt attempt : dispatchAttempts) {
            String probeId = normalizeText(attempt == null ? null : attempt.getProbeId());
            if (!StringUtils.hasText(probeId)) {
                continue;
            }
            for (String citationUrl : readAttemptCitationUrls(attempt)) {
                String identityKey = buildEvidenceIdentityKey(attempt.getId(), attempt.getProbeId(), citationUrl);
                if (!seenKeys.add(identityKey)) {
                    continue;
                }
                counts.merge(probeId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<WorkbenchProblemAnalysisView.ProbeRowView> buildProblemAnalysisProbeRows(
            BrandSpaceContext context,
            ReportResponse latestReport,
            List<DiagnosisProbeRecord> diagnosisProbes,
            List<ProbeDispatchAttempt> dispatchAttempts,
            List<ProbeBrandMentionRecord> brandMentions,
            Map<String, Integer> evidenceCountByProbeId,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            Map<String, PromptLookup> promptLookupByText,
            RunPromptBridge runPromptBridge) {
        if (diagnosisProbes != null && !diagnosisProbes.isEmpty()) {
            return buildActualProblemAnalysisProbeRows(
                    context,
                    diagnosisProbes,
                    dispatchAttempts,
                    brandMentions,
                    evidenceCountByProbeId,
                    promptRows,
                    promptLookupByText,
                    runPromptBridge
            );
        }
        return buildProjectedProblemAnalysisProbeRows(
                latestReport,
                evidenceCountByProbeId,
                promptRows,
                promptLookupByText,
                runPromptBridge
        );
    }

    private List<WorkbenchProblemAnalysisView.ProbeRowView> buildActualProblemAnalysisProbeRows(
            BrandSpaceContext context,
            List<DiagnosisProbeRecord> diagnosisProbes,
            List<ProbeDispatchAttempt> dispatchAttempts,
            List<ProbeBrandMentionRecord> brandMentions,
            Map<String, Integer> evidenceCountByProbeId,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            Map<String, PromptLookup> promptLookupByText,
            RunPromptBridge runPromptBridge) {
        if (diagnosisProbes == null || diagnosisProbes.isEmpty()) {
            return List.of();
        }
        Map<String, ProbeDispatchAttempt> latestAttemptByProbeId = selectLatestAttemptByProbeId(dispatchAttempts);
        Map<String, List<ProbeBrandMentionRecord>> mentionsByProbeId = groupBrandMentionsByProbeId(brandMentions);
        String targetBrandKey = normalizeBrandKey(resolveBrandName(context));
        List<WorkbenchProblemAnalysisView.ProbeRowView> rows = new ArrayList<>();

        for (DiagnosisProbeRecord probe : diagnosisProbes.stream()
                .sorted(Comparator.comparing(DiagnosisProbeRecord::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList()) {
            ProbeDispatchAttempt latestAttempt = latestAttemptByProbeId.get(probe.getId());
            String candidatePrompt = firstText(
                    probe.getRenderText(),
                    firstListText(latestAttempt == null ? null : latestAttempt.getSearchQueriesJson())
            );
            PromptLookup lookup = firstNonNull(
                    resolvePromptLookupBySequence(runPromptBridge, diagnosisProbes.size(), probe.getSeqNo()),
                    resolvePromptLookup(candidatePrompt, promptRows, promptLookupByText)
            );
            List<ProbeBrandMentionRecord> targetMentions = filterTargetBrandMentions(
                    mentionsByProbeId.getOrDefault(probe.getId(), List.of()),
                    targetBrandKey
            );
            String rawAnswer = normalizeText(latestAttempt == null ? null : latestAttempt.getRawResponseText());
            String responsePreview = readResponsePreview(latestAttempt);
            WorkbenchProblemAnalysisView.ProbeRowView row = WorkbenchProblemAnalysisView.ProbeRowView.builder()
                    .probeId(probe.getId())
                    .probeRef(firstText(probe.getProbeRef(), probe.getId()))
                    .seqNo(probe.getSeqNo())
                    .topicId(lookup == null ? null : lookup.topicId())
                    .topicName(lookup == null ? null : lookup.topicName())
                    .problemSpaceBucket(firstText(
                            resolveOfficialProblemSpaceBucket(probe),
                            lookup == null ? null : lookup.problemSpaceBucket()
                    ))
                    .promptId(lookup == null ? null : lookup.promptId())
                    .promptText(firstText(lookup == null ? null : lookup.promptText(), candidatePrompt))
                    .renderText(candidatePrompt)
                    .status(resolveProblemAnalysisProbeStatus(probe.getStatusCode(), latestAttempt == null ? null : latestAttempt.getStatusCode()))
                    .evidenceCount(evidenceCountByProbeId.getOrDefault(probe.getId(), 0))
                    .hasRawAnswer(StringUtils.hasText(rawAnswer))
                    .latestAttemptId(latestAttempt == null ? null : latestAttempt.getId())
                    .model(resolveModelLabel(latestAttempt == null ? null : firstText(latestAttempt.getModelId(), latestAttempt.getProvider())))
                    .answerPreview(trimTo(firstText(responsePreview, rawAnswer), 140))
                    .rawAnswer(rawAnswer)
                    .visibilityScore(resolveVisibilityScoreFromMentions(targetMentions))
                    .averagePosition(resolveAveragePositionFromMentions(targetMentions))
                    .sentimentScore(resolveSentimentScoreFromMentions(targetMentions))
                    .mentionCount(targetMentions.size())
                    .build();
            rows.add(row);
        }
        return rows;
    }

    private List<WorkbenchProblemAnalysisView.ProbeRowView> buildProjectedProblemAnalysisProbeRows(
            ReportResponse latestReport,
            Map<String, Integer> evidenceCountByProbeId,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            Map<String, PromptLookup> promptLookupByText,
            RunPromptBridge runPromptBridge) {
        List<WorkbenchProblemAnalysisView.ProbeRowView> rows = new ArrayList<>();
        if (latestReport != null && latestReport.getEvidenceChains() != null) {
            int chainIndex = 0;
            for (ReportResponse.EvidenceChainCard chain : latestReport.getEvidenceChains()) {
                chainIndex++;
                String promptText = normalizeText(chain == null ? null : chain.getPrompt());
                PromptLookup lookup = firstNonNull(
                        resolvePromptLookupBySequence(runPromptBridge, latestReport.getEvidenceChains().size(), chainIndex),
                        resolvePromptLookup(promptText, promptRows, promptLookupByText)
                );
                String rawAnswer = firstText(chain == null ? null : chain.getAnswer(), chain == null ? null : chain.getJudgement());
                String probeId = firstText(chain == null ? null : chain.getKey(), "projection_probe_" + chainIndex);
                rows.add(WorkbenchProblemAnalysisView.ProbeRowView.builder()
                        .probeId(probeId)
                        .probeRef(probeId)
                        .seqNo(chainIndex)
                        .topicId(lookup == null ? null : lookup.topicId())
                        .topicName(lookup == null ? null : lookup.topicName())
                        .problemSpaceBucket(firstText(
                                normalizeText(chain == null ? null : chain.getProblemSpaceBucket()),
                                lookup == null ? null : lookup.problemSpaceBucket()
                        ))
                        .promptId(lookup == null ? null : lookup.promptId())
                        .promptText(lookup == null ? promptText : lookup.promptText())
                        .renderText(promptText)
                        .status("completed")
                        .evidenceCount(evidenceCountByProbeId.getOrDefault(probeId, 0))
                        .hasRawAnswer(StringUtils.hasText(rawAnswer))
                        .latestAttemptId(null)
                        .model(null)
                        .answerPreview(trimTo(rawAnswer, 140))
                        .rawAnswer(rawAnswer)
                        .visibilityScore(null)
                        .averagePosition(null)
                        .sentimentScore(null)
                        .mentionCount(0)
                        .build());
            }
        }
        for (Map.Entry<String, Integer> entry : evidenceCountByProbeId.entrySet()) {
            String probeId = entry.getKey();
            boolean exists = rows.stream().anyMatch(row -> Objects.equals(row.getProbeId(), probeId));
            if (exists) {
                continue;
            }
            rows.add(WorkbenchProblemAnalysisView.ProbeRowView.builder()
                    .probeId(probeId)
                    .probeRef(probeId)
                    .problemSpaceBucket(null)
                    .status("captured")
                    .evidenceCount(entry.getValue())
                    .hasRawAnswer(false)
                    .mentionCount(0)
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.ProbeRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.PromptRowView> enrichProblemAnalysisPromptRows(
            BrandSpaceContext context,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
            List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows) {
        if (promptRows == null || promptRows.isEmpty()) {
            return List.of();
        }
        Map<String, List<WorkbenchProblemAnalysisView.ProbeRowView>> probesByPromptId = new LinkedHashMap<>();
        Map<String, List<WorkbenchProblemAnalysisView.ProbeRowView>> probesByPromptText = new LinkedHashMap<>();
        int totalEvidenceCount = evidenceRows == null ? 0 : evidenceRows.size();
        if (probeRows != null) {
            for (WorkbenchProblemAnalysisView.ProbeRowView probeRow : probeRows) {
                if (StringUtils.hasText(probeRow.getPromptId())) {
                    probesByPromptId.computeIfAbsent(probeRow.getPromptId(), ignored -> new ArrayList<>()).add(probeRow);
                }
                String key = normalizeBrandKey(probeRow.getPromptText());
                if (StringUtils.hasText(key)) {
                    probesByPromptText.computeIfAbsent(key, ignored -> new ArrayList<>()).add(probeRow);
                }
            }
        }
        List<WorkbenchProblemAnalysisView.PromptRowView> result = new ArrayList<>();
        for (WorkbenchProblemAnalysisView.PromptRowView row : promptRows) {
            List<WorkbenchProblemAnalysisView.ProbeRowView> linked = probesByPromptId.get(row.getPromptId());
            if ((linked == null || linked.isEmpty()) && StringUtils.hasText(row.getPromptText())) {
                linked = probesByPromptText.get(normalizeBrandKey(row.getPromptText()));
            }
            int probeCount = linked == null ? 0 : linked.size();
            int evidenceCount = linked == null ? 0 : linked.stream()
                    .map(WorkbenchProblemAnalysisView.ProbeRowView::getEvidenceCount)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            boolean hasRawAnswer = linked != null && linked.stream().anyMatch(item -> Boolean.TRUE.equals(item.getHasRawAnswer()));
            String latestProbeStatus = linked == null || linked.isEmpty() ? null : linked.getFirst().getStatus();
            Double visibilityScore = linked == null || linked.isEmpty()
                    ? 0.0d
                    : round2(linked.stream()
                    .map(WorkbenchProblemAnalysisView.ProbeRowView::getVisibilityScore)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0d));
            Double averagePosition = linked == null || linked.isEmpty()
                    ? null
                    : round2(linked.stream()
                    .map(WorkbenchProblemAnalysisView.ProbeRowView::getAveragePosition)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0d));
            Double citationShare = totalEvidenceCount <= 0 ? 0.0d : round2(evidenceCount * 100.0d / totalEvidenceCount);
            WorkbenchProblemAnalysisView.PromptRowView enriched = WorkbenchProblemAnalysisView.PromptRowView.builder()
                    .promptId(row.getPromptId())
                    .topicId(row.getTopicId())
                    .topicName(row.getTopicName())
                    .problemSpaceBucket(firstText(
                            row.getProblemSpaceBucket(),
                            resolveDominantProblemSpaceBucket(linked == null ? List.of() : linked.stream()
                                    .map(WorkbenchProblemAnalysisView.ProbeRowView::getProblemSpaceBucket)
                                    .toList())
                    ))
                    .seqNo(row.getSeqNo())
                    .promptText(row.getPromptText())
                    .enabled(row.getEnabled())
                    .probeCount(probeCount)
                    .evidenceCount(evidenceCount)
                    .hasRawAnswer(hasRawAnswer)
                    .latestProbeStatus(latestProbeStatus)
                    .visibilityRank(null)
                    .visibilityScore(visibilityScore)
                    .visibilityDelta(0.0d)
                    .averagePosition(averagePosition)
                    .averageDelta(0.0d)
                    .citationShare(citationShare)
                    .citationDelta(0.0d)
                    .build();
            result.add(enriched);
        }
        applyPromptVisibilityRanks(result);
        return result;
    }

    private void applyPromptVisibilityRanks(List<WorkbenchProblemAnalysisView.PromptRowView> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<WorkbenchProblemAnalysisView.PromptRowView> sorted = rows.stream()
                .sorted(Comparator
                        .comparing(WorkbenchProblemAnalysisView.PromptRowView::getVisibilityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkbenchProblemAnalysisView.PromptRowView::getCitationShare, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkbenchProblemAnalysisView.PromptRowView::getAveragePosition, Comparator.nullsLast(Double::compareTo))
                        .thenComparing(WorkbenchProblemAnalysisView.PromptRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        int rank = 0;
        for (WorkbenchProblemAnalysisView.PromptRowView row : sorted) {
            rank++;
            row.setVisibilityRank(rank);
        }
    }

    private List<WorkbenchProblemAnalysisView.AttemptRowView> buildProblemAnalysisAttemptRows(
            List<ProbeDispatchAttempt> dispatchAttempts,
            List<ProbeBrandMentionRecord> brandMentions,
            List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows,
            Map<String, WorkbenchProblemAnalysisView.ProbeRowView> probeRowById) {
        if (dispatchAttempts == null || dispatchAttempts.isEmpty()) {
            return List.of();
        }
        Map<String, List<ProbeBrandMentionRecord>> mentionsByAttemptId = groupBrandMentionsByAttemptId(brandMentions);
        Map<String, List<WorkbenchProblemAnalysisView.EvidenceRowView>> evidenceByAttemptId = new LinkedHashMap<>();
        if (evidenceRows != null) {
            for (WorkbenchProblemAnalysisView.EvidenceRowView row : evidenceRows) {
                String attemptId = normalizeText(row.getAttemptId());
                if (!StringUtils.hasText(attemptId)) {
                    continue;
                }
                evidenceByAttemptId.computeIfAbsent(attemptId, ignored -> new ArrayList<>()).add(row);
            }
        }
        return dispatchAttempts.stream()
                .filter(this::shouldExposeAttemptRow)
                .sorted(Comparator
                        .comparing(ProbeDispatchAttempt::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProbeDispatchAttempt::getAttemptNo, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(attempt -> {
                    WorkbenchProblemAnalysisView.ProbeRowView probeRow = probeRowById.get(normalizeText(attempt.getProbeId()));
                    List<ProbeBrandMentionRecord> attemptMentions = mentionsByAttemptId.getOrDefault(attempt.getId(), List.of());
                    List<WorkbenchProblemAnalysisView.EvidenceRowView> attemptEvidence = evidenceByAttemptId.getOrDefault(attempt.getId(), List.of());
                    String rawAnswer = normalizeText(attempt.getRawResponseText());
                    String responsePreview = readResponsePreview(attempt);
                    List<WorkbenchProblemAnalysisView.CitationView> citations = buildAttemptCitations(attempt, attemptEvidence);
                    return WorkbenchProblemAnalysisView.AttemptRowView.builder()
                            .attemptId(attempt.getId())
                            .probeId(attempt.getProbeId())
                            .probeRef(probeRow == null ? null : probeRow.getProbeRef())
                            .topicId(probeRow == null ? null : probeRow.getTopicId())
                            .topicName(probeRow == null ? null : probeRow.getTopicName())
                            .problemSpaceBucket(probeRow == null ? null : probeRow.getProblemSpaceBucket())
                            .promptId(probeRow == null ? null : probeRow.getPromptId())
                            .promptText(probeRow == null ? null : probeRow.getPromptText())
                            .provider(normalizeText(attempt.getProvider()))
                            .model(resolveModelLabel(firstText(attempt.getModelId(), attempt.getProvider())))
                            .status(resolveAttemptStatus(attempt.getStatusCode()))
                            .createdAt(formatDateTime(attempt.getCreatedAt()))
                            .query(firstText(
                                    probeRow == null ? null : probeRow.getRenderText(),
                                    firstListText(attempt.getSearchQueriesJson()),
                                    probeRow == null ? null : probeRow.getPromptText(),
                                    probeRow == null ? null : probeRow.getProbeRef()
                            ))
                            .answerPreview(trimTo(firstText(responsePreview, rawAnswer), 220))
                            .rawAnswer(firstText(rawAnswer, responsePreview))
                            .summaryBullets(buildAttemptSummaryBullets(citations.size(), attemptMentions, probeRow))
                            .brandHits(buildAttemptBrandHits(attemptMentions))
                            .citations(citations)
                            .build();
                })
                .toList();
    }

    private boolean shouldExposeAttemptRow(ProbeDispatchAttempt attempt) {
        if (attempt == null) {
            return false;
        }
        return Boolean.TRUE.equals(attempt.getSelectedFinal())
                || StringUtils.hasText(attempt.getRawResponseText())
                || (attempt.getCitationsJson() != null && !attempt.getCitationsJson().isEmpty());
    }

    private List<String> buildAttemptSummaryBullets(
            int citationCount,
            List<ProbeBrandMentionRecord> mentions,
            WorkbenchProblemAnalysisView.ProbeRowView probeRow) {
        List<String> result = new ArrayList<>();
        if (probeRow != null && StringUtils.hasText(probeRow.getTopicName())) {
            int evidenceCount = Math.max(safeInt(probeRow.getEvidenceCount()), citationCount);
            result.add(probeRow.getTopicName() + " · " + evidenceCount + " 条证据");
        }
        if (citationCount > 0) {
            result.add("引用源 " + citationCount + " 条");
        } else {
            result.add("暂无引用源");
        }
        long mentionCount = mentions == null ? 0L : mentions.stream().filter(item -> StringUtils.hasText(item.getBrandName())).count();
        result.add(mentionCount > 0 ? "命中品牌 " + mentionCount + " 个" : "暂未识别品牌命中");
        return result;
    }

    private List<WorkbenchProblemAnalysisView.BrandHitView> buildAttemptBrandHits(List<ProbeBrandMentionRecord> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        Map<String, Double> bestScoreByBrand = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : mentions) {
            String brandName = normalizeText(mention.getBrandName());
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            double score = mention.getConfidenceScore() == null
                    ? (Boolean.TRUE.equals(mention.getRecommendedFlag()) ? 90.0d : 72.0d)
                    : round2(mention.getConfidenceScore().doubleValue() * 100.0d);
            bestScoreByBrand.merge(brandName, score, Math::max);
        }
        return bestScoreByBrand.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> WorkbenchProblemAnalysisView.BrandHitView.builder()
                        .brandName(entry.getKey())
                        .score(entry.getValue())
                        .build())
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.CitationView> buildAttemptCitations(
            ProbeDispatchAttempt attempt,
            List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows) {
        LinkedHashMap<String, WorkbenchProblemAnalysisView.CitationView> citations = new LinkedHashMap<>();
        if (evidenceRows != null) {
            for (WorkbenchProblemAnalysisView.EvidenceRowView row : evidenceRows) {
                String key = firstText(row.getEvidenceId(), row.getUrl(), row.getDomain());
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                citations.putIfAbsent(key, WorkbenchProblemAnalysisView.CitationView.builder()
                        .evidenceId(row.getEvidenceId())
                        .title(firstText(row.getTitle(), row.getDomain(), "引用证据"))
                        .url(row.getUrl())
                        .domain(row.getDomain())
                        .build());
            }
        }
        for (String citationUrl : readAttemptCitationUrls(attempt)) {
            String domain = domainOf(citationUrl);
            String key = firstText(citationUrl, domain);
            if (!StringUtils.hasText(key)) {
                continue;
            }
            citations.putIfAbsent(key, WorkbenchProblemAnalysisView.CitationView.builder()
                    .evidenceId(buildSyntheticEvidenceId(attempt == null ? null : attempt.getId(), citationUrl))
                    .title(resolveSyntheticCitationTitle(domain, citationUrl))
                    .url(citationUrl)
                    .domain(domain)
                    .build());
        }
        return List.copyOf(citations.values());
    }

    private List<String> readAttemptCitationUrls(ProbeDispatchAttempt attempt) {
        if (attempt == null || attempt.getCitationsJson() == null || attempt.getCitationsJson().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (String rawUrl : attempt.getCitationsJson()) {
            String normalized = normalizeText(rawUrl);
            if (StringUtils.hasText(normalized)) {
                urls.add(normalized);
            }
        }
        return List.copyOf(urls);
    }

    private String buildEvidenceIdentityKey(String attemptId, String probeId, String url) {
        return firstText(normalizeText(attemptId), normalizeText(probeId), "unknown")
                + "|" + firstText(normalizeText(url), "unknown");
    }

    private String buildSyntheticEvidenceId(String attemptId, String citationUrl) {
        String normalizedAttemptId = firstText(normalizeText(attemptId), "synthetic-attempt");
        String normalizedUrl = firstText(normalizeText(citationUrl), "citation");
        return "synthetic-evidence-" + normalizedAttemptId + "-" + Integer.toHexString(normalizedUrl.hashCode());
    }

    private String resolveSyntheticCitationTitle(String domain, String citationUrl) {
        return firstText(domain, citationUrl, "回答引用");
    }

    private Map<String, WorkbenchProblemAnalysisView.ProbeRowView> indexProbeRowsById(
            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows) {
        Map<String, WorkbenchProblemAnalysisView.ProbeRowView> index = new LinkedHashMap<>();
        if (probeRows == null || probeRows.isEmpty()) {
            return index;
        }
        for (WorkbenchProblemAnalysisView.ProbeRowView probeRow : probeRows) {
            String probeId = normalizeText(probeRow.getProbeId());
            if (StringUtils.hasText(probeId)) {
                index.put(probeId, probeRow);
            }
        }
        return index;
    }

    private List<WorkbenchProblemAnalysisView.EvidenceRowView> buildProblemAnalysisEvidenceRows(
            BrandSpaceContext context,
            List<ProbeEvidenceRecord> evidenceRecords,
            List<ProbeDispatchAttempt> dispatchAttempts,
            Map<String, WorkbenchProblemAnalysisView.ProbeRowView> probeRowById) {
        Set<String> ownedDomains = resolveOwnedDomains(context);
        if ((evidenceRecords == null || evidenceRecords.isEmpty())
                && (dispatchAttempts == null || dispatchAttempts.isEmpty())) {
            return List.of();
        }
        List<WorkbenchProblemAnalysisView.EvidenceRowView> rows = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        if (evidenceRecords != null) {
            for (ProbeEvidenceRecord item : evidenceRecords) {
                String domain = firstText(item.getDomain(), domainOf(item.getUrl()));
                String sourceType = resolveSourceType(item.getSourceTypeCode(), domain, ownedDomains);
                LocalDateTime capturedAt = firstNonNull(item.getRetrievedAt(), item.getCreatedAt());
                WorkbenchProblemAnalysisView.ProbeRowView probeRow = probeRowById.get(normalizeText(item.getProbeId()));
                String topicId = probeRow == null ? null : probeRow.getTopicId();
                String topicName = probeRow == null ? null : probeRow.getTopicName();
                String promptId = probeRow == null ? null : probeRow.getPromptId();
                String resolvedPromptText = probeRow == null ? null : probeRow.getPromptText();
                rows.add(WorkbenchProblemAnalysisView.EvidenceRowView.builder()
                        .evidenceId(item.getId())
                        .attemptId(item.getDispatchAttemptId())
                        .probeId(item.getProbeId())
                        .topicId(topicId)
                        .topicName(topicName)
                        .problemSpaceBucket(probeRow == null ? null : probeRow.getProblemSpaceBucket())
                        .promptId(promptId)
                        .promptText(resolvedPromptText)
                        .sourceType(resolveSourceTypeLabel(sourceType))
                        .domain(domain)
                        .url(item.getUrl())
                        .title(item.getTitle())
                        .snippet(item.getSnippet())
                        .capturedAt(formatDateTime(capturedAt))
                        .build());
                seenKeys.add(buildEvidenceIdentityKey(item.getDispatchAttemptId(), item.getProbeId(), item.getUrl()));
            }
        }
        if (dispatchAttempts != null) {
            for (ProbeDispatchAttempt attempt : dispatchAttempts) {
                if (attempt == null) {
                    continue;
                }
                WorkbenchProblemAnalysisView.ProbeRowView probeRow = probeRowById.get(normalizeText(attempt.getProbeId()));
                String topicId = probeRow == null ? null : probeRow.getTopicId();
                String topicName = probeRow == null ? null : probeRow.getTopicName();
                String promptId = probeRow == null ? null : probeRow.getPromptId();
                String promptText = probeRow == null ? null : probeRow.getPromptText();
                for (String citationUrl : readAttemptCitationUrls(attempt)) {
                    String identityKey = buildEvidenceIdentityKey(attempt.getId(), attempt.getProbeId(), citationUrl);
                    if (!seenKeys.add(identityKey)) {
                        continue;
                    }
                    String domain = domainOf(citationUrl);
                    String sourceType = resolveSourceType(null, domain, ownedDomains);
                    rows.add(WorkbenchProblemAnalysisView.EvidenceRowView.builder()
                            .evidenceId(buildSyntheticEvidenceId(attempt.getId(), citationUrl))
                            .attemptId(attempt.getId())
                            .probeId(attempt.getProbeId())
                            .topicId(topicId)
                            .topicName(topicName)
                            .problemSpaceBucket(probeRow == null ? null : probeRow.getProblemSpaceBucket())
                            .promptId(promptId)
                            .promptText(promptText)
                            .sourceType(resolveSourceTypeLabel(sourceType))
                            .domain(domain)
                            .url(citationUrl)
                            .title(resolveSyntheticCitationTitle(domain, citationUrl))
                            .snippet(trimTo(firstText(
                                    attempt.getRawResponseText(),
                                    probeRow == null ? null : probeRow.getAnswerPreview()
                            ), 140))
                            .capturedAt(formatDateTime(attempt.getCreatedAt()))
                            .build());
                }
            }
        }
        return rows;
    }

    private List<WorkbenchProblemAnalysisView.ProblemClusterView> buildProblemClusters(ReportResponse latestReport) {
        List<WorkbenchProblemAnalysisView.ProblemClusterView> result = new ArrayList<>();
        if (latestReport != null && latestReport.getClusterProfiles() != null && !latestReport.getClusterProfiles().isEmpty()) {
            int index = 0;
            for (ReportResponse.ClusterProfile profile : latestReport.getClusterProfiles()) {
                index++;
                result.add(WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id(firstText(profile.getKey(), "cluster_" + index))
                        .title(firstText(profile.getTitle(), profile.getArchetype(), "问题簇 " + index))
                        .summary(firstText(profile.getDescription(), "待补充问题簇摘要"))
                        .severity(Boolean.TRUE.equals(profile.getBlindSpot()) ? "high" : "medium")
                        .problemSpaceBucket(normalizeText(profile.getProblemSpaceBucket()))
                        .signalCount(profile.getSampleCount())
                        .representativeQuestions(profile.getPrompts() == null ? List.of() : profile.getPrompts().stream()
                                .filter(StringUtils::hasText)
                                .limit(5)
                                .toList())
                        .build());
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        if (latestReport != null
                && latestReport.getClusterBreakdown() != null
                && latestReport.getClusterBreakdown().getClusters() != null
                && !latestReport.getClusterBreakdown().getClusters().isEmpty()) {
            for (ClusterDetail cluster : latestReport.getClusterBreakdown().getClusters()) {
                if (cluster == null) {
                    continue;
                }
                result.add(WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id(firstText(cluster.getClusterId(), "cluster_breakdown_" + (result.size() + 1)))
                        .title(firstText(cluster.getClusterName(), "未识别场景"))
                        .summary(firstText(
                                cluster.getDescription(),
                                Boolean.TRUE.equals(cluster.getBlindSpot())
                                        ? "这是当前还没打透的决策视角，容易把机会让给竞品。"
                                        : "这是已经进入比较池的决策视角，下一步要争取从被提到走向被选中。"
                        ))
                        .severity(Boolean.TRUE.equals(cluster.getBlindSpot()) ? "high" : "medium")
                        .problemSpaceBucket(resolveProblemSpaceBucketFromStructuralBucket(cluster.getClusterId()))
                        .signalCount(cluster.getSampleCount())
                        .representativeQuestions(cluster.getSamplePrompts() == null
                                ? List.of()
                                : cluster.getSamplePrompts().stream()
                                .filter(StringUtils::hasText)
                                .limit(5)
                                .toList())
                        .build());
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        if (latestReport == null || latestReport.getNegativeSignals() == null || latestReport.getNegativeSignals().isEmpty()) {
            return List.of();
        }
        int index = 0;
        for (Map<String, Object> signal : latestReport.getNegativeSignals()) {
            index++;
            String signalText = firstText(asText(signal.get("signal")), "待确认问题");
            Integer count = readInteger(signal.get("count"));
            result.add(WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                    .id("signal_" + index)
                    .title(signalText)
                    .summary("来自负向信号聚合")
                    .severity(count != null && count >= 10 ? "high" : "medium")
                    .signalCount(count)
                    .representativeQuestions(List.of(signalText))
                    .build());
        }
        return result;
    }

    private ProblemAnalysisFrame buildProblemAnalysisFrame(BrandSpaceContext context,
                                                           DiagnosisReportRecord reportRecord,
                                                           ReportResponse reportProjection) {
        if (reportRecord == null) {
            return ProblemAnalysisFrame.empty();
        }
        String runId = reportRecord.getDiagnosisTaskId();
        DiagnosisTask runTask = resolveTaskForRun(context, runId);
        List<DiagnosisProbeRecord> diagnosisProbes = loadDiagnosisProbes(runId);
        List<ProbeDispatchAttempt> dispatchAttempts = loadDispatchAttempts(runId);
        List<ProbeBrandMentionRecord> brandMentions = loadBrandMentions(runId);
        List<ProbeEvidenceRecord> evidenceRecords = loadEvidence(runId);

        List<WorkbenchProblemAnalysisView.TopicRowView> configuredTopicRows = buildProblemAnalysisTopicRows(context, reportProjection);
        List<WorkbenchProblemAnalysisView.PromptRowView> configuredPromptRows = buildProblemAnalysisPromptRows(context);
        Map<String, PromptLookup> configuredLookupByText = buildPromptLookupByText(configuredPromptRows);
        RunPromptBridge runPromptBridge = buildRunPromptBridge(runTask, configuredPromptRows);
        Map<String, Integer> evidenceCountByProbeId = buildEvidenceCountByProbeId(evidenceRecords, dispatchAttempts);

        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows = buildProblemAnalysisProbeRows(
                context,
                reportProjection,
                diagnosisProbes,
                dispatchAttempts,
                brandMentions,
                evidenceCountByProbeId,
                configuredPromptRows,
                configuredLookupByText,
                    runPromptBridge
        );

        List<WorkbenchProblemAnalysisView.PromptRowView> promptSeedRows = runPromptBridge.hasPromptRows()
                ? runPromptBridge.toPromptRows()
                : configuredPromptRows.isEmpty()
                ? deriveProblemAnalysisPromptRowsFromProbes(probeRows, diagnosisProbes, reportProjection)
                : configuredPromptRows;
        List<WorkbenchProblemAnalysisView.ProbeRowView> linkedProbeRows = attachPromptCoverageToProbeRows(probeRows, promptSeedRows);
        Map<String, WorkbenchProblemAnalysisView.ProbeRowView> linkedProbeRowById = indexProbeRowsById(linkedProbeRows);
        List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows = buildProblemAnalysisEvidenceRows(
                context,
                evidenceRecords,
                dispatchAttempts,
                linkedProbeRowById
        );
        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows = enrichProblemAnalysisPromptRows(context, promptSeedRows, linkedProbeRows, evidenceRows);
        List<WorkbenchProblemAnalysisView.ProbeRowView> finalProbeRows = attachPromptCoverageToProbeRows(linkedProbeRows, promptRows);
        Map<String, WorkbenchProblemAnalysisView.ProbeRowView> finalProbeRowById = indexProbeRowsById(finalProbeRows);
        List<WorkbenchProblemAnalysisView.EvidenceRowView> finalEvidenceRows = buildProblemAnalysisEvidenceRows(
                context,
                evidenceRecords,
                dispatchAttempts,
                finalProbeRowById
        );
        List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows = buildProblemAnalysisAttemptRows(
                dispatchAttempts,
                brandMentions,
                finalEvidenceRows,
                finalProbeRowById
        );
        List<WorkbenchProblemAnalysisView.TopicRowView> topicSeedRows = runPromptBridge.hasPromptRows()
                ? deriveProblemAnalysisTopicRowsFromPromptRows(promptRows, reportProjection)
                : configuredTopicRows.isEmpty()
                ? deriveProblemAnalysisTopicRowsFromPromptRows(promptRows, reportProjection)
                : configuredTopicRows;
        List<WorkbenchProblemAnalysisView.TopicRowView> topicRows = attachProblemSpaceBucketsToTopicRows(topicSeedRows, promptRows);

        return new ProblemAnalysisFrame(
                topicRows,
                promptRows,
                buildProblemClusters(reportProjection),
                finalProbeRows,
                finalEvidenceRows,
                attemptRows,
                diagnosisProbes,
                dispatchAttempts,
                brandMentions,
                evidenceRecords
        );
    }

    private List<WorkbenchProblemAnalysisView.PromptRowView> deriveProblemAnalysisPromptRowsFromProbes(
            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
            List<DiagnosisProbeRecord> diagnosisProbes,
            ReportResponse reportProjection) {
        if (probeRows == null || probeRows.isEmpty()) {
            return List.of();
        }
        Map<String, DiagnosisProbeRecord> rawProbeById = diagnosisProbes == null
                ? Map.of()
                : diagnosisProbes.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getId()))
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), Map::putAll);
        Map<String, MutableDerivedPrompt> promptByKey = new LinkedHashMap<>();
        for (WorkbenchProblemAnalysisView.ProbeRowView row : probeRows) {
            String promptText = firstText(row.getPromptText(), row.getProbeRef(), row.getProbeId(), "未命名探针");
            String promptKey = normalizeBrandKey(promptText);
            if (!StringUtils.hasText(promptKey)) {
                continue;
            }
            DiagnosisProbeRecord rawProbe = rawProbeById.get(normalizeText(row.getProbeId()));
            String topicName = resolveDerivedProblemTopicName(rawProbe, promptText, reportProjection);
            String topicId = "derived_topic_" + normalizeBrandKey(topicName);
            String problemSpaceBucket = firstText(
                    row.getProblemSpaceBucket(),
                    resolveOfficialProblemSpaceBucket(rawProbe)
            );
            MutableDerivedPrompt current = promptByKey.get(promptKey);
            if (current == null) {
                current = new MutableDerivedPrompt();
                current.promptId = "derived_prompt_" + promptKey;
                current.promptText = promptText;
                current.topicId = topicId;
                current.topicName = topicName;
                current.problemSpaceBucket = problemSpaceBucket;
                current.seqNo = row.getSeqNo();
                promptByKey.put(promptKey, current);
            } else {
                if (current.seqNo == null || (row.getSeqNo() != null && row.getSeqNo() < current.seqNo)) {
                    current.seqNo = row.getSeqNo();
                }
                if (!StringUtils.hasText(current.topicName) && StringUtils.hasText(topicName)) {
                    current.topicName = topicName;
                    current.topicId = topicId;
                }
                if (!StringUtils.hasText(current.problemSpaceBucket) && StringUtils.hasText(problemSpaceBucket)) {
                    current.problemSpaceBucket = problemSpaceBucket;
                }
            }
        }
        return promptByKey.values().stream()
                .map(item -> WorkbenchProblemAnalysisView.PromptRowView.builder()
                        .promptId(item.promptId)
                        .topicId(item.topicId)
                        .topicName(item.topicName)
                        .problemSpaceBucket(item.problemSpaceBucket)
                        .seqNo(item.seqNo)
                        .promptText(item.promptText)
                        .enabled(true)
                        .probeCount(0)
                        .evidenceCount(0)
                        .hasRawAnswer(false)
                        .latestProbeStatus(null)
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.PromptRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.TopicRowView> deriveProblemAnalysisTopicRowsFromPromptRows(
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            ReportResponse reportProjection) {
        if (promptRows == null || promptRows.isEmpty()) {
            return List.of();
        }
        Map<String, List<WorkbenchProblemAnalysisView.PromptRowView>> promptsByTopicId = new LinkedHashMap<>();
        for (WorkbenchProblemAnalysisView.PromptRowView row : promptRows) {
            String topicId = firstText(row.getTopicId(), "derived_topic_uncategorized");
            promptsByTopicId.computeIfAbsent(topicId, ignored -> new ArrayList<>()).add(row);
        }
        Map<String, Integer> signalCountByTopicName = readSignalCountByTopicName(reportProjection);
        List<WorkbenchProblemAnalysisView.TopicRowView> rows = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<WorkbenchProblemAnalysisView.PromptRowView>> entry : promptsByTopicId.entrySet()) {
            index++;
            List<WorkbenchProblemAnalysisView.PromptRowView> topicPrompts = entry.getValue();
            String topicName = firstText(topicPrompts.getFirst().getTopicName(), "未分组问题");
            Integer signalCount = firstNonNull(
                    signalCountByTopicName.get(normalizeBrandKey(topicName)),
                    topicPrompts.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getProbeCount)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .sum()
            );
            rows.add(WorkbenchProblemAnalysisView.TopicRowView.builder()
                    .topicId(entry.getKey())
                    .topicName(topicName)
                    .problemSpaceBucket(resolveDominantProblemSpaceBucket(
                            topicPrompts.stream()
                                    .map(WorkbenchProblemAnalysisView.PromptRowView::getProblemSpaceBucket)
                                    .toList()
                    ))
                    .seqNo(firstNonNull(
                            topicPrompts.stream()
                                    .map(WorkbenchProblemAnalysisView.PromptRowView::getSeqNo)
                                    .filter(Objects::nonNull)
                                    .min(Integer::compareTo)
                                    .orElse(null),
                            index
                    ))
                    .enabled(topicPrompts.stream().anyMatch(row -> Boolean.TRUE.equals(row.getEnabled())))
                    .promptCount(topicPrompts.size())
                    .enabledPromptCount((int) topicPrompts.stream().filter(row -> Boolean.TRUE.equals(row.getEnabled())).count())
                    .signalCount(signalCount)
                    .sampleQuestions(topicPrompts.stream()
                            .map(WorkbenchProblemAnalysisView.PromptRowView::getPromptText)
                            .filter(StringUtils::hasText)
                            .limit(3)
                            .toList())
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.TopicRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.TopicRowView> attachProblemSpaceBucketsToTopicRows(
            List<WorkbenchProblemAnalysisView.TopicRowView> topicRows,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        if (topicRows == null || topicRows.isEmpty()) {
            return List.of();
        }
        if (promptRows == null || promptRows.isEmpty()) {
            return topicRows;
        }
        Map<String, List<String>> bucketsByTopicId = new LinkedHashMap<>();
        Map<String, List<String>> bucketsByTopicName = new LinkedHashMap<>();
        for (WorkbenchProblemAnalysisView.PromptRowView promptRow : promptRows) {
            String bucket = normalizeText(promptRow.getProblemSpaceBucket());
            if (!StringUtils.hasText(bucket)) {
                continue;
            }
            String topicId = normalizeText(promptRow.getTopicId());
            if (StringUtils.hasText(topicId)) {
                bucketsByTopicId.computeIfAbsent(topicId, ignored -> new ArrayList<>()).add(bucket);
            }
            String topicName = normalizeText(promptRow.getTopicName());
            if (StringUtils.hasText(topicName)) {
                bucketsByTopicName.computeIfAbsent(topicName, ignored -> new ArrayList<>()).add(bucket);
            }
        }
        return topicRows.stream()
                .map(row -> WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId(row.getTopicId())
                        .topicName(row.getTopicName())
                        .problemSpaceBucket(firstText(
                                row.getProblemSpaceBucket(),
                                resolveDominantProblemSpaceBucket(firstNonNull(
                                        bucketsByTopicId.get(normalizeText(row.getTopicId())),
                                        bucketsByTopicName.get(normalizeText(row.getTopicName())),
                                        List.of()
                                ))
                        ))
                        .seqNo(row.getSeqNo())
                        .enabled(row.getEnabled())
                        .promptCount(row.getPromptCount())
                        .enabledPromptCount(row.getEnabledPromptCount())
                        .signalCount(row.getSignalCount())
                        .sampleQuestions(row.getSampleQuestions())
                        .build())
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.ProbeRowView> attachPromptCoverageToProbeRows(
            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        if (probeRows == null || probeRows.isEmpty() || promptRows == null || promptRows.isEmpty()) {
            return probeRows == null ? List.of() : probeRows;
        }
        Map<String, PromptLookup> promptLookupByText = buildPromptLookupByText(promptRows);
        return probeRows.stream()
                .map(row -> {
                    String candidatePrompt = firstText(row.getPromptText(), row.getProbeRef(), row.getProbeId());
                    PromptLookup lookup = resolvePromptLookup(candidatePrompt, promptRows, promptLookupByText);
                    if (lookup == null) {
                        return row;
                    }
                    return WorkbenchProblemAnalysisView.ProbeRowView.builder()
                            .probeId(row.getProbeId())
                            .probeRef(row.getProbeRef())
                            .seqNo(row.getSeqNo())
                            .topicId(firstText(row.getTopicId(), lookup.topicId()))
                            .topicName(firstText(row.getTopicName(), lookup.topicName()))
                            .problemSpaceBucket(firstText(row.getProblemSpaceBucket(), lookup.problemSpaceBucket()))
                            .promptId(firstText(row.getPromptId(), lookup.promptId()))
                            .promptText(firstText(row.getPromptText(), lookup.promptText()))
                            .renderText(row.getRenderText())
                            .status(row.getStatus())
                            .evidenceCount(row.getEvidenceCount())
                            .hasRawAnswer(row.getHasRawAnswer())
                            .latestAttemptId(row.getLatestAttemptId())
                            .model(row.getModel())
                            .answerPreview(row.getAnswerPreview())
                            .rawAnswer(row.getRawAnswer())
                            .visibilityScore(row.getVisibilityScore())
                            .averagePosition(row.getAveragePosition())
                            .sentimentScore(row.getSentimentScore())
                            .mentionCount(row.getMentionCount())
                            .build();
                })
                .toList();
    }

    private String resolveDerivedProblemTopicName(DiagnosisProbeRecord probe,
                                                  String promptText,
                                                  ReportResponse reportProjection) {
        String clusterTitle = resolveClusterTitleForPrompt(reportProjection, promptText);
        if (StringUtils.hasText(clusterTitle)) {
            return clusterTitle;
        }
        String painPoint = humanizePainPointTopic(probe == null ? null : probe.getD3PainPoint());
        if (StringUtils.hasText(painPoint)) {
            return painPoint;
        }
        String job = humanizeJobTopic(probe == null ? null : probe.getD4Jtbd());
        if (StringUtils.hasText(job)) {
            return job;
        }
        return deriveTopicFromPromptText(promptText);
    }

    private String resolveClusterTitleForPrompt(ReportResponse reportProjection, String promptText) {
        if (reportProjection == null || reportProjection.getClusterProfiles() == null || reportProjection.getClusterProfiles().isEmpty()) {
            return null;
        }
        String normalizedPrompt = normalizeBrandKey(promptText);
        if (!StringUtils.hasText(normalizedPrompt)) {
            return null;
        }
        for (ReportResponse.ClusterProfile profile : reportProjection.getClusterProfiles()) {
            List<String> prompts = profile == null ? null : profile.getPrompts();
            if (prompts == null || prompts.isEmpty()) {
                continue;
            }
            for (String item : prompts) {
                String normalizedItem = normalizeBrandKey(item);
                if (!StringUtils.hasText(normalizedItem)) {
                    continue;
                }
                if (normalizedPrompt.contains(normalizedItem) || normalizedItem.contains(normalizedPrompt)) {
                    return firstText(profile.getTitle(), profile.getArchetype());
                }
            }
        }
        return null;
    }

    private String humanizePainPointTopic(String code) {
        String normalized = normalizeText(code);
        if (!StringUtils.hasText(normalized) || "LEGACY".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "subsistence_protection" -> "风险与踩坑顾虑";
            case "affection_belonging_participation" -> "口碑与他人选择";
            case "understanding_competence" -> "参数理解与能力确认";
            case "identity_freedom" -> "风格偏好与品牌表达";
            case "creation_idleness" -> "省心易用与轻松维护";
            default -> normalized.replace('_', ' ');
        };
    }

    private String humanizeJobTopic(String code) {
        String normalized = normalizeText(code);
        if (!StringUtils.hasText(normalized) || "LEGACY".equals(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "browse_and_compare" -> "选项浏览与初筛";
            case "confirm_fit" -> "适配确认";
            case "avoid_regret" -> "降低后悔风险";
            case "plan_purchase" -> "购买路径规划";
            default -> null;
        };
    }

    private String deriveTopicFromPromptText(String promptText) {
        String text = normalizeText(promptText);
        if (!StringUtils.hasText(text)) {
            text = "";
        }
        if (containsAny(text, "安装", "改造", "兼容", "适配", "管径")) {
            return "安装与适配";
        }
        if (containsAny(text, "售后", "质保", "维修", "换新", "客服")) {
            return "售后与可靠性";
        }
        if (containsAny(text, "价格", "预算", "成本", "溢价", "值不值", "性价比")) {
            return "预算与价格决策";
        }
        if (containsAny(text, "对比", "推荐", "选哪个", "哪个好", "差异")) {
            return "对比与购买决策";
        }
        if (containsAny(text, "出水", "续航", "性能", "稳定", "耐用", "做工", "材质")) {
            return "性能与体验";
        }
        if (containsAny(text, "品牌", "口碑", "评价", "值得买吗")) {
            return "品牌认知与口碑";
        }
        return "综合问题";
    }

    private WorkbenchProblemAnalysisView.RichView buildProblemAnalysisRich(BrandSpaceContext context,
                                                                          List<DiagnosisReportRecord> recentReports,
                                                                          ProblemAnalysisFrame currentFrame,
                                                                          ProblemAnalysisFrame previousFrame,
                                                                          ProblemAnalysisScope scope) {
        ProblemRichSnapshot currentSnapshot = buildProblemRichSnapshot(context, currentFrame);
        ProblemRichSnapshot previousSnapshot = buildProblemRichSnapshot(context, previousFrame);
        return WorkbenchProblemAnalysisView.RichView.builder()
                .volume(buildProblemAnalysisVolume(context, recentReports, currentSnapshot, previousSnapshot, scope))
                .intent(buildProblemAnalysisIntent(currentSnapshot, previousSnapshot))
                .audience(buildProblemAnalysisAudience(currentSnapshot))
                .hierarchy(buildProblemAnalysisHierarchy(currentSnapshot, currentFrame.problemClusters(), scope.active()))
                .fanout(buildProblemAnalysisFanout(currentSnapshot, previousSnapshot))
                .build();
    }

    private ProblemAnalysisFrame aggregateProblemAnalysisFrames(BrandSpaceContext context,
                                                               List<DiagnosisReportRecord> recentReports) {
        if (recentReports == null || recentReports.isEmpty()) {
            return ProblemAnalysisFrame.empty();
        }
        List<ProblemAnalysisFrame> frames = recentReports.stream()
                .filter(Objects::nonNull)
                .map(report -> buildProblemAnalysisFrame(context, report, toReportResponse(report)))
                .toList();
        if (frames.isEmpty()) {
            return ProblemAnalysisFrame.empty();
        }
        LinkedHashMap<String, WorkbenchProblemAnalysisView.PromptRowView> promptCatalog = new LinkedHashMap<>();
        LinkedHashMap<String, MutableAggregatedTopicRow> topicRows = new LinkedHashMap<>();
        LinkedHashMap<String, MutableAggregatedClusterRow> clusterRows = new LinkedHashMap<>();
        LinkedHashMap<String, WorkbenchProblemAnalysisView.ProbeRowView> probeRows = new LinkedHashMap<>();
        LinkedHashMap<String, WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows = new LinkedHashMap<>();
        LinkedHashMap<String, WorkbenchProblemAnalysisView.AttemptRowView> attemptRows = new LinkedHashMap<>();
        LinkedHashMap<String, DiagnosisProbeRecord> diagnosisProbes = new LinkedHashMap<>();
        LinkedHashMap<String, ProbeDispatchAttempt> dispatchAttempts = new LinkedHashMap<>();
        LinkedHashMap<String, ProbeBrandMentionRecord> brandMentions = new LinkedHashMap<>();
        LinkedHashMap<String, ProbeEvidenceRecord> evidenceRecords = new LinkedHashMap<>();

        for (ProblemAnalysisFrame frame : frames) {
            for (WorkbenchProblemAnalysisView.PromptRowView row : frame.promptRows()) {
                String key = firstText(normalizeText(row.getPromptId()), normalizeText(row.getPromptText()));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                promptCatalog.putIfAbsent(key, WorkbenchProblemAnalysisView.PromptRowView.builder()
                        .promptId(row.getPromptId())
                        .topicId(row.getTopicId())
                        .topicName(row.getTopicName())
                        .problemSpaceBucket(row.getProblemSpaceBucket())
                        .seqNo(row.getSeqNo())
                        .promptText(row.getPromptText())
                        .enabled(row.getEnabled())
                        .build());
            }
            for (WorkbenchProblemAnalysisView.TopicRowView row : frame.topicRows()) {
                String key = firstText(normalizeText(row.getTopicId()), normalizeText(row.getTopicName()));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                MutableAggregatedTopicRow accumulator =
                        topicRows.computeIfAbsent(key, ignored -> new MutableAggregatedTopicRow());
                accumulator.topicId = firstText(accumulator.topicId, row.getTopicId());
                accumulator.topicName = firstText(accumulator.topicName, row.getTopicName());
                accumulator.problemSpaceBucket = firstText(accumulator.problemSpaceBucket, row.getProblemSpaceBucket());
                accumulator.seqNo = firstNonNull(accumulator.seqNo, row.getSeqNo());
                accumulator.enabled = accumulator.enabled || Boolean.TRUE.equals(row.getEnabled());
                accumulator.promptCount = Math.max(accumulator.promptCount, safeInt(row.getPromptCount()));
                accumulator.enabledPromptCount = Math.max(accumulator.enabledPromptCount, safeInt(row.getEnabledPromptCount()));
                accumulator.signalCount += safeInt(row.getSignalCount());
                if (row.getSampleQuestions() != null) {
                    accumulator.sampleQuestions.addAll(row.getSampleQuestions());
                }
            }
            for (WorkbenchProblemAnalysisView.ProblemClusterView row : frame.problemClusters()) {
                String key = firstText(normalizeText(row.getId()),
                        normalizeText(row.getProblemSpaceBucket()) + "|" + normalizeText(row.getTitle()));
                if (!StringUtils.hasText(key)) {
                    continue;
                }
                MutableAggregatedClusterRow accumulator =
                        clusterRows.computeIfAbsent(key, ignored -> new MutableAggregatedClusterRow());
                accumulator.id = firstText(accumulator.id, row.getId());
                accumulator.title = firstText(accumulator.title, row.getTitle());
                accumulator.summary = firstText(accumulator.summary, row.getSummary());
                accumulator.severity = firstText(accumulator.severity, row.getSeverity());
                accumulator.problemSpaceBucket = firstText(accumulator.problemSpaceBucket, row.getProblemSpaceBucket());
                accumulator.signalCount += safeInt(row.getSignalCount());
                if (row.getRepresentativeQuestions() != null) {
                    accumulator.representativeQuestions.addAll(row.getRepresentativeQuestions());
                }
            }
            for (WorkbenchProblemAnalysisView.ProbeRowView row : frame.probeRows()) {
                probeRows.putIfAbsent(normalizeText(row.getProbeId()), row);
            }
            for (WorkbenchProblemAnalysisView.EvidenceRowView row : frame.evidenceRows()) {
                String key = firstText(normalizeText(row.getEvidenceId()),
                        buildEvidenceIdentityKey(row.getAttemptId(), row.getProbeId(), row.getUrl()));
                if (StringUtils.hasText(key)) {
                    evidenceRows.putIfAbsent(key, row);
                }
            }
            for (WorkbenchProblemAnalysisView.AttemptRowView row : frame.attemptRows()) {
                String key = normalizeText(row.getAttemptId());
                if (StringUtils.hasText(key)) {
                    attemptRows.putIfAbsent(key, row);
                }
            }
            for (DiagnosisProbeRecord row : frame.diagnosisProbes()) {
                String key = normalizeText(row.getId());
                if (StringUtils.hasText(key)) {
                    diagnosisProbes.putIfAbsent(key, row);
                }
            }
            for (ProbeDispatchAttempt row : frame.dispatchAttempts()) {
                String key = normalizeText(row.getId());
                if (StringUtils.hasText(key)) {
                    dispatchAttempts.putIfAbsent(key, row);
                }
            }
            for (ProbeBrandMentionRecord row : frame.brandMentions()) {
                String key = firstText(normalizeText(row.getId()),
                        normalizeText(row.getDispatchAttemptId()) + "|" + normalizeText(row.getProbeId()) + "|" + normalizeText(row.getBrandName()));
                if (StringUtils.hasText(key)) {
                    brandMentions.putIfAbsent(key, row);
                }
            }
            for (ProbeEvidenceRecord row : frame.evidenceRecords()) {
                String key = firstText(normalizeText(row.getId()),
                        buildEvidenceIdentityKey(row.getDispatchAttemptId(), row.getProbeId(), row.getUrl()));
                if (StringUtils.hasText(key)) {
                    evidenceRecords.putIfAbsent(key, row);
                }
            }
        }

        List<WorkbenchProblemAnalysisView.ProbeRowView> mergedProbeRows = probeRows.values().stream()
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.ProbeRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<WorkbenchProblemAnalysisView.EvidenceRowView> mergedEvidenceRows = evidenceRows.values().stream()
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.EvidenceRowView::getCapturedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
        List<WorkbenchProblemAnalysisView.PromptRowView> mergedPromptRows = enrichProblemAnalysisPromptRows(
                context,
                new ArrayList<>(promptCatalog.values()),
                mergedProbeRows,
                mergedEvidenceRows
        );
        List<WorkbenchProblemAnalysisView.TopicRowView> mergedTopicRows = topicRows.values().stream()
                .map(item -> WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId(item.topicId)
                        .topicName(item.topicName)
                        .problemSpaceBucket(item.problemSpaceBucket)
                        .seqNo(item.seqNo)
                        .enabled(item.enabled)
                        .promptCount(item.promptCount)
                        .enabledPromptCount(item.enabledPromptCount)
                        .signalCount(item.signalCount)
                        .sampleQuestions(item.sampleQuestions.stream().filter(StringUtils::hasText).distinct().limit(3).toList())
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.TopicRowView::getSignalCount,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkbenchProblemAnalysisView.TopicRowView::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        List<WorkbenchProblemAnalysisView.ProblemClusterView> mergedClusters = clusterRows.values().stream()
                .map(item -> WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id(item.id)
                        .title(item.title)
                        .summary(item.summary)
                        .severity(item.severity)
                        .problemSpaceBucket(item.problemSpaceBucket)
                        .signalCount(item.signalCount)
                        .representativeQuestions(item.representativeQuestions.stream().filter(StringUtils::hasText).distinct().limit(5).toList())
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.ProblemClusterView::getSignalCount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return new ProblemAnalysisFrame(
                mergedTopicRows,
                mergedPromptRows,
                mergedClusters,
                mergedProbeRows,
                mergedEvidenceRows,
                new ArrayList<>(attemptRows.values()),
                new ArrayList<>(diagnosisProbes.values()),
                new ArrayList<>(dispatchAttempts.values()),
                new ArrayList<>(brandMentions.values()),
                new ArrayList<>(evidenceRecords.values())
        );
    }

    private KnowledgeRunWindowSelection resolveKnowledgeRunWindowSelection(KnowledgeAssetRunRepository runRepository,
                                                                           String brandScopeId,
                                                                           WorkbenchAnalysisContextView analysisContext) {
        if (runRepository == null || !StringUtils.hasText(brandScopeId) || analysisContext == null) {
            return null;
        }
        LocalDate windowFrom = parseDate(analysisContext.getDateFrom());
        LocalDate windowTo = parseDate(analysisContext.getDateTo());
        if (windowFrom == null || windowTo == null) {
            return null;
        }
        if (windowFrom.isAfter(windowTo)) {
            LocalDate swap = windowFrom;
            windowFrom = windowTo;
            windowTo = swap;
        }
        OffsetDateTime currentWindowStartAt = toWindowStartAt(windowFrom);
        OffsetDateTime currentWindowEndAt = toWindowEndAt(windowTo);
        List<KnowledgeAssetRunRepository.RunDetailView> currentRuns = runRepository.listRunDetailsByScopeWithinWindow(
                brandScopeId.trim(),
                currentWindowStartAt,
                currentWindowEndAt
        );
        List<KnowledgeAssetRunRepository.RunDetailView> previousRuns = List.of();
        if ("previous_period".equals(defaultIfBlank(analysisContext.getComparison(), "previous_period"))) {
            long windowDays = Math.max(1L, windowTo.toEpochDay() - windowFrom.toEpochDay() + 1L);
            LocalDate previousWindowTo = windowFrom.minusDays(1);
            LocalDate previousWindowFrom = previousWindowTo.minusDays(windowDays - 1L);
            previousRuns = runRepository.listRunDetailsByScopeWithinWindow(
                    brandScopeId.trim(),
                    toWindowStartAt(previousWindowFrom),
                    toWindowEndAt(previousWindowTo)
            );
        }
        return new KnowledgeRunWindowSelection(
                currentRuns == null ? List.of() : currentRuns,
                previousRuns == null ? List.of() : previousRuns,
                currentWindowStartAt,
                currentWindowEndAt
        );
    }

    private ProblemAnalysisFrame buildKnowledgeProblemAnalysisFrame(BrandSpaceContext context,
                                                                    List<KnowledgeAssetRunRepository.RunDetailView> runDetails) {
        if (runDetails == null || runDetails.isEmpty()) {
            return ProblemAnalysisFrame.empty();
        }
        List<DiagnosisReportRecord> knowledgeReports = runDetails.stream()
                .map(this::synthesizeReportFromKnowledgeRun)
                .filter(Objects::nonNull)
                .toList();
        if (knowledgeReports.isEmpty()) {
            return ProblemAnalysisFrame.empty();
        }
        ProblemAnalysisFrame runtimeFrame = knowledgeReports.size() == 1
                ? buildProblemAnalysisFrame(context, knowledgeReports.getFirst(), toReportResponse(knowledgeReports.getFirst()))
                : aggregateProblemAnalysisFrames(context, knowledgeReports);
        return reconcileKnowledgeProblemAnalysisFrame(context, runDetails, runtimeFrame);
    }

    private ProblemAnalysisFrame reconcileKnowledgeProblemAnalysisFrame(BrandSpaceContext context,
                                                                       List<KnowledgeAssetRunRepository.RunDetailView> runDetails,
                                                                       ProblemAnalysisFrame runtimeFrame) {
        if (runtimeFrame == null) {
            return ProblemAnalysisFrame.empty();
        }
        List<WorkbenchProblemAnalysisView.PromptRowView> promptSeedRows = runtimeFrame.promptRows().isEmpty()
                ? buildProblemAnalysisPromptRows(context)
                : runtimeFrame.promptRows();
        if (promptSeedRows.isEmpty()) {
            promptSeedRows = deriveProblemAnalysisPromptRowsFromProbes(runtimeFrame.probeRows(), runtimeFrame.diagnosisProbes(), null);
        }
        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows = attachPromptCoverageToProbeRows(runtimeFrame.probeRows(), promptSeedRows);
        Map<String, WorkbenchProblemAnalysisView.ProbeRowView> probeRowById = indexProbeRowsById(probeRows);
        List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows = buildProblemAnalysisEvidenceRows(
                context,
                runtimeFrame.evidenceRecords(),
                runtimeFrame.dispatchAttempts(),
                probeRowById
        );
        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows = enrichProblemAnalysisPromptRows(
                context,
                promptSeedRows,
                probeRows,
                evidenceRows
        );
        probeRows = attachPromptCoverageToProbeRows(runtimeFrame.probeRows(), promptRows);
        probeRowById = indexProbeRowsById(probeRows);
        evidenceRows = buildProblemAnalysisEvidenceRows(
                context,
                runtimeFrame.evidenceRecords(),
                runtimeFrame.dispatchAttempts(),
                probeRowById
        );
        List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows = buildProblemAnalysisAttemptRows(
                runtimeFrame.dispatchAttempts(),
                runtimeFrame.brandMentions(),
                evidenceRows,
                probeRowById
        );
        List<WorkbenchProblemAnalysisView.TopicRowView> topicRows = buildKnowledgeProblemTopicRows(
                context,
                runDetails,
                promptRows
        );
        List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters = buildKnowledgeProblemClusters(
                runDetails,
                promptRows
        );
        return new ProblemAnalysisFrame(
                topicRows,
                promptRows,
                problemClusters,
                probeRows,
                evidenceRows,
                attemptRows,
                runtimeFrame.diagnosisProbes(),
                runtimeFrame.dispatchAttempts(),
                runtimeFrame.brandMentions(),
                runtimeFrame.evidenceRecords()
        );
    }

    private List<WorkbenchProblemAnalysisView.TopicRowView> buildKnowledgeProblemTopicRows(
            BrandSpaceContext context,
            List<KnowledgeAssetRunRepository.RunDetailView> runDetails,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        List<WorkbenchProblemAnalysisView.TopicRowView> derivedRows = attachProblemSpaceBucketsToTopicRows(
                deriveProblemAnalysisTopicRowsFromPromptRows(promptRows, null),
                promptRows
        );
        if (context == null || context.scope() == null || !StringUtils.hasText(context.scope().getId()) || runDetails == null || runDetails.isEmpty()) {
            return derivedRows;
        }
        KnowledgeAssetProblemRepository repository = knowledgeAssetProblemRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return derivedRows;
        }
        List<String> runIds = runDetails.stream()
                .map(KnowledgeAssetRunRepository.RunDetailView::runId)
                .filter(StringUtils::hasText)
                .toList();
        if (runIds.isEmpty()) {
            return derivedRows;
        }
        List<KnowledgeAssetProblemRepository.TopicCatalogView> topicCatalog = repository.listActiveTopics(context.scope().getId());
        Map<String, KnowledgeAssetProblemRepository.TopicCatalogView> topicByCatalogId = new LinkedHashMap<>();
        for (KnowledgeAssetProblemRepository.TopicCatalogView item : topicCatalog) {
            if (item != null && StringUtils.hasText(item.topicCatalogId())) {
                topicByCatalogId.put(item.topicCatalogId(), item);
            }
        }
        Map<String, Integer> signalCountByTopicId = new LinkedHashMap<>();
        Map<String, Integer> signalCountByTopicName = new LinkedHashMap<>();
        for (KnowledgeAssetProblemRepository.ProblemSnapshotFactView fact : repository.listSnapshotFactsByRunIds(runIds)) {
            if (fact == null) {
                continue;
            }
            int signalCount = firstNonNull(
                    fact.evidenceCount(),
                    fact.probeCount(),
                    fact.promptCount(),
                    0
            );
            KnowledgeAssetProblemRepository.TopicCatalogView topic = topicByCatalogId.get(fact.topicCatalogId());
            if (topic != null) {
                if (StringUtils.hasText(topic.sourceTopicId())) {
                    signalCountByTopicId.merge(topic.sourceTopicId(), signalCount, Integer::sum);
                }
                if (StringUtils.hasText(topic.topicName())) {
                    signalCountByTopicName.merge(normalizeText(topic.topicName()), signalCount, Integer::sum);
                }
            }
        }
        return derivedRows.stream()
                .map(row -> WorkbenchProblemAnalysisView.TopicRowView.builder()
                        .topicId(row.getTopicId())
                        .topicName(row.getTopicName())
                        .problemSpaceBucket(row.getProblemSpaceBucket())
                        .seqNo(row.getSeqNo())
                        .enabled(row.getEnabled())
                        .promptCount(row.getPromptCount())
                        .enabledPromptCount(row.getEnabledPromptCount())
                        .signalCount(firstNonNull(
                                signalCountByTopicId.get(row.getTopicId()),
                                signalCountByTopicName.get(normalizeText(row.getTopicName())),
                                row.getSignalCount()
                        ))
                        .sampleQuestions(row.getSampleQuestions())
                        .build())
                .toList();
    }

    private List<WorkbenchProblemAnalysisView.ProblemClusterView> buildKnowledgeProblemClusters(
            List<KnowledgeAssetRunRepository.RunDetailView> runDetails,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        if (runDetails == null || runDetails.isEmpty()) {
            return List.of();
        }
        KnowledgeAssetProblemRepository repository = knowledgeAssetProblemRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return List.of();
        }
        List<String> runIds = runDetails.stream()
                .map(KnowledgeAssetRunRepository.RunDetailView::runId)
                .filter(StringUtils::hasText)
                .toList();
        if (runIds.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MutableAggregatedClusterRow> clusters = new LinkedHashMap<>();
        for (KnowledgeAssetProblemRepository.ProblemSnapshotFactView fact : repository.listSnapshotFactsByRunIds(runIds)) {
            if (fact == null) {
                continue;
            }
            String bucket = normalizeText(fact.problemSpaceBucket());
            String key = firstText(normalizeText(fact.clusterKey()), bucket, "knowledge-cluster");
            MutableAggregatedClusterRow cluster = clusters.computeIfAbsent(key, ignored -> new MutableAggregatedClusterRow());
            cluster.id = firstText(cluster.id, fact.clusterKey(), key);
            cluster.problemSpaceBucket = firstText(cluster.problemSpaceBucket, bucket);
            cluster.title = firstText(cluster.title, resolveKnowledgeProblemClusterTitle(fact.clusterKey(), bucket));
            cluster.signalCount += firstNonNull(
                    fact.evidenceCount(),
                    fact.probeCount(),
                    fact.promptCount(),
                    0
            );
        }
        if (clusters.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> sampleQuestionsByBucket = new LinkedHashMap<>();
        if (promptRows != null) {
            for (WorkbenchProblemAnalysisView.PromptRowView promptRow : promptRows) {
                String bucket = normalizeText(promptRow.getProblemSpaceBucket());
                if (!StringUtils.hasText(bucket) || !StringUtils.hasText(promptRow.getPromptText())) {
                    continue;
                }
                sampleQuestionsByBucket.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(promptRow.getPromptText());
            }
        }
        return clusters.values().stream()
                .map(item -> WorkbenchProblemAnalysisView.ProblemClusterView.builder()
                        .id(item.id)
                        .title(item.title)
                        .summary("最近窗口累计信号：" + item.signalCount)
                        .severity(item.signalCount >= 8 ? "high" : item.signalCount > 0 ? "medium" : "low")
                        .problemSpaceBucket(item.problemSpaceBucket)
                        .signalCount(item.signalCount)
                        .representativeQuestions(sampleQuestionsByBucket.getOrDefault(normalizeText(item.problemSpaceBucket), List.of())
                                .stream()
                                .filter(StringUtils::hasText)
                                .distinct()
                                .limit(4)
                                .toList())
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.ProblemClusterView::getSignalCount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private WorkbenchProblemAnalysisView.RichView buildKnowledgeProblemAnalysisRich(
            BrandSpaceContext context,
            List<KnowledgeAssetRunRepository.RunDetailView> currentRuns,
            ProblemAnalysisFrame currentFrame,
            ProblemAnalysisFrame previousFrame,
            ProblemAnalysisScope scope) {
        ProblemRichSnapshot currentSnapshot = buildProblemRichSnapshot(context, currentFrame);
        ProblemRichSnapshot previousSnapshot = buildProblemRichSnapshot(context, previousFrame);
        return WorkbenchProblemAnalysisView.RichView.builder()
                .volume(buildKnowledgeProblemAnalysisVolume(context, currentRuns, currentSnapshot, previousSnapshot, scope))
                .intent(buildProblemAnalysisIntent(currentSnapshot, previousSnapshot))
                .audience(buildProblemAnalysisAudience(currentSnapshot))
                .hierarchy(buildProblemAnalysisHierarchy(currentSnapshot, currentFrame.problemClusters(), scope.active()))
                .fanout(buildProblemAnalysisFanout(currentSnapshot, previousSnapshot))
                .build();
    }

    private WorkbenchProblemAnalysisView.VolumeView buildKnowledgeProblemAnalysisVolume(
            BrandSpaceContext context,
            List<KnowledgeAssetRunRepository.RunDetailView> currentRuns,
            ProblemRichSnapshot currentSnapshot,
            ProblemRichSnapshot previousSnapshot,
            ProblemAnalysisScope scope) {
        List<WorkbenchProblemAnalysisView.PlatformDemandRowView> platformRows = buildVolumePlatformRows(currentSnapshot, previousSnapshot);

        List<WorkbenchProblemAnalysisView.PromptOpportunityRowView> promptOpportunityRows = currentSnapshot.prompts().stream()
                .sorted(Comparator.comparing(PromptSignalView::opportunityScore, Comparator.reverseOrder())
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .limit(16)
                .map(item -> {
                    PromptSignalView previous = previousSnapshot.promptByKey().get(item.promptKey());
                    double trendDelta = previous == null || previous.demandIndex() <= 0
                            ? 0.0d
                            : round2((item.demandIndex() - previous.demandIndex()) * 100.0d / previous.demandIndex());
                    int projection = Math.max(item.demandIndex(), (int) Math.round(item.demandIndex() * (trendDelta >= 0 ? 1.18d : 1.06d)));
                    return WorkbenchProblemAnalysisView.PromptOpportunityRowView.builder()
                            .prompt(item.promptText())
                            .topic(item.topic())
                            .problemSpaceBucket(item.problemSpaceBucket())
                            .demandIndex(item.demandIndex())
                            .projection(projection)
                            .competitionScore(item.competitionScore())
                            .relevanceScore(item.relevanceScore())
                            .opportunityScore(item.opportunityScore())
                            .intentLabel(item.intentLabel())
                            .trendDelta(trendDelta)
                            .build();
                })
                .toList();

        List<WorkbenchProblemAnalysisView.PromptProjectionRowView> promptProjectionRows = promptOpportunityRows.stream()
                .limit(8)
                .map(item -> WorkbenchProblemAnalysisView.PromptProjectionRowView.builder()
                        .prompt(item.getPrompt())
                        .projection(item.getProjection())
                        .share(promptOpportunityRows.isEmpty()
                                ? 0.0d
                                : round2(item.getProjection() * 100.0d / Math.max(1, promptOpportunityRows.stream()
                                .map(WorkbenchProblemAnalysisView.PromptOpportunityRowView::getProjection)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .sum())))
                        .intentLabel(item.getIntentLabel())
                        .action(resolveProjectionAction(item.getOpportunityScore()))
                        .build())
                .toList();

        List<WorkbenchProblemAnalysisView.PromptLoopRowView> promptLoopRows = currentSnapshot.prompts().stream()
                .sorted(Comparator.comparing(PromptSignalView::opportunityScore, Comparator.reverseOrder())
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .limit(16)
                .map(item -> WorkbenchProblemAnalysisView.PromptLoopRowView.builder()
                        .id(item.promptId())
                        .topic(item.topic())
                        .problemSpaceBucket(item.problemSpaceBucket())
                        .prompt(item.promptText())
                        .status(item.loopStatus())
                        .citedTarget(firstText(item.competitorLeadBrand(), item.targetBrandCited() ? "当前品牌" : "待抢占"))
                        .opportunity(item.opportunityScore())
                        .action(item.loopAction())
                        .targetBrandCited(item.targetBrandCited())
                        .citationCoverage(item.citationShare())
                        .competitorLeadBrand(item.competitorLeadBrand())
                        .citedBrandNames(item.citedBrandNames())
                        .gapType(item.gapType())
                        .build())
                .toList();

        double coverage = currentSnapshot.prompts().isEmpty()
                ? 0.0d
                : round2(currentSnapshot.prompts().stream()
                .filter(item -> "已被引用".equals(item.loopStatus()))
                .count() * 100.0d / currentSnapshot.prompts().size());

        return WorkbenchProblemAnalysisView.VolumeView.builder()
                .trendPoints(buildKnowledgeProblemVolumeTrendPoints(context, currentRuns, scope))
                .summary(buildVolumeSummary(currentSnapshot, previousSnapshot))
                .platformRows(platformRows)
                .promptOpportunityRows(promptOpportunityRows)
                .promptProjectionSummary(WorkbenchProblemAnalysisView.PromptProjectionSummaryView.builder()
                        .promptCount(promptProjectionRows.size())
                        .totalProjection(promptProjectionRows.stream()
                                .map(WorkbenchProblemAnalysisView.PromptProjectionRowView::getProjection)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .sum())
                        .highValuePromptCount((int) promptOpportunityRows.stream().filter(item -> safeInt(item.getOpportunityScore()) >= 75).count())
                        .build())
                .promptProjectionRows(promptProjectionRows)
                .promptLoopSummary(WorkbenchProblemAnalysisView.PromptLoopSummaryView.builder()
                        .coverage(coverage)
                        .gapCount((int) currentSnapshot.prompts().stream()
                                .filter(item -> "竞品被引（我方缺口）".equals(item.loopStatus()))
                                .count())
                        .highOpportunityCount((int) currentSnapshot.prompts().stream()
                                .filter(item -> item.opportunityScore() >= 75)
                                .count())
                        .build())
                .promptLoopRows(promptLoopRows)
                .build();
    }

    private List<WorkbenchProblemAnalysisView.VolumeTrendPointView> buildKnowledgeProblemVolumeTrendPoints(
            BrandSpaceContext context,
            List<KnowledgeAssetRunRepository.RunDetailView> currentRuns,
            ProblemAnalysisScope scope) {
        if (currentRuns == null || currentRuns.isEmpty()) {
            return List.of();
        }
        List<KnowledgeAssetRunRepository.RunDetailView> orderedRuns = currentRuns.stream()
                .sorted(Comparator.comparing(KnowledgeAssetRunRepository.RunDetailView::observedAt,
                        Comparator.nullsLast(OffsetDateTime::compareTo)))
                .toList();
        List<KnowledgeAssetRunRepository.RunDetailView> sorted = orderedRuns;
        List<ProblemVolumeSlice> slices = new ArrayList<>();
        for (KnowledgeAssetRunRepository.RunDetailView runDetail : sorted) {
            ProblemAnalysisFrame frame = buildKnowledgeProblemAnalysisFrame(context, List.of(runDetail));
            ProblemRichSnapshot snapshot = buildProblemRichSnapshot(context, applyProblemAnalysisScope(frame, scope));
            slices.add(new ProblemVolumeSlice(
                    synthesizeReportFromKnowledgeRun(runDetail),
                    snapshot
            ));
        }
        List<WorkbenchProblemAnalysisView.VolumeTrendPointView> points = new ArrayList<>();
        for (int index = 0; index < slices.size(); index++) {
            ProblemVolumeSlice current = slices.get(index);
            ProblemVolumeSlice previous = index == 0 ? null : slices.get(index - 1);
            points.add(WorkbenchProblemAnalysisView.VolumeTrendPointView.builder()
                    .sliceKey(current.report() == null ? null : current.report().getDiagnosisTaskId())
                    .label(resolveTrendLabel(current.report() == null ? null : current.report().getGeneratedAt()))
                    .generatedAt(formatDateTime(current.report() == null ? null : current.report().getGeneratedAt()))
                    .currentValue(Math.max(current.snapshot().totalDemandIndex(), 0))
                    .previousValue(previous == null ? null : Math.max(previous.snapshot().totalDemandIndex(), 0))
                    .isLatest(index == slices.size() - 1)
                    .snapshot(buildVolumeTimeSliceSnapshot(
                            current.snapshot(),
                            previous == null ? ProblemRichSnapshot.empty() : previous.snapshot()
                    ))
                    .build());
        }
        return points;
    }

    private void markKnowledgeProblemAnalysisContext(WorkbenchAnalysisContextView analysisContext,
                                                     KnowledgeRunWindowSelection selection) {
        if (analysisContext == null) {
            return;
        }
        List<String> notes = new ArrayList<>();
        notes.add("当前结果来自知识资产正式查询，目录层、运行时证据层与深层 rich 视图不再直接依赖旧报告快照。");
        if (analysisContext.getPlatforms() != null && !analysisContext.getPlatforms().isEmpty()) {
            notes.add("当前平台筛选在知识运行的 attempt / probe / evidence 层按统一平台分类生效。");
        }
        if (!selection.previousRuns().isEmpty()) {
            notes.add("当前对比口径使用上一自然时间窗口。");
        }
        if (selection.currentRuns().isEmpty()) {
            notes.add("当前窗口未命中知识运行，已返回正式空态。");
        }
        analysisContext.setTruthSource("configuration+knowledge_window_query+knowledge_evidence");
        analysisContext.setSourceWindowKey(defaultIfBlank(analysisContext.getWindowKey(), "custom"));
        analysisContext.setNotes(List.copyOf(notes));
    }

    private WorkbenchProblemAnalysisView buildStrictKnowledgeProblemAnalysisEmptyView(BrandSpaceContext context,
                                                                                      ProblemAnalysisScope scope,
                                                                                      WorkbenchAnalysisContextView analysisContext) {
        ProblemAnalysisFrame emptyFrame = ProblemAnalysisFrame.empty();
        populateProblemAnalysisPlatforms(analysisContext, emptyFrame);
        markStrictKnowledgeWorkbenchAnalysisContext(analysisContext, "problem-analysis");
        WorkbenchDataAvailabilityView availability = buildKnowledgeWindowEmptyAvailability(context, analysisContext);
        return WorkbenchProblemAnalysisView.builder()
                .identity(buildBrandIdentity(context))
                .brandSpaceId(context.scope().getId())
                .brandId(context.brand() == null ? null : context.brand().getId())
                .brandName(resolveBrandName(context))
                .category(resolveCategory(context))
                .officialWebsite(resolveOfficialWebsite(context))
                .primaryDomain(resolvePrimaryDomain(context))
                .brandSpaceStatus(mapBrandSpaceStatus(context.latestTask(), context.latestReport()))
                .latestRunId(context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context.latestReport() == null ? null : context.latestReport().getId())
                .lastGeneratedAt(formatDateTime(context.latestReport() == null ? null : context.latestReport().getGeneratedAt()))
                .analysisContext(analysisContext)
                .dataAvailability(availability)
                .summary(WorkbenchProblemAnalysisView.SummaryView.builder()
                        .topicCount(0)
                        .promptCount(0)
                        .enabledPromptCount(0)
                        .problemClusterCount(0)
                        .probeCount(0)
                        .attemptCount(0)
                        .linkedProbeCount(0)
                        .evidenceCount(0)
                        .linkedEvidenceCount(0)
                        .rawAnswerCount(0)
                        .linkedPromptCount(0)
                        .build())
                .catalog(buildProblemAnalysisCatalog(emptyFrame))
                .topicRows(List.of())
                .promptRows(List.of())
                .problemClusters(List.of())
                .probeRows(List.of())
                .evidenceRows(List.of())
                .attemptRows(List.of())
                .rich(buildKnowledgeProblemAnalysisRich(context, List.of(), emptyFrame, emptyFrame, scope))
                .build();
    }

    private String resolveKnowledgeProblemClusterTitle(String clusterKey, String bucket) {
        if (StringUtils.hasText(clusterKey)) {
            return clusterKey;
        }
        ProblemSpaceBucketDefinition definition = PROBLEM_SPACE_BUCKET_DEFINITIONS.get(normalizeText(bucket));
        return definition == null ? "未分类问题" : definition.label();
    }

    private OffsetDateTime toWindowStartAt(LocalDate value) {
        if (value == null) {
            return null;
        }
        return value.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private OffsetDateTime toWindowEndAt(LocalDate value) {
        if (value == null) {
            return null;
        }
        return value.plusDays(1L)
                .atStartOfDay(ZoneId.systemDefault())
                .toOffsetDateTime()
                .minusNanos(1L);
    }

    private WorkbenchProblemAnalysisView.CatalogView buildProblemAnalysisCatalog(ProblemAnalysisFrame frame) {
        if (frame == null) {
            return WorkbenchProblemAnalysisView.CatalogView.builder()
                    .problemSpaceBuckets(List.of())
                    .build();
        }
        LinkedHashMap<String, LinkedHashSet<String>> topicKeysByBucket = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> promptCountByBucket = new LinkedHashMap<>();

        for (WorkbenchProblemAnalysisView.TopicRowView row : frame.topicRows()) {
            String bucket = normalizeText(row.getProblemSpaceBucket());
            if (!StringUtils.hasText(bucket)) {
                continue;
            }
            String topicKey = firstText(normalizeText(row.getTopicId()), normalizeText(row.getTopicName()));
            if (StringUtils.hasText(topicKey)) {
                topicKeysByBucket.computeIfAbsent(bucket, ignored -> new LinkedHashSet<>()).add(topicKey);
            }
        }
        for (WorkbenchProblemAnalysisView.PromptRowView row : frame.promptRows()) {
            String bucket = normalizeText(row.getProblemSpaceBucket());
            if (!StringUtils.hasText(bucket)) {
                continue;
            }
            promptCountByBucket.merge(bucket, 1, Integer::sum);
            String topicKey = firstText(normalizeText(row.getTopicId()), normalizeText(row.getTopicName()));
            if (StringUtils.hasText(topicKey)) {
                topicKeysByBucket.computeIfAbsent(bucket, ignored -> new LinkedHashSet<>()).add(topicKey);
            }
        }
        Set<String> bucketsWithPromptCounts = new LinkedHashSet<>(promptCountByBucket.keySet());
        for (WorkbenchProblemAnalysisView.ProbeRowView row : frame.probeRows()) {
            String bucket = normalizeText(row.getProblemSpaceBucket());
            if (!StringUtils.hasText(bucket) || bucketsWithPromptCounts.contains(bucket)) {
                continue;
            }
            promptCountByBucket.merge(bucket, 1, Integer::sum);
            String topicKey = firstText(normalizeText(row.getTopicId()), normalizeText(row.getTopicName()));
            if (StringUtils.hasText(topicKey)) {
                topicKeysByBucket.computeIfAbsent(bucket, ignored -> new LinkedHashSet<>()).add(topicKey);
            }
        }

        List<WorkbenchProblemAnalysisView.CatalogBucketView> rows = PROBLEM_SPACE_BUCKET_ORDER.stream()
                .map(PROBLEM_SPACE_BUCKET_DEFINITIONS::get)
                .filter(Objects::nonNull)
                .map(definition -> {
                    int promptCount = promptCountByBucket.getOrDefault(definition.key(), 0);
                    int topicCount = topicKeysByBucket.getOrDefault(definition.key(), new LinkedHashSet<>()).size();
                    if (promptCount <= 0 && topicCount <= 0) {
                        return null;
                    }
                    return WorkbenchProblemAnalysisView.CatalogBucketView.builder()
                            .key(definition.key())
                            .label(definition.label())
                            .description(definition.description())
                            .topicCount(topicCount)
                            .promptCount(promptCount)
                            .volume(promptCount > 0 ? promptCount : topicCount)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return WorkbenchProblemAnalysisView.CatalogView.builder()
                .problemSpaceBuckets(rows)
                .build();
    }

    private String resolveOfficialProblemSpaceBucket(DiagnosisProbeRecord rawProbe) {
        if (rawProbe == null) {
            return null;
        }

        String d3 = normalizeText(rawProbe.getD3PainPoint());
        if (StringUtils.hasText(d3)) {
            return switch (d3) {
                case "subsistence_protection" -> PROBLEM_SPACE_RISK_OBJECTION;
                case "affection_belonging_participation", "identity_freedom" -> PROBLEM_SPACE_BRAND_AWARENESS;
                case "understanding_competence" -> PROBLEM_SPACE_CAPABILITY_VALIDATION;
                case "creation_idleness" -> PROBLEM_SPACE_SCENARIO_FIT;
                default -> null;
            };
        }

        String d4 = normalizeText(rawProbe.getD4Jtbd());
        if (StringUtils.hasText(d4)) {
            return switch (d4) {
                case "browse_and_compare", "plan_purchase" -> PROBLEM_SPACE_COMPETITIVE_DECISION;
                case "confirm_fit" -> PROBLEM_SPACE_SCENARIO_FIT;
                case "avoid_regret" -> PROBLEM_SPACE_RISK_OBJECTION;
                default -> null;
            };
        }

        String surfaceIntent = normalizeText(rawProbe.getSurfaceIntent());
        if (StringUtils.hasText(surfaceIntent)) {
            return switch (surfaceIntent) {
                case "confirm_and_buy", "high_frequency_decision_query" -> PROBLEM_SPACE_COMPETITIVE_DECISION;
                case "ask_for_actionable_steps" -> PROBLEM_SPACE_SCENARIO_FIT;
                case "brand_stress_test" -> PROBLEM_SPACE_RISK_OBJECTION;
                case "seek_clear_answer" -> PROBLEM_SPACE_CAPABILITY_VALIDATION;
                case "browse_for_options", "brand_exclusion_test" -> PROBLEM_SPACE_BRAND_AWARENESS;
                default -> null;
            };
        }

        String bucketType = normalizeText(rawProbe.getBucketTypeCode());
        if (StringUtils.hasText(bucketType)) {
            return resolveProblemSpaceBucketFromStructuralBucket(bucketType);
        }

        return null;
    }

    private String resolveProblemSpaceBucketFromStructuralBucket(String bucketType) {
        String normalized = normalizeText(bucketType);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return switch (normalized) {
            case "adversarial" -> PROBLEM_SPACE_RISK_OBJECTION;
            case "pareto" -> PROBLEM_SPACE_COMPETITIVE_DECISION;
            case "orthogonal" -> PROBLEM_SPACE_CAPABILITY_VALIDATION;
            default -> null;
        };
    }

    private String resolveDominantProblemSpaceBucket(Collection<String> bucketKeys) {
        if (bucketKeys == null || bucketKeys.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String bucketKey : bucketKeys) {
            String normalized = normalizeText(bucketKey);
            if (StringUtils.hasText(normalized)) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }
        return resolveDominantKey(counts, null);
    }

    private void rememberProblemSpaceBucket(MutablePromptSignal signal, String bucketKey) {
        if (signal == null) {
            return;
        }
        String normalized = normalizeText(bucketKey);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        signal.problemSpaceBucket = firstText(signal.problemSpaceBucket, normalized);
        signal.problemSpaceBucketCounts.merge(normalized, 1, Integer::sum);
    }

    private ProblemRichSnapshot buildProblemRichSnapshot(BrandSpaceContext context,
                                                         ProblemAnalysisFrame frame) {
        if (frame == null || (frame.promptRows().isEmpty() && frame.probeRows().isEmpty())) {
            return ProblemRichSnapshot.empty();
        }
        Set<String> ownedDomains = resolveOwnedDomains(context);
        String targetBrandKey = normalizeBrandKey(resolveBrandName(context));
        Map<String, DiagnosisProbeRecord> rawProbeById = new LinkedHashMap<>();
        for (DiagnosisProbeRecord probe : frame.diagnosisProbes()) {
            if (probe != null && StringUtils.hasText(probe.getId())) {
                rawProbeById.put(probe.getId(), probe);
            }
        }

        LinkedHashMap<String, MutablePromptSignal> signalsByKey = new LinkedHashMap<>();
        for (WorkbenchProblemAnalysisView.PromptRowView row : frame.promptRows()) {
            String key = promptSignalKey(row.getPromptId(), row.getPromptText());
            MutablePromptSignal signal = signalsByKey.computeIfAbsent(key, ignored -> new MutablePromptSignal());
            signal.promptKey = key;
            signal.promptId = firstText(signal.promptId, row.getPromptId(), "prompt_" + key);
            signal.promptText = firstText(signal.promptText, row.getPromptText(), "未命名问题");
            signal.topic = firstText(signal.topic, row.getTopicName(), "未分组问题");
            signal.seqNo = firstNonNull(signal.seqNo, row.getSeqNo(), 9999);
            rememberProblemSpaceBucket(signal, row.getProblemSpaceBucket());
            signal.visibilityScore = firstNonNull(row.getVisibilityScore(), signal.visibilityScore, 0.0d);
            signal.averagePosition = firstNonNull(row.getAveragePosition(), signal.averagePosition);
            signal.citationShare = firstNonNull(row.getCitationShare(), signal.citationShare, 0.0d);
            signal.evidenceCount = Math.max(signal.evidenceCount, safeInt(row.getEvidenceCount()));
            signal.hasRawAnswer = signal.hasRawAnswer || Boolean.TRUE.equals(row.getHasRawAnswer());
        }

        Map<String, String> promptKeyByProbeId = new LinkedHashMap<>();
        for (WorkbenchProblemAnalysisView.ProbeRowView probeRow : frame.probeRows()) {
            String key = promptSignalKey(probeRow.getPromptId(), probeRow.getPromptText());
            MutablePromptSignal signal = signalsByKey.computeIfAbsent(key, ignored -> new MutablePromptSignal());
            signal.promptKey = key;
            signal.promptId = firstText(signal.promptId, probeRow.getPromptId(), "prompt_" + key);
            signal.promptText = firstText(signal.promptText, probeRow.getPromptText(), probeRow.getProbeRef(), "未命名问题");
            signal.topic = firstText(signal.topic, probeRow.getTopicName(), "未分组问题");
            signal.seqNo = firstNonNull(signal.seqNo, probeRow.getSeqNo(), 9999);
            rememberProblemSpaceBucket(signal, probeRow.getProblemSpaceBucket());
            signal.visibilityScore = firstNonNull(signal.visibilityScore, probeRow.getVisibilityScore(), 0.0d);
            signal.averagePosition = firstNonNull(signal.averagePosition, probeRow.getAveragePosition());
            signal.evidenceCount = Math.max(signal.evidenceCount, safeInt(probeRow.getEvidenceCount()));
            signal.mentionCount = Math.max(signal.mentionCount, safeInt(probeRow.getMentionCount()));
            signal.hasRawAnswer = signal.hasRawAnswer || Boolean.TRUE.equals(probeRow.getHasRawAnswer());

            DiagnosisProbeRecord rawProbe = rawProbeById.get(normalizeText(probeRow.getProbeId()));
            String intentKey = resolveRichIntentKey(rawProbe, signal.promptText);
            signal.intentCounts.merge(intentKey, 1, Integer::sum);
            signal.subIntentCounts.merge(resolveRichSubIntentLabel(signal.promptText, intentKey), 1, Integer::sum);
            signal.audienceCounts.merge(resolveAudienceSegment(rawProbe, signal.promptText, intentKey), 1, Integer::sum);
            signal.budgetCounts.merge(resolveBudgetSegment(rawProbe, signal.promptText, intentKey), 1, Integer::sum);
            if (StringUtils.hasText(probeRow.getProbeId())) {
                promptKeyByProbeId.put(probeRow.getProbeId(), key);
            }
        }

        for (ProbeBrandMentionRecord mention : frame.brandMentions()) {
            String key = promptKeyByProbeId.get(normalizeText(mention.getProbeId()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            MutablePromptSignal signal = signalsByKey.get(key);
            if (signal == null) {
                continue;
            }
            String brandName = normalizeText(mention.getBrandName());
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            signal.mentionCount += 1;
            if (intEquals(mention.getBrandRoleCode(), 0) || Objects.equals(normalizeBrandKey(brandName), targetBrandKey)) {
                signal.targetBrandCited = true;
                signal.targetMentionCount += 1;
            } else {
                signal.competitorMentions.merge(brandName, 1, Integer::sum);
            }
        }

        LinkedHashMap<String, MutablePlatformSignal> platformsByName = new LinkedHashMap<>();
        for (ProbeDispatchAttempt attempt : frame.dispatchAttempts()) {
            String key = promptKeyByProbeId.get(normalizeText(attempt.getProbeId()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            MutablePromptSignal signal = signalsByKey.get(key);
            if (signal == null) {
                continue;
            }
            signal.attemptCount += 1;
            String platform = resolvePlatformLabel(attempt.getModelId(), attempt.getProvider());
            MutablePlatformSignal platformSignal = platformsByName.computeIfAbsent(platform, ignored -> new MutablePlatformSignal());
            platformSignal.platform = platform;
            platformSignal.attemptCount += 1;
            List<String> queries = resolveAttemptQueries(attempt, rawProbeById.get(normalizeText(attempt.getProbeId())), signal.promptText);
            for (String query : queries) {
                String normalizedQuery = normalizeText(query);
                if (!StringUtils.hasText(normalizedQuery)) {
                    continue;
                }
                signal.queryCount += 1;
                signal.queryCounts.merge(normalizedQuery, 1, Integer::sum);
                signal.platformQueryCounts.merge(platform, 1, Integer::sum);
                platformSignal.queryCount += 1;
            }
        }

        for (ProbeEvidenceRecord evidence : frame.evidenceRecords()) {
            String key = promptKeyByProbeId.get(normalizeText(evidence.getProbeId()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            MutablePromptSignal signal = signalsByKey.get(key);
            if (signal == null) {
                continue;
            }
            String domain = firstText(evidence.getDomain(), domainOf(evidence.getUrl()));
            if (StringUtils.hasText(domain) && ownedDomains.contains(domain)) {
                signal.targetBrandCited = true;
            }
            if (signal.queryCount > 0) {
                continue;
            }
            String query = normalizeText(evidence.getSearchQuery());
            if (!StringUtils.hasText(query)) {
                continue;
            }
            signal.queryCount += 1;
            signal.queryCounts.merge(query, 1, Integer::sum);
            String platform = resolvePlatformLabel(evidence.getProvider(), "搜索");
            signal.platformQueryCounts.merge(platform, 1, Integer::sum);
            MutablePlatformSignal platformSignal = platformsByName.computeIfAbsent(platform, ignored -> new MutablePlatformSignal());
            platformSignal.platform = platform;
            platformSignal.queryCount += 1;
        }

        List<PromptSignalView> prompts = new ArrayList<>();
        LinkedHashMap<String, PromptSignalView> promptByKey = new LinkedHashMap<>();
        int totalDemandIndex = 0;
        int totalQueryCount = 0;
        for (MutablePromptSignal signal : signalsByKey.values()) {
            signal.problemSpaceBucket = resolveDominantKey(signal.problemSpaceBucketCounts, signal.problemSpaceBucket);
            signal.intentKey = resolveDominantKey(signal.intentCounts, "discovery");
            signal.intentLabel = resolveRichIntentLabel(signal.intentKey);
            signal.subIntentLabel = resolveDominantKey(signal.subIntentCounts, resolveRichSubIntentLabel(signal.promptText, signal.intentKey));
            signal.audienceSegment = resolveDominantKey(signal.audienceCounts, "综合决策");
            signal.budgetSegment = resolveDominantKey(signal.budgetCounts, "平衡取舍");
            signal.competitorLeadBrand = signal.competitorMentions.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            signal.citedBrandNames = signal.competitorMentions.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .limit(3)
                    .toList();
            signal.coverageScore = computeCoverageScore(signal);
            signal.competitionScore = computeCompetitionScore(signal);
            signal.relevanceScore = computeRelevanceScore(signal);
            signal.demandIndex = computeDemandIndex(signal);
            signal.opportunityScore = computeOpportunityScore(signal);
            signal.loopStatus = resolvePromptLoopStatus(signal);
            signal.loopAction = resolvePromptLoopAction(signal.loopStatus);
            signal.gapType = resolvePromptGapType(signal);

            PromptSignalView promptSignal = new PromptSignalView(
                    signal.promptKey,
                    firstText(signal.promptId, "prompt_" + signal.promptKey),
                    firstText(signal.topic, "未分组问题"),
                    firstNonNull(signal.seqNo, 9999),
                    firstText(signal.promptText, "未命名问题"),
                    signal.problemSpaceBucket,
                    signal.intentKey,
                    signal.intentLabel,
                    signal.subIntentLabel,
                    signal.audienceSegment,
                    signal.budgetSegment,
                    signal.demandIndex,
                    signal.attemptCount,
                    signal.queryCount,
                    signal.evidenceCount,
                    signal.mentionCount,
                    firstNonNull(signal.visibilityScore, 0.0d),
                    signal.averagePosition,
                    firstNonNull(signal.citationShare, 0.0d),
                    signal.coverageScore,
                    signal.targetBrandCited,
                    signal.competitorLeadBrand,
                    List.copyOf(signal.citedBrandNames),
                    signal.competitionScore,
                    signal.relevanceScore,
                    signal.opportunityScore,
                    signal.loopStatus,
                    signal.loopAction,
                    signal.gapType,
                    Map.copyOf(signal.queryCounts),
                    Map.copyOf(signal.platformQueryCounts)
            );
            prompts.add(promptSignal);
            promptByKey.put(promptSignal.promptKey(), promptSignal);
            totalDemandIndex += promptSignal.demandIndex();
            totalQueryCount += promptSignal.queryCount();
        }

        List<PlatformSignalView> platforms = platformsByName.values().stream()
                .map(item -> new PlatformSignalView(item.platform, item.queryCount, item.attemptCount))
                .sorted(Comparator.comparing(PlatformSignalView::queryCount, Comparator.reverseOrder()))
                .toList();
        List<PromptSignalView> sortedPrompts = prompts.stream()
                .sorted(Comparator.comparing(PromptSignalView::seqNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .toList();
        return new ProblemRichSnapshot(sortedPrompts, promptByKey, platforms, totalDemandIndex, totalQueryCount);
    }

    private ProblemAnalysisFrame applyProblemAnalysisScope(ProblemAnalysisFrame frame,
                                                           ProblemAnalysisScope scope) {
        if (frame == null) {
            return ProblemAnalysisFrame.empty();
        }
        if (!scope.active()) {
            return frame;
        }

        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows = frame.promptRows().stream()
                .filter(row -> matchesProblemAnalysisScope(row.getTopicName(), row.getProblemSpaceBucket(), scope))
                .toList();
        Set<String> promptIds = promptRows.stream()
                .map(WorkbenchProblemAnalysisView.PromptRowView::getPromptId)
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Set<String> promptTexts = promptRows.stream()
                .map(WorkbenchProblemAnalysisView.PromptRowView::getPromptText)
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows = frame.probeRows().stream()
                .filter(row -> promptIds.contains(normalizeText(row.getPromptId()))
                        || promptTexts.contains(normalizeText(row.getPromptText()))
                        || matchesProblemAnalysisScope(row.getTopicName(), row.getProblemSpaceBucket(), scope))
                .toList();
        Set<String> probeIds = probeRows.stream()
                .map(WorkbenchProblemAnalysisView.ProbeRowView::getProbeId)
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows = frame.evidenceRows().stream()
                .filter(row -> probeIds.contains(normalizeText(row.getProbeId()))
                        || promptIds.contains(normalizeText(row.getPromptId()))
                        || promptTexts.contains(normalizeText(row.getPromptText()))
                        || matchesProblemAnalysisScope(row.getTopicName(), row.getProblemSpaceBucket(), scope))
                .toList();

        List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows = frame.attemptRows().stream()
                .filter(row -> probeIds.contains(normalizeText(row.getProbeId()))
                        || promptIds.contains(normalizeText(row.getPromptId()))
                        || promptTexts.contains(normalizeText(row.getPromptText()))
                        || matchesProblemAnalysisScope(row.getTopicName(), row.getProblemSpaceBucket(), scope))
                .toList();

        Set<String> topicIds = new LinkedHashSet<>();
        Set<String> topicNames = new LinkedHashSet<>();
        promptRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        probeRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        evidenceRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        attemptRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));

        List<WorkbenchProblemAnalysisView.TopicRowView> topicRows = frame.topicRows().stream()
                .filter(row -> topicIds.contains(normalizeText(row.getTopicId()))
                        || topicNames.contains(normalizeText(row.getTopicName()))
                        || matchesProblemAnalysisScope(row.getTopicName(), row.getProblemSpaceBucket(), scope))
                .toList();

        List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters = frame.problemClusters().stream()
                .filter(cluster -> matchesProblemClusterScope(cluster, scope, promptTexts))
                .toList();

        List<DiagnosisProbeRecord> diagnosisProbes = frame.diagnosisProbes().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getId())))
                .toList();
        List<ProbeDispatchAttempt> dispatchAttempts = frame.dispatchAttempts().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getProbeId())))
                .toList();
        List<ProbeBrandMentionRecord> brandMentions = frame.brandMentions().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getProbeId())))
                .toList();
        List<ProbeEvidenceRecord> evidenceRecords = frame.evidenceRecords().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getProbeId())))
                .toList();

        return new ProblemAnalysisFrame(
                topicRows,
                promptRows,
                problemClusters,
                probeRows,
                evidenceRows,
                attemptRows,
                diagnosisProbes,
                dispatchAttempts,
                brandMentions,
                evidenceRecords
        );
    }

    private ProblemAnalysisFrame applyProblemAnalysisAnalysisContext(ProblemAnalysisFrame frame,
                                                                    WorkbenchAnalysisContextView analysisContext) {
        if (frame == null) {
            return ProblemAnalysisFrame.empty();
        }
        if (analysisContext == null) {
            return frame;
        }
        Set<String> selectedPlatforms = normalizedSelectionSet(analysisContext.getPlatforms());
        LocalDate windowStart = parseDate(analysisContext.getDateFrom());
        LocalDate windowEnd = parseDate(analysisContext.getDateTo());

        ProblemAnalysisFrame platformScopedFrame = selectedPlatforms.isEmpty()
                ? frame
                : applyProblemAnalysisPlatformScope(frame, selectedPlatforms);
        if (windowStart == null || windowEnd == null) {
            return platformScopedFrame;
        }
        return applyProblemAnalysisTimeScope(platformScopedFrame, windowStart, windowEnd);
    }

    private ProblemAnalysisFrame applyProblemAnalysisPlatformScope(ProblemAnalysisFrame frame,
                                                                  Set<String> selectedPlatforms) {
        if (frame == null || selectedPlatforms == null || selectedPlatforms.isEmpty()) {
            return frame == null ? ProblemAnalysisFrame.empty() : frame;
        }

        List<WorkbenchProblemAnalysisView.AttemptRowView> platformScopedAttemptRows = frame.attemptRows().stream()
                .filter(row -> matchesProblemAnalysisPlatform(row.getModel(), row.getProvider(), selectedPlatforms))
                .toList();
        Set<String> platformScopedAttemptProbeIds = collectNormalizedValues(
                platformScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getProbeId).toList()
        );

        List<WorkbenchProblemAnalysisView.ProbeRowView> platformScopedProbeRows = frame.probeRows().stream()
                .filter(row -> matchesProblemAnalysisPlatform(row.getModel(), null, selectedPlatforms)
                        || platformScopedAttemptProbeIds.contains(normalizeText(row.getProbeId())))
                .toList();

        Set<String> platformScopedPromptIds = collectNormalizedValues(
                concatValues(
                        platformScopedProbeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getPromptId).toList(),
                        platformScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptId).toList()
                )
        );
        Set<String> platformScopedPromptTexts = collectNormalizedValues(
                concatValues(
                        platformScopedProbeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getPromptText).toList(),
                        platformScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptText).toList()
                )
        );

        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows = frame.promptRows().stream()
                .filter(row -> platformScopedPromptIds.contains(normalizeText(row.getPromptId()))
                        || platformScopedPromptTexts.contains(normalizeText(row.getPromptText())))
                .toList();

        Set<String> scopedPromptIds = collectNormalizedValues(
                promptRows.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getPromptId).toList()
        );
        Set<String> scopedPromptTexts = collectNormalizedValues(
                promptRows.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getPromptText).toList()
        );

        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows = frame.probeRows().stream()
                .filter(row -> matchesProblemAnalysisPlatform(row.getModel(), null, selectedPlatforms)
                        || platformScopedAttemptProbeIds.contains(normalizeText(row.getProbeId()))
                        || scopedPromptIds.contains(normalizeText(row.getPromptId()))
                        || scopedPromptTexts.contains(normalizeText(row.getPromptText())))
                .toList();

        Set<String> probeIds = collectNormalizedValues(
                probeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getProbeId).toList()
        );

        List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows = platformScopedAttemptRows.stream()
                .filter(row -> probeIds.contains(normalizeText(row.getProbeId()))
                        || scopedPromptIds.contains(normalizeText(row.getPromptId()))
                        || scopedPromptTexts.contains(normalizeText(row.getPromptText())))
                .toList();
        Set<String> attemptIds = collectNormalizedValues(
                attemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getAttemptId).toList()
        );

        List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows = frame.evidenceRows().stream()
                .filter(row -> attemptIds.contains(normalizeText(row.getAttemptId()))
                        || probeIds.contains(normalizeText(row.getProbeId()))
                        || scopedPromptIds.contains(normalizeText(row.getPromptId()))
                        || scopedPromptTexts.contains(normalizeText(row.getPromptText())))
                .toList();

        return rebuildProblemAnalysisFrame(frame, promptRows, probeRows, evidenceRows, attemptRows);
    }

    private ProblemAnalysisFrame applyProblemAnalysisTimeScope(ProblemAnalysisFrame frame,
                                                              LocalDate windowStart,
                                                              LocalDate windowEnd) {
        if (frame == null || windowStart == null || windowEnd == null) {
            return frame == null ? ProblemAnalysisFrame.empty() : frame;
        }

        List<WorkbenchProblemAnalysisView.AttemptRowView> timeScopedAttemptRows = frame.attemptRows().stream()
                .filter(row -> matchesProblemAnalysisTimeWindow(row.getCreatedAt(), windowStart, windowEnd))
                .toList();
        Set<String> timeScopedAttemptIds = collectNormalizedValues(
                timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getAttemptId).toList()
        );
        List<WorkbenchProblemAnalysisView.EvidenceRowView> timeScopedEvidenceRows = frame.evidenceRows().stream()
                .filter(row -> matchesProblemAnalysisTimeWindow(row.getCapturedAt(), windowStart, windowEnd)
                        || timeScopedAttemptIds.contains(normalizeText(row.getAttemptId())))
                .toList();
        if (timeScopedAttemptRows.isEmpty() && timeScopedEvidenceRows.isEmpty()) {
            return frame;
        }

        Set<String> timeScopedProbeIds = collectNormalizedValues(
                concatValues(
                        timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getProbeId).toList(),
                        timeScopedEvidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getProbeId).toList()
                )
        );
        Set<String> timeScopedPromptIds = collectNormalizedValues(
                concatValues(
                        timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptId).toList(),
                        timeScopedEvidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getPromptId).toList()
                )
        );
        Set<String> timeScopedPromptTexts = collectNormalizedValues(
                concatValues(
                        timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptText).toList(),
                        timeScopedEvidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getPromptText).toList()
                )
        );

        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows = frame.probeRows().stream()
                .filter(row -> timeScopedProbeIds.contains(normalizeText(row.getProbeId()))
                        || timeScopedPromptIds.contains(normalizeText(row.getPromptId()))
                        || timeScopedPromptTexts.contains(normalizeText(row.getPromptText())))
                .toList();
        Set<String> promptIds = collectNormalizedValues(
                concatValues(
                        probeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getPromptId).toList(),
                        concatValues(
                                timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptId).toList(),
                                timeScopedEvidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getPromptId).toList()
                        )
                )
        );
        Set<String> promptTexts = collectNormalizedValues(
                concatValues(
                        probeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getPromptText).toList(),
                        concatValues(
                                timeScopedAttemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getPromptText).toList(),
                                timeScopedEvidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getPromptText).toList()
                        )
                )
        );
        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows = frame.promptRows().stream()
                .filter(row -> promptIds.contains(normalizeText(row.getPromptId()))
                        || promptTexts.contains(normalizeText(row.getPromptText())))
                .toList();

        return rebuildProblemAnalysisFrame(frame, promptRows, probeRows, timeScopedEvidenceRows, timeScopedAttemptRows);
    }

    private ProblemAnalysisFrame rebuildProblemAnalysisFrame(ProblemAnalysisFrame baseFrame,
                                                            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                                            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
                                                            List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows,
                                                            List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows) {
        Set<String> promptIds = collectNormalizedValues(
                promptRows.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getPromptId).toList()
        );
        Set<String> promptTexts = collectNormalizedValues(
                promptRows.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getPromptText).toList()
        );
        Set<String> probeIds = collectNormalizedValues(
                probeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getProbeId).toList()
        );
        Set<String> attemptIds = collectNormalizedValues(
                attemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getAttemptId).toList()
        );
        Set<String> topicIds = new LinkedHashSet<>();
        Set<String> topicNames = new LinkedHashSet<>();
        promptRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        probeRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        evidenceRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        attemptRows.forEach(row -> collectScopedTopic(row.getTopicId(), row.getTopicName(), topicIds, topicNames));
        Set<String> bucketKeys = collectNormalizedValues(
                concatValues(
                        promptRows.stream().map(WorkbenchProblemAnalysisView.PromptRowView::getProblemSpaceBucket).toList(),
                        concatValues(
                                probeRows.stream().map(WorkbenchProblemAnalysisView.ProbeRowView::getProblemSpaceBucket).toList(),
                                concatValues(
                                        evidenceRows.stream().map(WorkbenchProblemAnalysisView.EvidenceRowView::getProblemSpaceBucket).toList(),
                                        attemptRows.stream().map(WorkbenchProblemAnalysisView.AttemptRowView::getProblemSpaceBucket).toList()
                                )
                        )
                )
        );

        List<WorkbenchProblemAnalysisView.TopicRowView> topicRows = baseFrame.topicRows().stream()
                .filter(row -> topicIds.contains(normalizeText(row.getTopicId()))
                        || topicNames.contains(normalizeText(row.getTopicName()))
                        || bucketKeys.contains(normalizeText(row.getProblemSpaceBucket())))
                .toList();

        List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters = baseFrame.problemClusters().stream()
                .filter(cluster -> bucketKeys.contains(normalizeText(cluster.getProblemSpaceBucket()))
                        || cluster.getRepresentativeQuestions() != null
                        && cluster.getRepresentativeQuestions().stream()
                        .map(this::normalizeText)
                        .anyMatch(promptTexts::contains))
                .toList();

        List<DiagnosisProbeRecord> diagnosisProbes = baseFrame.diagnosisProbes().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getId())))
                .toList();
        List<ProbeDispatchAttempt> dispatchAttempts = baseFrame.dispatchAttempts().stream()
                .filter(item -> attemptIds.contains(normalizeText(item.getId()))
                        || probeIds.contains(normalizeText(item.getProbeId())))
                .toList();
        List<ProbeBrandMentionRecord> brandMentions = baseFrame.brandMentions().stream()
                .filter(item -> probeIds.contains(normalizeText(item.getProbeId())))
                .toList();
        List<ProbeEvidenceRecord> evidenceRecords = baseFrame.evidenceRecords().stream()
                .filter(item -> attemptIds.contains(normalizeText(item.getDispatchAttemptId()))
                        || probeIds.contains(normalizeText(item.getProbeId())))
                .toList();

        return new ProblemAnalysisFrame(
                topicRows,
                promptRows,
                problemClusters,
                probeRows,
                evidenceRows,
                attemptRows,
                diagnosisProbes,
                dispatchAttempts,
                brandMentions,
                evidenceRecords
        );
    }

    private boolean matchesProblemAnalysisPlatform(String model,
                                                   String provider,
                                                   Set<String> selectedPlatforms) {
        if (selectedPlatforms == null || selectedPlatforms.isEmpty()) {
            return true;
        }
        return matchesPlatformSelection(model, provider, selectedPlatforms);
    }

    private boolean matchesPlatformSelection(String model,
                                             String provider,
                                             Set<String> selectedPlatforms) {
        if (selectedPlatforms == null || selectedPlatforms.isEmpty()) {
            return true;
        }
        Set<String> aliases = resolvePlatformAliases(model, provider);
        if (aliases.isEmpty()) {
            return false;
        }
        return aliases.stream().anyMatch(selectedPlatforms::contains);
    }

    private Set<String> resolvePlatformAliases(String model, String provider) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        Stream.of(model, provider)
                .map(this::normalizeText)
                .filter(StringUtils::hasText)
                .map(this::normalizeSelectionValue)
                .forEach(aliases::add);
        String platformLabel = resolvePlatformLabel(model, provider);
        if (StringUtils.hasText(platformLabel)) {
            aliases.add(normalizeSelectionValue(platformLabel));
        }
        return aliases;
    }

    private boolean matchesProblemAnalysisTimeWindow(String rawDateTime,
                                                     LocalDate windowStart,
                                                     LocalDate windowEnd) {
        if (windowStart == null || windowEnd == null) {
            return true;
        }
        LocalDateTime value = parseDateTime(rawDateTime);
        if (value == null) {
            return false;
        }
        LocalDate observedDate = value.toLocalDate();
        return !observedDate.isBefore(windowStart) && !observedDate.isAfter(windowEnd);
    }

    private LocalDateTime parseDateTime(String rawDateTime) {
        if (!StringUtils.hasText(rawDateTime)) {
            return null;
        }
        try {
            return LocalDateTime.parse(rawDateTime.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Set<String> collectNormalizedValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null || values.isEmpty()) {
            return normalized;
        }
        for (String value : values) {
            String normalizedValue = normalizeText(value);
            if (StringUtils.hasText(normalizedValue)) {
                normalized.add(normalizedValue);
            }
        }
        return normalized;
    }

    private List<String> concatValues(List<String> left, List<String> right) {
        List<String> combined = new ArrayList<>();
        if (left != null && !left.isEmpty()) {
            combined.addAll(left);
        }
        if (right != null && !right.isEmpty()) {
            combined.addAll(right);
        }
        return combined;
    }

    private void collectScopedTopic(String topicId,
                                    String topicName,
                                    Set<String> topicIds,
                                    Set<String> topicNames) {
        String normalizedTopicId = normalizeText(topicId);
        if (StringUtils.hasText(normalizedTopicId)) {
            topicIds.add(normalizedTopicId);
        }
        String normalizedTopicName = normalizeText(topicName);
        if (StringUtils.hasText(normalizedTopicName)) {
            topicNames.add(normalizedTopicName);
        }
    }

    private boolean matchesProblemClusterScope(WorkbenchProblemAnalysisView.ProblemClusterView cluster,
                                               ProblemAnalysisScope scope,
                                               Set<String> promptTexts) {
        if (cluster == null) {
            return false;
        }
        if (!scope.active()) {
            return true;
        }
        if (!scope.matchesBucket(cluster.getProblemSpaceBucket())) {
            return false;
        }
        if (scope.topicNames().isEmpty()) {
            return true;
        }
        if (cluster.getRepresentativeQuestions() != null) {
            for (String question : cluster.getRepresentativeQuestions()) {
                if (promptTexts.contains(normalizeText(question))) {
                    return true;
                }
            }
        }
        return scope.matchesTopic(cluster.getTitle());
    }

    private boolean matchesProblemAnalysisScope(String topicName,
                                                String problemSpaceBucket,
                                                ProblemAnalysisScope scope) {
        if (!scope.active()) {
            return true;
        }
        return scope.matchesTopic(topicName)
                && scope.matchesBucket(problemSpaceBucket);
    }

    private List<String> resolveAttemptQueries(ProbeDispatchAttempt attempt,
                                               DiagnosisProbeRecord rawProbe,
                                               String promptText) {
        if (attempt != null && attempt.getSearchQueriesJson() != null && !attempt.getSearchQueriesJson().isEmpty()) {
            return attempt.getSearchQueriesJson();
        }
        String fallback = firstText(
                rawProbe == null ? null : rawProbe.getRenderText(),
                promptText
        );
        return StringUtils.hasText(fallback) ? List.of(fallback) : List.of();
    }

    private WorkbenchProblemAnalysisView.VolumeView buildProblemAnalysisVolume(BrandSpaceContext context,
                                                                              List<DiagnosisReportRecord> recentReports,
                                                                              ProblemRichSnapshot currentSnapshot,
                                                                              ProblemRichSnapshot previousSnapshot,
                                                                              ProblemAnalysisScope scope) {
        List<WorkbenchProblemAnalysisView.PlatformDemandRowView> platformRows = buildVolumePlatformRows(currentSnapshot, previousSnapshot);

        List<WorkbenchProblemAnalysisView.PromptOpportunityRowView> promptOpportunityRows = currentSnapshot.prompts().stream()
                .sorted(Comparator.comparing(PromptSignalView::opportunityScore, Comparator.reverseOrder())
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .limit(16)
                .map(item -> {
                    PromptSignalView previous = previousSnapshot.promptByKey().get(item.promptKey());
                    double trendDelta = previous == null || previous.demandIndex() <= 0
                            ? 0.0d
                            : round2((item.demandIndex() - previous.demandIndex()) * 100.0d / previous.demandIndex());
                    int projection = Math.max(item.demandIndex(), (int) Math.round(item.demandIndex() * (trendDelta >= 0 ? 1.18d : 1.06d)));
                    return WorkbenchProblemAnalysisView.PromptOpportunityRowView.builder()
                            .prompt(item.promptText())
                            .topic(item.topic())
                            .problemSpaceBucket(item.problemSpaceBucket())
                            .demandIndex(item.demandIndex())
                            .projection(projection)
                            .competitionScore(item.competitionScore())
                            .relevanceScore(item.relevanceScore())
                            .opportunityScore(item.opportunityScore())
                            .intentLabel(item.intentLabel())
                            .trendDelta(trendDelta)
                            .build();
                })
                .toList();

        List<WorkbenchProblemAnalysisView.PromptProjectionRowView> promptProjectionRows = promptOpportunityRows.stream()
                .limit(8)
                .map(item -> WorkbenchProblemAnalysisView.PromptProjectionRowView.builder()
                        .prompt(item.getPrompt())
                        .projection(item.getProjection())
                        .share(promptOpportunityRows.isEmpty()
                                ? 0.0d
                                : round2(item.getProjection() * 100.0d / Math.max(1, promptOpportunityRows.stream()
                                .map(WorkbenchProblemAnalysisView.PromptOpportunityRowView::getProjection)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .sum())))
                        .intentLabel(item.getIntentLabel())
                        .action(resolveProjectionAction(item.getOpportunityScore()))
                        .build())
                .toList();

        List<WorkbenchProblemAnalysisView.PromptLoopRowView> promptLoopRows = currentSnapshot.prompts().stream()
                .sorted(Comparator.comparing(PromptSignalView::opportunityScore, Comparator.reverseOrder())
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .limit(16)
                .map(item -> WorkbenchProblemAnalysisView.PromptLoopRowView.builder()
                        .id(item.promptId())
                        .topic(item.topic())
                        .problemSpaceBucket(item.problemSpaceBucket())
                        .prompt(item.promptText())
                        .status(item.loopStatus())
                        .citedTarget(firstText(item.competitorLeadBrand(), item.targetBrandCited() ? "当前品牌" : "待抢占"))
                        .opportunity(item.opportunityScore())
                        .action(item.loopAction())
                        .targetBrandCited(item.targetBrandCited())
                        .citationCoverage(item.citationShare())
                        .competitorLeadBrand(item.competitorLeadBrand())
                        .citedBrandNames(item.citedBrandNames())
                        .gapType(item.gapType())
                        .build())
                .toList();

        double coverage = currentSnapshot.prompts().isEmpty()
                ? 0.0d
                : round2(currentSnapshot.prompts().stream()
                .filter(item -> "已被引用".equals(item.loopStatus()))
                .count() * 100.0d / currentSnapshot.prompts().size());

        return WorkbenchProblemAnalysisView.VolumeView.builder()
                .trendPoints(buildProblemVolumeTrendPoints(context, recentReports, scope))
                .summary(buildVolumeSummary(currentSnapshot, previousSnapshot))
                .platformRows(platformRows)
                .promptOpportunityRows(promptOpportunityRows)
                .promptProjectionSummary(WorkbenchProblemAnalysisView.PromptProjectionSummaryView.builder()
                        .promptCount(promptProjectionRows.size())
                        .totalProjection(promptProjectionRows.stream()
                                .map(WorkbenchProblemAnalysisView.PromptProjectionRowView::getProjection)
                                .filter(Objects::nonNull)
                                .mapToInt(Integer::intValue)
                                .sum())
                        .highValuePromptCount((int) promptOpportunityRows.stream().filter(item -> safeInt(item.getOpportunityScore()) >= 75).count())
                        .build())
                .promptProjectionRows(promptProjectionRows)
                .promptLoopSummary(WorkbenchProblemAnalysisView.PromptLoopSummaryView.builder()
                        .coverage(coverage)
                        .gapCount((int) currentSnapshot.prompts().stream()
                                .filter(item -> "竞品被引（我方缺口）".equals(item.loopStatus()))
                                .count())
                        .highOpportunityCount((int) currentSnapshot.prompts().stream()
                                .filter(item -> item.opportunityScore() >= 75)
                                .count())
                        .build())
                .promptLoopRows(promptLoopRows)
                .build();
    }

    private List<WorkbenchProblemAnalysisView.VolumeTrendPointView> buildProblemVolumeTrendPoints(
            BrandSpaceContext context,
            List<DiagnosisReportRecord> recentReports,
            ProblemAnalysisScope scope) {
        if (recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        List<DiagnosisReportRecord> sorted = recentReports.stream()
                .sorted(Comparator.comparing(DiagnosisReportRecord::getGeneratedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .toList();
        List<ProblemVolumeSlice> slices = new ArrayList<>();
        for (DiagnosisReportRecord report : sorted) {
            ProblemAnalysisFrame frame = buildProblemAnalysisFrame(context, report, toReportResponse(report));
            ProblemRichSnapshot snapshot = buildProblemRichSnapshot(context, applyProblemAnalysisScope(frame, scope));
            slices.add(new ProblemVolumeSlice(report, snapshot));
        }
        List<WorkbenchProblemAnalysisView.VolumeTrendPointView> points = new ArrayList<>();
        for (int index = 0; index < slices.size(); index++) {
            ProblemVolumeSlice current = slices.get(index);
            ProblemVolumeSlice previous = index == 0 ? null : slices.get(index - 1);
            points.add(WorkbenchProblemAnalysisView.VolumeTrendPointView.builder()
                    .sliceKey(current.report() == null ? null : current.report().getId())
                    .label(resolveTrendLabel(current.report() == null ? null : current.report().getGeneratedAt()))
                    .generatedAt(formatDateTime(current.report() == null ? null : current.report().getGeneratedAt()))
                    .currentValue(Math.max(current.snapshot().totalDemandIndex(), 0))
                    .previousValue(previous == null ? null : Math.max(previous.snapshot().totalDemandIndex(), 0))
                    .isLatest(index == slices.size() - 1)
                    .snapshot(buildVolumeTimeSliceSnapshot(current.snapshot(), previous == null ? ProblemRichSnapshot.empty() : previous.snapshot()))
                    .build());
        }
        return points;
    }

    private WorkbenchProblemAnalysisView.VolumeTimeSliceSnapshotView buildVolumeTimeSliceSnapshot(
            ProblemRichSnapshot currentSnapshot,
            ProblemRichSnapshot previousSnapshot) {
        return WorkbenchProblemAnalysisView.VolumeTimeSliceSnapshotView.builder()
                .summary(buildVolumeSummary(currentSnapshot, previousSnapshot))
                .topTopics(buildVolumeTopTopics(currentSnapshot))
                .platformRows(buildVolumePlatformRows(currentSnapshot, previousSnapshot))
                .build();
    }

    private WorkbenchProblemAnalysisView.VolumeSummaryView buildVolumeSummary(ProblemRichSnapshot currentSnapshot,
                                                                             ProblemRichSnapshot previousSnapshot) {
        int demandTotal = currentSnapshot.totalDemandIndex();
        int previousDemandTotal = previousSnapshot.totalDemandIndex();
        double demandDelta = previousDemandTotal <= 0
                ? 0.0d
                : round2((demandTotal - previousDemandTotal) * 100.0d / previousDemandTotal);
        return WorkbenchProblemAnalysisView.VolumeSummaryView.builder()
                .demandTotal(demandTotal)
                .demandDelta(demandDelta)
                .activeTopicCount((int) currentSnapshot.prompts().stream().map(PromptSignalView::topic).distinct().count())
                .activePlatformCount(currentSnapshot.platforms().size())
                .build();
    }

    private List<WorkbenchProblemAnalysisView.PlatformDemandRowView> buildVolumePlatformRows(
            ProblemRichSnapshot currentSnapshot,
            ProblemRichSnapshot previousSnapshot) {
        List<WorkbenchProblemAnalysisView.PlatformDemandRowView> platformRows = new ArrayList<>();
        int totalQueries = Math.max(1, currentSnapshot.totalQueryCount());
        Map<String, PlatformSignalView> previousPlatformsByName = previousSnapshot.platformsByName();
        for (PlatformSignalView platform : currentSnapshot.platforms()) {
            PlatformSignalView previous = previousPlatformsByName.get(platform.platform());
            double share = round2(platform.queryCount() * 100.0d / totalQueries);
            Double delta = previous == null
                    ? 0.0d
                    : round2(platform.queryCount() - previous.queryCount());
            platformRows.add(WorkbenchProblemAnalysisView.PlatformDemandRowView.builder()
                    .platform(platform.platform())
                    .value(platform.queryCount())
                    .share(share)
                    .delta(delta)
                    .build());
        }
        return platformRows;
    }

    private List<WorkbenchProblemAnalysisView.VolumeTopicSliceRowView> buildVolumeTopTopics(ProblemRichSnapshot snapshot) {
        if (snapshot.prompts().isEmpty() || snapshot.totalDemandIndex() <= 0) {
            return List.of();
        }
        LinkedHashMap<String, MutableVolumeTopicSlice> slicesByTopic = new LinkedHashMap<>();
        for (PromptSignalView prompt : snapshot.prompts()) {
            String topicName = firstText(prompt.topic(), "未分组问题");
            MutableVolumeTopicSlice slice = slicesByTopic.computeIfAbsent(topicName, ignored -> new MutableVolumeTopicSlice());
            slice.topicName = topicName;
            slice.problemSpaceBucket = firstText(slice.problemSpaceBucket, prompt.problemSpaceBucket());
            slice.demandCount += Math.max(prompt.demandIndex(), 0);
            if (slice.leadPromptText == null || prompt.demandIndex() > slice.leadPromptDemand) {
                slice.leadPromptText = prompt.promptText();
                slice.leadPromptDemand = prompt.demandIndex();
            }
        }
        return slicesByTopic.values().stream()
                .map(item -> WorkbenchProblemAnalysisView.VolumeTopicSliceRowView.builder()
                        .topicId(normalizeText(item.topicName))
                        .topicName(item.topicName)
                        .problemSpaceBucket(item.problemSpaceBucket)
                        .demandCount(item.demandCount)
                        .share(round2(item.demandCount * 100.0d / snapshot.totalDemandIndex()))
                        .leadPromptText(item.leadPromptText)
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.VolumeTopicSliceRowView::getDemandCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WorkbenchProblemAnalysisView.VolumeTopicSliceRowView::getTopicName, Comparator.nullsLast(String::compareTo)))
                .limit(4)
                .toList();
    }

    private WorkbenchProblemAnalysisView.IntentView buildProblemAnalysisIntent(ProblemRichSnapshot currentSnapshot,
                                                                              ProblemRichSnapshot previousSnapshot) {
        Map<String, Integer> currentIntentCounts = new LinkedHashMap<>();
        Map<String, List<String>> intentExamples = new LinkedHashMap<>();
        Map<String, Integer> currentSubIntentCounts = new LinkedHashMap<>();
        Map<String, String> subIntentParent = new LinkedHashMap<>();
        for (PromptSignalView item : currentSnapshot.prompts()) {
            currentIntentCounts.merge(item.intentKey(), 1, Integer::sum);
            intentExamples.computeIfAbsent(item.intentKey(), ignored -> new ArrayList<>());
            List<String> examples = intentExamples.get(item.intentKey());
            if (examples.size() < 3) {
                examples.add(item.promptText());
            }
            String subKey = item.intentKey() + "::" + item.subIntentLabel();
            currentSubIntentCounts.merge(subKey, 1, Integer::sum);
            subIntentParent.put(subKey, item.intentKey());
        }
        Map<String, Integer> previousIntentCounts = new LinkedHashMap<>();
        Map<String, Integer> previousSubIntentCounts = new LinkedHashMap<>();
        for (PromptSignalView item : previousSnapshot.prompts()) {
            previousIntentCounts.merge(item.intentKey(), 1, Integer::sum);
            previousSubIntentCounts.merge(item.intentKey() + "::" + item.subIntentLabel(), 1, Integer::sum);
        }
        int total = Math.max(1, currentSnapshot.prompts().size());
        List<WorkbenchProblemAnalysisView.IntentStatView> intents = currentIntentCounts.entrySet().stream()
                .map(entry -> WorkbenchProblemAnalysisView.IntentStatView.builder()
                        .key(entry.getKey())
                        .label(resolveRichIntentLabel(entry.getKey()))
                        .count(entry.getValue())
                        .share(round2(entry.getValue() * 100.0d / total))
                        .examples(intentExamples.getOrDefault(entry.getKey(), List.of()))
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.IntentStatView::getCount, Comparator.reverseOrder()))
                .toList();

        List<WorkbenchProblemAnalysisView.IntentSubIntentView> subIntents = currentSubIntentCounts.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("::", 2);
                    String parent = parts[0];
                    String label = parts.length > 1 ? parts[1] : "综合子意图";
                    int previous = previousSubIntentCounts.getOrDefault(entry.getKey(), 0);
                    double change = previous <= 0
                            ? (entry.getValue() > 0 ? 100.0d : 0.0d)
                            : round2((entry.getValue() - previous) * 100.0d / previous);
                    return WorkbenchProblemAnalysisView.IntentSubIntentView.builder()
                            .key(entry.getKey())
                            .parent(parent)
                            .parentLabel(resolveRichIntentLabel(parent))
                            .label(label)
                            .count(entry.getValue())
                            .share(round2(entry.getValue() * 100.0d / total))
                            .change(change)
                            .build();
                })
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.IntentSubIntentView::getShare, Comparator.reverseOrder()))
                .toList();
        return WorkbenchProblemAnalysisView.IntentView.builder()
                .intents(intents)
                .subIntents(subIntents)
                .build();
    }

    private WorkbenchProblemAnalysisView.AudienceView buildProblemAnalysisAudience(ProblemRichSnapshot snapshot) {
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        Map<String, Integer> budgetCounts = new LinkedHashMap<>();
        for (PromptSignalView item : snapshot.prompts()) {
            roleCounts.merge(item.audienceSegment(), 1, Integer::sum);
            budgetCounts.merge(item.budgetSegment(), 1, Integer::sum);
        }
        int total = Math.max(1, snapshot.prompts().size());
        List<WorkbenchProblemAnalysisView.AudienceDistributionRowView> roleRows = roleCounts.entrySet().stream()
                .map(entry -> WorkbenchProblemAnalysisView.AudienceDistributionRowView.builder()
                        .label(entry.getKey())
                        .share(round2(entry.getValue() * 100.0d / total))
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.AudienceDistributionRowView::getShare, Comparator.reverseOrder()))
                .toList();
        List<WorkbenchProblemAnalysisView.AudienceDistributionRowView> budgetRows = budgetCounts.entrySet().stream()
                .map(entry -> WorkbenchProblemAnalysisView.AudienceDistributionRowView.builder()
                        .label(entry.getKey())
                        .share(round2(entry.getValue() * 100.0d / total))
                        .build())
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.AudienceDistributionRowView::getShare, Comparator.reverseOrder()))
                .toList();
        List<WorkbenchProblemAnalysisView.AudiencePlatformRowView> platformRows = snapshot.platforms().stream()
                .map(item -> {
                    double share = snapshot.totalQueryCount() <= 0 ? 0.0d : item.queryCount() * 100.0d / snapshot.totalQueryCount();
                    double confidence = round2(Math.min(95.0d, 48.0d + item.attemptCount() * 6.5d + share * 0.4d));
                    double score = round2(Math.min(100.0d, 42.0d + share * 0.7d + item.avgQueriesPerExecution() * 8.0d));
                    return WorkbenchProblemAnalysisView.AudiencePlatformRowView.builder()
                            .platform(item.platform())
                            .score(score)
                            .confidence(confidence)
                            .build();
                })
                .toList();
        return WorkbenchProblemAnalysisView.AudienceView.builder()
                .roleRows(roleRows)
                .budgetRows(budgetRows)
                .platformRows(platformRows)
                .build();
    }

    private WorkbenchProblemAnalysisView.HierarchyView buildProblemAnalysisHierarchy(
            ProblemRichSnapshot snapshot,
            List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters,
            boolean scopeActive) {
        Map<String, List<PromptSignalView>> promptsByTopic = new LinkedHashMap<>();
        for (PromptSignalView item : snapshot.prompts()) {
            promptsByTopic.computeIfAbsent(item.topic(), ignored -> new ArrayList<>()).add(item);
        }
        if (promptsByTopic.isEmpty() && !scopeActive && problemClusters != null && !problemClusters.isEmpty()) {
            return WorkbenchProblemAnalysisView.HierarchyView.builder()
                    .topics(problemClusters.stream()
                            .map(cluster -> WorkbenchProblemAnalysisView.HierarchyTopicView.builder()
                                    .topic(cluster.getTitle())
                                    .problemSpaceBucket(null)
                                    .demandIndex(firstNonNull(cluster.getSignalCount(), 0))
                                    .clusters(List.of(
                                            WorkbenchProblemAnalysisView.HierarchyClusterView.builder()
                                                    .label("代表问题")
                                                    .count(cluster.getRepresentativeQuestions() == null ? 0 : cluster.getRepresentativeQuestions().size())
                                                    .samples(cluster.getRepresentativeQuestions() == null ? List.of() : cluster.getRepresentativeQuestions())
                                                    .build()
                                    ))
                                    .build())
                            .toList())
                    .build();
        }
        if (promptsByTopic.isEmpty()) {
            return WorkbenchProblemAnalysisView.HierarchyView.builder()
                    .topics(List.of())
                    .build();
        }
        List<WorkbenchProblemAnalysisView.HierarchyTopicView> topics = new ArrayList<>();
        for (Map.Entry<String, List<PromptSignalView>> entry : promptsByTopic.entrySet()) {
            List<PromptSignalView> rows = entry.getValue().stream()
                    .sorted(Comparator.comparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                    .toList();
            List<String> coreSamples = rows.stream().map(PromptSignalView::promptText).limit(3).toList();
            List<String> decisionSamples = rows.stream()
                    .filter(item -> !"discovery".equals(item.intentKey()))
                    .map(PromptSignalView::promptText)
                    .limit(3)
                    .toList();
            List<String> longTailSamples = rows.stream()
                    .flatMap(item -> item.queryCounts().keySet().stream())
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(3)
                    .toList();
            if (longTailSamples.isEmpty()) {
                longTailSamples = rows.stream().skip(1).map(PromptSignalView::promptText).limit(3).toList();
            }
            topics.add(WorkbenchProblemAnalysisView.HierarchyTopicView.builder()
                    .topic(entry.getKey())
                    .problemSpaceBucket(resolveDominantProblemSpaceBucket(
                            rows.stream()
                                    .map(PromptSignalView::problemSpaceBucket)
                                    .toList()
                    ))
                    .demandIndex(rows.stream().mapToInt(PromptSignalView::demandIndex).sum())
                    .clusters(List.of(
                            WorkbenchProblemAnalysisView.HierarchyClusterView.builder()
                                    .label("核心问法")
                                    .count(coreSamples.size())
                                    .samples(coreSamples)
                                    .build(),
                            WorkbenchProblemAnalysisView.HierarchyClusterView.builder()
                                    .label("决策问法")
                                    .count(decisionSamples.size())
                                    .samples(decisionSamples.isEmpty() ? coreSamples : decisionSamples)
                                    .build(),
                            WorkbenchProblemAnalysisView.HierarchyClusterView.builder()
                                    .label("长尾延展")
                                    .count(longTailSamples.size())
                                    .samples(longTailSamples)
                                    .build()
                    ))
                    .build());
        }
        topics.sort(Comparator.comparing(WorkbenchProblemAnalysisView.HierarchyTopicView::getDemandIndex, Comparator.reverseOrder()));
        return WorkbenchProblemAnalysisView.HierarchyView.builder()
                .topics(topics)
                .build();
    }

    private WorkbenchProblemAnalysisView.FanoutView buildProblemAnalysisFanout(ProblemRichSnapshot currentSnapshot,
                                                                               ProblemRichSnapshot previousSnapshot) {
        List<WorkbenchProblemAnalysisView.FanoutPromptRowView> promptRows = currentSnapshot.prompts().stream()
                .filter(item -> item.queryCount() > 0 || !item.queryCounts().isEmpty())
                .sorted(Comparator.comparing(PromptSignalView::queryCount, Comparator.reverseOrder())
                        .thenComparing(PromptSignalView::demandIndex, Comparator.reverseOrder()))
                .limit(16)
                .map(item -> {
                    PromptSignalView previous = previousSnapshot.promptByKey().get(item.promptKey());
                    List<WorkbenchProblemAnalysisView.FanoutVariationRowView> variations = item.queryCounts().entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(6)
                            .map(entry -> {
                                int previousCount = previous == null ? 0 : previous.queryCounts().getOrDefault(entry.getKey(), 0);
                                double delta = previousCount <= 0
                                        ? entry.getValue()
                                        : round2(entry.getValue() - previousCount);
                                return WorkbenchProblemAnalysisView.FanoutVariationRowView.builder()
                                        .id(item.promptId() + "::" + normalizeBrandKey(entry.getKey()))
                                        .prompt(entry.getKey())
                                        .queryCount(entry.getValue())
                                        .delta(delta)
                                        .build();
                            })
                            .toList();
                    int totalPromptQueries = Math.max(1, item.queryCount());
                    List<WorkbenchProblemAnalysisView.FanoutPlatformInsightRowView> platformRows = item.platformQueryCounts().entrySet().stream()
                            .map(entry -> WorkbenchProblemAnalysisView.FanoutPlatformInsightRowView.builder()
                                    .platform(entry.getKey())
                                    .queryCount(entry.getValue())
                                    .share(round2(entry.getValue() * 100.0d / totalPromptQueries))
                                    .build())
                            .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.FanoutPlatformInsightRowView::getQueryCount, Comparator.reverseOrder()))
                            .toList();
                    return WorkbenchProblemAnalysisView.FanoutPromptRowView.builder()
                            .id(item.promptId())
                            .topic(item.topic())
                            .problemSpaceBucket(item.problemSpaceBucket())
                            .prompt(item.promptText())
                            .queryCount(item.queryCount())
                            .avgQueriesPerExecution(item.attemptCount() <= 0
                                    ? 0.0d
                                    : round2(item.queryCount() * 1.0d / item.attemptCount()))
                            .variations(variations)
                            .platformRows(platformRows)
                            .build();
                })
                .toList();

        List<WorkbenchProblemAnalysisView.FanoutPlatformRowView> platformRows = new ArrayList<>();
        for (PlatformSignalView item : currentSnapshot.platforms()) {
            PlatformSignalView previous = previousSnapshot.platformsByName().get(item.platform());
            platformRows.add(WorkbenchProblemAnalysisView.FanoutPlatformRowView.builder()
                    .rank(0)
                    .platform(item.platform())
                    .avgQueriesPerExecution(item.avgQueriesPerExecution())
                    .delta(previous == null
                            ? item.avgQueriesPerExecution()
                            : round2(item.avgQueriesPerExecution() - previous.avgQueriesPerExecution()))
                    .build());
        }
        List<WorkbenchProblemAnalysisView.FanoutPlatformRowView> rankedPlatformRows = new ArrayList<>();
        List<WorkbenchProblemAnalysisView.FanoutPlatformRowView> sorted = platformRows.stream()
                .sorted(Comparator.comparing(WorkbenchProblemAnalysisView.FanoutPlatformRowView::getAvgQueriesPerExecution, Comparator.reverseOrder()))
                .toList();
        for (int index = 0; index < sorted.size(); index++) {
            WorkbenchProblemAnalysisView.FanoutPlatformRowView row = sorted.get(index);
            rankedPlatformRows.add(WorkbenchProblemAnalysisView.FanoutPlatformRowView.builder()
                    .rank(index + 1)
                    .platform(row.getPlatform())
                    .avgQueriesPerExecution(row.getAvgQueriesPerExecution())
                    .delta(row.getDelta())
                    .build());
        }
        return WorkbenchProblemAnalysisView.FanoutView.builder()
                .promptRows(promptRows)
                .platformRows(rankedPlatformRows)
                .build();
    }

    private String promptSignalKey(String promptId, String promptText) {
        return StringUtils.hasText(promptId)
                ? promptId.trim()
                : "prompt_" + normalizeBrandKey(firstText(promptText, "untitled"));
    }

    private String resolveDominantKey(Map<String, Integer> counts, String fallback) {
        if (counts == null || counts.isEmpty()) {
            return fallback;
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(fallback);
    }

    private String resolveRichIntentKey(DiagnosisProbeRecord rawProbe, String promptText) {
        String lower = normalizeText(promptText);
        if (!StringUtils.hasText(lower)) {
            lower = "";
        }
        lower = lower.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "价格", "预算", "成本", "性价比", "折扣", "促销")) {
            return "cost";
        }
        String surfaceIntent = normalizeText(rawProbe == null ? null : rawProbe.getSurfaceIntent());
        if ("confirm_and_buy".equals(surfaceIntent) || "high_frequency_decision_query".equals(surfaceIntent)) {
            return "decision";
        }
        if ("ask_for_actionable_steps".equals(surfaceIntent)) {
            return containsAny(lower, "成本", "预算", "价格") ? "cost" : "decision";
        }
        if ("brand_stress_test".equals(surfaceIntent)) {
            return "risk";
        }
        if (containsAny(lower, "对比", "差异", "替代", "谁更", "推荐", "选哪个", "值得买吗")) {
            return "decision";
        }
        if (containsAny(lower, "风险", "售后", "漏水", "故障", "稳定", "兼容", "掉漆", "投诉", "质保")) {
            return "risk";
        }
        return "discovery";
    }

    private String resolveRichIntentLabel(String key) {
        String normalized = normalizeText(key);
        if (!StringUtils.hasText(normalized)) {
            return "信息探索";
        }
        return switch (normalized) {
            case "decision" -> "购买决策";
            case "risk" -> "风险验证";
            case "cost" -> "成本评估";
            default -> "信息探索";
        };
    }

    private String resolveRichSubIntentLabel(String promptText, String parent) {
        String text = normalizeText(promptText);
        if (!StringUtils.hasText(text)) {
            text = "";
        }
        if ("decision".equals(parent)) {
            if (containsAny(text, "对比", "差异", "谁更")) {
                return "同档对比";
            }
            if (containsAny(text, "推荐", "选", "系列", "型号")) {
                return "型号选择";
            }
            return "购买路径";
        }
        if ("risk".equals(parent)) {
            if (containsAny(text, "安装", "兼容", "改造")) {
                return "安装风险";
            }
            if (containsAny(text, "售后", "客服", "质保", "报修")) {
                return "售后风险";
            }
            return "质量风险";
        }
        if ("cost".equals(parent)) {
            if (containsAny(text, "活动", "折扣", "促销")) {
                return "价格窗口";
            }
            return "预算控制";
        }
        if (containsAny(text, "参数", "材质", "做工", "认证")) {
            return "参数理解";
        }
        if (containsAny(text, "口碑", "品牌", "评价")) {
            return "品牌认知";
        }
        return "需求识别";
    }

    private String resolveAudienceSegment(DiagnosisProbeRecord rawProbe, String promptText, String intentKey) {
        String text = normalizeText(promptText);
        if (!StringUtils.hasText(text)) {
            text = "";
        }
        text = text.toLowerCase(Locale.ROOT);
        String d1 = normalizeText(rawProbe == null ? null : rawProbe.getD1Identity());
        String d3 = normalizeText(rawProbe == null ? null : rawProbe.getD3PainPoint());
        if (containsAny(text, "工程", "批量", "项目", "商用", "交付")) {
            return "工程采购";
        }
        if (containsAny(text, "新手", "第一次", "入门", "小白") || "initiation".equals(d1)) {
            return "新手入门";
        }
        if (containsAny(text, "安装", "改造", "老房", "小户型", "兼容")) {
            return "场景改造";
        }
        if ("risk".equals(intentKey) || "subsistence_protection".equals(d3)) {
            return "风险规避";
        }
        if ("decision".equals(intentKey) || "verification".equals(d1)) {
            return "效率决策";
        }
        if (containsAny(text, "升级", "高端", "旗舰", "差异", "对比")) {
            return "品质进阶";
        }
        return "综合决策";
    }

    private String resolveBudgetSegment(DiagnosisProbeRecord rawProbe, String promptText, String intentKey) {
        String text = normalizeText(promptText);
        if (!StringUtils.hasText(text)) {
            text = "";
        }
        text = text.toLowerCase(Locale.ROOT);
        if (containsAny(text, "预算", "价格", "成本", "便宜", "性价比", "促销", "折扣")) {
            return "预算敏感";
        }
        if (containsAny(text, "高端", "升级", "旗舰", "品质")) {
            return "品质优先";
        }
        if (containsAny(text, "马上", "尽快", "工期", "下单", "现在买")) {
            return "效率优先";
        }
        if ("cost".equals(intentKey)) {
            return "预算敏感";
        }
        if (rawProbe != null && "verification".equals(normalizeText(rawProbe.getD1Identity()))) {
            return "效率优先";
        }
        return "平衡取舍";
    }

    private double computeCoverageScore(MutablePromptSignal signal) {
        double citation = firstNonNull(signal.citationShare, 0.0d);
        double bonus = signal.targetBrandCited ? 24.0d : 0.0d;
        double visibility = firstNonNull(signal.visibilityScore, 0.0d) * 0.18d;
        return round2(Math.min(100.0d, citation + bonus + visibility));
    }

    private int computeCompetitionScore(MutablePromptSignal signal) {
        int competitorMentions = signal.competitorMentions.values().stream().mapToInt(Integer::intValue).sum();
        int totalMentions = Math.max(1, competitorMentions + signal.targetMentionCount);
        double share = competitorMentions * 100.0d / totalMentions;
        if (competitorMentions == 0 && StringUtils.hasText(signal.competitorLeadBrand)) {
            share = 68.0d;
        }
        return (int) Math.round(Math.min(100.0d, Math.max(12.0d, share)));
    }

    private int computeRelevanceScore(MutablePromptSignal signal) {
        double visibility = firstNonNull(signal.visibilityScore, 0.0d) * 0.55d;
        double citation = firstNonNull(signal.citationShare, 0.0d) * 0.25d;
        double execution = signal.attemptCount > 0 ? 10.0d : 0.0d;
        double evidence = signal.evidenceCount > 0 ? 10.0d : 0.0d;
        return (int) Math.round(Math.min(100.0d, visibility + citation + execution + evidence));
    }

    private int computeDemandIndex(MutablePromptSignal signal) {
        double visibility = firstNonNull(signal.visibilityScore, 0.0d) * 1.1d;
        double positionBonus = signal.averagePosition == null ? 0.0d : Math.max(0.0d, 18.0d - signal.averagePosition * 2.0d);
        double raw = signal.attemptCount * 22.0d
                + signal.queryCount * 12.0d
                + signal.evidenceCount * 14.0d
                + signal.mentionCount * 6.0d
                + visibility
                + positionBonus
                + (signal.hasRawAnswer ? 12.0d : 0.0d);
        return Math.max(18, (int) Math.round(raw));
    }

    private int computeOpportunityScore(MutablePromptSignal signal) {
        double demandNormalized = Math.min(100.0d, signal.demandIndex / 6.0d);
        double gapScore = 100.0d - signal.coverageScore;
        double score = demandNormalized * 0.40d + signal.competitionScore * 0.35d + gapScore * 0.25d;
        return (int) Math.round(Math.max(18.0d, Math.min(98.0d, score)));
    }

    private String resolvePromptLoopStatus(MutablePromptSignal signal) {
        if (!signal.targetBrandCited && StringUtils.hasText(signal.competitorLeadBrand)) {
            return "竞品被引（我方缺口）";
        }
        if (signal.targetBrandCited && firstNonNull(signal.citationShare, 0.0d) < 10.0d) {
            return "待强化";
        }
        if (signal.targetBrandCited) {
            return "已被引用";
        }
        return "潜力词";
    }

    private String resolvePromptLoopAction(String status) {
        String normalized = normalizeText(status);
        if (!StringUtils.hasText(normalized)) {
            return "加入下一轮探针池";
        }
        return switch (normalized) {
            case "竞品被引（我方缺口）" -> "补对比页 + FAQ + 第三方证据";
            case "待强化" -> "补官网证据并细化标准说法";
            case "已被引用" -> "维持覆盖并扩到更多平台";
            default -> "加入下一轮探针池";
        };
    }

    private String resolvePromptGapType(MutablePromptSignal signal) {
        if (!signal.targetBrandCited && StringUtils.hasText(signal.competitorLeadBrand)) {
            return "competitor_gap";
        }
        if (signal.targetBrandCited && firstNonNull(signal.citationShare, 0.0d) < 10.0d) {
            return "needs_reinforcement";
        }
        if (signal.targetBrandCited) {
            return "covered";
        }
        return "emerging";
    }

    private String resolveProjectionAction(Integer opportunityScore) {
        int score = safeInt(opportunityScore);
        if (score >= 75) {
            return "优先扩展探针";
        }
        if (score >= 56) {
            return "保留观察";
        }
        return "低优先级";
    }

    private BrandSpaceContext loadContext(String userId, String brandSpaceId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(brandSpaceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        if (!StringUtils.hasText(organizationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BrandScope scope = brandScopeMapper.selectById(brandSpaceId.trim());
        if (scope == null || !Objects.equals(scope.getOrganizationId(), organizationId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在或无权访问");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
        }
        Brand brand = StringUtils.hasText(scope.getBrandId()) ? brandMapper.selectById(scope.getBrandId()) : null;
        DiagnosisTask latestTask = diagnosisTaskMapper.selectOne(new LambdaQueryWrapper<DiagnosisTask>()
                .eq(DiagnosisTask::getBrandScopeId, scope.getId())
                .orderByDesc(DiagnosisTask::getCreatedAt)
                .orderByDesc(DiagnosisTask::getUpdatedAt)
                .last("limit 1"));
        DiagnosisReportRecord latestReport = loadLatestKnowledgeReport(scope.getId());
        if (latestReport == null) {
            DiagnosisReportRecord latestReportHead = diagnosisReportRecordMapper.selectLatestWorkbenchHeadByBrandScopeId(scope.getId());
            latestReport = hydrateReportPayload(scope.getId(), latestReportHead);
        }
        return new BrandSpaceContext(userId, organizationId, scope, brand, latestTask, latestReport);
    }

    private List<DiagnosisReportRecord> loadRecentReports(BrandSpaceContext context, int limit) {
        if (context == null) {
            return List.of();
        }
        List<DiagnosisReportRecord> knowledgeReports = loadKnowledgeRecentReports(context.scope().getId(), limit);
        if (!knowledgeReports.isEmpty()) {
            return mergeLatestReportPayload(context.scope().getId(), knowledgeReports, context.latestReport());
        }
        List<DiagnosisReportRecord> heads = diagnosisReportRecordMapper.selectRecentWorkbenchHeadsByBrandScopeId(
                context.scope().getId(),
                Math.max(1, limit)
        );
        return mergeLatestReportPayload(context.scope().getId(), heads, context.latestReport());
    }

    private DiagnosisReportRecord loadLatestKnowledgeReport(String brandScopeId) {
        ensureKnowledgeHistoryReady(brandScopeId);
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || !StringUtils.hasText(brandScopeId)) {
            return null;
        }
        KnowledgeAssetRunRepository.RunDetailView latestRun = repository.findLatestRunDetailByScope(brandScopeId.trim());
        return synthesizeReportFromKnowledgeRun(latestRun);
    }

    private List<DiagnosisReportRecord> loadKnowledgeRecentReports(String brandScopeId, int limit) {
        ensureKnowledgeHistoryReady(brandScopeId);
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || !StringUtils.hasText(brandScopeId) || limit <= 0) {
            return List.of();
        }
        return repository.listRecentRunDetailsByScope(brandScopeId.trim(), Math.max(1, limit)).stream()
                .map(this::synthesizeReportFromKnowledgeRun)
                .filter(Objects::nonNull)
                .toList();
    }

    private DiagnosisReportRecord hydrateReportPayload(String brandSpaceId, DiagnosisReportRecord reportHead) {
        if (reportHead == null || !StringUtils.hasText(reportHead.getId())) {
            return null;
        }
        if (isKnowledgeRunReport(reportHead)) {
            return reportHead;
        }
        try {
            DiagnosisReportRecord payload = diagnosisReportRecordMapper.selectPayloadById(reportHead.getId());
            return payload == null ? reportHead : payload;
        } catch (RuntimeException ex) {
            log.warn("Workbench report payload fallback to head-only. brandSpaceId={}, reportId={}, reason={}",
                    brandSpaceId, reportHead.getId(), ex.getMessage());
            return reportHead;
        }
    }

    private List<DiagnosisReportRecord> mergeLatestReportPayload(String brandSpaceId,
                                                                 List<DiagnosisReportRecord> reports,
                                                                 DiagnosisReportRecord latestReport) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        return reports.stream()
                .map(report -> {
                    if (report == null || !StringUtils.hasText(report.getId())) {
                        return report;
                    }
                    if (latestReport != null && Objects.equals(report.getId(), latestReport.getId())) {
                        return latestReport;
                    }
                    if (isKnowledgeRunReport(report)) {
                        return report;
                    }
                    return hydrateReportPayload(brandSpaceId, report);
                })
                .toList();
    }

    private ReportResponse toReportResponse(DiagnosisReportRecord report) {
        if (report == null || report.getReportProjectionJson() == null || report.getReportProjectionJson().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(report.getReportProjectionJson(), ReportResponse.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private DiagnosisReportRecord synthesizeReportFromKnowledgeRun(KnowledgeAssetRunRepository.RunDetailView runDetail) {
        if (runDetail == null || !StringUtils.hasText(runDetail.runId())) {
            return null;
        }
        return DiagnosisReportRecord.builder()
                .id(firstText(runDetail.sourceReportId(), runDetail.runId()))
                .diagnosisTaskId(firstText(runDetail.sourceTaskId(), runDetail.runId()))
                .organizationId(runDetail.organizationId())
                .brandScopeId(runDetail.brandScopeId())
                .targetModelProduct(runDetail.targetModelProduct())
                .sampleSize(runDetail.sampleSize())
                .summaryJson(readObjectMapFromJson(runDetail.summaryPayloadJson()))
                .reportProjectionJson(readObjectMapFromJson(runDetail.reportProjectionJson()))
                .generatedAt(toLocalDateTime(runDetail.observedAt()))
                .createdAt(toLocalDateTime(runDetail.observedAt()))
                .build();
    }

    private DiagnosisTask synthesizeTaskFromKnowledgeRun(KnowledgeAssetRunRepository.RunDetailView runDetail) {
        if (runDetail == null || !StringUtils.hasText(runDetail.runId())) {
            return null;
        }
        Map<String, Object> snapshot = readObjectMapFromJson(runDetail.taskSnapshotJson());
        return DiagnosisTask.builder()
                .id(firstText(asText(snapshot.get("taskId")), runDetail.sourceTaskId(), runDetail.runId()))
                .organizationId(firstText(asText(snapshot.get("organizationId")), runDetail.organizationId()))
                .brandId(asText(snapshot.get("brandId")))
                .brandScopeId(firstText(asText(snapshot.get("brandScopeId")), runDetail.brandScopeId()))
                .engineVersion(asText(snapshot.get("engineVersion")))
                .statusCode(readInteger(snapshot.get("statusCode")))
                .targetProbeCount(readInteger(snapshot.get("targetProbeCount")))
                .targetModelProduct(firstText(asText(snapshot.get("targetModelProduct")), runDetail.targetModelProduct()))
                .brandNameSnapshot(asText(snapshot.get("brandNameSnapshot")))
                .categorySnapshot(asText(snapshot.get("categorySnapshot")))
                .officialWebsiteSnapshot(asText(snapshot.get("officialWebsiteSnapshot")))
                .taskOrderJson(readObjectMap(snapshot.get("taskOrder")))
                .taskMetadataJson(readObjectMap(snapshot.get("taskMetadata")))
                .build();
    }

    private Map<String, Object> readObjectMapFromJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception exception) {
            log.warn("Workbench knowledge JSON decode fallback. reason={}", exception.getMessage());
            return Map.of();
        }
    }

    private LocalDateTime toLocalDateTime(java.time.OffsetDateTime value) {
        return KnowledgeAssetTimeSupport.toProductLocalDateTime(value);
    }

    private void ensureKnowledgeHistoryReady(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return;
        }
        KnowledgeAssetHistorySyncService historySyncService = knowledgeAssetHistorySyncServiceProvider.getIfAvailable();
        if (historySyncService == null) {
            return;
        }
        try {
            historySyncService.ensureScopeHistoryUpToDate(brandScopeId.trim());
        } catch (RuntimeException exception) {
            if (isProblemAnalysisRuntimeSchemaFailure(exception)) {
                log.warn("Knowledge history sync skipped due to problem-analysis runtime schema drift. brandScopeId={}, reason={}",
                        brandScopeId, exception.getMessage());
                return;
            }
            throw exception;
        }
    }

    private boolean isProblemAnalysisRuntimeSchemaFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = defaultIfBlank(current.getMessage(), "").toLowerCase(Locale.ROOT);
            if (message.contains("diagnosis_probes") || message.contains("probe_dispatch_attempts")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isKnowledgeRunReport(DiagnosisReportRecord report) {
        return report != null && StringUtils.hasText(report.getDiagnosisTaskId())
                && report.getDiagnosisTaskId().startsWith("ka-run-");
    }

    private boolean isKnowledgeRunId(String runId) {
        return StringUtils.hasText(runId) && runId.trim().startsWith("ka-run-");
    }

    private DiagnosisProbeRecord toDiagnosisProbeRecord(KnowledgeAssetEvidenceRepository.ProbeView probe) {
        return DiagnosisProbeRecord.builder()
                .id(firstText(probe.sourceProbeId(), probe.id()))
                .diagnosisTaskId(probe.runId())
                .probeRef(probe.probeRef())
                .seqNo(probe.seqNo())
                .bucketTypeCode(probe.problemSpaceBucket())
                .renderText(probe.renderText())
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private ProbeDispatchAttempt toProbeDispatchAttempt(KnowledgeAssetEvidenceRepository.AttemptView attempt) {
        return ProbeDispatchAttempt.builder()
                .id(firstText(attempt.sourceAttemptId(), attempt.id()))
                .diagnosisTaskId(attempt.runId())
                .probeId(attempt.probeId())
                .provider(attempt.provider())
                .modelId(attempt.modelId())
                .rawResponseText(attempt.rawResponseText())
                .citationsJson(readStringListFromJson(attempt.citationsPayloadJson()))
                .responseMetadataJson(readObjectMapFromJson(attempt.responseMetadataJson()))
                .statusCode(attempt.statusCode())
                .selectedFinal(Boolean.TRUE.equals(attempt.selectedFinal()))
                .createdAt(attempt.createdAt())
                .build();
    }

    private ProbeBrandMentionRecord toProbeBrandMentionRecord(KnowledgeAssetEvidenceRepository.BrandMentionView mention) {
        return ProbeBrandMentionRecord.builder()
                .id(firstText(mention.sourceMentionId(), mention.id()))
                .diagnosisTaskId(mention.runId())
                .probeId(mention.probeId())
                .dispatchAttemptId(mention.attemptId())
                .brandId(mention.brandKey())
                .brandName(mention.brandName())
                .brandRoleCode(readInteger(mention.brandRole()))
                .mentionedFlag(Boolean.TRUE.equals(mention.mentionedFlag()))
                .recommendedFlag(Boolean.TRUE.equals(mention.recommendedFlag()))
                .sentimentCode(mention.sentimentCode())
                .positionIndex(mention.positionIndex())
                .confidenceScore(mention.confidenceScore())
                .mentionMetadataJson(readObjectMapFromJson(mention.mentionPayloadJson()))
                .createdAt(mention.createdAt())
                .build();
    }

    private ProbeEvidenceRecord toProbeEvidenceRecord(KnowledgeAssetSourceRepository.CitationView citation) {
        return ProbeEvidenceRecord.builder()
                .id(firstText(citation.sourceEvidenceId(), citation.sourceKey(), citation.url()))
                .dispatchAttemptId(citation.attemptId())
                .diagnosisTaskId(citation.runId())
                .probeId(citation.probeId())
                .sourceTypeCode(citation.sourceType())
                .provider(citation.providerKey())
                .domain(citation.domainName())
                .url(citation.url())
                .title(citation.title())
                .snippet(citation.snippet())
                .retrievedAt(toLocalDateTime(citation.retrievedAt()))
                .createdAt(toLocalDateTime(citation.retrievedAt()))
                .build();
    }

    private List<String> readStringListFromJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawJson, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception exception) {
            return List.of();
        }
    }

    private WorkbenchOverviewView.CurrentPerformanceView buildCurrentPerformance(BrandSpaceContext context,
                                                                                ReportResponse latestReport,
                                                                                List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows) {
        DiagnosisReportRecord report = context.latestReport();
        if (report == null) {
            return null;
        }
        Double top3Rate = resolveTop3Rate(context, report);
        return WorkbenchOverviewView.CurrentPerformanceView.builder()
                .visibilityScore(resolveVisibilityScore(report, top3Rate))
                .competitorVisibilityAverage(resolveCompetitorVisibilityAverage(report, latestReport, competitorRows))
                .recommendRate(resolveRecommendRate(report))
                .mentionRate(resolveMentionRate(report))
                .top3Rate(top3Rate)
                .abvGrade(normalizeText(report.getAbvGrade()))
                .headline(firstText(
                        latestReport == null ? null : latestReport.getBrandPerception() == null ? null : latestReport.getBrandPerception().getOneLineSummary(),
                        latestReport == null ? null : latestReport.getHeroSummary() == null ? null : latestReport.getHeroSummary().getHeadline()
                ))
                .generatedAt(formatDateTime(report.getGeneratedAt()))
                .build();
    }

    private WorkbenchOverviewView.CurrentPerformanceView buildWindowCurrentPerformance(
            BrandSpaceContext context,
            ReportResponse latestReport,
            List<DiagnosisReportRecord> recentReports,
            List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows) {
        if (recentReports == null || recentReports.isEmpty()) {
            return null;
        }
        List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints = buildTrendPoints(context, recentReports);
        WorkbenchBrandPerformanceView.CompetitorRowView targetRow = competitorRows == null
                ? null
                : competitorRows.stream().filter(item -> Boolean.TRUE.equals(item.getIsTarget())).findFirst().orElse(null);
        Double competitorVisibilityAverage = competitorRows == null
                ? null
                : averageValues(competitorRows.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsTarget()))
                .map(WorkbenchBrandPerformanceView.CompetitorRowView::getVisibilityScore)
                .toList());
        DiagnosisReportRecord latestScopedReport = recentReports.getFirst();
        return WorkbenchOverviewView.CurrentPerformanceView.builder()
                .visibilityScore(firstNonNull(targetRow == null ? null : targetRow.getVisibilityScore(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getVisibilityScore).toList())))
                .competitorVisibilityAverage(firstNonNull(competitorVisibilityAverage, 0.0d))
                .recommendRate(firstNonNull(targetRow == null ? null : targetRow.getRecommendRate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getRecommendRate).toList())))
                .mentionRate(firstNonNull(targetRow == null ? null : targetRow.getMentionRate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getMentionRate).toList())))
                .top3Rate(firstNonNull(targetRow == null ? null : targetRow.getTop3Rate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getTop3Rate).toList())))
                .abvGrade(normalizeText(latestScopedReport.getAbvGrade()))
                .headline(firstText(
                        latestReport == null ? null : latestReport.getBrandPerception() == null ? null : latestReport.getBrandPerception().getOneLineSummary(),
                        latestReport == null ? null : latestReport.getHeroSummary() == null ? null : latestReport.getHeroSummary().getHeadline()
                ))
                .generatedAt(formatDateTime(latestScopedReport.getGeneratedAt()))
                .build();
    }

    private WorkbenchOverviewView.CurrentPerformanceView buildOverviewCurrentPerformanceFromBrandPerformance(
            WorkbenchBrandPerformanceView.SummaryMetricView summary,
            List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
            WorkbenchOverviewView.CurrentPerformanceView fallbackView) {
        if (summary == null && fallbackView == null) {
            return null;
        }
        Double competitorVisibilityAverage = competitorRows == null
                ? null
                : averageValues(competitorRows.stream()
                .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsTarget()))
                .map(WorkbenchBrandPerformanceView.CompetitorRowView::getVisibilityScore)
                .toList());
        return WorkbenchOverviewView.CurrentPerformanceView.builder()
                .visibilityScore(firstNonNull(summary == null ? null : summary.getVisibilityScore(),
                        fallbackView == null ? null : fallbackView.getVisibilityScore()))
                .competitorVisibilityAverage(firstNonNull(
                        fallbackView == null ? null : fallbackView.getCompetitorVisibilityAverage(),
                        competitorVisibilityAverage))
                .recommendRate(firstNonNull(summary == null ? null : summary.getRecommendRate(),
                        fallbackView == null ? null : fallbackView.getRecommendRate()))
                .mentionRate(firstNonNull(summary == null ? null : summary.getMentionRate(),
                        fallbackView == null ? null : fallbackView.getMentionRate()))
                .top3Rate(firstNonNull(summary == null ? null : summary.getTop3Rate(),
                        fallbackView == null ? null : fallbackView.getTop3Rate()))
                .abvGrade(firstText(summary == null ? null : summary.getAbvGrade(),
                        fallbackView == null ? null : fallbackView.getAbvGrade()))
                .headline(firstText(summary == null ? null : summary.getHeadline(),
                        fallbackView == null ? null : fallbackView.getHeadline()))
                .generatedAt(firstText(summary == null ? null : summary.getGeneratedAt(),
                        fallbackView == null ? null : fallbackView.getGeneratedAt()))
                .build();
    }

    private WorkbenchOverviewView.CurrentPerformanceView applyFirstBatchMetricTruthToCurrentPerformance(
            BrandSpaceContext context,
            WorkbenchAnalysisContextView analysisContext,
            WorkbenchOverviewView.CurrentPerformanceView currentPerformance) {
        FirstBatchMetricTruth truth = loadFirstBatchMetricTruth(context, analysisContext);
        if (truth == null || currentPerformance == null) {
            return currentPerformance;
        }
        return WorkbenchOverviewView.CurrentPerformanceView.builder()
                .visibilityScore(truth.visibilityScore())
                .competitorVisibilityAverage(currentPerformance.getCompetitorVisibilityAverage())
                .recommendRate(truth.recommendRate())
                .mentionRate(truth.mentionRate())
                .top3Rate(truth.top3Rate())
                .abvGrade(currentPerformance.getAbvGrade())
                .headline(currentPerformance.getHeadline())
                .generatedAt(currentPerformance.getGeneratedAt())
                .build();
    }

    private FirstBatchMetricTruth loadFirstBatchMetricTruth(BrandSpaceContext context,
                                                            WorkbenchAnalysisContextView analysisContext) {
        if (context == null || context.scope() == null || hasExplicitProjectionFilter(analysisContext)) {
            return null;
        }
        KnowledgeAssetEvidenceRepository repository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return null;
        }
        LocalDate windowFrom = parseDate(analysisContext == null ? null : analysisContext.getDateFrom());
        LocalDate windowTo = parseDate(analysisContext == null ? null : analysisContext.getDateTo());
        if (windowFrom == null || windowTo == null) {
            return null;
        }
        KnowledgeAssetEvidenceRepository.MetricSampleRowsResult result = repository.listMetricSampleRows(
                context.scope().getId(),
                context.scope().getScopeName(),
                toWindowStartAt(windowFrom),
                toWindowEndAt(windowTo),
                0,
                1
        );
        int denominator = safeInt(result.effectiveAnswerCount());
        if (denominator <= 0) {
            return null;
        }
        int mentionHitCount = safeInt(result.mentionHitCount());
        int recommendHitCount = safeInt(result.recommendHitCount());
        int top3HitCount = safeInt(result.top3HitCount());
        double mentionRate = round2(mentionHitCount * 100.0d / denominator);
        double recommendRate = round2(recommendHitCount * 100.0d / denominator);
        double top3Rate = round2(top3HitCount * 100.0d / denominator);
        return new FirstBatchMetricTruth(
                denominator,
                mentionHitCount,
                recommendHitCount,
                top3HitCount,
                mentionRate,
                recommendRate,
                top3Rate,
                round2(mentionRate * 0.35d + recommendRate * 0.40d + top3Rate * 0.25d)
        );
    }

    private void enrichPreferredBrandPerformanceView(BrandSpaceContext context,
                                                     ReportResponse latestReport,
                                                     List<DiagnosisReportRecord> recentReports,
                                                     WorkbenchAnalysisContextView analysisContext,
                                                     WorkbenchBrandPerformanceView view) {
        if (context == null || view == null) {
            return;
        }
        List<WorkbenchBrandPerformanceView.TopicSummaryView> preferredTopicSummaries =
                buildPreferredTopicSummaries(context.scope().getId(), recentReports, analysisContext);
        if (!preferredTopicSummaries.isEmpty()) {
            view.setTopicSummaries(preferredTopicSummaries);
        }
        List<WorkbenchBrandPerformanceView.TrendPointView> preferredTrendPoints = buildTrendPoints(context, recentReports);
        if (!preferredTrendPoints.isEmpty()) {
            view.setTrendPoints(preferredTrendPoints);
        }
        WorkbenchBrandPerformanceView.SummaryMetricView preferredSummary = buildWindowBrandPerformanceSummary(
                context,
                latestReport,
                recentReports,
                view.getCompetitorRows(),
                view.getTopicSummaries()
        );
        if (preferredSummary != null) {
            view.setSummary(preferredSummary);
        }
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildPreferredTopicSummaries(
            String brandScopeId,
            List<DiagnosisReportRecord> recentReports,
            WorkbenchAnalysisContextView analysisContext) {
        if (hasExplicitProjectionFilter(analysisContext)) {
            return buildKnowledgeScopedTopicSummaries(brandScopeId, recentReports);
        }
        return buildKnowledgeWindowTopicSummaries(brandScopeId);
    }

    private WorkbenchOverviewView.LatestInsightView buildLatestInsight(BrandSpaceContext context,
                                                                       ReportResponse latestReport) {
        DiagnosisReportRecord report = context.latestReport();
        if (report == null) {
            return null;
        }
        String runId = report.getDiagnosisTaskId();
        DiagnosisTask runTask = resolveTaskForRun(context, runId);
        Map<String, Object> commercialInsights = readCommercialInsightsMap(report);
        List<WorkbenchOverviewView.InsightItemView> items = readOverviewInsightItems(report);
        if (items.isEmpty()) {
            items = resolveFallbackOverviewInsightItems(context, report);
        }
        return WorkbenchOverviewView.LatestInsightView.builder()
                .generatedAt(formatDateTime(report.getGeneratedAt()))
                .engineVersion(normalizeFormalRuntimeVersion(resolveLatestInsightEngineVersion(report, runTask)))
                .runId(runId)
                .reportId(report.getId())
                .insightSource(firstText(asText(commercialInsights.get("insightSource")), asText(commercialInsights.get("source"))))
                .sampleSize(report.getSampleSize())
                .headline(firstText(
                        asText(commercialInsights.get("headlineInsight")),
                        latestReport == null ? null : latestReport.getBrandPerception() == null ? null : latestReport.getBrandPerception().getOneLineSummary(),
                        latestReport == null ? null : latestReport.getHeroSummary() == null ? null : latestReport.getHeroSummary().getHeadline()
                ))
                .items(items)
                .build();
    }

    private List<WorkbenchOverviewView.InsightItemView> resolveFallbackOverviewInsightItems(BrandSpaceContext context,
                                                                                             DiagnosisReportRecord report) {
        if (context == null || !isKnowledgeRunReport(report) || !StringUtils.hasText(report.getId())) {
            return List.of();
        }
        DiagnosisReportRecord payload = diagnosisReportRecordMapper.selectPayloadById(report.getId());
        if (payload == null) {
            return List.of();
        }
        return readOverviewInsightItems(payload);
    }

    private WorkbenchOverviewView.LatestInsightView buildKnowledgeLatestInsight(
            WorkbenchBrandPerformanceView brandPerformanceView,
            WorkbenchCitationSourcesView citationSourcesView,
            List<WorkbenchOverviewView.PriorityItemView> priorities,
            WorkbenchOverviewView.LatestInsightView fallbackInsight) {
        WorkbenchBrandPerformanceView.SummaryMetricView summary = brandPerformanceView == null ? null : brandPerformanceView.getSummary();
        String generatedAt = firstText(
                summary == null ? null : summary.getGeneratedAt(),
                brandPerformanceView == null ? null : brandPerformanceView.getLastGeneratedAt(),
                citationSourcesView == null ? null : citationSourcesView.getLastGeneratedAt(),
                fallbackInsight == null ? null : fallbackInsight.getGeneratedAt()
        );
        List<WorkbenchOverviewView.InsightItemView> items = (priorities == null ? List.<WorkbenchOverviewView.PriorityItemView>of() : priorities)
                .stream()
                .limit(3)
                .map(this::toKnowledgeInsightItem)
                .toList();
        if (items.isEmpty() && fallbackInsight != null && fallbackInsight.getItems() != null) {
            items = fallbackInsight.getItems();
        }
        return WorkbenchOverviewView.LatestInsightView.builder()
                .generatedAt(generatedAt)
                .engineVersion(normalizeFormalRuntimeVersion(firstText(
                        "5.0",
                        fallbackInsight == null ? null : fallbackInsight.getEngineVersion()
                )))
                .runId(brandPerformanceView == null ? null : brandPerformanceView.getLatestRunId())
                .reportId(brandPerformanceView == null ? null : brandPerformanceView.getLatestReportId())
                .insightSource("knowledge_window_projection")
                .sampleSize(firstNonNull(
                        summary == null ? null : summary.getSampleSize(),
                        fallbackInsight == null ? null : fallbackInsight.getSampleSize()
                ))
                .headline(firstText(
                        summary == null ? null : summary.getHeadline(),
                        priorities == null || priorities.isEmpty() ? null : priorities.getFirst().getSummary(),
                        fallbackInsight == null ? null : fallbackInsight.getHeadline(),
                        "当前结论来自最近一段时间的 5.0 真实运行汇总。"
                ))
                .items(items)
                .build();
    }

    private WorkbenchOverviewView.InsightItemView toKnowledgeInsightItem(WorkbenchOverviewView.PriorityItemView priorityItem) {
        if (priorityItem == null) {
            return null;
        }
        String routeKey = firstText(priorityItem.getDetailRouteKey(), "probe_analysis");
        return WorkbenchOverviewView.InsightItemView.builder()
                .title(priorityItem.getTitle())
                .evidence(priorityItem.getReason())
                .evidenceSummary(priorityItem.getReason())
                .recommendation(priorityItem.getSummary())
                .nextStepLabel(resolveInsightNextStepLabel(routeKey))
                .nextStepText(firstText(priorityItem.getSummary(), "继续查看对应频道"))
                .nextStepRouteKey(routeKey)
                .build();
    }

    private String resolveInsightNextStepLabel(String routeKey) {
        return switch (defaultIfBlank(routeKey, "probe_analysis")) {
            case "engine" -> "去品牌表现";
            case "sources" -> "去引用来源";
            default -> "去问题分析";
        };
    }

    private String resolveLatestInsightEngineVersion(DiagnosisReportRecord report,
                                                     DiagnosisTask runTask) {
        String taskVersion = runTask == null ? null : normalizeText(runTask.getEngineVersion());
        if (StringUtils.hasText(taskVersion)) {
            return taskVersion;
        }
        if (report == null || report.getReportProjectionJson() == null || report.getReportProjectionJson().isEmpty()) {
            return null;
        }
        Map<String, Object> meta = readObjectMap(report.getReportProjectionJson().get("meta"));
        return firstText(
                asText(meta.get("engineVersion")),
                asText(meta.get("reportVersion"))
        );
    }

    private String normalizeFormalRuntimeVersion(String rawVersion) {
        String normalized = normalizeText(rawVersion);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if ("4.3".equals(lower) || "v4.3".equals(lower) || "5.0".equals(lower) || "v5.0".equals(lower)) {
            return "5.0";
        }
        if ("4.2".equals(lower) || "v4.2".equals(lower)) {
            return "4.2";
        }
        return normalized;
    }

    private Double averageValues(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double total = 0.0d;
        int count = 0;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            total += value;
            count++;
        }
        if (count <= 0) {
            return null;
        }
        return round2(total / count);
    }

    private WorkbenchBrandPerformanceView.SummaryMetricView buildBrandPerformanceSummary(BrandSpaceContext context,
                                                                                         DiagnosisReportRecord currentReport,
                                                                                         DiagnosisReportRecord previousReport,
                                                                                         ReportResponse currentProjection,
                                                                                         List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
                                                                                         Map<String, Double> currentAveragePositionByBrand,
                                                                                         Map<String, Double> previousAveragePositionByBrand,
                                                                                         Map<String, Double> currentTop3RateByBrand,
                                                                                         List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        if (currentReport == null) {
            return null;
        }
        Double currentTop3Rate = resolveTargetTop3Rate(context, currentReport, currentTop3RateByBrand);
        WorkbenchBrandPerformanceView.CompetitorRowView selfRow = competitorRows == null
                ? null
                : competitorRows.stream().filter(item -> Boolean.TRUE.equals(item.getIsTarget())).findFirst().orElse(null);
        Integer previousRank = previousReport == null
                ? null
                : buildPreviousRankMap(
                        context,
                        previousReport,
                        null,
                        previousAveragePositionByBrand,
                        Map.of()
                ).get(normalizeBrandKey(resolveBrandName(context)));
        List<String> topTopicLabels = extractTopTopicLabels(currentProjection);
        return WorkbenchBrandPerformanceView.SummaryMetricView.builder()
                .visibilityScore(resolveVisibilityScore(currentReport, currentTop3Rate))
                .recommendRate(resolveRecommendRate(currentReport))
                .mentionRate(resolveMentionRate(currentReport))
                .top3Rate(currentTop3Rate)
                .abvGrade(normalizeText(currentReport.getAbvGrade()))
                .sampleSize(currentReport.getSampleSize())
                .generatedAt(formatDateTime(currentReport.getGeneratedAt()))
                .visibilityRank(selfRow == null ? null : selfRow.getRank())
                .visibilityRankDelta(selfRow == null || previousRank == null ? null : previousRank - selfRow.getRank())
                .shareOfVoice(resolveMentionRate(currentReport))
                .shareOfVoiceDelta(subtract(resolveMentionRate(currentReport), resolveMentionRate(previousReport)))
                .averagePosition(currentAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context))))
                .averagePositionDelta(subtract(
                        currentAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context))),
                        previousAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context)))
                ))
                .headline(firstText(
                        currentProjection == null || currentProjection.getHeroSummary() == null
                                ? null
                                : currentProjection.getHeroSummary().getHeadline(),
                        currentProjection == null || currentProjection.getBrandPerception() == null
                                ? null
                                : currentProjection.getBrandPerception().getOneLineSummary()
                ))
                .issueCount(readIssueCount(currentProjection))
                .evidenceCount(readEvidenceCount(currentReport))
                .topTopicLabels(topTopicLabels.isEmpty() ? extractTopicLabels(topicSummaries) : topTopicLabels)
                .build();
    }

    private WorkbenchBrandPerformanceView.SummaryMetricView buildWindowBrandPerformanceSummary(
            BrandSpaceContext context,
            ReportResponse latestReport,
            List<DiagnosisReportRecord> recentReports,
            List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
            List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        if (recentReports == null || recentReports.isEmpty()) {
            return null;
        }
        DiagnosisReportRecord latestScopedReport = recentReports.getFirst();
        WorkbenchBrandPerformanceView.CompetitorRowView targetRow = competitorRows == null
                ? null
                : competitorRows.stream().filter(item -> Boolean.TRUE.equals(item.getIsTarget())).findFirst().orElse(null);
        List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints = buildTrendPoints(context, recentReports);
        List<String> topTopicLabels = extractTopTopicLabels(latestReport);
        return WorkbenchBrandPerformanceView.SummaryMetricView.builder()
                .visibilityScore(firstNonNull(targetRow == null ? null : targetRow.getVisibilityScore(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getVisibilityScore).toList())))
                .recommendRate(firstNonNull(targetRow == null ? null : targetRow.getRecommendRate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getRecommendRate).toList())))
                .mentionRate(firstNonNull(targetRow == null ? null : targetRow.getMentionRate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getMentionRate).toList())))
                .top3Rate(firstNonNull(targetRow == null ? null : targetRow.getTop3Rate(),
                        averageValues(trendPoints.stream().map(WorkbenchBrandPerformanceView.TrendPointView::getTop3Rate).toList())))
                .abvGrade(normalizeText(latestScopedReport.getAbvGrade()))
                .sampleSize(recentReports.stream()
                        .map(DiagnosisReportRecord::getSampleSize)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum())
                .generatedAt(formatDateTime(latestScopedReport.getGeneratedAt()))
                .visibilityRank(targetRow == null ? null : targetRow.getRank())
                .visibilityRankDelta(targetRow == null ? null : targetRow.getRankDelta())
                .shareOfVoice(targetRow == null ? null : targetRow.getShareOfVoice())
                .shareOfVoiceDelta(targetRow == null ? null : targetRow.getShareOfVoiceDelta())
                .averagePosition(targetRow == null ? null : targetRow.getAveragePosition())
                .averagePositionDelta(targetRow == null ? null : targetRow.getAveragePositionDelta())
                .headline(resolveKnowledgeBrandPerformanceHeadline(targetRow, topicSummaries))
                .issueCount(resolveKnowledgeIssueCount(topicSummaries))
                .evidenceCount(recentReports.stream().mapToInt(this::readEvidenceCount).sum())
                .topTopicLabels(topTopicLabels.isEmpty() ? extractTopicLabels(topicSummaries) : topTopicLabels)
                .build();
    }

    private String resolveKnowledgeBrandPerformanceHeadline(
            WorkbenchBrandPerformanceView.CompetitorRowView targetRow,
            List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        String topTopic = topicSummaries == null || topicSummaries.isEmpty()
                ? "核心问题场景"
                : firstText(topicSummaries.getFirst().getTopicName(), "核心问题场景");
        if (targetRow == null) {
            return "当前窗口已经接通正式结果，但目标品牌仍缺少稳定的表现样本。";
        }
        Double shareOfVoice = targetRow.getShareOfVoice();
        Integer rank = targetRow.getRank();
        Double averagePosition = targetRow.getAveragePosition();
        if (rank != null && rank == 1 && shareOfVoice != null && shareOfVoice >= 20.0d) {
            return "品牌已进入候选池前列，下一步要围绕" + topTopic + "巩固优势。";
        }
        if (averagePosition != null && averagePosition > 2.5d) {
            return "品牌在用户候选路径里仍偏后，需要优先修复" + topTopic + "相关认知。";
        }
        if (shareOfVoice != null && shareOfVoice < 15.0d) {
            return "品牌在窗口内的心智份额仍然偏低，优先补强" + topTopic + "最关键。";
        }
        return "品牌已进入用户候选池，但仍需围绕" + topTopic + "继续提升稳定认知。";
    }

    private int resolveKnowledgeIssueCount(List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        if (topicSummaries == null || topicSummaries.isEmpty()) {
            return 0;
        }
        return (int) topicSummaries.stream()
                .filter(Objects::nonNull)
                .filter(item -> safeInt(item.getSignalCount()) > 0)
                .count();
    }

    private List<WorkbenchOverviewView.PriorityItemView> buildKnowledgePriorityItems(
            WorkbenchBrandPerformanceView brandPerformanceView,
            WorkbenchCitationSourcesView citationSourcesView) {
        List<WorkbenchOverviewView.PriorityItemView> items = new ArrayList<>();
        WorkbenchBrandPerformanceView.SummaryMetricView summary = brandPerformanceView == null ? null : brandPerformanceView.getSummary();
        List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows =
                brandPerformanceView == null ? List.of() : defaultIfNullList(brandPerformanceView.getCompetitorRows());
        WorkbenchBrandPerformanceView.CompetitorRowView targetRow = competitorRows.stream()
                .filter(item -> item != null && Boolean.TRUE.equals(item.getIsTarget()))
                .findFirst()
                .orElse(null);
        WorkbenchBrandPerformanceView.CompetitorRowView strongestCompetitor = competitorRows.stream()
                .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsTarget()))
                .max(Comparator.comparing(
                        item -> firstNonNull(item.getShareOfVoice(), item.getVisibilityScore(), 0.0d),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .orElse(null);
        if (targetRow != null && strongestCompetitor != null) {
            double gap = safeDouble(strongestCompetitor.getShareOfVoice()) - safeDouble(targetRow.getShareOfVoice());
            if (gap > 0.01d) {
                items.add(WorkbenchOverviewView.PriorityItemView.builder()
                        .id("knowledge_competitor_gap")
                        .title("竞品压制仍在扩大")
                        .summary("先看竞品对比，确认为什么 " + strongestCompetitor.getBrandName() + " 仍领先。")
                        .reason(String.format(Locale.ROOT,
                                "%s 在当前窗口的心智份额比目标品牌高 %.2f 个点。",
                                firstText(strongestCompetitor.getBrandName(), "头部竞品"),
                                round2(gap)))
                        .detailRouteKey("engine")
                        .build());
            }
        }
        List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries =
                brandPerformanceView == null ? List.of() : defaultIfNullList(brandPerformanceView.getTopicSummaries());
        topicSummaries.stream()
                .filter(Objects::nonNull)
                .filter(item -> safeInt(item.getSignalCount()) > 0)
                .max(Comparator.comparing(item -> safeInt(item.getSignalCount())))
                .ifPresent(item -> items.add(WorkbenchOverviewView.PriorityItemView.builder()
                        .id("knowledge_topic_" + firstText(item.getTopicId(), item.getTopicName()))
                        .title("优先修复「" + firstText(item.getTopicName(), "核心主题") + "」")
                        .summary("去问题分析里看这个主题下的高频信号和证据链。")
                        .reason(String.format(Locale.ROOT,
                                "当前窗口内该主题累计信号 %d，占比 %.2f%%。",
                                safeInt(item.getSignalCount()),
                                safeDouble(item.getSignalShare())))
                        .detailRouteKey("probe_analysis")
                        .build()));
        WorkbenchCitationSourcesView.SourceSummaryView sourceSummary =
                citationSourcesView == null ? null : citationSourcesView.getSummary();
        if (hasCitationFacts(sourceSummary) && sourceSummary.getOwnedShare() != null && sourceSummary.getOwnedShare() < 35.0d) {
            items.add(WorkbenchOverviewView.PriorityItemView.builder()
                    .id("knowledge_owned_share")
                    .title("自有来源占比偏低")
                    .summary("去引用来源看哪些第三方站点正在主导答案证据。")
                    .reason(String.format(Locale.ROOT,
                            "当前窗口内自有来源占比仅 %.2f%%，第三方证据仍占主导。",
                            safeDouble(sourceSummary.getOwnedShare())))
                    .detailRouteKey("sources")
                    .build());
        }
        if (items.isEmpty() && hasSummaryFacts(summary)) {
            items.add(WorkbenchOverviewView.PriorityItemView.builder()
                    .id("knowledge_default")
                    .title("继续稳定核心认知")
                    .summary("当前总览已经接通最新正式结果，下一步优先看核心主题与竞品差距。")
                    .reason(firstText(summary.getHeadline(), "当前窗口内仍需继续补强品牌核心认知。"))
                    .detailRouteKey("engine")
                    .build());
        }
        return items.stream().limit(3).toList();
    }

    private boolean hasSummaryFacts(WorkbenchBrandPerformanceView.SummaryMetricView summary) {
        return summary != null
                && (summary.getSampleSize() != null && summary.getSampleSize() > 0
                || summary.getVisibilityScore() != null
                || summary.getRecommendRate() != null
                || summary.getMentionRate() != null
                || summary.getTop3Rate() != null
                || summary.getShareOfVoice() != null
                || summary.getEvidenceCount() != null && summary.getEvidenceCount() > 0
                || summary.getIssueCount() != null && summary.getIssueCount() > 0);
    }

    private boolean hasCitationFacts(WorkbenchCitationSourcesView.SourceSummaryView sourceSummary) {
        return sourceSummary != null
                && sourceSummary.getTotalCitations() != null
                && sourceSummary.getTotalCitations() > 0;
    }

    private <T> List<T> defaultIfNullList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private WorkbenchOverviewView.TrendView buildTrendView(List<WorkbenchBrandPerformanceView.TrendPointView> points) {
        if (points == null || points.isEmpty()) {
            return WorkbenchOverviewView.TrendView.builder()
                    .direction("none")
                    .points(List.of())
                    .build();
        }
        WorkbenchBrandPerformanceView.TrendPointView latest = points.get(points.size() - 1);
        WorkbenchBrandPerformanceView.TrendPointView previous = points.size() > 1 ? points.get(points.size() - 2) : null;
        Double deltaVisibility = subtract(latest.getVisibilityScore(), previous == null ? null : previous.getVisibilityScore());
        Double deltaRecommend = subtract(latest.getRecommendRate(), previous == null ? null : previous.getRecommendRate());
        Double deltaMention = subtract(latest.getMentionRate(), previous == null ? null : previous.getMentionRate());
        return WorkbenchOverviewView.TrendView.builder()
                .direction(resolveDirection(deltaVisibility))
                .deltaVisibilityScore(deltaVisibility)
                .deltaRecommendRate(deltaRecommend)
                .deltaMentionRate(deltaMention)
                .points(points.stream()
                        .map(item -> WorkbenchOverviewView.TrendPointView.builder()
                                .reportId(item.getReportId())
                                .runId(item.getRunId())
                                .label(item.getLabel())
                                .generatedAt(item.getGeneratedAt())
                                .visibilityScore(item.getVisibilityScore())
                                .recommendRate(item.getRecommendRate())
                                .mentionRate(item.getMentionRate())
                                .top3Rate(item.getTop3Rate())
                                .build())
                        .toList())
                .build();
    }

    private List<WorkbenchBrandPerformanceView.TrendPointView> buildTrendPoints(List<DiagnosisReportRecord> recentReports) {
        if (recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        return buildTrendPoints(null, recentReports);
    }

    private List<WorkbenchBrandPerformanceView.TrendPointView> buildTrendPoints(BrandSpaceContext context,
                                                                                List<DiagnosisReportRecord> recentReports) {
        if (recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        return recentReports.stream()
                .sorted(Comparator.comparing(DiagnosisReportRecord::getGeneratedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .map(report -> {
                    Double top3Rate = context == null ? resolveTop3RateFallback(report) : resolveTop3Rate(context, report);
                    return WorkbenchBrandPerformanceView.TrendPointView.builder()
                            .reportId(report.getId())
                            .runId(report.getDiagnosisTaskId())
                            .label(resolveTrendLabel(report.getGeneratedAt()))
                            .generatedAt(formatDateTime(report.getGeneratedAt()))
                            .visibilityScore(resolveVisibilityScore(report, top3Rate))
                            .recommendRate(resolveRecommendRate(report))
                            .mentionRate(resolveMentionRate(report))
                            .top3Rate(top3Rate)
                            .build();
                })
                .toList();
    }

    private List<WorkbenchBrandPerformanceView.TrendPointView> buildTrendPoints(BrandSpaceContext context,
                                                                                List<DiagnosisReportRecord> recentReports,
                                                                                WorkbenchAnalysisContextView analysisContext) {
        return aggregateBrandTrendPointsByFrequency(
                buildTrendPoints(context, recentReports),
                analysisContext == null ? null : analysisContext.getFrequency()
        );
    }

    private List<WorkbenchBrandPerformanceView.TrendPointView> aggregateBrandTrendPointsByFrequency(
            List<WorkbenchBrandPerformanceView.TrendPointView> points,
            String frequency) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        String normalizedFrequency = normalizeText(frequency);
        if (!"day".equals(normalizedFrequency) && !"week".equals(normalizedFrequency)) {
            return points;
        }
        LinkedHashMap<String, MutableBrandTrendBucket> buckets = new LinkedHashMap<>();
        for (WorkbenchBrandPerformanceView.TrendPointView point : points) {
            LocalDateTime generatedAt = parseProjectionDateTime(point.getGeneratedAt());
            if (generatedAt == null) {
                generatedAt = parseProjectionDateTime(point.getLabel());
            }
            if (generatedAt == null) {
                buckets.computeIfAbsent(
                        firstText(point.getLabel(), point.getGeneratedAt(), "unknown"),
                        ignored -> new MutableBrandTrendBucket(
                                firstText(point.getLabel(), point.getGeneratedAt(), "unknown"),
                                point.getGeneratedAt()
                        )
                ).add(point);
                continue;
            }
            String label = resolveFrequencyBucketLabel(generatedAt, normalizedFrequency);
            String generatedAtLabel = resolveFrequencyBucketGeneratedAt(generatedAt, normalizedFrequency);
            buckets.computeIfAbsent(label, ignored -> new MutableBrandTrendBucket(label, generatedAtLabel)).add(point);
        }
        return buckets.values().stream()
                .map(MutableBrandTrendBucket::toPoint)
                .toList();
    }

    private List<WorkbenchOverviewView.PriorityItemView> buildPriorityItems(ReportResponse report) {
        if (report == null) {
            return List.of();
        }
        List<WorkbenchOverviewView.PriorityItemView> items = new ArrayList<>();
        appendPriorityItems(items, "p0", readActionItems(report, "p0"));
        appendPriorityItems(items, "p1", readActionItems(report, "p1"));
        appendPriorityItems(items, "p2", readActionItems(report, "p2"));
        if (!items.isEmpty()) {
            return items;
        }

        List<Map<String, Object>> negativeSignals = report.getNegativeSignals() == null ? List.of() : report.getNegativeSignals();
        int index = 1;
        for (Map<String, Object> signal : negativeSignals.stream().limit(3).toList()) {
            items.add(WorkbenchOverviewView.PriorityItemView.builder()
                    .id("signal_" + index++)
                    .title(firstText(asText(signal.get("signal")), "优先排查问题信号"))
                    .summary(firstText(asText(signal.get("signal")), "等待问题分析"))
                    .reason(readSignalReason(signal))
                    .detailRouteKey("probe_analysis")
                    .build());
        }
        return items;
    }

    private Map<String, Object> readCommercialInsightsMap(DiagnosisReportRecord report) {
        if (report == null || report.getReportProjectionJson() == null || report.getReportProjectionJson().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> reportMap = readObjectMap(report.getReportProjectionJson().get("report"));
        Map<String, Object> nested = readObjectMap(reportMap.get("commercialInsights"));
        if (!nested.isEmpty()) {
            return nested;
        }
        return readObjectMap(report.getReportProjectionJson().get("commercialInsights"));
    }

    private List<WorkbenchOverviewView.InsightItemView> readOverviewInsightItems(DiagnosisReportRecord report) {
        if (report == null || report.getTopInsightsJson() == null || report.getTopInsightsJson().isEmpty()) {
            return List.of();
        }
        return report.getTopInsightsJson().stream()
                .filter(Objects::nonNull)
                .map(this::buildOverviewInsightItem)
                .toList();
    }

    private WorkbenchOverviewView.InsightItemView buildOverviewInsightItem(Map<String, Object> item) {
        String title = firstText(asText(item.get("title")), "未命名洞察");
        String evidence = firstText(asText(item.get("evidence")), "暂无证据说明");
        String recommendation = normalizeText(asText(item.get("recommendation")));
        OverviewInsightCopy copy = resolveOverviewInsightCopy(title, evidence, recommendation);
        return WorkbenchOverviewView.InsightItemView.builder()
                .title(title)
                .evidence(evidence)
                .evidenceSummary(copy.evidenceSummary())
                .recommendation(recommendation)
                .nextStepLabel(copy.nextStepLabel())
                .nextStepText(copy.nextStepText())
                .nextStepRouteKey(copy.nextStepRouteKey())
                .build();
    }

    private OverviewInsightCopy resolveOverviewInsightCopy(String title,
                                                           String evidence,
                                                           String recommendation) {
        String normalizedTitle = normalizeLower(title);
        String normalizedEvidence = normalizeLower(evidence);

        if (containsInsightNeedle(normalizedTitle, normalizedEvidence, "对抗", "effectivesamplesize=0", "防御能力")) {
            return new OverviewInsightCopy(
                    "对抗场景样本还不够，暂时看不清真实防守能力。",
                    "去问题分析",
                    "看竞品决策和风险异议",
                    "probe_analysis"
            );
        }

        if (containsInsightNeedle(normalizedTitle, normalizedEvidence, "自发认知", "spontaneousmentionrate", "被动确认")) {
            return new OverviewInsightCopy(
                    "模型会在提示后认出你，但几乎不会主动想到你。",
                    "去品牌表现",
                    "看可见度和提及趋势",
                    "engine"
            );
        }

        if (containsInsightNeedle(normalizedTitle, normalizedEvidence, "竞品", "responseshare", "averagestrength", "压制")) {
            return new OverviewInsightCopy(
                    "竞品已经抢走一部分用户心智，压制强度也偏高。",
                    "去品牌表现",
                    "看竞品对比",
                    "engine"
            );
        }

        return new OverviewInsightCopy(
                summarizeInsightEvidence(evidence),
                "去问题分析",
                firstText(summarizeRecommendation(recommendation), "看这一轮核心问题"),
                "probe_analysis"
        );
    }

    private String summarizeInsightEvidence(String evidence) {
        String text = normalizeText(evidence);
        if (!StringUtils.hasText(text)) {
            return "这一轮证据还不够完整。";
        }
        return text
                .replace("spontaneousMentionRate", "自发提及率")
                .replace("confirmationResponseRate", "提示后确认率")
                .replace("effectiveSampleSize", "有效样本量")
                .replace("responseShare", "心智份额")
                .replace("averageStrength", "压制强度")
                .replace("总体 ", "")
                .replace("对抗桶 ", "对抗场景 ");
    }

    private String summarizeRecommendation(String recommendation) {
        String text = normalizeText(recommendation);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text
                .replace("立即", "")
                .replace("启动", "")
                .replace("专项研究", "")
                .replace("针对性设计", "")
                .replace("分析", "看")
                .replace("重构", "看")
                .trim();
    }

    private boolean containsInsightNeedle(String first, String second, String... needles) {
        for (String needle : needles) {
            if (StringUtils.hasText(needle)
                    && ((StringUtils.hasText(first) && first.contains(needle))
                    || (StringUtils.hasText(second) && second.contains(needle)))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeLower(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private List<String> readActionItems(ReportResponse report, String key) {
        if (report == null || report.getActionItems() == null) {
            return List.of();
        }
        Object raw = report.getActionItems().get(key);
        if (raw instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return List.of();
    }

    private void appendPriorityItems(List<WorkbenchOverviewView.PriorityItemView> target,
                                     String priorityKey,
                                     List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        int startIndex = target.size() + 1;
        for (int index = 0; index < actions.size(); index++) {
            String action = actions.get(index);
            target.add(WorkbenchOverviewView.PriorityItemView.builder()
                    .id(priorityKey + "_" + (startIndex + index))
                    .title("处理 " + priorityKey.toUpperCase(Locale.ROOT) + " 事项")
                    .summary(action)
                    .reason(priorityKey.toUpperCase(Locale.ROOT) + " 优先级建议")
                    .detailRouteKey("probe_analysis")
                    .build());
        }
    }

    private String readSignalReason(Map<String, Object> signal) {
        Object count = signal == null ? null : signal.get("count");
        if (count instanceof Number number) {
            return "当前信号出现 " + number.intValue() + " 次";
        }
        return "等待更多样本确认";
    }

    private List<WorkbenchBrandPerformanceView.CompetitorRowView> buildCompetitorRows(BrandSpaceContext context,
                                                                                      ReportResponse currentProjection) {
        return buildCompetitorRows(
                context,
                context.latestReport(),
                null,
                currentProjection,
                null,
                Map.of(),
                Map.of(),
                loadTop3RateByBrand(
                        context.latestReport() == null ? null : context.latestReport().getDiagnosisTaskId(),
                        context.latestReport() == null ? null : context.latestReport().getSampleSize()
                ),
                Map.of()
        );
    }

    private List<WorkbenchBrandPerformanceView.CompetitorRowView> buildWindowCompetitorRows(
            BrandSpaceContext context,
            List<DiagnosisReportRecord> recentReports) {
        if (recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MutableWindowCompetitorRow> rowsByBrand = new LinkedHashMap<>();
        for (DiagnosisReportRecord report : recentReports) {
            if (report == null) {
                continue;
            }
            ReportResponse projection = toReportResponse(report);
            Map<String, Double> averagePositionByBrand = loadAveragePositionByBrand(report.getDiagnosisTaskId());
            Map<String, Double> top3RateByBrand = loadTop3RateByBrand(report.getDiagnosisTaskId(), report.getSampleSize());
            List<WorkbenchBrandPerformanceView.CompetitorRowView> reportRows = buildCompetitorRows(
                    context,
                    report,
                    null,
                    projection,
                    null,
                    averagePositionByBrand,
                    Map.of(),
                    top3RateByBrand,
                    Map.of()
            );
            for (WorkbenchBrandPerformanceView.CompetitorRowView reportRow : reportRows) {
                String brandKey = normalizeBrandKey(reportRow.getBrandName());
                if (!StringUtils.hasText(brandKey)) {
                    continue;
                }
                MutableWindowCompetitorRow accumulator =
                        rowsByBrand.computeIfAbsent(brandKey, ignored -> new MutableWindowCompetitorRow());
                accumulator.id = firstText(accumulator.id, reportRow.getId());
                accumulator.brandName = firstText(accumulator.brandName, reportRow.getBrandName());
                accumulator.target = accumulator.target || Boolean.TRUE.equals(reportRow.getIsTarget());
                accumulator.visibilityScores.add(reportRow.getVisibilityScore());
                accumulator.recommendRates.add(reportRow.getRecommendRate());
                accumulator.mentionRates.add(reportRow.getMentionRate());
                accumulator.positiveRates.add(reportRow.getPositiveRate());
                accumulator.top3Rates.add(reportRow.getTop3Rate());
                accumulator.shareOfVoiceValues.add(reportRow.getShareOfVoice());
                accumulator.averagePositions.add(reportRow.getAveragePosition());
                accumulator.mentionCount += safeInt(reportRow.getMentionCount());
            }
        }
        List<WorkbenchBrandPerformanceView.CompetitorRowView> aggregated = rowsByBrand.values().stream()
                .map(item -> WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                        .id(item.id)
                        .brandName(item.brandName)
                        .isTarget(item.target)
                        .visibilityScore(averageValues(item.visibilityScores))
                        .recommendRate(averageValues(item.recommendRates))
                        .mentionRate(averageValues(item.mentionRates))
                        .positiveRate(averageValues(item.positiveRates))
                        .top3Rate(averageValues(item.top3Rates))
                        .rankDelta(null)
                        .shareOfVoice(averageValues(item.shareOfVoiceValues))
                        .shareOfVoiceDelta(null)
                        .averagePosition(averageValues(item.averagePositions))
                        .averagePositionDelta(null)
                        .mentionCount(item.mentionCount)
                        .build())
                .sorted(buildCompetitorRankComparator())
                .toList();
        List<WorkbenchBrandPerformanceView.CompetitorRowView> ranked = new ArrayList<>();
        for (int index = 0; index < aggregated.size(); index++) {
            WorkbenchBrandPerformanceView.CompetitorRowView item = aggregated.get(index);
            ranked.add(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                    .id(item.getId())
                    .brandName(item.getBrandName())
                    .rank(index + 1)
                    .isTarget(item.getIsTarget())
                    .visibilityScore(item.getVisibilityScore())
                    .recommendRate(item.getRecommendRate())
                    .mentionRate(item.getMentionRate())
                    .positiveRate(item.getPositiveRate())
                    .top3Rate(item.getTop3Rate())
                    .rankDelta(null)
                    .shareOfVoice(item.getShareOfVoice())
                    .shareOfVoiceDelta(null)
                    .averagePosition(item.getAveragePosition())
                    .averagePositionDelta(null)
                    .mentionCount(item.getMentionCount())
                    .build());
        }
        List<WorkbenchBrandPerformanceView.CompetitorRowView> referenceRows =
                buildLatestRunDeltaReferenceRows(context, recentReports);
        if (referenceRows.isEmpty()) {
            return ranked;
        }
        Map<String, WorkbenchBrandPerformanceView.CompetitorRowView> referenceByBrand = new LinkedHashMap<>();
        for (WorkbenchBrandPerformanceView.CompetitorRowView row : referenceRows) {
            if (row != null && StringUtils.hasText(row.getBrandName())) {
                referenceByBrand.put(normalizeBrandKey(row.getBrandName()), row);
            }
        }
        return ranked.stream()
                .map(currentRow -> {
                    WorkbenchBrandPerformanceView.CompetitorRowView referenceRow =
                            referenceByBrand.get(normalizeBrandKey(currentRow.getBrandName()));
                    return WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                            .id(currentRow.getId())
                            .brandName(currentRow.getBrandName())
                            .rank(currentRow.getRank())
                            .isTarget(currentRow.getIsTarget())
                            .visibilityScore(currentRow.getVisibilityScore())
                            .recommendRate(currentRow.getRecommendRate())
                            .mentionRate(currentRow.getMentionRate())
                            .positiveRate(currentRow.getPositiveRate())
                            .top3Rate(currentRow.getTop3Rate())
                            .rankDelta(referenceRow == null || referenceRow.getRank() == null || currentRow.getRank() == null
                                    ? null
                                    : referenceRow.getRank() - currentRow.getRank())
                            .shareOfVoice(currentRow.getShareOfVoice())
                            .shareOfVoiceDelta(subtract(
                                    currentRow.getShareOfVoice(),
                                    referenceRow == null ? null : referenceRow.getShareOfVoice()
                            ))
                            .averagePosition(currentRow.getAveragePosition())
                            .averagePositionDelta(subtract(
                                    currentRow.getAveragePosition(),
                                    referenceRow == null ? null : referenceRow.getAveragePosition()
                            ))
                            .mentionCount(currentRow.getMentionCount())
                            .build();
                })
                .toList();
    }

    private List<DiagnosisReportRecord> buildKnowledgeComparisonReports(KnowledgeWorkbenchSelection knowledgeSelection,
                                                                        WorkbenchAnalysisContextView analysisContext) {
        if (knowledgeSelection == null || knowledgeSelection.selection() == null) {
            return List.of();
        }
        List<DiagnosisReportRecord> comparisonReports = knowledgeSelection.selection().previousRuns().stream()
                .map(this::synthesizeReportFromKnowledgeRun)
                .filter(Objects::nonNull)
                .toList();
        return filterReportsByPlatforms(comparisonReports, analysisContext);
    }

    private List<WorkbenchBrandPerformanceView.CompetitorRowView> buildKnowledgeWindowCompetitorRows(
            BrandSpaceContext context,
            List<DiagnosisReportRecord> currentReports,
            List<DiagnosisReportRecord> comparisonReports) {
        List<WorkbenchBrandPerformanceView.CompetitorRowView> currentRows =
                buildWindowCompetitorRows(context, currentReports);
        if (currentRows.isEmpty()) {
            return currentRows;
        }
        if (comparisonReports == null || comparisonReports.isEmpty()) {
            return currentRows;
        }
        List<WorkbenchBrandPerformanceView.CompetitorRowView> referenceRows =
                buildWindowCompetitorRows(context, comparisonReports);
        if (referenceRows.isEmpty()) {
            return currentRows;
        }
        Map<String, WorkbenchBrandPerformanceView.CompetitorRowView> referenceByBrand = new LinkedHashMap<>();
        for (WorkbenchBrandPerformanceView.CompetitorRowView row : referenceRows) {
            if (row != null && StringUtils.hasText(row.getBrandName())) {
                referenceByBrand.put(normalizeBrandKey(row.getBrandName()), row);
            }
        }
        return currentRows.stream()
                .map(currentRow -> {
                    WorkbenchBrandPerformanceView.CompetitorRowView referenceRow =
                            referenceByBrand.get(normalizeBrandKey(currentRow.getBrandName()));
                    return WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                            .id(currentRow.getId())
                            .brandName(currentRow.getBrandName())
                            .rank(currentRow.getRank())
                            .isTarget(currentRow.getIsTarget())
                            .visibilityScore(currentRow.getVisibilityScore())
                            .recommendRate(currentRow.getRecommendRate())
                            .mentionRate(currentRow.getMentionRate())
                            .positiveRate(currentRow.getPositiveRate())
                            .top3Rate(currentRow.getTop3Rate())
                            .rankDelta(referenceRow == null || referenceRow.getRank() == null || currentRow.getRank() == null
                                    ? null
                                    : referenceRow.getRank() - currentRow.getRank())
                            .shareOfVoice(currentRow.getShareOfVoice())
                            .shareOfVoiceDelta(subtract(
                                    currentRow.getShareOfVoice(),
                                    referenceRow == null ? null : referenceRow.getShareOfVoice()
                            ))
                            .averagePosition(currentRow.getAveragePosition())
                            .averagePositionDelta(subtract(
                                    currentRow.getAveragePosition(),
                                    referenceRow == null ? null : referenceRow.getAveragePosition()
                            ))
                            .mentionCount(currentRow.getMentionCount())
                            .build();
                })
                .toList();
    }

    private List<WorkbenchBrandPerformanceView.CompetitorRowView> buildLatestRunDeltaReferenceRows(
            BrandSpaceContext context,
            List<DiagnosisReportRecord> currentReports) {
        if (currentReports == null || currentReports.size() < 2) {
            return List.of();
        }
        DiagnosisReportRecord previousReport = currentReports.get(1);
        return buildCompetitorRows(
                context,
                previousReport,
                null,
                toReportResponse(previousReport),
                null,
                loadAveragePositionByBrand(previousReport.getDiagnosisTaskId()),
                Map.of(),
                loadTop3RateByBrand(previousReport.getDiagnosisTaskId(), previousReport.getSampleSize()),
                Map.of()
        );
    }

    private List<WorkbenchBrandPerformanceView.CompetitorRowView> buildCompetitorRows(BrandSpaceContext context,
                                                                                      DiagnosisReportRecord currentReport,
                                                                                      DiagnosisReportRecord previousReport,
                                                                                      ReportResponse currentProjection,
                                                                                      ReportResponse previousProjection,
                                                                                      Map<String, Double> currentAveragePositionByBrand,
                                                                                      Map<String, Double> previousAveragePositionByBrand,
                                                                                      Map<String, Double> currentTop3RateByBrand,
                                                                                      Map<String, Double> previousTop3RateByBrand) {
        if (currentReport == null) {
            return List.of();
        }

        Map<String, Map<String, Object>> previousCompetitorsByBrand = readCompetitorRankingByBrand(previousProjection);
        Double currentTargetTop3Rate = resolveTargetTop3Rate(context, currentReport, currentTop3RateByBrand);
        List<WorkbenchBrandPerformanceView.CompetitorRowView> rows = new ArrayList<>();
        Set<String> existingBrandKeys = new LinkedHashSet<>();
        rows.add(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                .id(context.scope().getId())
                .brandName(resolveBrandName(context))
                .isTarget(true)
                .visibilityScore(resolveVisibilityScore(currentReport, currentTargetTop3Rate))
                .recommendRate(resolveRecommendRate(currentReport))
                .mentionRate(resolveMentionRate(currentReport))
                .positiveRate(resolvePositiveRate(currentReport))
                .top3Rate(currentTargetTop3Rate)
                .shareOfVoice(resolveMentionRate(currentReport))
                .shareOfVoiceDelta(subtract(resolveMentionRate(currentReport), resolveMentionRate(previousReport)))
                .averagePosition(currentAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context))))
                .averagePositionDelta(subtract(
                        currentAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context))),
                        previousAveragePositionByBrand.get(normalizeBrandKey(resolveBrandName(context)))
                ))
                .mentionCount(resolveMentionCount(currentReport))
                .build());
        existingBrandKeys.add(normalizeBrandKey(resolveBrandName(context)));

        List<Map<String, Object>> competitorRanking = currentProjection == null || currentProjection.getCompetitorRanking() == null
                ? List.of()
                : currentProjection.getCompetitorRanking();
        for (Map<String, Object> item : competitorRanking) {
            String brandName = normalizeText(asText(item.get("brand")));
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            String brandKey = normalizeBrandKey(brandName);
            if (!existingBrandKeys.add(brandKey)) {
                continue;
            }
            Map<String, Object> previousItem = previousCompetitorsByBrand.get(normalizeBrandKey(brandName));
            Double shareOfVoice = firstNonNull(
                    readPercent(item.get("shareOfVoice"), true),
                    readPercent(item.get("mentionRate"), true)
            );
            Double previousShareOfVoice = firstNonNull(
                    readPercent(previousItem == null ? null : previousItem.get("shareOfVoice"), true),
                    readPercent(previousItem == null ? null : previousItem.get("mentionRate"), true)
            );
            Double averagePosition = firstNonNull(
                    readNumber(item.get("averagePosition")),
                    currentAveragePositionByBrand.get(normalizeBrandKey(brandName))
            );
            Double previousAveragePosition = firstNonNull(
                    readNumber(previousItem == null ? null : previousItem.get("averagePosition")),
                    previousAveragePositionByBrand.get(normalizeBrandKey(brandName))
            );
            Double top3Rate = firstNonNull(
                    currentTop3RateByBrand.get(normalizeBrandKey(brandName)),
                    readPercent(item.get("top3Rate"), true)
            );
            rows.add(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                    .id("competitor_" + normalizeBrandKey(brandName))
                    .brandName(brandName)
                    .isTarget(false)
                    .visibilityScore(readPercent(item.get("visibilityScore"), true))
                    .recommendRate(readPercent(item.get("recommendRate"), true))
                    .mentionRate(readPercent(item.get("mentionRate"), true))
                    .positiveRate(readPercent(item.get("positiveRate"), true))
                    .top3Rate(top3Rate)
                    .shareOfVoice(shareOfVoice)
                    .shareOfVoiceDelta(subtract(shareOfVoice, previousShareOfVoice))
                    .averagePosition(averagePosition)
                    .averagePositionDelta(subtract(averagePosition, previousAveragePosition))
                    .mentionCount(readInteger(item.get("mentionCount")))
                    .build());
        }
        for (String competitorName : loadTaskSnapshotCompetitorNames(currentReport.getDiagnosisTaskId())) {
            String brandKey = normalizeBrandKey(competitorName);
            if (!StringUtils.hasText(brandKey) || !existingBrandKeys.add(brandKey)) {
                continue;
            }
            rows.add(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                    .id("competitor_" + brandKey)
                    .brandName(competitorName)
                    .isTarget(false)
                    .build());
        }

        Map<String, Integer> previousRankByBrand = buildPreviousRankMap(
                context,
                previousReport,
                previousProjection,
                previousAveragePositionByBrand,
                previousTop3RateByBrand
        );
        List<WorkbenchBrandPerformanceView.CompetitorRowView> sorted = rows.stream()
                .sorted(buildCompetitorRankComparator())
                .toList();
        List<WorkbenchBrandPerformanceView.CompetitorRowView> ranked = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            WorkbenchBrandPerformanceView.CompetitorRowView item = sorted.get(index);
            Integer currentRank = index + 1;
            Integer previousRank = previousRankByBrand.get(normalizeBrandKey(item.getBrandName()));
            ranked.add(WorkbenchBrandPerformanceView.CompetitorRowView.builder()
                    .id(item.getId())
                    .brandName(item.getBrandName())
                    .rank(currentRank)
                    .isTarget(item.getIsTarget())
                    .visibilityScore(item.getVisibilityScore())
                    .recommendRate(item.getRecommendRate())
                    .mentionRate(item.getMentionRate())
                    .positiveRate(item.getPositiveRate())
                    .top3Rate(item.getTop3Rate())
                    .rankDelta(previousRank == null ? null : previousRank - currentRank)
                    .shareOfVoice(item.getShareOfVoice())
                    .shareOfVoiceDelta(item.getShareOfVoiceDelta())
                    .averagePosition(item.getAveragePosition())
                    .averagePositionDelta(item.getAveragePositionDelta())
                    .mentionCount(item.getMentionCount())
                    .build());
        }
        return ranked;
    }

    private Map<String, Integer> buildPreviousRankMap(BrandSpaceContext context,
                                                      DiagnosisReportRecord previousReport,
                                                      ReportResponse previousProjection,
                                                      Map<String, Double> previousAveragePositionByBrand,
                                                      Map<String, Double> previousTop3RateByBrand) {
        if (previousReport == null) {
            return Map.of();
        }
        List<WorkbenchBrandPerformanceView.CompetitorRowView> previousRows = buildCompetitorRows(
                context,
                previousReport,
                null,
                previousProjection,
                null,
                previousAveragePositionByBrand,
                Map.of(),
                previousTop3RateByBrand,
                Map.of()
        );
        LinkedHashMap<String, Integer> previousRankByBrand = new LinkedHashMap<>();
        for (WorkbenchBrandPerformanceView.CompetitorRowView row : previousRows) {
            previousRankByBrand.put(normalizeBrandKey(row.getBrandName()), row.getRank());
        }
        return previousRankByBrand;
    }

    private Comparator<WorkbenchBrandPerformanceView.CompetitorRowView> buildCompetitorRankComparator() {
        return Comparator.comparing(
                        WorkbenchBrandPerformanceView.CompetitorRowView::getVisibilityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        WorkbenchBrandPerformanceView.CompetitorRowView::getRecommendRate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        WorkbenchBrandPerformanceView.CompetitorRowView::getShareOfVoice,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        WorkbenchBrandPerformanceView.CompetitorRowView::getMentionRate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        WorkbenchBrandPerformanceView.CompetitorRowView::getBrandName,
                        Comparator.nullsLast(String::compareTo)
                );
    }

    private Map<String, Map<String, Object>> readCompetitorRankingByBrand(ReportResponse report) {
        if (report == null || report.getCompetitorRanking() == null || report.getCompetitorRanking().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : report.getCompetitorRanking()) {
            String brandName = normalizeText(asText(item.get("brand")));
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            result.put(normalizeBrandKey(brandName), item);
        }
        return result;
    }

    private List<String> loadTaskSnapshotCompetitorNames(String diagnosisTaskId) {
        if (!StringUtils.hasText(diagnosisTaskId)) {
            return List.of();
        }
        List<DiagnosisTaskCompetitor> taskCompetitors = diagnosisTaskCompetitorMapper.selectList(
                new LambdaQueryWrapper<DiagnosisTaskCompetitor>()
                        .eq(DiagnosisTaskCompetitor::getDiagnosisTaskId, diagnosisTaskId.trim())
                        .orderByAsc(DiagnosisTaskCompetitor::getSeqNo)
                        .orderByAsc(DiagnosisTaskCompetitor::getCreatedAt)
                        .orderByAsc(DiagnosisTaskCompetitor::getId)
        );
        LinkedHashSet<String> competitorNames = new LinkedHashSet<>();
        if (taskCompetitors != null) {
            for (DiagnosisTaskCompetitor item : taskCompetitors) {
                String competitorName = normalizeText(item == null ? null : item.getCompetitorNameSnapshot());
                if (StringUtils.hasText(competitorName)) {
                    competitorNames.add(competitorName);
                }
            }
        }
        if (!competitorNames.isEmpty()) {
            return List.copyOf(competitorNames);
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisTaskId.trim());
        if (task == null || task.getTaskOrderJson() == null || task.getTaskOrderJson().isEmpty()) {
            return List.of();
        }
        for (String item : readStringList(task.getTaskOrderJson().get("competitors"))) {
            String competitorName = normalizeText(item);
            if (StringUtils.hasText(competitorName)) {
                competitorNames.add(competitorName);
            }
        }
        return competitorNames.isEmpty() ? List.of() : List.copyOf(competitorNames);
    }

    private Map<String, Double> loadAveragePositionByBrand(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        List<ProbeBrandMentionRecord> mentions = loadBrandMentions(runId).stream()
                .filter(mention -> Boolean.TRUE.equals(mention.getMentionedFlag()))
                .filter(mention -> mention.getPositionIndex() != null)
                .toList();
        if (mentions == null || mentions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, PositionAccumulator> accumulatorByBrand = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : mentions) {
            String brandName = normalizeText(mention.getBrandName());
            if (!StringUtils.hasText(brandName) || mention.getPositionIndex() == null) {
                continue;
            }
            accumulatorByBrand.computeIfAbsent(normalizeBrandKey(brandName), ignored -> new PositionAccumulator())
                    .add(mention.getPositionIndex());
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, PositionAccumulator> entry : accumulatorByBrand.entrySet()) {
            result.put(entry.getKey(), entry.getValue().average());
        }
        return result;
    }

    private Map<String, Double> loadTop3RateByBrand(String runId, Integer sampleSize) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        int denominator = resolveTop3Denominator(runId, sampleSize);
        if (denominator <= 0) {
            return Map.of();
        }
        List<ProbeBrandMentionRecord> mentions = loadBrandMentions(runId).stream()
                .filter(mention -> Boolean.TRUE.equals(mention.getMentionedFlag()))
                .filter(mention -> mention.getPositionIndex() != null && mention.getPositionIndex() <= 3)
                .toList();
        if (mentions == null || mentions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Set<String>> probesByBrand = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : mentions) {
            String brandName = normalizeText(mention.getBrandName());
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            String probeKey = firstText(mention.getProbeId(), mention.getDispatchAttemptId(), mention.getId());
            if (!StringUtils.hasText(probeKey)) {
                continue;
            }
            probesByBrand.computeIfAbsent(normalizeBrandKey(brandName), ignored -> new LinkedHashSet<>())
                    .add(probeKey);
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : probesByBrand.entrySet()) {
            result.put(entry.getKey(), round2(entry.getValue().size() * 100.0d / denominator));
        }
        return result;
    }

    private Map<String, CompetitorMetricSnapshot> loadCompetitorMetricsByBrand(String runId, Integer sampleSize) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        int denominator = resolveTop3Denominator(runId, sampleSize);
        if (denominator <= 0) {
            return Map.of();
        }
        List<ProbeBrandMentionRecord> mentions = loadBrandMentions(runId).stream()
                .filter(mention -> Boolean.TRUE.equals(mention.getMentionedFlag()))
                .toList();
        if (mentions == null || mentions.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, CompetitorMetricAccumulator> accumulatorByBrand = new LinkedHashMap<>();
        for (ProbeBrandMentionRecord mention : mentions) {
            String brandName = normalizeText(mention.getBrandName());
            if (!StringUtils.hasText(brandName)) {
                continue;
            }
            String probeKey = firstText(mention.getProbeId(), mention.getDispatchAttemptId(), mention.getId());
            if (!StringUtils.hasText(probeKey)) {
                continue;
            }
            accumulatorByBrand.computeIfAbsent(normalizeBrandKey(brandName), ignored -> new CompetitorMetricAccumulator())
                    .add(probeKey, Boolean.TRUE.equals(mention.getRecommendedFlag()), mention.getPositionIndex());
        }
        LinkedHashMap<String, CompetitorMetricSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, CompetitorMetricAccumulator> entry : accumulatorByBrand.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot(denominator));
        }
        return result;
    }

    private int resolveTop3Denominator(String runId, Integer sampleSize) {
        if (sampleSize != null && sampleSize > 0) {
            return sampleSize;
        }
        if (!StringUtils.hasText(runId)) {
            return 0;
        }
        Long probeCount = (long) loadDiagnosisProbes(runId).size();
        if (probeCount == null || probeCount <= 0) {
            return 0;
        }
        return probeCount.intValue();
    }

    private Double resolveCompetitorVisibilityAverage(DiagnosisReportRecord report,
                                                      ReportResponse latestReport,
                                                      List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows) {
        if (report == null || !StringUtils.hasText(report.getDiagnosisTaskId())) {
            return null;
        }
        Map<String, CompetitorMetricSnapshot> metricByBrand = loadCompetitorMetricsByBrand(
                report.getDiagnosisTaskId(),
                report.getSampleSize()
        );
        List<String> competitorBrandKeys = new ArrayList<>();
        if (competitorRows != null && !competitorRows.isEmpty()) {
            for (WorkbenchBrandPerformanceView.CompetitorRowView row : competitorRows) {
                if (row == null || Boolean.TRUE.equals(row.getIsTarget())) {
                    continue;
                }
                String brandKey = normalizeBrandKey(row.getBrandName());
                if (StringUtils.hasText(brandKey)) {
                    competitorBrandKeys.add(brandKey);
                }
            }
        } else if (latestReport != null && latestReport.getCompetitorRanking() != null) {
            for (Map<String, Object> item : latestReport.getCompetitorRanking()) {
                String brandKey = normalizeBrandKey(normalizeText(asText(item.get("brand"))));
                if (StringUtils.hasText(brandKey)) {
                    competitorBrandKeys.add(brandKey);
                }
            }
        }
        if (competitorBrandKeys.isEmpty()) {
            competitorBrandKeys.addAll(metricByBrand.keySet());
        }
        if (competitorBrandKeys.isEmpty()) {
            return null;
        }

        double total = 0.0d;
        int count = 0;
        for (String brandKey : competitorBrandKeys.stream().distinct().toList()) {
            CompetitorMetricSnapshot snapshot = metricByBrand.getOrDefault(brandKey, CompetitorMetricSnapshot.zero());
            Double visibilityScore = resolveVisibilityScore(
                    snapshot.mentionRate(),
                    snapshot.recommendRate(),
                    snapshot.top3Rate()
            );
            if (visibilityScore == null) {
                continue;
            }
            total += visibilityScore;
            count++;
        }
        if (count <= 0) {
            return null;
        }
        return round2(total / count);
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildTopicSummaries(BrandSpaceContext context,
                                                                                     ReportResponse report) {
        if (context == null || context.scope() == null || !StringUtils.hasText(context.scope().getId())) {
            return List.of();
        }
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, context.scope().getId())
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, context.scope().getId())
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt));
        Map<String, List<BrandSpacePrompt>> promptsByTopicId = new LinkedHashMap<>();
        for (BrandSpacePrompt prompt : prompts) {
            promptsByTopicId.computeIfAbsent(prompt.getTopicId(), ignored -> new ArrayList<>()).add(prompt);
        }
        Map<String, Integer> signalCountByTopicName = readSignalCountByTopicName(report);
        int totalEnabledPromptCount = prompts.stream()
                .filter(prompt -> intEquals(prompt.getStatusCode(), 1))
                .toList()
                .size();
        int totalSignalCount = signalCountByTopicName.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();

        return topics.stream()
                .map(topic -> {
                    List<BrandSpacePrompt> topicPrompts = promptsByTopicId.getOrDefault(topic.getId(), List.of());
                    int enabledPromptCount = (int) topicPrompts.stream().filter(prompt -> intEquals(prompt.getStatusCode(), 1)).count();
                    Integer signalCount = signalCountByTopicName.get(normalizeBrandKey(topic.getName()));
                    return WorkbenchBrandPerformanceView.TopicSummaryView.builder()
                            .topicId(topic.getId())
                            .topicName(topic.getName())
                            .seqNo(topic.getSeqNo())
                            .enabled(intEquals(topic.getStatusCode(), 1))
                            .promptCount(topicPrompts.size())
                            .enabledPromptCount(enabledPromptCount)
                            .promptShare(totalEnabledPromptCount == 0 ? null : round2(enabledPromptCount * 100.0d / totalEnabledPromptCount))
                            .signalCount(signalCount)
                            .signalShare(signalCount == null || totalSignalCount == 0 ? null : round2(signalCount * 100.0d / totalSignalCount))
                            .sampleQuestions(topicPrompts.stream()
                                    .filter(prompt -> intEquals(prompt.getStatusCode(), 1))
                                    .map(BrandSpacePrompt::getPromptText)
                                    .filter(StringUtils::hasText)
                                    .limit(3)
                                    .toList())
                            .build();
                })
                .sorted(Comparator.comparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getSignalCount,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getEnabledPromptCount,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getSeqNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildWorkbenchTopicSummaries(
            BrandSpaceContext context,
            ReportResponse latestReport,
            List<DiagnosisReportRecord> recentReports,
            WorkbenchAnalysisContextView analysisContext,
            KnowledgeWorkbenchSelection knowledgeSelection) {
        if (context == null || context.scope() == null || !StringUtils.hasText(context.scope().getId())) {
            return List.of();
        }
        if (knowledgeSelection != null || shouldEnforceKnowledgeWorkbenchRead()) {
            if (hasExplicitProjectionFilter(analysisContext)) {
                return buildKnowledgeScopedTopicSummaries(context.scope().getId(), recentReports);
            }
            return buildKnowledgeWindowTopicSummaries(context.scope().getId());
        }
        return buildTopicSummaries(context, latestReport);
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildKnowledgeWindowTopicSummaries(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return List.of();
        }
        KnowledgeAssetProblemRepository repository = knowledgeAssetProblemRepositoryProvider.getIfAvailable();
        KnowledgeAssetMetricRepository metricRepository = knowledgeAssetMetricRepositoryProvider.getIfAvailable();
        if (repository == null || metricRepository == null) {
            return List.of();
        }
        try {
            List<KnowledgeAssetProblemRepository.TopicCatalogView> topics = repository.listActiveTopics(brandScopeId.trim());
            if (topics == null || topics.isEmpty()) {
                return List.of();
            }
            List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts = repository.listActivePrompts(brandScopeId.trim());
            List<KnowledgeAssetProblemRepository.ProblemWindowFactView> problemWindowFacts =
                    repository.listWindowFacts(brandScopeId.trim(), "last_14_days");
            List<KnowledgeAssetMetricRepository.MetricWindowFactView> metricWindowFacts =
                    metricRepository.listWindowFacts(brandScopeId.trim(), "last_14_days");
            Map<String, Integer> evidenceCountByTopicId = new LinkedHashMap<>();
            for (KnowledgeAssetProblemRepository.ProblemWindowFactView fact : problemWindowFacts) {
                if (!StringUtils.hasText(fact.topicCatalogId())) {
                    continue;
                }
                evidenceCountByTopicId.merge(fact.topicCatalogId(), safeInt(fact.evidenceCount()), Integer::sum);
            }
            Map<String, KnowledgeTopicMetricSnapshot> metricByTopicId =
                    indexKnowledgeWindowTopicMetrics(metricWindowFacts);
            return buildKnowledgeTopicSummaries(topics, prompts, evidenceCountByTopicId, metricByTopicId);
        } catch (Exception exception) {
            log.warn("Knowledge topic summary preferred read ignored. brandScopeId={}, reason={}",
                    brandScopeId, exception.getMessage(), exception);
            return List.of();
        }
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildKnowledgeScopedTopicSummaries(String brandScopeId,
                                                                                                     List<DiagnosisReportRecord> recentReports) {
        if (!StringUtils.hasText(brandScopeId) || recentReports == null || recentReports.isEmpty()) {
            return List.of();
        }
        KnowledgeAssetProblemRepository repository = knowledgeAssetProblemRepositoryProvider.getIfAvailable();
        KnowledgeAssetMetricRepository metricRepository = knowledgeAssetMetricRepositoryProvider.getIfAvailable();
        if (repository == null || metricRepository == null) {
            return List.of();
        }
        try {
            List<String> knowledgeRunIds = resolveKnowledgeRunIds(recentReports);
            if (knowledgeRunIds.isEmpty()) {
                return List.of();
            }
            List<KnowledgeAssetProblemRepository.TopicCatalogView> topics = repository.listActiveTopics(brandScopeId.trim());
            if (topics == null || topics.isEmpty()) {
                return List.of();
            }
            List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts = repository.listActivePrompts(brandScopeId.trim());
            List<KnowledgeAssetProblemRepository.ProblemSnapshotFactView> snapshotFacts =
                    repository.listSnapshotFactsByRunIds(knowledgeRunIds);
            List<KnowledgeAssetMetricRepository.MetricFactView> metricFacts =
                    metricRepository.listRunFactsByRunIds(knowledgeRunIds);
            Map<String, Integer> evidenceCountByTopicId = new LinkedHashMap<>();
            for (KnowledgeAssetProblemRepository.ProblemSnapshotFactView fact : snapshotFacts) {
                if (!StringUtils.hasText(fact.topicCatalogId())) {
                    continue;
                }
                evidenceCountByTopicId.merge(fact.topicCatalogId(), safeInt(fact.evidenceCount()), Integer::sum);
            }
            Map<String, KnowledgeTopicMetricSnapshot> metricByTopicId =
                    aggregateKnowledgeScopedTopicMetrics(metricFacts);
            return buildKnowledgeTopicSummaries(topics, prompts, evidenceCountByTopicId, metricByTopicId);
        } catch (Exception exception) {
            log.warn("Knowledge scoped topic summary preferred read ignored. brandScopeId={}, reason={}",
                    brandScopeId, exception.getMessage(), exception);
            return List.of();
        }
    }

    private List<WorkbenchBrandPerformanceView.TopicSummaryView> buildKnowledgeTopicSummaries(
            List<KnowledgeAssetProblemRepository.TopicCatalogView> topics,
            List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts,
            Map<String, Integer> evidenceCountByTopicId,
            Map<String, KnowledgeTopicMetricSnapshot> metricByTopicId) {
        if (topics == null || topics.isEmpty() || metricByTopicId == null || metricByTopicId.isEmpty()) {
            return List.of();
        }
        Map<String, List<KnowledgeAssetProblemRepository.PromptCatalogView>> promptsByTopicId = new LinkedHashMap<>();
        if (prompts != null) {
            for (KnowledgeAssetProblemRepository.PromptCatalogView prompt : prompts) {
                promptsByTopicId.computeIfAbsent(prompt.topicCatalogId(), ignored -> new ArrayList<>()).add(prompt);
            }
        }
        int totalPromptCount = prompts == null ? 0 : prompts.size();
        int totalSignalCount = evidenceCountByTopicId == null ? 0 : evidenceCountByTopicId.values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        List<WorkbenchBrandPerformanceView.TopicSummaryView> rows = new ArrayList<>();
        for (KnowledgeAssetProblemRepository.TopicCatalogView topic : topics) {
            if (topic == null || !StringUtils.hasText(topic.topicCatalogId())) {
                continue;
            }
            KnowledgeTopicMetricSnapshot metricSnapshot =
                    metricByTopicId.getOrDefault(topic.topicCatalogId(), KnowledgeTopicMetricSnapshot.empty());
            List<KnowledgeAssetProblemRepository.PromptCatalogView> topicPrompts =
                    promptsByTopicId.getOrDefault(topic.topicCatalogId(), List.of());
            int promptCount = topicPrompts.size();
            int signalCount = evidenceCountByTopicId == null ? 0 : safeInt(evidenceCountByTopicId.get(topic.topicCatalogId()));
            rows.add(WorkbenchBrandPerformanceView.TopicSummaryView.builder()
                    .topicId(topic.topicCatalogId())
                    .topicName(topic.topicName())
                    .seqNo(resolveKnowledgeTopicSeqNo(topicPrompts))
                    .enabled(topic.activeFlag())
                    .sampleSize(metricSnapshot.sampleSize())
                    .visibilityScore(metricSnapshot.visibilityScore())
                    .mentionRate(metricSnapshot.mentionRate())
                    .recommendRate(metricSnapshot.recommendRate())
                    .top3Rate(metricSnapshot.top3Rate())
                    .averagePosition(metricSnapshot.averagePosition())
                    .mentionCount(metricSnapshot.mentionCount())
                    .promptCount(promptCount)
                    .enabledPromptCount(promptCount)
                    .promptShare(totalPromptCount <= 0 ? null : round2(promptCount * 100.0d / totalPromptCount))
                    .signalCount(signalCount)
                    .signalShare(totalSignalCount <= 0 ? null : round2(signalCount * 100.0d / totalSignalCount))
                    .sampleQuestions(topicPrompts.stream()
                            .map(KnowledgeAssetProblemRepository.PromptCatalogView::promptText)
                            .filter(StringUtils::hasText)
                            .limit(3)
                            .toList())
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getSignalCount,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getSampleSize,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getEnabledPromptCount,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getSeqNo,
                        Comparator.nullsLast(Integer::compareTo)
                ).thenComparing(
                        WorkbenchBrandPerformanceView.TopicSummaryView::getTopicName,
                        Comparator.nullsLast(String::compareTo)
                ))
                .toList();
    }

    private Map<String, KnowledgeTopicMetricSnapshot> indexKnowledgeWindowTopicMetrics(
            List<KnowledgeAssetMetricRepository.MetricWindowFactView> windowFacts) {
        if (windowFacts == null || windowFacts.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, MutableKnowledgeTopicMetricSnapshot> metricByTopicId = new LinkedHashMap<>();
        for (KnowledgeAssetMetricRepository.MetricWindowFactView fact : windowFacts) {
            if (fact == null
                    || !Objects.equals(METRIC_TOPIC, normalizeText(fact.sourceObjectType()))
                    || !StringUtils.hasText(fact.sourceObjectKey())) {
                continue;
            }
            MutableKnowledgeTopicMetricSnapshot snapshot = metricByTopicId.computeIfAbsent(
                    fact.sourceObjectKey(),
                    ignored -> new MutableKnowledgeTopicMetricSnapshot()
            );
            snapshot.apply(fact.metricKey(), fact.numericValue());
        }
        return freezeKnowledgeTopicMetricSnapshots(metricByTopicId);
    }

    private Map<String, KnowledgeTopicMetricSnapshot> aggregateKnowledgeScopedTopicMetrics(
            List<KnowledgeAssetMetricRepository.MetricFactView> metricFacts) {
        if (metricFacts == null || metricFacts.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, MutableKnowledgeTopicMetricAccumulator> accumulators = new LinkedHashMap<>();
        for (KnowledgeAssetMetricRepository.MetricFactView fact : metricFacts) {
            if (fact == null
                    || !Objects.equals(METRIC_TOPIC, normalizeText(fact.sourceObjectType()))
                    || !StringUtils.hasText(fact.sourceObjectKey())) {
                continue;
            }
            MutableKnowledgeTopicMetricAccumulator accumulator = accumulators.computeIfAbsent(
                    fact.sourceObjectKey(),
                    ignored -> new MutableKnowledgeTopicMetricAccumulator()
            );
            accumulator.apply(fact.metricKey(), fact.metricValue(), readMetricWeightValue(fact.factPayloadJson()));
        }
        LinkedHashMap<String, KnowledgeTopicMetricSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableKnowledgeTopicMetricAccumulator> entry : accumulators.entrySet()) {
            KnowledgeTopicMetricSnapshot snapshot = entry.getValue().snapshot();
            if (snapshot != null) {
                result.put(entry.getKey(), snapshot);
            }
        }
        return result;
    }

    private Map<String, KnowledgeTopicMetricSnapshot> freezeKnowledgeTopicMetricSnapshots(
            Map<String, MutableKnowledgeTopicMetricSnapshot> metricByTopicId) {
        if (metricByTopicId == null || metricByTopicId.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, KnowledgeTopicMetricSnapshot> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableKnowledgeTopicMetricSnapshot> entry : metricByTopicId.entrySet()) {
            KnowledgeTopicMetricSnapshot snapshot = entry.getValue().snapshot();
            if (snapshot != null) {
                result.put(entry.getKey(), snapshot);
            }
        }
        return result;
    }

    private Double readMetricWeightValue(String factPayloadJson) {
        if (!StringUtils.hasText(factPayloadJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(factPayloadJson);
            return payload.path("weightValue").isNumber() ? payload.path("weightValue").asDouble() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> resolveKnowledgeRunIds(List<DiagnosisReportRecord> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        LinkedHashSet<String> runIds = new LinkedHashSet<>();
        for (DiagnosisReportRecord report : reports) {
            String reportRunId = normalizeText(report == null ? null : report.getDiagnosisTaskId());
            if (!StringUtils.hasText(reportRunId)) {
                continue;
            }
            if (isKnowledgeRunId(reportRunId)) {
                runIds.add(reportRunId);
                continue;
            }
            if (repository == null) {
                continue;
            }
            KnowledgeAssetRunRepository.RunDetailView runDetail = repository.findRunDetailBySourceTaskId(reportRunId);
            if (runDetail != null && StringUtils.hasText(runDetail.runId())) {
                runIds.add(runDetail.runId());
            }
        }
        return List.copyOf(runIds);
    }

    private Integer resolveKnowledgeTopicSeqNo(List<KnowledgeAssetProblemRepository.PromptCatalogView> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return null;
        }
        return prompts.stream()
                .map(KnowledgeAssetProblemRepository.PromptCatalogView::seqNo)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private Map<String, Integer> readSignalCountByTopicName(ReportResponse report) {
        if (report == null || report.getBrandPerception() == null || report.getBrandPerception().getTopTags() == null) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        report.getBrandPerception().getTopTags().stream()
                .filter(Objects::nonNull)
                .forEach(tag -> {
                    String name = normalizeText(tag.getTag());
                    if (StringUtils.hasText(name) && tag.getCount() != null) {
                        result.put(normalizeBrandKey(name), tag.getCount());
                    }
                });
        return result;
    }

    private List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> buildModelPerformanceRows(BrandSpaceContext context,
                                                                                                   List<DiagnosisReportRecord> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        Map<String, List<DiagnosisReportRecord>> reportsByModel = new LinkedHashMap<>();
        for (DiagnosisReportRecord report : reports) {
            String key = resolveModelGroupLabel(report);
            reportsByModel.computeIfAbsent(key, ignored -> new ArrayList<>()).add(report);
        }

        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> rows = new ArrayList<>();
        String primaryModelId = resolveModelGroupLabel(reports.getFirst());
        for (Map.Entry<String, List<DiagnosisReportRecord>> entry : reportsByModel.entrySet()) {
            List<DiagnosisReportRecord> modelReports = entry.getValue().stream()
                    .sorted(Comparator.comparing(DiagnosisReportRecord::getGeneratedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
            DiagnosisReportRecord current = resolveLatestModelMetricReport(modelReports);
            DiagnosisReportRecord previous = resolvePreviousModelMetricReport(modelReports, current);
            Double currentTop3Rate = resolveTop3Rate(context, current);
            Double previousTop3Rate = previous == null ? null : resolveTop3Rate(context, previous);
            rows.add(WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                    .id(entry.getKey())
                    .label(entry.getKey())
                    .primary(Objects.equals(entry.getKey(), primaryModelId))
                    .reportCount(modelReports.size())
                    .latestRunId(current == null ? null : current.getDiagnosisTaskId())
                    .latestReportId(current == null ? null : current.getId())
                    .visibilityScore(resolveVisibilityScore(current, currentTop3Rate))
                    .recommendRate(resolveRecommendRate(current))
                    .mentionRate(resolveMentionRate(current))
                    .top3Rate(currentTop3Rate)
                    .deltaVisibilityScore(subtract(
                            resolveVisibilityScore(current, currentTop3Rate),
                            previous == null ? null : resolveVisibilityScore(previous, previousTop3Rate)
                    ))
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(
                        WorkbenchBrandPerformanceView.ModelPerformanceItemView::getPrimary,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.ModelPerformanceItemView::getVisibilityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> buildWindowModelPerformanceRows(
            BrandSpaceContext context,
            List<DiagnosisReportRecord> reports) {
        if (reports == null || reports.isEmpty()) {
            return List.of();
        }
        Map<String, List<DiagnosisReportRecord>> reportsByModel = new LinkedHashMap<>();
        for (DiagnosisReportRecord report : reports) {
            String key = resolveModelGroupLabel(report);
            reportsByModel.computeIfAbsent(key, ignored -> new ArrayList<>()).add(report);
        }
        String primaryModelId = resolveModelGroupLabel(reports.getFirst());
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> rows = new ArrayList<>();
        for (Map.Entry<String, List<DiagnosisReportRecord>> entry : reportsByModel.entrySet()) {
            List<DiagnosisReportRecord> modelReports = entry.getValue().stream()
                    .sorted(Comparator.comparing(DiagnosisReportRecord::getGeneratedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                    .toList();
            DiagnosisReportRecord current = resolveLatestModelMetricReport(modelReports);
            DiagnosisReportRecord previous = resolvePreviousModelMetricReport(modelReports, current);
            rows.add(WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                    .id(entry.getKey())
                    .label(entry.getKey())
                    .primary(Objects.equals(entry.getKey(), primaryModelId))
                    .reportCount(modelReports.size())
                    .latestRunId(current == null ? null : current.getDiagnosisTaskId())
                    .latestReportId(current == null ? null : current.getId())
                    .visibilityScore(averageValues(modelReports.stream()
                            .map(report -> resolveVisibilityScore(report, resolveTop3Rate(context, report)))
                            .toList()))
                    .recommendRate(averageValues(modelReports.stream().map(this::resolveRecommendRate).toList()))
                    .mentionRate(averageValues(modelReports.stream().map(this::resolveMentionRate).toList()))
                    .top3Rate(averageValues(modelReports.stream().map(report -> resolveTop3Rate(context, report)).toList()))
                    .deltaVisibilityScore(subtract(
                            resolveVisibilityScore(current, resolveTop3Rate(context, current)),
                            previous == null ? null : resolveVisibilityScore(previous, resolveTop3Rate(context, previous))
                    ))
                    .build());
        }
        return rows.stream()
                .sorted(Comparator.comparing(
                        WorkbenchBrandPerformanceView.ModelPerformanceItemView::getPrimary,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        WorkbenchBrandPerformanceView.ModelPerformanceItemView::getVisibilityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private String resolveModelGroupLabel(DiagnosisReportRecord report) {
        if (report == null) {
            return "未记录平台";
        }
        return resolvePlatformLabel(report.getTargetModelProduct(), null);
    }

    private DiagnosisReportRecord resolveLatestModelMetricReport(List<DiagnosisReportRecord> reports) {
        if (reports == null || reports.isEmpty()) {
            return null;
        }
        for (DiagnosisReportRecord report : reports) {
            if (hasModelMetricValue(report)) {
                return report;
            }
        }
        return reports.getFirst();
    }

    private DiagnosisReportRecord resolvePreviousModelMetricReport(List<DiagnosisReportRecord> reports,
                                                                   DiagnosisReportRecord current) {
        if (reports == null || reports.isEmpty() || current == null) {
            return null;
        }
        boolean currentSkipped = false;
        for (DiagnosisReportRecord report : reports) {
            if (!currentSkipped && Objects.equals(report.getId(), current.getId())) {
                currentSkipped = true;
                continue;
            }
            if (hasModelMetricValue(report)) {
                return report;
            }
        }
        return null;
    }

    private boolean hasModelMetricValue(DiagnosisReportRecord report) {
        if (report == null) {
            return false;
        }
        return resolveVisibilityScore(report) != null
                || resolveRecommendRate(report) != null
                || resolveMentionRate(report) != null
                || resolveTop3RateFallback(report) != null;
    }

    private List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalizeModelPerformanceRows(
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
            WorkbenchBrandPerformanceView.SummaryMetricView summary) {
        return normalizeModelPerformanceRows(
                modelRows,
                summary == null ? null : summary.getVisibilityScore(),
                summary == null ? null : summary.getRecommendRate(),
                summary == null ? null : summary.getMentionRate(),
                summary == null ? null : summary.getTop3Rate(),
                null
        );
    }

    private List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalizeModelPerformanceRows(
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
            WorkbenchOverviewView.CurrentPerformanceView currentPerformance) {
        return normalizeModelPerformanceRows(
                modelRows,
                currentPerformance == null ? null : currentPerformance.getVisibilityScore(),
                currentPerformance == null ? null : currentPerformance.getRecommendRate(),
                currentPerformance == null ? null : currentPerformance.getMentionRate(),
                currentPerformance == null ? null : currentPerformance.getTop3Rate(),
                null
        );
    }

    private List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalizeModelPerformanceRows(
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
            Double visibilityScore,
            Double recommendRate,
            Double mentionRate,
            Double top3Rate,
            Double deltaVisibilityScore) {
        if (modelRows == null || modelRows.isEmpty()) {
            return List.of();
        }
        List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> normalizedRows = modelRows.stream()
                .filter(Objects::nonNull)
                .toList();
        if (normalizedRows.size() > 1) {
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> usableRows = normalizedRows.stream()
                    .filter(this::hasUsableModelPerformanceRow)
                    .toList();
            normalizedRows = usableRows.isEmpty() ? List.of() : usableRows;
        }
        if (normalizedRows.isEmpty()) {
            return List.of();
        }
        if (normalizedRows.size() != 1) {
            return normalizedRows;
        }
        WorkbenchBrandPerformanceView.ModelPerformanceItemView row = normalizedRows.getFirst();
        if (hasUsableModelPerformanceRow(row)
                || (visibilityScore == null && recommendRate == null && mentionRate == null && top3Rate == null)) {
            return normalizedRows;
        }
        return List.of(WorkbenchBrandPerformanceView.ModelPerformanceItemView.builder()
                .id(row.getId())
                .label(row.getLabel())
                .primary(row.getPrimary())
                .reportCount(row.getReportCount())
                .latestRunId(row.getLatestRunId())
                .latestReportId(row.getLatestReportId())
                .visibilityScore(visibilityScore)
                .recommendRate(recommendRate)
                .mentionRate(mentionRate)
                .top3Rate(top3Rate)
                .deltaVisibilityScore(firstNonNull(row.getDeltaVisibilityScore(), deltaVisibilityScore))
                .build());
    }

    private boolean hasUsableModelPerformanceRows(List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows) {
        return modelRows != null && modelRows.stream().anyMatch(this::hasUsableModelPerformanceRow);
    }

    private boolean hasUsableModelPerformanceRow(WorkbenchBrandPerformanceView.ModelPerformanceItemView row) {
        if (row == null) {
            return false;
        }
        return row.getVisibilityScore() != null
                || row.getRecommendRate() != null
                || row.getMentionRate() != null
                || row.getTop3Rate() != null;
    }

    private WorkbenchDataAvailabilityView buildAvailability(BrandSpaceContext context,
                                                            boolean hasCompletedReport,
                                                            List<String> missingFields,
                                                            WorkbenchAnalysisContextView analysisContext) {
        DiagnosisTask latestTask = context.latestTask();
        DiagnosisReportRecord latestReport = context.latestReport();
        boolean running = isGenerating(latestTask == null ? null : latestTask.getStatusCode());
        boolean explicitFilter = hasExplicitProjectionFilter(analysisContext);
        String status;
        String message;
        if (latestReport == null && explicitFilter) {
            status = "empty";
            message = running
                    ? "当前筛选窗口暂无可展示数据，最新一轮仍在生成或窗口外"
                    : "当前筛选窗口暂无可展示数据";
        } else if (latestReport != null && running) {
            status = "partial";
            message = "已有上一轮报告，当前新一轮生成中";
        } else if (latestReport != null) {
            status = missingFields == null || missingFields.isEmpty() ? "ready" : "partial";
            message = null;
        } else if (running) {
            status = "generating";
            message = "首轮数据生成中，请稍后回来查看";
        } else if (latestTask != null && intEquals(latestTask.getStatusCode(), TASK_STATUS_FAILED)) {
            status = "failed";
            message = "最近一轮运行失败，当前无可用报告";
        } else if (latestTask != null && intEquals(latestTask.getStatusCode(), TASK_STATUS_CANCELLED)) {
            status = "failed";
            message = "最近一轮运行已取消，当前无可用报告";
        } else {
            status = "empty";
            message = "品牌空间尚未生成可展示数据";
        }
        message = refineAvailabilityMessage(latestTask, hasCompletedReport, missingFields, message);
        String reasonCode = resolveAvailabilityReasonCode(latestTask, hasCompletedReport, missingFields, status);
        return WorkbenchDataAvailabilityView.builder()
                .status(status)
                .message(message)
                .reasonCode(reasonCode)
                .hasCompletedReport(hasCompletedReport)
                .hasRunningRun(running)
                .latestRunId(latestTask == null ? null : latestTask.getId())
                .latestReportId(latestReport == null ? null : latestReport.getId())
                .missingFields(missingFields == null ? List.of() : List.copyOf(missingFields))
                .missingFieldDetails(buildMissingFieldDetails(missingFields))
                .build();
    }

    private Map<String, List<String>> buildMissingFieldDetails(List<String> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> details = new LinkedHashMap<>();
        for (String field : missingFields) {
            details.put(field, switch (defaultIfBlank(field, "")) {
                case "competitorPositiveRate" -> List.of("竞品情感结果还不够稳定，当前暂时无法展示正向率。");
                case "competitorShareOfVoice" -> List.of("竞品声量占比还在补齐，当前暂时无法稳定展示。");
                case "averagePosition" -> List.of("当前窗口只有提及/推荐信号，未记录回答中的出现位次，因此不能计算当前品牌平均位次。");
                case "competitorAveragePosition" -> List.of("当前窗口竞品只有提及/推荐信号，未记录回答中的出现位次，因此不能计算竞品平均位次。");
                case "competitorDelta" -> List.of("竞品对比字段缺失，当前只能展示窗口累计值，无法展示相邻窗口变化。");
                case "modelPerformance" -> List.of("模型表现结果还在补齐，当前暂时无法稳定展示。");
                case "domains", "urls", "categories", "sourceCitations" -> List.of("引用来源证据仍在补齐，当前还不足以完整展开来源结构。");
                case "promptCoverage", "probeTopicMapping", "evidencePromptMapping" -> List.of("问题分析结果仍在补齐，问题、回答和证据暂未完全对应。");
                default -> List.of("当前模块缺少字段：" + field);
            });
        }
        return details;
    }

    private String resolveAvailabilityReasonCode(DiagnosisTask latestTask,
                                                 boolean hasCompletedReport,
                                                 List<String> missingFields,
                                                 String status) {
        if (!hasCompletedReport && "empty".equals(status)) {
            return "filtered_window_empty";
        }
        if (!hasCompletedReport || missingFields == null || missingFields.isEmpty()) {
            return switch (status) {
                case "ready" -> "ready";
                case "generating" -> "first_run_generating";
                case "failed" -> "latest_run_failed";
                case "empty" -> "brand_space_empty";
                default -> "runtime_partial";
            };
        }
        if ("partial".equals(status) && Boolean.TRUE.equals(isGenerating(latestTask == null ? null : latestTask.getStatusCode()))) {
            return "previous_report_while_running";
        }
        if (missingFields.stream().allMatch(this::isCitationRelatedMissingField)) {
            if (Boolean.FALSE.equals(latestTask == null ? null : latestTask.getRealtimeSearchEnabled())) {
                return "citation_search_disabled";
            }
            if (safeInt(latestTask == null ? null : latestTask.getSearchRequestedCount()) <= 0) {
                return "citation_search_not_requested";
            }
            if (safeInt(latestTask == null ? null : latestTask.getSearchSatisfiedCount()) <= 0) {
                return "citation_no_hits";
            }
            return "citation_projection_empty";
        }
        return "partial_contract_gap";
    }

    private WorkbenchDataAvailabilityView refineProblemAnalysisAvailability(WorkbenchDataAvailabilityView availability,
                                                                            int rawAnswerCount) {
        if (availability == null || availability.getMissingFields() == null || availability.getMissingFields().isEmpty()) {
            return availability;
        }
        if ("empty".equals(availability.getStatus())
                || "filtered_window_empty".equals(defaultIfBlank(availability.getReasonCode(), ""))) {
            return availability;
        }
        List<String> missingFields = availability.getMissingFields();
        boolean hasStructureGap = missingFields.contains("topics")
                || missingFields.contains("prompts")
                || missingFields.contains("problemClusters");
        if (rawAnswerCount > 0 && hasStructureGap) {
            availability.setStatus("raw_only");
            availability.setReasonCode("problem_analysis_raw_only");
            availability.setMessage("真实问答已接通，但结构化问题分析仍在补齐");
            return availability;
        }
        boolean hasProblemMappingGap = missingFields.contains("promptCoverage")
                || missingFields.contains("probeTopicMapping")
                || missingFields.contains("evidencePromptMapping");
        if (hasProblemMappingGap) {
            availability.setReasonCode("problem_analysis_contract_gap");
            return availability;
        }
        if (missingFields.contains("evidenceRows")) {
            availability.setReasonCode(rawAnswerCount > 0 ? "problem_analysis_no_evidence" : "problem_analysis_no_answers");
        }
        return availability;
    }

    private boolean isProblemAnalysisFilterEmpty(ProblemAnalysisFrame frame) {
        if (frame == null) {
            return true;
        }
        return frame.topicRows().isEmpty()
                && frame.promptRows().isEmpty()
                && frame.problemClusters().isEmpty()
                && frame.probeRows().isEmpty()
                && frame.attemptRows().isEmpty()
                && frame.evidenceRows().isEmpty();
    }

    private WorkbenchDataAvailabilityView buildFilteredWindowEmptyAvailability(BrandSpaceContext context) {
        return WorkbenchDataAvailabilityView.builder()
                .status("empty")
                .message("当前筛选窗口暂无可展示数据")
                .reasonCode("filtered_window_empty")
                .hasCompletedReport(context != null && context.latestReport() != null)
                .hasRunningRun(isGenerating(context == null || context.latestTask() == null ? null : context.latestTask().getStatusCode()))
                .latestRunId(context == null || context.latestTask() == null ? null : context.latestTask().getId())
                .latestReportId(context == null || context.latestReport() == null ? null : context.latestReport().getId())
                .missingFields(List.of())
                .missingFieldDetails(Map.of())
                .build();
    }

    private String refineAvailabilityMessage(DiagnosisTask latestTask,
                                             boolean hasCompletedReport,
                                             List<String> missingFields,
                                             String defaultMessage) {
        if (!hasCompletedReport || missingFields == null || missingFields.isEmpty()) {
            return defaultMessage;
        }
        if (!missingFields.stream().allMatch(this::isCitationRelatedMissingField)) {
            return defaultMessage;
        }
        if (Boolean.FALSE.equals(latestTask == null ? null : latestTask.getRealtimeSearchEnabled())) {
            return "最近一轮未开启联网搜索，当前无可展示引用证据";
        }
        if (safeInt(latestTask == null ? null : latestTask.getSearchRequestedCount()) <= 0) {
            return "最近一轮未实际触发联网搜索，当前无可展示引用证据";
        }
        if (safeInt(latestTask == null ? null : latestTask.getSearchSatisfiedCount()) <= 0) {
            return "最近一轮联网搜索未命中有效证据，当前无可展示引用证据";
        }
        return "最近一轮未产出可展示引用证据";
    }

    private boolean isCitationRelatedMissingField(String field) {
        return Objects.equals("sourceCitations", field)
                || Objects.equals("domains", field)
                || Objects.equals("urls", field)
                || Objects.equals("categories", field);
    }

    private List<String> collectOverviewMissingFields(WorkbenchOverviewView.CurrentPerformanceView currentPerformance,
                                                      List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
                                                      WorkbenchCitationSourcesView citationSourcesView,
                                                      List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
                                                      List<WorkbenchOverviewView.PriorityItemView> priorities) {
        List<String> missing = new ArrayList<>();
        if (currentPerformance == null) {
            missing.add("currentPerformance");
        }
        if (competitorRows == null || competitorRows.isEmpty()) {
            missing.add("competitorRank");
        }
        if (citationSourcesView == null || citationSourcesView.getDomainRows() == null || citationSourcesView.getDomainRows().isEmpty()) {
            missing.add("sourceCitations");
        }
        if (!hasUsableModelPerformanceRows(modelRows)) {
            missing.add("modelPerformance");
        }
        if (priorities == null || priorities.isEmpty()) {
            missing.add("priorities");
        }
        return missing;
    }

    private WorkbenchDataAvailabilityView reconcileOverviewAvailabilityAfterPreferredRead(
            WorkbenchDataAvailabilityView availability,
            WorkbenchOverviewView.CurrentPerformanceView currentPerformance,
            List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
            WorkbenchCitationSourcesView citationSourcesView,
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows) {
        if (availability == null || availability.getMissingFields() == null || availability.getMissingFields().isEmpty()) {
            return availability;
        }
        List<String> missingFields = new ArrayList<>(availability.getMissingFields());
        if (currentPerformance != null) {
            missingFields.remove("currentPerformance");
        }
        if (competitorRows != null && !competitorRows.isEmpty()) {
            missingFields.remove("competitorRank");
        }
        if (citationSourcesView != null
                && citationSourcesView.getDomainRows() != null
                && !citationSourcesView.getDomainRows().isEmpty()) {
            missingFields.remove("sourceCitations");
        }
        if (hasUsableModelPerformanceRows(modelRows)) {
            missingFields.remove("modelPerformance");
        }
        if (missingFields.size() == availability.getMissingFields().size()) {
            return availability;
        }
        availability.setMissingFields(List.copyOf(missingFields));
        availability.setMissingFieldDetails(buildMissingFieldDetails(missingFields));
        return promoteAvailabilityWhenResolved(availability, missingFields);
    }

    private WorkbenchDataAvailabilityView reconcileBrandPerformanceAvailabilityAfterPreferredRead(
            WorkbenchDataAvailabilityView availability,
            WorkbenchBrandPerformanceView.SummaryMetricView summary,
            List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
            List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
            List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints,
            List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        if (availability == null || availability.getMissingFields() == null || availability.getMissingFields().isEmpty()) {
            return availability;
        }
        List<String> missingFields = new ArrayList<>(availability.getMissingFields());
        if (summary != null) {
            missingFields.remove("summary");
            if (summary.getShareOfVoice() != null) {
                missingFields.remove("shareOfVoice");
            }
            if (summary.getAveragePosition() != null) {
                missingFields.remove("averagePosition");
            }
        }
        if (competitorRows != null && !competitorRows.isEmpty()) {
            missingFields.remove("competitors");
            if (competitorRows.stream().anyMatch(item -> item != null && item.getAveragePosition() != null)) {
                missingFields.remove("competitorAveragePosition");
            }
            if (competitorRows.stream()
                    .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsTarget()))
                    .anyMatch(item -> item.getShareOfVoice() != null)) {
                missingFields.remove("competitorShareOfVoice");
            }
            if (competitorRows.stream().anyMatch(item -> item != null
                    && (item.getRankDelta() != null
                    || item.getShareOfVoiceDelta() != null
                    || item.getAveragePositionDelta() != null))) {
                missingFields.remove("competitorDelta");
            }
            if (competitorRows.stream()
                    .filter(item -> item != null && !Boolean.TRUE.equals(item.getIsTarget()))
                    .anyMatch(item -> item.getPositiveRate() != null)) {
                missingFields.remove("competitorPositiveRate");
            }
        }
        if (hasUsableModelPerformanceRows(modelRows)) {
            missingFields.remove("models");
            missingFields.remove("modelPerformance");
        }
        if (trendPoints != null && trendPoints.size() >= 2) {
            missingFields.remove("trend");
            missingFields.remove("trendPoints");
        }
        if (topicSummaries != null && !topicSummaries.isEmpty()) {
            missingFields.remove("topicSummaries");
        }
        if (missingFields.size() == availability.getMissingFields().size()) {
            return availability;
        }
        availability.setMissingFields(List.copyOf(missingFields));
        availability.setMissingFieldDetails(buildMissingFieldDetails(missingFields));
        return promoteAvailabilityWhenResolved(availability, missingFields);
    }

    private WorkbenchDataAvailabilityView reconcileCitationSourcesAvailabilityAfterPreferredRead(
            WorkbenchDataAvailabilityView availability,
            WorkbenchCitationSourcesView.SourceSummaryView summary,
            List<WorkbenchCitationSourcesView.CategorySliceView> categorySlices,
            List<WorkbenchCitationSourcesView.DomainRowView> domainRows,
            List<WorkbenchCitationSourcesView.UrlRowView> urlRows) {
        if (availability == null || availability.getMissingFields() == null || availability.getMissingFields().isEmpty()) {
            return availability;
        }
        List<String> missingFields = new ArrayList<>(availability.getMissingFields());
        if (summary != null && safeInt(summary.getTotalCitations()) > 0) {
            missingFields.remove("sourceCitations");
        }
        if (categorySlices != null && !categorySlices.isEmpty()) {
            missingFields.remove("categories");
        }
        if (domainRows != null && !domainRows.isEmpty()) {
            missingFields.remove("domains");
            missingFields.remove("sourceCitations");
        }
        if (urlRows != null && !urlRows.isEmpty()) {
            missingFields.remove("urls");
        }
        if (missingFields.size() == availability.getMissingFields().size()) {
            return availability;
        }
        availability.setMissingFields(List.copyOf(missingFields));
        availability.setMissingFieldDetails(buildMissingFieldDetails(missingFields));
        return promoteAvailabilityWhenResolved(availability, missingFields);
    }

    private WorkbenchDataAvailabilityView reconcileProblemAnalysisAvailabilityAfterPreferredRead(
            WorkbenchDataAvailabilityView availability,
            WorkbenchProblemAnalysisView.SummaryView summary,
            List<WorkbenchProblemAnalysisView.TopicRowView> topicRows,
            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
            List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters,
            List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
            List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows,
            List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows) {
        if (availability == null) {
            return availability;
        }
        int rawAnswerCount = safeInt(summary == null ? null : summary.getRawAnswerCount());
        List<String> missingFields = collectProblemAnalysisMissingFields(
                topicRows,
                promptRows,
                problemClusters,
                probeRows,
                attemptRows,
                evidenceRows,
                rawAnswerCount,
                safeInt(summary == null ? null : summary.getLinkedPromptCount()),
                safeInt(summary == null ? null : summary.getLinkedProbeCount()),
                safeInt(summary == null ? null : summary.getLinkedEvidenceCount())
        );
        availability.setMissingFields(List.copyOf(missingFields));
        availability.setMissingFieldDetails(buildMissingFieldDetails(missingFields));
        if (!missingFields.isEmpty()) {
            if ("ready".equals(defaultIfBlank(availability.getStatus(), ""))) {
                availability.setStatus("partial");
            }
            if ("ready".equals(defaultIfBlank(availability.getReasonCode(), ""))) {
                availability.setReasonCode("partial_contract_gap");
            }
        }
        WorkbenchDataAvailabilityView reconciled = promoteAvailabilityWhenResolved(availability, missingFields);
        return refineProblemAnalysisAvailability(reconciled, rawAnswerCount);
    }

    private WorkbenchDataAvailabilityView promoteAvailabilityWhenResolved(WorkbenchDataAvailabilityView availability,
                                                                          List<String> missingFields) {
        if (availability == null || missingFields == null || !missingFields.isEmpty()) {
            return availability;
        }
        String reasonCode = defaultIfBlank(availability.getReasonCode(), "");
        if ("partial".equals(availability.getStatus())
                && isAvailabilityGapReason(reasonCode)) {
            availability.setStatus("ready");
            availability.setReasonCode("ready");
        }
        return availability;
    }

    private boolean isAvailabilityGapReason(String reasonCode) {
        return switch (defaultIfBlank(reasonCode, "")) {
            case "partial_contract_gap",
                 "citation_search_disabled",
                 "citation_search_not_requested",
                 "citation_no_hits",
                 "citation_projection_empty",
                 "problem_analysis_contract_gap" -> true;
            default -> false;
        };
    }

    private List<String> collectBrandPerformanceMissingFields(WorkbenchBrandPerformanceView.SummaryMetricView summary,
                                                              List<WorkbenchBrandPerformanceView.CompetitorRowView> competitorRows,
                                                              List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows,
                                                              List<WorkbenchBrandPerformanceView.TrendPointView> trendPoints,
                                                              List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        List<String> missing = new ArrayList<>();
        if (summary == null) {
            missing.add("summary");
        } else {
            if (summary.getShareOfVoice() == null) {
                missing.add("shareOfVoice");
            }
            if (summary.getAveragePosition() == null) {
                missing.add("averagePosition");
            }
        }
        if (competitorRows == null || competitorRows.isEmpty()) {
            missing.add("competitors");
        } else if (competitorRows.stream().noneMatch(item -> item.getAveragePosition() != null)) {
            missing.add("competitorAveragePosition");
        } else {
            if (competitorRows.stream().filter(item -> !Boolean.TRUE.equals(item.getIsTarget())).noneMatch(item -> item.getPositiveRate() != null)) {
                missing.add("competitorPositiveRate");
            }
            if (competitorRows.stream().filter(item -> !Boolean.TRUE.equals(item.getIsTarget())).noneMatch(item -> item.getShareOfVoice() != null)) {
                missing.add("competitorShareOfVoice");
            }
            if (competitorRows.stream().noneMatch(item -> item.getRankDelta() != null || item.getShareOfVoiceDelta() != null || item.getAveragePositionDelta() != null)) {
                missing.add("competitorDelta");
            }
        }
        if (!hasUsableModelPerformanceRows(modelRows)) {
            missing.add("modelPerformance");
        }
        if (trendPoints == null || trendPoints.size() < 2) {
            missing.add("trendPoints");
        }
        if (topicSummaries == null || topicSummaries.isEmpty()) {
            missing.add("topicSummaries");
        }
        return missing;
    }

    private List<String> collectProblemAnalysisMissingFields(List<WorkbenchProblemAnalysisView.TopicRowView> topicRows,
                                                             List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                                             List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters,
                                                             List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
                                                             List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows,
                                                             List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows,
                                                             int rawAnswerCount,
                                                             int linkedPromptCount,
                                                             int linkedProbeCount,
                                                             int linkedEvidenceCount) {
        List<String> missing = new ArrayList<>();
        if (topicRows == null || topicRows.isEmpty()) {
            missing.add("topics");
        }
        if (promptRows == null || promptRows.isEmpty()) {
            missing.add("prompts");
        }
        if (problemClusters == null || problemClusters.isEmpty()) {
            missing.add("problemClusters");
        }
        if (probeRows == null || probeRows.isEmpty()) {
            missing.add("probeRows");
        }
        if (evidenceRows == null || evidenceRows.isEmpty()) {
            missing.add("evidenceRows");
        }
        if (rawAnswerCount <= 0) {
            missing.add("rawAnswers");
        }
        if (promptRows != null && !promptRows.isEmpty() && linkedPromptCount <= 0) {
            missing.add("promptCoverage");
        }
        if (probeRows != null && !probeRows.isEmpty() && linkedProbeCount <= 0) {
            missing.add("probeTopicMapping");
        }
        if (evidenceRows != null && !evidenceRows.isEmpty() && linkedEvidenceCount <= 0) {
            missing.add("evidencePromptMapping");
        }
        if (attemptRows == null || attemptRows.isEmpty()) {
            missing.add("attemptRows");
        }
        return missing;
    }

    private boolean hasPromptOrTopicLink(WorkbenchProblemAnalysisView.ProbeRowView row) {
        return row != null && (StringUtils.hasText(row.getPromptId()) || StringUtils.hasText(row.getTopicId()));
    }

    private boolean hasPromptOrTopicLink(WorkbenchProblemAnalysisView.EvidenceRowView row) {
        return row != null && (StringUtils.hasText(row.getPromptId()) || StringUtils.hasText(row.getTopicId()));
    }

    private Map<String, ProbeDispatchAttempt> selectLatestAttemptByProbeId(List<ProbeDispatchAttempt> dispatchAttempts) {
        Map<String, ProbeDispatchAttempt> latestAttemptByProbeId = new LinkedHashMap<>();
        if (dispatchAttempts == null || dispatchAttempts.isEmpty()) {
            return latestAttemptByProbeId;
        }
        for (ProbeDispatchAttempt attempt : dispatchAttempts.stream()
                .sorted(Comparator
                        .comparing(ProbeDispatchAttempt::getSelectedFinal, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProbeDispatchAttempt::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProbeDispatchAttempt::getAttemptNo, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList()) {
            String probeId = normalizeText(attempt.getProbeId());
            if (!StringUtils.hasText(probeId) || latestAttemptByProbeId.containsKey(probeId)) {
                continue;
            }
            latestAttemptByProbeId.put(probeId, attempt);
        }
        return latestAttemptByProbeId;
    }

    private Map<String, List<ProbeBrandMentionRecord>> groupBrandMentionsByProbeId(List<ProbeBrandMentionRecord> mentions) {
        Map<String, List<ProbeBrandMentionRecord>> grouped = new LinkedHashMap<>();
        if (mentions == null || mentions.isEmpty()) {
            return grouped;
        }
        for (ProbeBrandMentionRecord mention : mentions) {
            String probeId = normalizeText(mention.getProbeId());
            if (!StringUtils.hasText(probeId)) {
                continue;
            }
            grouped.computeIfAbsent(probeId, ignored -> new ArrayList<>()).add(mention);
        }
        return grouped;
    }

    private Map<String, List<ProbeBrandMentionRecord>> groupBrandMentionsByAttemptId(List<ProbeBrandMentionRecord> mentions) {
        Map<String, List<ProbeBrandMentionRecord>> grouped = new LinkedHashMap<>();
        if (mentions == null || mentions.isEmpty()) {
            return grouped;
        }
        for (ProbeBrandMentionRecord mention : mentions) {
            String attemptId = normalizeText(mention.getDispatchAttemptId());
            if (!StringUtils.hasText(attemptId)) {
                continue;
            }
            grouped.computeIfAbsent(attemptId, ignored -> new ArrayList<>()).add(mention);
        }
        return grouped;
    }

    private List<ProbeBrandMentionRecord> filterTargetBrandMentions(List<ProbeBrandMentionRecord> mentions, String targetBrandKey) {
        if (mentions == null || mentions.isEmpty()) {
            return List.of();
        }
        return mentions.stream()
                .filter(item -> intEquals(item.getBrandRoleCode(), 0)
                        || Objects.equals(normalizeBrandKey(item.getBrandName()), targetBrandKey))
                .toList();
    }

    private PromptLookup resolvePromptLookup(String candidatePrompt,
                                            List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                            Map<String, PromptLookup> promptLookupByText) {
        String key = normalizeBrandKey(candidatePrompt);
        if (StringUtils.hasText(key)) {
            PromptLookup exact = promptLookupByText.get(key);
            if (exact != null) {
                return exact;
            }
        }
        if (promptRows == null || promptRows.isEmpty() || !StringUtils.hasText(candidatePrompt)) {
            return null;
        }
        String normalizedCandidate = key.replace("_", "");
        for (WorkbenchProblemAnalysisView.PromptRowView row : promptRows) {
            String rowKey = normalizeBrandKey(row.getPromptText());
            if (!StringUtils.hasText(rowKey)) {
                continue;
            }
            String normalizedRowKey = rowKey.replace("_", "");
            if (normalizedCandidate.contains(normalizedRowKey) || normalizedRowKey.contains(normalizedCandidate)) {
                return new PromptLookup(
                        row.getPromptId(),
                        row.getTopicId(),
                        row.getTopicName(),
                        row.getProblemSpaceBucket(),
                        row.getPromptText()
                );
            }
        }
        return null;
    }

    private PromptLookup resolvePromptLookupById(String promptId,
                                                 List<WorkbenchProblemAnalysisView.PromptRowView> promptRows) {
        String normalizedPromptId = normalizeText(promptId);
        if (!StringUtils.hasText(normalizedPromptId) || promptRows == null || promptRows.isEmpty()) {
            return null;
        }
        for (WorkbenchProblemAnalysisView.PromptRowView row : promptRows) {
            if (!Objects.equals(normalizedPromptId, normalizeText(row.getPromptId()))) {
                continue;
            }
            return new PromptLookup(
                    row.getPromptId(),
                    row.getTopicId(),
                    row.getTopicName(),
                    row.getProblemSpaceBucket(),
                    row.getPromptText()
            );
        }
        return null;
    }

    private PromptLookup resolvePromptLookupBySequence(RunPromptBridge runPromptBridge,
                                                       int actualProbeCount,
                                                       Integer seqNo) {
        if (runPromptBridge == null || seqNo == null || seqNo <= 0) {
            return null;
        }
        if (!runPromptBridge.supportsSequenceLinking(actualProbeCount)) {
            return null;
        }
        return runPromptBridge.lookupBySequence(seqNo);
    }

    private String readResponsePreview(ProbeDispatchAttempt attempt) {
        if (attempt == null || attempt.getResponseMetadataJson() == null) {
            return null;
        }
        return normalizeText(asText(attempt.getResponseMetadataJson().get("responsePreview")));
    }

    private Double resolveVisibilityScoreFromMentions(List<ProbeBrandMentionRecord> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return 0.0d;
        }
        return round2(mentions.stream()
                .mapToDouble(item -> item.getConfidenceScore() == null
                        ? (Boolean.TRUE.equals(item.getRecommendedFlag()) ? 0.9d : 0.72d)
                        : item.getConfidenceScore().doubleValue())
                .average()
                .orElse(0.0d) * 100.0d);
    }

    private Double resolveAveragePositionFromMentions(List<ProbeBrandMentionRecord> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return null;
        }
        return mentions.stream()
                .map(ProbeBrandMentionRecord::getPositionIndex)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream()
                .map(this::round2)
                .boxed()
                .findFirst()
                .orElse(null);
    }

    private Integer resolveSentimentScoreFromMentions(List<ProbeBrandMentionRecord> mentions) {
        if (mentions == null || mentions.isEmpty()) {
            return null;
        }
        double average = mentions.stream()
                .map(ProbeBrandMentionRecord::getSentimentCode)
                .filter(Objects::nonNull)
                .mapToInt(code -> switch (code) {
                    case 1 -> 82;
                    case 2 -> 24;
                    default -> 52;
                })
                .average()
                .orElse(52.0d);
        return (int) Math.round(average);
    }

    private String resolveProblemAnalysisProbeStatus(Integer probeStatusCode, Integer attemptStatusCode) {
        if (intEquals(attemptStatusCode, TASK_STATUS_FAILED)) {
            return "failed";
        }
        if (intEquals(attemptStatusCode, TASK_STATUS_RUNNING) || intEquals(attemptStatusCode, TASK_STATUS_PENDING) || intEquals(attemptStatusCode, TASK_STATUS_QUEUED)) {
            return "running";
        }
        if (intEquals(probeStatusCode, TASK_STATUS_FAILED)) {
            return "failed";
        }
        if (intEquals(probeStatusCode, TASK_STATUS_RUNNING) || intEquals(probeStatusCode, TASK_STATUS_PENDING) || intEquals(probeStatusCode, TASK_STATUS_QUEUED)) {
            return "running";
        }
        return "completed";
    }

    private String resolveAttemptStatus(Integer statusCode) {
        if (intEquals(statusCode, TASK_STATUS_FAILED) || intEquals(statusCode, TASK_STATUS_CANCELLED)) {
            return "failed";
        }
        if (intEquals(statusCode, TASK_STATUS_RUNNING) || intEquals(statusCode, TASK_STATUS_PENDING) || intEquals(statusCode, TASK_STATUS_QUEUED)) {
            return "running";
        }
        return "completed";
    }

    private String firstListText(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0d : value;
    }

    private List<WorkbenchOverviewView.CardView> buildOverviewCards(WorkbenchOverviewView.CurrentPerformanceView currentPerformance,
                                                                    WorkbenchOverviewView.TrendView trend,
                                                                    List<WorkbenchOverviewView.PriorityItemView> priorities,
                                                                    List<WorkbenchOverviewView.CompetitorSummaryView> competitorRows,
                                                                    WorkbenchCitationSourcesView citationSourcesView,
                                                                    List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> modelRows) {
        WorkbenchOverviewView.PriorityItemView topPriority = priorities == null || priorities.isEmpty() ? null : priorities.getFirst();
        WorkbenchOverviewView.CompetitorSummaryView selfRow = competitorRows == null
                ? null
                : competitorRows.stream().filter(item -> Boolean.TRUE.equals(item.getIsTarget())).findFirst().orElse(null);
        WorkbenchOverviewView.CompetitorSummaryView topPeer = competitorRows == null
                ? null
                : competitorRows.stream().filter(item -> !Boolean.TRUE.equals(item.getIsTarget())).findFirst().orElse(null);
        WorkbenchCitationSourcesView.DomainRowView topDomain = citationSourcesView == null
                || citationSourcesView.getDomainRows() == null
                || citationSourcesView.getDomainRows().isEmpty()
                ? null
                : citationSourcesView.getDomainRows().getFirst();
        WorkbenchBrandPerformanceView.ModelPerformanceItemView topModel = modelRows == null || modelRows.isEmpty()
                ? null
                : modelRows.getFirst();

        String competitorHeadline = buildCompetitorHeadline(selfRow, topPeer);
        return List.of(
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("current_performance")
                        .title("当前表现")
                        .primary(currentPerformance == null ? null : formatScore(currentPerformance.getVisibilityScore()))
                        .secondary(currentPerformance == null
                                ? null
                                : joinTexts(
                                "AI 推荐率 " + formatPercent(currentPerformance.getRecommendRate()),
                                "AI 提及率 " + formatPercent(currentPerformance.getMentionRate())
                        ))
                        .detailRouteKey("engine")
                        .build(),
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("trend")
                        .title("变化趋势")
                        .primary(buildTrendCardPrimary(trend))
                        .secondary(buildTrendCardSecondary(trend))
                        .detailRouteKey("engine")
                        .build(),
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("priorities")
                        .title("优先关注")
                        .primary(topPriority == null ? null : topPriority.getTitle())
                        .secondary(topPriority == null ? null : topPriority.getSummary())
                        .detailRouteKey("probe_analysis")
                        .build(),
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("competitor_rank")
                        .title("竞品排名")
                        .primary(selfRow == null || selfRow.getRank() == null ? "暂无排名" : "#" + selfRow.getRank())
                        .secondary(competitorHeadline)
                        .detailRouteKey("engine")
                        .build(),
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("source_citations")
                        .title("引用信源")
                        .primary(topDomain == null ? "暂无引用数据" : topDomain.getDomain())
                        .secondary(topDomain == null
                                ? "去引用来源查看"
                                : joinTexts("占比 " + formatPercent(topDomain.getShare()), "引用 " + topDomain.getUsed()))
                        .detailRouteKey("sources")
                        .build(),
                WorkbenchOverviewView.CardView.builder()
                        .cardKey("model_performance")
                        .title("模型表现")
                        .primary(topModel == null ? null : topModel.getLabel())
                        .secondary(topModel == null
                                ? null
                                : joinTexts(
                                topModel.getVisibilityScore() == null ? null : "可见度 " + formatScore(topModel.getVisibilityScore()),
                                topModel.getReportCount() == null ? null : "近 " + topModel.getReportCount() + " 份报告"))
                        .detailRouteKey("engine")
                        .build()
        );
    }

    private String buildTrendCardPrimary(WorkbenchOverviewView.TrendView trend) {
        if (trend == null || trend.getPoints() == null || trend.getPoints().size() < 2) {
            return "趋势样本不足";
        }
        if (trend.getDeltaVisibilityScore() == null) {
            return "可见度暂无对比";
        }
        return formatSigned(trend.getDeltaVisibilityScore(), false);
    }

    private String buildTrendCardSecondary(WorkbenchOverviewView.TrendView trend) {
        if (trend == null || trend.getPoints() == null || trend.getPoints().size() < 2) {
            return "等待下一轮对比";
        }
        return joinTexts(
                formatMetricDelta("推荐率", trend.getDeltaRecommendRate(), true),
                formatMetricDelta("提及率", trend.getDeltaMentionRate(), true)
        );
    }

    private String formatMetricDelta(String label, Double delta, boolean percent) {
        if (!StringUtils.hasText(label)) {
            return null;
        }
        String formatted = formatSigned(delta, percent);
        return formatted == null ? label + "暂无对比" : label + " " + formatted;
    }

    private String buildCompetitorHeadline(WorkbenchOverviewView.CompetitorSummaryView selfRow,
                                           WorkbenchOverviewView.CompetitorSummaryView topPeer) {
        if (selfRow == null || topPeer == null) {
            return "等待竞品对比";
        }
        Double selfScore = selfRow.getDisplayScore();
        Double peerScore = topPeer.getDisplayScore();
        if (selfScore == null || peerScore == null) {
            return "等待竞品对比";
        }
        String metricLabel = resolveOverviewRankingMetricLabel(selfRow.getRankingMetric());
        double gap = round2(selfScore - peerScore);
        if (gap >= 0.0d) {
            return metricLabel + "领先 " + topPeer.getBrandName() + " " + formatScore(Math.abs(gap));
        }
        return metricLabel + "落后 " + topPeer.getBrandName() + " " + formatScore(Math.abs(gap));
    }

    private List<WorkbenchOverviewView.CompetitorSummaryView> toOverviewCompetitorRows(List<WorkbenchBrandPerformanceView.CompetitorRowView> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        String rankingMetric = resolveOverviewSharedRankingMetric(source);
        List<WorkbenchOverviewView.CompetitorSummaryView> rows = source.stream()
                .map(item -> WorkbenchOverviewView.CompetitorSummaryView.builder()
                        .id(item.getId())
                        .brandName(item.getBrandName())
                        .isTarget(item.getIsTarget())
                        .visibilityScore(item.getVisibilityScore())
                        .displayScore(resolveOverviewDisplayScore(item, rankingMetric))
                        .rankingMetric(resolveOverviewRankingMetric(item, rankingMetric))
                        .recommendRate(item.getRecommendRate())
                        .mentionRate(item.getMentionRate())
                        .positiveRate(item.getPositiveRate())
                        .build())
                .toList();
        List<WorkbenchOverviewView.CompetitorSummaryView> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator
                .comparing(
                        WorkbenchOverviewView.CompetitorSummaryView::getDisplayScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        WorkbenchOverviewView.CompetitorSummaryView::getBrandName,
                        Comparator.nullsLast(String::compareTo)
                ));
        int currentRank = 0;
        Double previousScore = null;
        for (int index = 0; index < ordered.size(); index++) {
            Double displayScore = ordered.get(index).getDisplayScore();
            if (displayScore == null) {
                currentRank = index + 1;
            } else if (previousScore == null || Double.compare(displayScore, previousScore) != 0) {
                currentRank = index + 1;
            }
            ordered.get(index).setRank(currentRank);
            previousScore = displayScore;
        }
        return List.copyOf(ordered);
    }

    private Double resolveOverviewDisplayScore(WorkbenchBrandPerformanceView.CompetitorRowView row,
                                               String rankingMetric) {
        if (row == null) {
            return null;
        }
        if (StringUtils.hasText(rankingMetric)) {
            return resolveOverviewMetricValue(row, rankingMetric);
        }
        return firstNonNull(
                row.getVisibilityScore(),
                row.getRecommendRate(),
                row.getShareOfVoice(),
                row.getMentionRate(),
                row.getPositiveRate()
        );
    }

    private String resolveOverviewRankingMetric(WorkbenchBrandPerformanceView.CompetitorRowView row,
                                                String rankingMetric) {
        if (row == null) {
            return null;
        }
        if (StringUtils.hasText(rankingMetric) && resolveOverviewMetricValue(row, rankingMetric) != null) {
            return rankingMetric;
        }
        return firstAvailableOverviewMetric(row);
    }

    private String resolveOverviewRankingMetricLabel(String metric) {
        return switch (firstText(metric, "displayScore")) {
            case "visibilityScore" -> "可见度";
            case "recommendRate" -> "推荐率";
            case "shareOfVoice" -> "声量占比";
            case "mentionRate" -> "提及率";
            case "positiveRate" -> "正向率";
            default -> "表现";
        };
    }

    private String resolveOverviewSharedRankingMetric(List<WorkbenchBrandPerformanceView.CompetitorRowView> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String bestMetric = null;
        int bestCoverage = 0;
        for (String metric : OVERVIEW_COMPETITOR_METRIC_PRIORITY) {
            int coverage = 0;
            for (WorkbenchBrandPerformanceView.CompetitorRowView row : rows) {
                if (resolveOverviewMetricValue(row, metric) != null) {
                    coverage++;
                }
            }
            if (coverage >= 2 && coverage > bestCoverage) {
                bestCoverage = coverage;
                bestMetric = metric;
            }
        }
        return bestMetric;
    }

    private String firstAvailableOverviewMetric(WorkbenchBrandPerformanceView.CompetitorRowView row) {
        for (String metric : OVERVIEW_COMPETITOR_METRIC_PRIORITY) {
            if (resolveOverviewMetricValue(row, metric) != null) {
                return metric;
            }
        }
        return null;
    }

    private Double resolveOverviewMetricValue(WorkbenchBrandPerformanceView.CompetitorRowView row,
                                              String metric) {
        if (row == null || !StringUtils.hasText(metric)) {
            return null;
        }
        return switch (metric) {
            case "visibilityScore" -> row.getVisibilityScore();
            case "recommendRate" -> row.getRecommendRate();
            case "shareOfVoice" -> row.getShareOfVoice();
            case "mentionRate" -> row.getMentionRate();
            case "positiveRate" -> row.getPositiveRate();
            default -> null;
        };
    }

    private List<WorkbenchOverviewView.SourceSummaryView> toOverviewSourceRows(WorkbenchCitationSourcesView sourceView) {
        if (sourceView == null || sourceView.getDomainRows() == null || sourceView.getDomainRows().isEmpty()) {
            return List.of();
        }
        return sourceView.getDomainRows().stream()
                .limit(10)
                .map(item -> WorkbenchOverviewView.SourceSummaryView.builder()
                        .id(item.getId())
                        .label(item.getDomain())
                        .type(item.getType())
                        .share(item.getShare())
                        .shareLabel(formatPercent(item.getShare()))
                        .build())
                .toList();
    }

    private List<WorkbenchOverviewView.ModelSummaryView> toOverviewModelRows(List<WorkbenchBrandPerformanceView.ModelPerformanceItemView> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(item -> WorkbenchOverviewView.ModelSummaryView.builder()
                        .id(item.getId())
                        .label(item.getLabel())
                        .visibilityScore(item.getVisibilityScore())
                        .recommendRate(item.getRecommendRate())
                        .mentionRate(item.getMentionRate())
                        .deltaVisibilityScore(item.getDeltaVisibilityScore())
                        .reportCount(item.getReportCount())
                        .primary(item.getPrimary())
                        .build())
                .toList();
    }

    private List<ProbeEvidenceRecord> loadEvidence(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        KnowledgeAssetSourceRepository sourceRepository = knowledgeAssetSourceRepositoryProvider.getIfAvailable();
        if (sourceRepository != null && isKnowledgeRunId(runId)) {
            List<ProbeEvidenceRecord> knowledgeEvidence = sourceRepository.listCitationsByRunIds(List.of(runId.trim())).stream()
                    .map(this::toProbeEvidenceRecord)
                    .toList();
            if (!knowledgeEvidence.isEmpty()) {
                return knowledgeEvidence;
            }
        }
        return probeEvidenceRecordMapper.selectList(new LambdaQueryWrapper<ProbeEvidenceRecord>()
                .eq(ProbeEvidenceRecord::getDiagnosisTaskId, runId.trim())
                .orderByDesc(ProbeEvidenceRecord::getCreatedAt)
                .last("limit 500"));
    }

    private List<DiagnosisProbeRecord> loadDiagnosisProbes(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        KnowledgeAssetEvidenceRepository evidenceRepository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
        if (evidenceRepository != null && isKnowledgeRunId(runId)) {
            List<DiagnosisProbeRecord> knowledgeProbes = evidenceRepository.listProbesByRunId(runId.trim()).stream()
                    .map(this::toDiagnosisProbeRecord)
                    .toList();
            if (!knowledgeProbes.isEmpty()) {
                return knowledgeProbes;
            }
        }
        ensureProblemAnalysisTableAvailable("diagnosis_probes", runId);
        try {
            return diagnosisProbeRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisProbeRecord>()
                    .eq(DiagnosisProbeRecord::getDiagnosisTaskId, runId.trim())
                    .orderByAsc(DiagnosisProbeRecord::getSeqNo)
                    .orderByAsc(DiagnosisProbeRecord::getCreatedAt)
                    .last("limit 500"));
        } catch (BadSqlGrammarException ex) {
            throw missingProblemAnalysisSchema("diagnosis_probes", runId, ex);
        }
    }

    private List<ProbeDispatchAttempt> loadDispatchAttempts(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        KnowledgeAssetEvidenceRepository evidenceRepository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
        if (evidenceRepository != null && isKnowledgeRunId(runId)) {
            List<ProbeDispatchAttempt> knowledgeAttempts = evidenceRepository.listAttemptsByRunId(runId.trim()).stream()
                    .map(this::toProbeDispatchAttempt)
                    .toList();
            if (!knowledgeAttempts.isEmpty()) {
                return knowledgeAttempts;
            }
        }
        ensureProblemAnalysisTableAvailable("probe_dispatch_attempts", runId);
        try {
            return probeDispatchAttemptMapper.selectList(new LambdaQueryWrapper<ProbeDispatchAttempt>()
                    .eq(ProbeDispatchAttempt::getDiagnosisTaskId, runId.trim())
                    .orderByDesc(ProbeDispatchAttempt::getSelectedFinal)
                    .orderByDesc(ProbeDispatchAttempt::getCreatedAt)
                    .last("limit 500"));
        } catch (BadSqlGrammarException ex) {
            throw missingProblemAnalysisSchema("probe_dispatch_attempts", runId, ex);
        }
    }

    private void ensureProblemAnalysisTableAvailable(String tableName, String runId) {
        if (!StringUtils.hasText(tableName)) {
            return;
        }
        if (problemAnalysisTableExists(tableName.trim())) {
            return;
        }
        throw missingProblemAnalysisSchema(tableName, runId, null);
    }

    private boolean problemAnalysisTableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String candidate : List.of(tableName, tableName.toUpperCase(Locale.ROOT), tableName.toLowerCase(Locale.ROOT))) {
                try (ResultSet tables = metaData.getTables(catalog, null, candidate, new String[]{"TABLE"})) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException ex) {
            log.warn("problem-analysis schema probe failed: table={}, msg={}", tableName, ex.getMessage());
            return true;
        }
    }

    private BusinessException missingProblemAnalysisSchema(String tableName, String runId, BadSqlGrammarException ex) {
        if (ex == null) {
            log.error("problem-analysis schema drift: table={} unavailable, runId={}", tableName, runId);
        } else {
            log.error("problem-analysis schema drift: table={} unavailable, runId={}", tableName, runId, ex);
        }
        String displayName = switch (defaultIfBlank(tableName, "")) {
            case "diagnosis_probes" -> "问题分析探针记录";
            case "probe_dispatch_attempts" -> "问题分析调度记录";
            default -> "问题分析底层记录";
        };
        return new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                displayName + "暂未就绪，请先完成运行数据迁移后再重试"
        );
    }

    private List<ProbeBrandMentionRecord> loadBrandMentions(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        KnowledgeAssetEvidenceRepository evidenceRepository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
        if (evidenceRepository != null && isKnowledgeRunId(runId)) {
            List<ProbeBrandMentionRecord> knowledgeMentions = evidenceRepository.listBrandMentionsByRunId(runId.trim()).stream()
                    .map(this::toProbeBrandMentionRecord)
                    .toList();
            if (!knowledgeMentions.isEmpty()) {
                return knowledgeMentions;
            }
        }
        return probeBrandMentionRecordMapper.selectList(new LambdaQueryWrapper<ProbeBrandMentionRecord>()
                .eq(ProbeBrandMentionRecord::getDiagnosisTaskId, runId.trim())
                .orderByAsc(ProbeBrandMentionRecord::getCreatedAt)
                .last("limit 1000"));
    }

    private List<ProbeEvidenceRecord> loadCitationEvidence(DiagnosisReportRecord report) {
        if (report == null) {
            return List.of();
        }
        List<ProbeEvidenceRecord> persisted = loadEvidence(report.getDiagnosisTaskId());
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return loadProjectionEvidence(report);
    }

    private List<ProbeEvidenceRecord> loadProjectionEvidence(DiagnosisReportRecord report) {
        ReportResponse projection = toReportResponse(report);
        if (projection == null || projection.getEvidenceChain() == null || projection.getEvidenceChain().isEmpty()) {
            return List.of();
        }
        List<ProbeEvidenceRecord> result = new ArrayList<>();
        int chainIndex = 0;
        for (Map<String, Object> chain : projection.getEvidenceChain()) {
            chainIndex++;
            if (chain == null || chain.isEmpty()) {
                continue;
            }
            String probeId = firstText(asText(chain.get("probeId")), "projection_probe_" + chainIndex);
            Object rawEvidence = chain.get("evidence");
            if (!(rawEvidence instanceof List<?> evidenceItems) || evidenceItems.isEmpty()) {
                continue;
            }
            for (Object item : evidenceItems) {
                if (!(item instanceof Map<?, ?> evidenceMap) || evidenceMap.isEmpty()) {
                    continue;
                }
                String url = normalizeText(asText(evidenceMap.get("url")));
                String domain = firstText(normalizeText(asText(evidenceMap.get("domain"))), domainOf(url));
                if (!StringUtils.hasText(url) && !StringUtils.hasText(domain)) {
                    continue;
                }
                LocalDateTime retrievedAt = parseProjectionDateTime(asText(evidenceMap.get("retrievedAt")));
                result.add(ProbeEvidenceRecord.builder()
                        .diagnosisTaskId(report.getDiagnosisTaskId())
                        .probeId(probeId)
                        .sourceTypeCode(normalizeText(asText(evidenceMap.get("sourceType"))))
                        .provider(normalizeText(asText(evidenceMap.get("provider"))))
                        .domain(domain)
                        .url(url)
                        .title(normalizeText(asText(evidenceMap.get("title"))))
                        .snippet(normalizeText(asText(evidenceMap.get("snippet"))))
                        .searchQuery(normalizeText(asText(evidenceMap.get("query"))))
                        .retrievedAt(retrievedAt)
                        .createdAt(firstNonNull(retrievedAt, report.getGeneratedAt(), report.getCreatedAt()))
                        .build());
            }
        }
        return result;
    }

    private LocalDateTime parseProjectionDateTime(String raw) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private SourceAggregation buildSourceAggregation(BrandSpaceContext context,
                                                     List<ProbeEvidenceRecord> currentEvidence,
                                                     List<ProbeEvidenceRecord> previousEvidence) {
        Set<String> ownedDomains = resolveOwnedDomains(context);
        Map<String, EvidenceBucket> previousDomainBuckets = aggregateBySourceSite(
                aggregateByDomain(previousEvidence, ownedDomains)
        );
        Map<String, EvidenceBucket> previousUrlBuckets = aggregateByUrl(previousEvidence, ownedDomains);
        Map<String, EvidenceBucket> currentDomainBuckets = aggregateBySourceSite(
                aggregateByDomain(currentEvidence, ownedDomains)
        );
        Map<String, EvidenceBucket> currentUrlBuckets = aggregateByUrl(currentEvidence, ownedDomains);
        int currentTotal = currentEvidence == null ? 0 : currentEvidence.size();
        int previousDomainTotal = sumBucketCounts(previousDomainBuckets);
        int previousUrlTotal = sumBucketCounts(previousUrlBuckets);

        List<WorkbenchCitationSourcesView.DomainRowView> domainRows = currentDomainBuckets.entrySet().stream()
                .map(entry -> toDomainRow(entry.getKey(), entry.getValue(), currentTotal, previousDomainBuckets, previousDomainTotal))
                .sorted(Comparator.comparing(WorkbenchCitationSourcesView.DomainRowView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<WorkbenchCitationSourcesView.UrlRowView> urlRows = currentUrlBuckets.entrySet().stream()
                .map(entry -> toUrlRow(entry.getKey(), entry.getValue(), currentTotal, previousUrlBuckets, previousUrlTotal))
                .sorted(Comparator.comparing(WorkbenchCitationSourcesView.UrlRowView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(100)
                .toList();

        Map<String, Integer> categoryCounter = new LinkedHashMap<>();
        for (EvidenceBucket bucket : currentDomainBuckets.values()) {
            categoryCounter.merge(bucket.type(), bucket.count(), Integer::sum);
        }
        List<WorkbenchCitationSourcesView.CategorySliceView> categorySlices = categoryCounter.entrySet().stream()
                .map(entry -> WorkbenchCitationSourcesView.CategorySliceView.builder()
                        .key(entry.getKey())
                        .label(resolveSourceTypeLabel(entry.getKey()))
                        .share(round2(currentTotal == 0 ? 0.0d : entry.getValue() * 100.0d / currentTotal))
                        .used(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(WorkbenchCitationSourcesView.CategorySliceView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<WorkbenchCitationSourcesView.DomainDetailRowView> domainDetails = buildCitationDomainDetails(
                currentEvidence,
                ownedDomains,
                currentTotal,
                previousDomainBuckets,
                previousDomainTotal,
                previousUrlBuckets,
                previousUrlTotal
        );
        WorkbenchCitationSourcesView.OwnershipView ownership = buildCitationOwnership(currentTotal, domainDetails);
        int ownedCount = currentDomainBuckets.values().stream()
                .filter(bucket -> SOURCE_TYPE_OWNED.equals(bucket.type()))
                .mapToInt(EvidenceBucket::count)
                .sum();
        WorkbenchCitationSourcesView.DomainRowView topDomain = domainRows.isEmpty() ? null : domainRows.getFirst();
        List<String> missingFields = new ArrayList<>();
        if (domainRows.isEmpty()) {
            missingFields.add("domains");
        }
        if (urlRows.isEmpty()) {
            missingFields.add("urls");
        }
        if (categorySlices.isEmpty()) {
            missingFields.add("categories");
        }

        return new SourceAggregation(
                WorkbenchCitationSourcesView.SourceSummaryView.builder()
                        .totalCitations(currentTotal)
                        .ownedShare(round2(currentTotal == 0 ? 0.0d : ownedCount * 100.0d / currentTotal))
                        .thirdPartyShare(round2(currentTotal == 0 ? 0.0d : (currentTotal - ownedCount) * 100.0d / currentTotal))
                        .competitorShare(null)
                        .dominantLabel(topDomain == null ? null : topDomain.getDomain())
                        .dominantShare(topDomain == null ? null : topDomain.getShare())
                        .build(),
                categorySlices,
                domainRows,
                urlRows,
                ownership,
                domainDetails,
                missingFields
        );
    }

    private List<WorkbenchCitationSourcesView.TrendPointView> buildCitationTrendPoints(BrandSpaceContext context,
                                                                                       List<CitationEvidenceSnapshot> snapshots,
                                                                                       WorkbenchAnalysisContextView analysisContext) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<CitationEvidenceSnapshot> sorted = snapshots.stream()
                .filter(snapshot -> snapshot != null
                        && snapshot.report() != null
                        && snapshot.evidence() != null
                        && !snapshot.evidence().isEmpty())
                .sorted(Comparator.comparing(
                        snapshot -> snapshot.report().getGeneratedAt(),
                        Comparator.nullsLast(LocalDateTime::compareTo)
                ))
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }
        List<CitationEvidenceSnapshot> visible = sorted;
        List<WorkbenchCitationSourcesView.TrendPointView> points = new ArrayList<>();
        for (int index = 0; index < visible.size(); index++) {
            CitationEvidenceSnapshot current = visible.get(index);
            CitationEvidenceSnapshot previous = index == 0 ? null : visible.get(index - 1);
            SourceAggregation aggregation = buildSourceAggregation(
                    context,
                    current.evidence(),
                    previous == null ? List.of() : previous.evidence()
            );
            points.add(WorkbenchCitationSourcesView.TrendPointView.builder()
                    .sliceKey(current.report().getId())
                    .label(resolveTrendLabel(current.report().getGeneratedAt()))
                    .generatedAt(formatDateTime(current.report().getGeneratedAt()))
                    .totalCitations(aggregation.summary() == null ? 0 : aggregation.summary().getTotalCitations())
                    .ownedShare(aggregation.summary() == null ? null : aggregation.summary().getOwnedShare())
                    .thirdPartyShare(aggregation.summary() == null ? null : aggregation.summary().getThirdPartyShare())
                    .competitorShare(aggregation.summary() == null ? null : aggregation.summary().getCompetitorShare())
                    .isLatest(index == visible.size() - 1)
                    .topDomains(buildCitationTrendTopDomains(aggregation.domainDetails()))
                    .build());
        }
        return aggregateCitationTrendPointsByFrequency(points, analysisContext == null ? null : analysisContext.getFrequency());
    }

    private List<WorkbenchCitationSourcesView.TrendPointView> aggregateCitationTrendPointsByFrequency(
            List<WorkbenchCitationSourcesView.TrendPointView> points,
            String frequency) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        String normalizedFrequency = normalizeText(frequency);
        if (!"day".equals(normalizedFrequency) && !"week".equals(normalizedFrequency)) {
            return points;
        }
        LinkedHashMap<String, MutableCitationTrendBucket> buckets = new LinkedHashMap<>();
        for (WorkbenchCitationSourcesView.TrendPointView point : points) {
            LocalDateTime generatedAt = parseProjectionDateTime(point.getGeneratedAt());
            if (generatedAt == null) {
                generatedAt = parseProjectionDateTime(point.getLabel());
            }
            if (generatedAt == null) {
                buckets.computeIfAbsent(
                        firstText(point.getLabel(), point.getGeneratedAt(), "unknown"),
                        ignored -> new MutableCitationTrendBucket(
                                firstText(point.getLabel(), point.getGeneratedAt(), "unknown"),
                                point.getGeneratedAt()
                        )
                ).add(point);
                continue;
            }
            String label = resolveFrequencyBucketLabel(generatedAt, normalizedFrequency);
            String generatedAtLabel = resolveFrequencyBucketGeneratedAt(generatedAt, normalizedFrequency);
            buckets.computeIfAbsent(label, ignored -> new MutableCitationTrendBucket(label, generatedAtLabel)).add(point);
        }
        return buckets.values().stream()
                .map(MutableCitationTrendBucket::toPoint)
                .toList();
    }

    private List<WorkbenchCitationSourcesView.TrendDomainSliceView> buildCitationTrendTopDomains(
            List<WorkbenchCitationSourcesView.DomainDetailRowView> domainDetails) {
        if (domainDetails == null || domainDetails.isEmpty()) {
            return List.of();
        }
        return domainDetails.stream()
                .limit(3)
                .map(item -> WorkbenchCitationSourcesView.TrendDomainSliceView.builder()
                        .domain(item.getDomain())
                        .used(item.getUsed())
                        .share(item.getShare())
                        .type(item.getType())
                        .ownershipKey(item.getOwnershipKey())
                        .ownershipLabel(item.getOwnershipLabel())
                        .build())
                .toList();
    }

    private List<WorkbenchCitationSourcesView.DomainDetailRowView> buildCitationDomainDetails(
            List<ProbeEvidenceRecord> currentEvidence,
            Set<String> ownedDomains,
            int currentTotal,
            Map<String, EvidenceBucket> previousDomainBuckets,
            int previousDomainTotal,
            Map<String, EvidenceBucket> previousUrlBuckets,
            int previousUrlTotal) {
        if (currentEvidence == null || currentEvidence.isEmpty()) {
            return List.of();
        }
        Map<String, MutableCitationDomainDetail> grouped = new LinkedHashMap<>();
        for (ProbeEvidenceRecord item : currentEvidence) {
            if (item == null) {
                continue;
            }
            String rawDomain = firstText(normalizeText(item.getDomain()), domainOf(item.getUrl()));
            if (!StringUtils.hasText(rawDomain)) {
                continue;
            }
            SourceSiteGrouping grouping = resolveSourceSiteGrouping(rawDomain);
            String groupKey = grouping.key();
            String sourceType = resolveSourceType(item.getSourceTypeCode(), rawDomain, ownedDomains);
            String ownershipKey = resolveOwnershipKey(sourceType);
            MutableCitationDomainDetail detail = grouped.computeIfAbsent(groupKey, ignored -> new MutableCitationDomainDetail());
            detail.groupKey = groupKey;
            detail.domainId = "domain_" + normalizeBrandKey(groupKey);
            detail.domain = grouping.label();
            detail.type = chooseSourceType(detail.type, sourceType, detail.used, 1);
            detail.ownershipKey = chooseOwnershipKey(detail.ownershipKey, ownershipKey, detail.used, 1);
            detail.used++;
            detail.probeIds.add(normalizeText(item.getProbeId()));

            String url = normalizeText(item.getUrl());
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String canonicalUrlKey = firstText(normalizeCitationUrlKey(url), url);
            MutableCitationUrlDetail urlDetail = detail.urls.computeIfAbsent(canonicalUrlKey, ignored -> new MutableCitationUrlDetail());
            urlDetail.urlKey = canonicalUrlKey;
            urlDetail.urlId = "url_" + normalizeBrandKey(canonicalUrlKey);
            urlDetail.url = chooseRepresentativeCitationUrl(urlDetail.url, url);
            if (!StringUtils.hasText(urlDetail.title) && StringUtils.hasText(item.getTitle())) {
                urlDetail.title = normalizeText(item.getTitle());
            }
            urlDetail.used++;
        }

        List<WorkbenchCitationSourcesView.DomainDetailRowView> rows = new ArrayList<>();
        for (MutableCitationDomainDetail detail : grouped.values()) {
            EvidenceBucket previousDomain = previousDomainBuckets.get(detail.groupKey);
            double share = shareOf(detail.used, currentTotal);
            List<WorkbenchCitationSourcesView.UrlDetailRowView> urls = detail.urls.values().stream()
                    .map(urlDetail -> {
                        EvidenceBucket previousUrl = previousUrlBuckets.get(firstText(urlDetail.urlKey, urlDetail.url));
                        return WorkbenchCitationSourcesView.UrlDetailRowView.builder()
                                .urlId(urlDetail.urlId)
                                .url(urlDetail.url)
                                .title(urlDetail.title)
                                .used(urlDetail.used)
                                .share(round2(shareOf(urlDetail.used, currentTotal)))
                                .delta(computeShareDelta(urlDetail.used, currentTotal, previousUrl, previousUrlTotal))
                                .build();
                    })
                    .sorted(Comparator.comparing(WorkbenchCitationSourcesView.UrlDetailRowView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(6)
                    .toList();
            rows.add(WorkbenchCitationSourcesView.DomainDetailRowView.builder()
                    .domainId(detail.domainId)
                    .domain(detail.domain)
                    .type(detail.type)
                    .ownershipKey(detail.ownershipKey)
                    .ownershipLabel(resolveOwnershipLabel(detail.ownershipKey))
                    .used(detail.used)
                    .avgCitations(round2(averagePerProbe(detail.used, detail.probeIds)))
                    .share(round2(share))
                    .delta(computeShareDelta(detail.used, currentTotal, previousDomain, previousDomainTotal))
                    .urlCount(detail.urls.size())
                    .urls(urls)
                    .build());
        }
        rows.sort(Comparator.comparing(WorkbenchCitationSourcesView.DomainDetailRowView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private WorkbenchCitationSourcesView.OwnershipView buildCitationOwnership(
            int currentTotal,
            List<WorkbenchCitationSourcesView.DomainDetailRowView> domainDetails) {
        if (currentTotal <= 0 || domainDetails == null || domainDetails.isEmpty()) {
            return WorkbenchCitationSourcesView.OwnershipView.builder()
                    .summary(null)
                    .slices(List.of())
                    .build();
        }
        Map<String, List<WorkbenchCitationSourcesView.DomainDetailRowView>> grouped = new LinkedHashMap<>();
        for (WorkbenchCitationSourcesView.DomainDetailRowView row : domainDetails) {
            String key = normalizeText(row.getOwnershipKey());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<WorkbenchCitationSourcesView.OwnershipSliceView> slices = new ArrayList<>();
        for (Map.Entry<String, List<WorkbenchCitationSourcesView.DomainDetailRowView>> entry : grouped.entrySet()) {
            int used = entry.getValue().stream()
                    .map(WorkbenchCitationSourcesView.DomainDetailRowView::getUsed)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            int urlCount = entry.getValue().stream()
                    .map(WorkbenchCitationSourcesView.DomainDetailRowView::getUrlCount)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            List<WorkbenchCitationSourcesView.OwnershipDomainRowView> topDomains = entry.getValue().stream()
                    .sorted(Comparator.comparing(WorkbenchCitationSourcesView.DomainDetailRowView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(5)
                    .map(item -> WorkbenchCitationSourcesView.OwnershipDomainRowView.builder()
                            .domain(item.getDomain())
                            .used(item.getUsed())
                            .share(item.getShare())
                            .type(item.getType())
                            .build())
                    .toList();
            slices.add(WorkbenchCitationSourcesView.OwnershipSliceView.builder()
                    .key(entry.getKey())
                    .label(resolveOwnershipLabel(entry.getKey()))
                    .share(round2(shareOf(used, currentTotal)))
                    .used(used)
                    .domainCount(entry.getValue().size())
                    .urlCount(urlCount)
                    .topDomains(topDomains)
                    .build());
        }
        slices.sort(Comparator.comparing(WorkbenchCitationSourcesView.OwnershipSliceView::getUsed, Comparator.nullsLast(Comparator.reverseOrder())));
        Double ownedShare = ownershipShareForKey(slices, "owned");
        Double thirdPartyShare = ownershipShareForKey(slices, "third-party");
        double resolvedShare = slices.stream()
                .map(WorkbenchCitationSourcesView.OwnershipSliceView::getShare)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return WorkbenchCitationSourcesView.OwnershipView.builder()
                .summary(WorkbenchCitationSourcesView.OwnershipSummaryView.builder()
                        .ownedShare(ownedShare)
                        .thirdPartyShare(thirdPartyShare)
                        .competitorShare(null)
                        .otherShare(round2(Math.max(0.0d, 100.0d - resolvedShare)))
                        .build())
                .slices(slices)
                .build();
    }

    private Double ownershipShareForKey(List<WorkbenchCitationSourcesView.OwnershipSliceView> slices, String key) {
        return slices.stream()
                .filter(item -> Objects.equals(normalizeText(item.getKey()), normalizeText(key)))
                .map(WorkbenchCitationSourcesView.OwnershipSliceView::getShare)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0.0d);
    }

    private int sumBucketCounts(Map<String, EvidenceBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return 0;
        }
        return buckets.values().stream().mapToInt(EvidenceBucket::count).sum();
    }

    private double shareOf(int count, int total) {
        if (count <= 0 || total <= 0) {
            return 0.0d;
        }
        return count * 100.0d / total;
    }

    private Double computeShareDelta(int currentCount,
                                     int currentTotal,
                                     EvidenceBucket previous,
                                     int previousTotal) {
        if (previous == null || currentTotal <= 0 || previousTotal <= 0) {
            return null;
        }
        double currentShare = shareOf(currentCount, currentTotal);
        double previousShare = shareOf(previous.count(), previousTotal);
        return round2(currentShare - previousShare);
    }

    private double averagePerProbe(int count, Set<String> probeIds) {
        int probeCount = probeIds == null
                ? 0
                : (int) probeIds.stream().filter(StringUtils::hasText).count();
        if (probeCount <= 0) {
            return count;
        }
        return count * 1.0d / probeCount;
    }

    private String resolveOwnershipKey(String sourceType) {
        return SOURCE_TYPE_OWNED.equals(normalizeText(sourceType)) ? "owned" : "third-party";
    }

    private String chooseOwnershipKey(String currentKey, String candidateKey, int currentCount, int candidateCount) {
        if (!StringUtils.hasText(currentKey)) {
            return candidateKey;
        }
        if (!StringUtils.hasText(candidateKey) || Objects.equals(currentKey, candidateKey)) {
            return currentKey;
        }
        return candidateCount > currentCount ? candidateKey : currentKey;
    }

    private String resolveOwnershipLabel(String ownershipKey) {
        return switch (normalizeText(ownershipKey)) {
            case "owned" -> "自有资产";
            case "competitor" -> "竞品引用";
            case "other" -> "其他";
            default -> "第三方引用";
        };
    }

    private WorkbenchCitationSourcesView.DomainRowView toDomainRow(String domain,
                                                                   EvidenceBucket bucket,
                                                                   int total,
                                                                   Map<String, EvidenceBucket> previousBuckets,
                                                                   int previousTotal) {
        double share = shareOf(bucket.count(), total);
        EvidenceBucket previous = previousBuckets.get(domain);
        return WorkbenchCitationSourcesView.DomainRowView.builder()
                .id("domain_" + normalizeBrandKey(domain))
                .domain(domain)
                .type(bucket.type())
                .used(bucket.count())
                .avgCitations(round2(bucket.averagePerProbe()))
                .share(round2(share))
                .delta(computeShareDelta(bucket.count(), total, previous, previousTotal))
                .url(bucket.domain() == null ? null : "https://" + bucket.domain())
                .build();
    }

    private WorkbenchCitationSourcesView.UrlRowView toUrlRow(String url,
                                                             EvidenceBucket bucket,
                                                             int total,
                                                             Map<String, EvidenceBucket> previousBuckets,
                                                             int previousTotal) {
        double share = shareOf(bucket.count(), total);
        EvidenceBucket previous = previousBuckets.get(url);
        String displayUrl = firstText(bucket.url(), url);
        return WorkbenchCitationSourcesView.UrlRowView.builder()
                .id("url_" + normalizeBrandKey(url))
                .title(firstText(bucket.title(), resolveSyntheticCitationTitle(bucket.domain(), displayUrl)))
                .url(displayUrl)
                .domain(bucket.domain())
                .type(bucket.type())
                .used(bucket.count())
                .avgCitations(round2(bucket.averagePerProbe()))
                .share(round2(share))
                .delta(computeShareDelta(bucket.count(), total, previous, previousTotal))
                .build();
    }

    private String resolveSourceDisplayName(String domain) {
        String normalized = normalizeText(domain);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : SOURCE_DISPLAY_NAME_BY_SUFFIX.entrySet()) {
            String suffix = entry.getKey();
            if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                return entry.getValue();
            }
        }
        return normalized;
    }

    private Map<String, EvidenceBucket> aggregateBySourceSite(Map<String, EvidenceBucket> rawDomainBuckets) {
        if (rawDomainBuckets == null || rawDomainBuckets.isEmpty()) {
            return Map.of();
        }
        Map<String, MutableEvidenceBucket> buckets = new LinkedHashMap<>();
        for (Map.Entry<String, EvidenceBucket> entry : rawDomainBuckets.entrySet()) {
            String rawDomain = normalizeText(entry.getKey());
            EvidenceBucket bucket = entry.getValue();
            if (!StringUtils.hasText(rawDomain) || bucket == null) {
                continue;
            }
            SourceSiteGrouping grouping = resolveSourceSiteGrouping(rawDomain);
            MutableEvidenceBucket aggregated = buckets.computeIfAbsent(grouping.key(), ignored -> new MutableEvidenceBucket());
            aggregated.domain = grouping.label();
            if (!StringUtils.hasText(aggregated.representativeDomain)
                    || bucket.count() > aggregated.representativeDomainCount) {
                aggregated.representativeDomain = grouping.representativeDomain();
                aggregated.representativeDomainCount = bucket.count();
            }
            aggregated.type = chooseSourceType(aggregated.type, bucket.type(), aggregated.count, bucket.count());
            aggregated.count += bucket.count();
            aggregated.probeIds.addAll(bucket.probeIds());
        }
        return freezeGroupedBuckets(buckets);
    }

    private SourceSiteGrouping resolveSourceSiteGrouping(String domain) {
        String normalized = normalizeText(domain);
        if (!StringUtils.hasText(normalized)) {
            return new SourceSiteGrouping(normalized, normalized, normalized);
        }
        String displayName = resolveSourceDisplayName(normalized);
        if (!Objects.equals(displayName, normalized)) {
            return new SourceSiteGrouping(displayName, displayName, normalized);
        }
        return new SourceSiteGrouping(normalized, normalized, normalized);
    }

    private String chooseSourceType(String currentType, String candidateType, int currentCount, int candidateCount) {
        if (!StringUtils.hasText(currentType)) {
            return candidateType;
        }
        if (!StringUtils.hasText(candidateType) || Objects.equals(currentType, candidateType)) {
            return currentType;
        }
        return candidateCount > currentCount ? candidateType : currentType;
    }

    private Map<String, EvidenceBucket> aggregateByDomain(List<ProbeEvidenceRecord> evidence,
                                                          Set<String> ownedDomains) {
        Map<String, MutableEvidenceBucket> buckets = new LinkedHashMap<>();
        if (evidence != null) {
            for (ProbeEvidenceRecord item : evidence) {
                String domain = firstText(normalizeText(item.getDomain()), domainOf(item.getUrl()));
                if (!StringUtils.hasText(domain)) {
                    continue;
                }
                MutableEvidenceBucket bucket = buckets.computeIfAbsent(domain, ignored -> new MutableEvidenceBucket());
                bucket.count++;
                bucket.probeIds.add(normalizeText(item.getProbeId()));
                bucket.domain = domain;
                bucket.type = resolveSourceType(item.getSourceTypeCode(), domain, ownedDomains);
                bucket.title = firstText(bucket.title, normalizeText(item.getTitle()));
            }
        }
        return freezeBuckets(buckets);
    }

    private Map<String, EvidenceBucket> aggregateByUrl(List<ProbeEvidenceRecord> evidence,
                                                       Set<String> ownedDomains) {
        Map<String, MutableEvidenceBucket> buckets = new LinkedHashMap<>();
        if (evidence != null) {
            for (ProbeEvidenceRecord item : evidence) {
                String url = normalizeText(item.getUrl());
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                String canonicalUrlKey = firstText(normalizeCitationUrlKey(url), url);
                MutableEvidenceBucket bucket = buckets.computeIfAbsent(canonicalUrlKey, ignored -> new MutableEvidenceBucket());
                bucket.count++;
                bucket.probeIds.add(normalizeText(item.getProbeId()));
                bucket.domain = firstText(normalizeText(item.getDomain()), domainOf(url));
                bucket.type = resolveSourceType(item.getSourceTypeCode(), bucket.domain, ownedDomains);
                bucket.title = firstText(bucket.title, normalizeText(item.getTitle()));
                bucket.url = chooseRepresentativeCitationUrl(bucket.url, url);
            }
        }
        return freezeBuckets(buckets);
    }

    private Map<String, EvidenceBucket> freezeBuckets(Map<String, MutableEvidenceBucket> buckets) {
        Map<String, EvidenceBucket> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, MutableEvidenceBucket> entry : buckets.entrySet()) {
            MutableEvidenceBucket value = entry.getValue();
            frozen.put(entry.getKey(), new EvidenceBucket(
                    value.url,
                    value.domain,
                    value.title,
                    value.type == null ? SOURCE_TYPE_REFERENCE : value.type,
                    value.count,
                    value.probeIds
            ));
        }
        return frozen;
    }

    private Map<String, EvidenceBucket> freezeGroupedBuckets(Map<String, MutableEvidenceBucket> buckets) {
        Map<String, EvidenceBucket> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, MutableEvidenceBucket> entry : buckets.entrySet()) {
            MutableEvidenceBucket value = entry.getValue();
            frozen.put(entry.getKey(), new EvidenceBucket(
                    null,
                    value.representativeDomain,
                    value.title,
                    value.type == null ? SOURCE_TYPE_REFERENCE : value.type,
                    value.count,
                    value.probeIds
            ));
        }
        return frozen;
    }

    private String normalizeCitationUrlKey(String rawUrl) {
        String normalized = normalizeText(rawUrl);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String withoutFragment = normalized.replaceFirst("#.*$", "");
        String withoutProtocol = withoutFragment.replaceFirst("^https?://", "");
        int slashIndex = withoutProtocol.indexOf('/');
        String host = slashIndex >= 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
        String pathAndQuery = slashIndex >= 0 ? withoutProtocol.substring(slashIndex) : "/";
        String normalizedHost = normalizeCitationHost(host);
        if (!StringUtils.hasText(normalizedHost)) {
            return normalized;
        }
        return normalizedHost + normalizeCitationPathAndQuery(pathAndQuery);
    }

    private String normalizeCitationHost(String rawHost) {
        String normalized = normalizeText(rawHost);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String hostWithoutPort = normalized.replaceFirst(":\\d+$", "");
        String[] labels = hostWithoutPort.toLowerCase(Locale.ROOT).split("\\.");
        List<String> normalizedLabels = new ArrayList<>();
        for (String label : labels) {
            if (!StringUtils.hasText(label)) {
                continue;
            }
            if (labels.length > 2 && isMobileCitationHostLabel(label)) {
                continue;
            }
            normalizedLabels.add(label);
        }
        return normalizedLabels.isEmpty() ? hostWithoutPort : String.join(".", normalizedLabels);
    }

    private boolean isMobileCitationHostLabel(String label) {
        return "m".equals(label) || "mobile".equals(label) || "wap".equals(label);
    }

    private String normalizeCitationPathAndQuery(String rawPathAndQuery) {
        String normalized = normalizeText(rawPathAndQuery);
        if (!StringUtils.hasText(normalized)) {
            return "/";
        }
        String[] parts = normalized.split("\\?", 2);
        String path = parts[0];
        if (!StringUtils.hasText(path)) {
            path = "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
            return path;
        }
        return path + "?" + parts[1];
    }

    private String chooseRepresentativeCitationUrl(String currentUrl, String candidateUrl) {
        String current = normalizeText(currentUrl);
        String candidate = normalizeText(candidateUrl);
        if (!StringUtils.hasText(current)) {
            return candidate;
        }
        if (!StringUtils.hasText(candidate)) {
            return current;
        }
        boolean currentMobile = isMobileCitationUrl(current);
        boolean candidateMobile = isMobileCitationUrl(candidate);
        if (currentMobile != candidateMobile) {
            return candidateMobile ? current : candidate;
        }
        return current;
    }

    private boolean isMobileCitationUrl(String rawUrl) {
        String host = domainOf(rawUrl);
        if (!StringUtils.hasText(host)) {
            return false;
        }
        for (String label : host.toLowerCase(Locale.ROOT).split("\\.")) {
            if (isMobileCitationHostLabel(label)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveOwnedDomains(BrandSpaceContext context) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        Brand brand = context.brand();
        if (brand != null) {
            addDomain(domains, brand.getCanonicalDomain());
            addDomain(domains, brand.getOfficialWebsite());
            if (brand.getOfficialDomainsJson() != null) {
                brand.getOfficialDomainsJson().forEach(item -> addDomain(domains, item));
            }
        }
        addDomain(domains, context.scope().getPrimaryDomain());
        addDomain(domains, resolvePrimaryDomain(context));
        addDomain(domains, resolveOfficialWebsite(context));
        addDomain(domains, context.latestTask() == null ? null : context.latestTask().getOfficialWebsiteSnapshot());
        return domains;
    }

    private void addDomain(Set<String> domains, String raw) {
        String domain = domainOf(raw);
        if (!StringUtils.hasText(domain)) {
            domain = normalizeText(raw);
        }
        if (StringUtils.hasText(domain)) {
            String lower = domain.toLowerCase(Locale.ROOT);
            domains.add(lower);
            if (lower.startsWith("www.") && lower.length() > 4) {
                domains.add(lower.substring(4));
            }
        }
    }

    private String resolveSourceType(String sourceTypeCode, String domain, Set<String> ownedDomains) {
        String normalizedDomain = normalizeText(domain);
        if (isOwnedDomain(normalizedDomain, ownedDomains)) {
            return SOURCE_TYPE_OWNED;
        }
        String sourceTypeByDomain = resolveSourceTypeFromDomain(normalizedDomain);
        if (StringUtils.hasText(sourceTypeByDomain)) {
            return sourceTypeByDomain;
        }
        String normalized = normalizeText(sourceTypeCode);
        if (!StringUtils.hasText(normalized)) {
            return SOURCE_TYPE_REFERENCE;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "official", "brand", "corporate", "corp", "官网", "品牌", "企业")) {
            return SOURCE_TYPE_CORPORATE;
        }
        if (containsAny(lower, "media", "news", "editorial", "article", "媒体", "资讯", "新闻")) {
            return SOURCE_TYPE_EDITORIAL;
        }
        if (containsAny(lower, "social", "forum", "ugc", "community", "评价", "口碑", "问答", "帖子", "讨论")) {
            return SOURCE_TYPE_UGC;
        }
        if (containsAny(lower, "reference", "wiki", "doc", "百科", "资料")) {
            return SOURCE_TYPE_REFERENCE;
        }
        return SOURCE_TYPE_REFERENCE;
    }

    private boolean isOwnedDomain(String domain, Set<String> ownedDomains) {
        String normalizedDomain = normalizeText(domain);
        if (!StringUtils.hasText(normalizedDomain) || ownedDomains == null || ownedDomains.isEmpty()) {
            return false;
        }
        String lower = normalizedDomain.toLowerCase(Locale.ROOT);
        String rootLikeDomain = stripLeadingWww(lower);
        for (String ownedDomain : ownedDomains) {
            String normalizedOwnedDomain = stripLeadingWww(normalizeText(ownedDomain));
            if (!StringUtils.hasText(normalizedOwnedDomain)) {
                continue;
            }
            if (lower.equals(normalizedOwnedDomain) || lower.endsWith("." + normalizedOwnedDomain)) {
                return true;
            }
            if (rootLikeDomain.equals(normalizedOwnedDomain) || rootLikeDomain.endsWith("." + normalizedOwnedDomain)) {
                return true;
            }
            if (matchesOwnedDomainFamily(lower, normalizedOwnedDomain)
                    || matchesOwnedDomainFamily(rootLikeDomain, normalizedOwnedDomain)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOwnedDomainFamily(String candidateDomain, String ownedDomain) {
        String candidateLabel = extractDomainFamilyLabel(candidateDomain);
        String ownedLabel = extractDomainFamilyLabel(ownedDomain);
        if (!StringUtils.hasText(candidateLabel)
                || !StringUtils.hasText(ownedLabel)
                || !looksLikeStrongOwnedDomainFamilySeed(ownedLabel)
                || candidateLabel.equals(ownedLabel)
                || !candidateLabel.startsWith(ownedLabel)) {
            return false;
        }
        String remainder = normalizeDomainFamilyToken(candidateLabel.substring(ownedLabel.length()));
        return isAllowedOwnedDomainFamilyRemainder(remainder);
    }

    private String extractDomainFamilyLabel(String domain) {
        String normalized = stripLeadingWww(domain);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] labels = normalized.split("\\.");
        if (labels.length < 2) {
            return normalizeDomainFamilyToken(labels[0]);
        }
        int labelIndex = labels.length - 2;
        if (labels.length >= 3
                && isCountryCodeTld(labels[labels.length - 1])
                && COUNTRY_CODE_SECOND_LEVEL_SUFFIXES.contains(labels[labels.length - 2])) {
            labelIndex = labels.length - 3;
        }
        if (labelIndex < 0 || labelIndex >= labels.length) {
            return null;
        }
        return normalizeDomainFamilyToken(labels[labelIndex]);
    }

    private boolean isCountryCodeTld(String label) {
        String normalized = normalizeText(label);
        return StringUtils.hasText(normalized) && normalized.length() == 2;
    }

    private boolean looksLikeStrongOwnedDomainFamilySeed(String label) {
        String normalized = normalizeDomainFamilyToken(label);
        if (!StringUtils.hasText(normalized) || normalized.length() < 3) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isLetter(normalized.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDomainFamilyToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        StringBuilder normalized = new StringBuilder();
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char current = lower.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                normalized.append(current);
            }
        }
        return normalized.isEmpty() ? null : normalized.toString();
    }

    private boolean isAllowedOwnedDomainFamilyRemainder(String remainder) {
        String normalized = normalizeDomainFamilyToken(remainder);
        if (!StringUtils.hasText(normalized) || normalized.length() > 24) {
            return false;
        }
        return consumeOwnedDomainFamilyQualifier(normalized);
    }

    private boolean consumeOwnedDomainFamilyQualifier(String remainder) {
        if (!StringUtils.hasText(remainder)) {
            return true;
        }
        for (String qualifier : OWNED_DOMAIN_FAMILY_SUFFIXES) {
            if (remainder.startsWith(qualifier)
                    && consumeOwnedDomainFamilyQualifier(remainder.substring(qualifier.length()))) {
                return true;
            }
        }
        return false;
    }

    private String stripLeadingWww(String domain) {
        String normalized = normalizeText(domain);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.") && lower.length() > 4) {
            return lower.substring(4);
        }
        return lower;
    }

    private String resolveSourceTypeFromDomain(String domain) {
        String normalized = normalizeText(domain);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "chejiahao", "club.", "baa.", "forum", "bbs", "tieba")) {
            return SOURCE_TYPE_UGC;
        }
        if (containsAny(lower, "baike.", "wiki")) {
            return SOURCE_TYPE_REFERENCE;
        }
        if (containsAny(lower, "youjia", "yoojia")) {
            return SOURCE_TYPE_EDITORIAL;
        }
        for (Map.Entry<String, String> entry : SOURCE_TYPE_BY_DOMAIN_SUFFIX) {
            if (matchesDomainSuffix(lower, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesDomainSuffix(String domain, String suffix) {
        return domain.equals(suffix) || domain.endsWith("." + suffix);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String resolveSourceTypeLabel(String type) {
        return switch (normalizeText(type)) {
            case SOURCE_TYPE_OWNED -> "自有资产";
            case SOURCE_TYPE_CORPORATE -> "企业站点";
            case SOURCE_TYPE_EDITORIAL -> "编辑媒体";
            case SOURCE_TYPE_UGC -> "社区 UGC";
            case SOURCE_TYPE_REFERENCE -> "参考资料";
            default -> "其他来源";
        };
    }

    private String mapBrandSpaceStatus(DiagnosisTask latestTask, DiagnosisReportRecord latestReport) {
        if (latestTask != null && isGenerating(latestTask.getStatusCode())) {
            return "generating";
        }
        if (latestReport != null) {
            return "active";
        }
        if (latestTask != null && (intEquals(latestTask.getStatusCode(), TASK_STATUS_FAILED) || intEquals(latestTask.getStatusCode(), TASK_STATUS_CANCELLED))) {
            return "failed";
        }
        return "active";
    }

    private boolean isGenerating(Integer statusCode) {
        return intEquals(statusCode, TASK_STATUS_PENDING)
                || intEquals(statusCode, TASK_STATUS_RUNNING)
                || intEquals(statusCode, TASK_STATUS_QUEUED);
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private String resolveBrandName(BrandSpaceContext context) {
        return firstText(
                context.brand() == null ? null : context.brand().getBrandName(),
                context.latestTask() == null ? null : context.latestTask().getBrandNameSnapshot(),
                context.scope().getScopeName()
        );
    }

    private String resolveCategory(BrandSpaceContext context) {
        return firstText(
                context.scope().getCategorySnapshot(),
                context.brand() == null ? null : context.brand().getCategoryName(),
                context.latestTask() == null ? null : context.latestTask().getCategorySnapshot()
        );
    }

    private String resolveOfficialWebsite(BrandSpaceContext context) {
        return firstText(
                context.brand() == null ? null : context.brand().getOfficialWebsite(),
                context.latestTask() == null ? null : context.latestTask().getOfficialWebsiteSnapshot()
        );
    }

    private String resolvePrimaryDomain(BrandSpaceContext context) {
        return firstText(
                context.scope().getPrimaryDomain(),
                context.brand() == null ? null : context.brand().getCanonicalDomain(),
                domainOf(resolveOfficialWebsite(context))
        );
    }

    private Double resolveVisibilityScore(DiagnosisReportRecord report) {
        return resolveVisibilityScore(report, resolveTop3RateFallback(report));
    }

    private Double resolveVisibilityScore(DiagnosisReportRecord report, Double top3Rate) {
        if (report == null) {
            return null;
        }
        Double mentionRate = resolveMentionRate(report);
        Double recommendRate = resolveRecommendRate(report);
        Double score = resolveVisibilityScore(mentionRate, recommendRate, top3Rate);
        if (score != null) {
            return score;
        }
        return report.getAbvIndex() == null ? null : round2(report.getAbvIndex().doubleValue());
    }

    private Double resolveVisibilityScore(Double mentionRate, Double recommendRate, Double top3Rate) {
        if (mentionRate == null || recommendRate == null || top3Rate == null) {
            return null;
        }
        return round2(mentionRate * 0.35d + recommendRate * 0.40d + top3Rate * 0.25d);
    }

    private Double resolveMentionRate(DiagnosisReportRecord report) {
        if (report == null) {
            return null;
        }
        if (report.getCitationRate() != null) {
            return round2(report.getCitationRate().doubleValue() * 100.0d);
        }
        return readPercent(readSummaryMetric(report, "overallMentionRate"), true);
    }

    private Double resolveRecommendRate(DiagnosisReportRecord report) {
        if (report == null) {
            return null;
        }
        return readPercent(readSummaryMetric(report, "recommendRate"), true);
    }

    private Double resolveTop3Rate(BrandSpaceContext context, DiagnosisReportRecord report) {
        if (context == null || report == null) {
            return resolveTop3RateFallback(report);
        }
        return resolveTargetTop3Rate(
                context,
                report,
                loadTop3RateByBrand(report.getDiagnosisTaskId(), report.getSampleSize())
        );
    }

    private Double resolveTargetTop3Rate(BrandSpaceContext context,
                                         DiagnosisReportRecord report,
                                         Map<String, Double> top3RateByBrand) {
        if (context == null || report == null) {
            return resolveTop3RateFallback(report);
        }
        String targetBrandKey = normalizeBrandKey(resolveBrandName(context));
        if (StringUtils.hasText(targetBrandKey) && top3RateByBrand != null) {
            Double rate = top3RateByBrand.get(targetBrandKey);
            if (rate != null) {
                return rate;
            }
        }
        return resolveTop3RateFallback(report);
    }

    private Double resolveTop3RateFallback(DiagnosisReportRecord report) {
        if (report == null) {
            return null;
        }
        if (report.getSummaryJson() != null) {
            Double explicitTop3 = firstNonNull(
                    readPercent(report.getSummaryJson().get("top3Rate"), true),
                    readPercent(report.getSummaryJson().get("l3Top3Rate"), true)
            );
            if (explicitTop3 != null) {
                return explicitTop3;
            }
        }
        if (report.getSpontaneousMentionRate() != null) {
            return round2(report.getSpontaneousMentionRate().doubleValue() * 100.0d);
        }
        return readPercent(report.getSummaryJson() == null ? null : report.getSummaryJson().get("spontaneousMentionRate"), true);
    }

    private Double resolvePositiveRate(DiagnosisReportRecord report) {
        return readPercent(readSummaryMetric(report, "positiveRate"), true);
    }

    private Object readSummaryMetric(DiagnosisReportRecord report, String key) {
        if (report == null || report.getSummaryJson() == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object direct = report.getSummaryJson().get(key);
        if (direct != null) {
            return direct;
        }
        Object kpis = report.getSummaryJson().get("kpis");
        if (kpis instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    private Double readPercent(Object raw, boolean ratioToPercent) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = BigDecimal.valueOf(number.doubleValue());
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                value = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value == null) {
            return null;
        }
        if (ratioToPercent && value.compareTo(BigDecimal.ONE) <= 0) {
            value = value.multiply(BigDecimal.valueOf(100));
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double readNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = BigDecimal.valueOf(number.doubleValue());
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                value = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Integer readInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer resolveMentionCount(DiagnosisReportRecord report) {
        if (report == null || report.getSummaryJson() == null) {
            return null;
        }
        return readInteger(report.getSummaryJson().get("mentionedCount"));
    }

    private Integer readIssueCount(ReportResponse report) {
        if (report == null || report.getNegativeSignals() == null) {
            return null;
        }
        return report.getNegativeSignals().size();
    }

    private Integer readEvidenceCount(DiagnosisReportRecord report) {
        if (report == null || !StringUtils.hasText(report.getDiagnosisTaskId())) {
            return null;
        }
        return loadEvidence(report.getDiagnosisTaskId()).size();
    }

    private List<String> extractTopTopicLabels(ReportResponse report) {
        if (report == null || report.getBrandPerception() == null || report.getBrandPerception().getTopTags() == null) {
            return List.of();
        }
        return report.getBrandPerception().getTopTags().stream()
                .filter(Objects::nonNull)
                .map(item -> normalizeText(item.getTag()))
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
    }

    private List<String> extractTopicLabels(List<WorkbenchBrandPerformanceView.TopicSummaryView> topicSummaries) {
        if (topicSummaries == null || topicSummaries.isEmpty()) {
            return List.of();
        }
        return topicSummaries.stream()
                .map(WorkbenchBrandPerformanceView.TopicSummaryView::getTopicName)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolveTrendLabel(LocalDateTime generatedAt) {
        if (generatedAt == null) {
            return "未生成";
        }
        return generatedAt.toLocalDate().toString();
    }

    private String resolveFrequencyBucketLabel(LocalDateTime generatedAt, String frequency) {
        if (generatedAt == null) {
            return "未生成";
        }
        if ("week".equals(normalizeText(frequency))) {
            return generatedAt.toLocalDate().with(java.time.DayOfWeek.MONDAY).toString();
        }
        return generatedAt.toLocalDate().toString();
    }

    private String resolveFrequencyBucketGeneratedAt(LocalDateTime generatedAt, String frequency) {
        if (generatedAt == null) {
            return null;
        }
        LocalDate bucketDate = "week".equals(normalizeText(frequency))
                ? generatedAt.toLocalDate().with(java.time.DayOfWeek.MONDAY)
                : generatedAt.toLocalDate();
        return DATE_TIME_FORMATTER.format(bucketDate.atStartOfDay());
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }

    private String trimTo(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String resolveDirection(Double delta) {
        if (delta == null) {
            return "none";
        }
        if (delta > 0.01d) {
            return "up";
        }
        if (delta < -0.01d) {
            return "down";
        }
        return "flat";
    }

    private Double subtract(Double current, Double previous) {
        if (current == null || previous == null) {
            return null;
        }
        return round2(current - previous);
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatPercent(Double value) {
        if (value == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    private String formatScore(Double value) {
        if (value == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatSigned(Double value, boolean percent) {
        if (value == null) {
            return null;
        }
        String pattern = percent ? "%+.2f%%" : "%+.2f";
        return String.format(Locale.ROOT, pattern, value);
    }

    private String joinTexts(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return normalizeText(right);
        }
        if (!StringUtils.hasText(right)) {
            return normalizeText(left);
        }
        return left + " | " + right;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(Object raw) {
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private List<Map<String, Object>> readObjectList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = readObjectMap(item);
            if (!map.isEmpty()) {
                result.add(Map.copyOf(map));
            }
        }
        return List.copyOf(result);
    }

    private List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = normalizeText(asText(item));
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private String normalizeBrandKey(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.toLowerCase(Locale.ROOT)
                .replace("https://", "")
                .replace("http://", "")
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "_");
    }

    private String domainOf(String raw) {
        String normalized = normalizeText(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String withoutProtocol = normalized.replaceFirst("^https?://", "");
        int slashIndex = withoutProtocol.indexOf('/');
        String host = slashIndex >= 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
        return normalizeText(host);
    }

    private String resolvePlatformLabel(String model, String provider) {
        String modelText = normalizeText(model);
        String providerText = normalizeText(provider);
        String merged = normalizeSelectionValue(firstText(modelText, providerText));
        if (!StringUtils.hasText(merged)) {
            return "未记录平台";
        }
        if (merged.startsWith("ep-") || containsAny(merged, "doubao", "ark", "volcengine", "火山")) {
            return "豆包";
        }
        if (containsAny(merged, "gpt", "openai")) {
            return "OpenAI";
        }
        if (containsAny(merged, "deepseek")) {
            return "DeepSeek";
        }
        if (containsAny(merged, "claude", "anthropic")) {
            return "Claude";
        }
        if (containsAny(merged, "gemini", "google")) {
            return "Gemini";
        }
        if (containsAny(merged, "搜索", "search")) {
            return "搜索";
        }
        return firstText(modelText, providerText, "未记录平台");
    }

    private static final class MutableKnowledgeTopicMetricSnapshot {

        private Integer sampleSize;
        private Double visibilityScore;
        private Double mentionRate;
        private Double recommendRate;
        private Double top3Rate;
        private Double averagePosition;
        private Integer mentionCount;

        private void apply(String metricKey, Double value) {
            String normalizedMetricKey = metricKey == null ? null : metricKey.trim();
            if (!StringUtils.hasText(normalizedMetricKey) || value == null) {
                return;
            }
            switch (normalizedMetricKey) {
                case METRIC_SAMPLE_SIZE -> sampleSize = (int) Math.round(value);
                case METRIC_VISIBILITY_SCORE -> visibilityScore = value;
                case METRIC_VISIBILITY_MENTION_RATE -> mentionRate = value;
                case METRIC_VISIBILITY_RECOMMEND_RATE -> recommendRate = value;
                case METRIC_VISIBILITY_TOP3_RATE -> top3Rate = value;
                case METRIC_VISIBILITY_AVERAGE_POSITION -> averagePosition = value;
                case METRIC_VISIBILITY_MENTION_COUNT -> mentionCount = (int) Math.round(value);
                default -> {
                }
            }
        }

        private KnowledgeTopicMetricSnapshot snapshot() {
            return new KnowledgeTopicMetricSnapshot(
                    sampleSize == null ? 0 : sampleSize,
                    visibilityScore,
                    mentionRate,
                    recommendRate,
                    top3Rate,
                    averagePosition,
                    mentionCount
            );
        }
    }

    private static final class MutableKnowledgeTopicMetricAccumulator {

        private int sampleSizeSum;
        private int mentionCountSum;
        private final WeightedAverageAccumulator visibilityScore = new WeightedAverageAccumulator();
        private final WeightedAverageAccumulator mentionRate = new WeightedAverageAccumulator();
        private final WeightedAverageAccumulator recommendRate = new WeightedAverageAccumulator();
        private final WeightedAverageAccumulator top3Rate = new WeightedAverageAccumulator();
        private final WeightedAverageAccumulator averagePosition = new WeightedAverageAccumulator();

        private void apply(String metricKey, Double value, Double weightValue) {
            String normalizedMetricKey = metricKey == null ? null : metricKey.trim();
            if (!StringUtils.hasText(normalizedMetricKey) || value == null) {
                return;
            }
            switch (normalizedMetricKey) {
                case METRIC_SAMPLE_SIZE -> sampleSizeSum += (int) Math.round(value);
                case METRIC_VISIBILITY_MENTION_COUNT -> mentionCountSum += (int) Math.round(value);
                case METRIC_VISIBILITY_SCORE -> visibilityScore.add(value, weightValue);
                case METRIC_VISIBILITY_MENTION_RATE -> mentionRate.add(value, weightValue);
                case METRIC_VISIBILITY_RECOMMEND_RATE -> recommendRate.add(value, weightValue);
                case METRIC_VISIBILITY_TOP3_RATE -> top3Rate.add(value, weightValue);
                case METRIC_VISIBILITY_AVERAGE_POSITION -> averagePosition.add(value, weightValue);
                default -> {
                }
            }
        }

        private KnowledgeTopicMetricSnapshot snapshot() {
            return new KnowledgeTopicMetricSnapshot(
                    sampleSizeSum,
                    visibilityScore.value(),
                    mentionRate.value(),
                    recommendRate.value(),
                    top3Rate.value(),
                    averagePosition.value(),
                    mentionCountSum
            );
        }
    }

    private static final class WeightedAverageAccumulator {

        private double weightSum;
        private double weightedValueSum;
        private double plainValueSum;
        private int plainCount;

        private void add(Double value, Double weight) {
            if (value == null) {
                return;
            }
            if (weight != null && weight > 0.0d) {
                weightSum += weight;
                weightedValueSum += value * weight;
                return;
            }
            plainValueSum += value;
            plainCount += 1;
        }

        private Double value() {
            if (weightSum > 0.0d) {
                return Math.round(weightedValueSum * 100.0d / weightSum) / 100.0d;
            }
            if (plainCount <= 0) {
                return null;
            }
            return Math.round(plainValueSum * 100.0d / plainCount) / 100.0d;
        }
    }

    private record KnowledgeTopicMetricSnapshot(Integer sampleSize,
                                                Double visibilityScore,
                                                Double mentionRate,
                                                Double recommendRate,
                                                Double top3Rate,
                                                Double averagePosition,
                                                Integer mentionCount) {

        private static KnowledgeTopicMetricSnapshot empty() {
            return new KnowledgeTopicMetricSnapshot(0, null, null, null, null, null, 0);
        }
    }

    private String resolveModelLabel(String modelId) {
        String normalized = normalizeText(modelId);
        return StringUtils.hasText(normalized) ? normalized : "未记录模型";
    }

    private record ProblemAnalysisScope(Set<String> topicNames,
                                        Set<String> problemSpaceBuckets) {
        private static ProblemAnalysisScope of(List<String> topicNames,
                                               List<String> problemSpaceBuckets) {
            return new ProblemAnalysisScope(
                    normalizeValues(topicNames),
                    normalizeValues(problemSpaceBuckets)
            );
        }

        private static Set<String> normalizeValues(List<String> values) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (values == null || values.isEmpty()) {
                return normalized;
            }
            for (String value : values) {
                String normalizedValue = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
                if (StringUtils.hasText(normalizedValue)) {
                    normalized.add(normalizedValue);
                }
            }
            return normalized;
        }

        private boolean active() {
            return !topicNames.isEmpty() || !problemSpaceBuckets.isEmpty();
        }

        private boolean matchesTopic(String topicName) {
            if (topicNames.isEmpty()) {
                return true;
            }
            String normalized = topicName == null ? null : topicName.trim().toLowerCase(Locale.ROOT);
            return StringUtils.hasText(normalized) && topicNames.contains(normalized);
        }

        private boolean matchesBucket(String bucketKey) {
            if (problemSpaceBuckets.isEmpty()) {
                return true;
            }
            String normalized = bucketKey == null ? null : bucketKey.trim().toLowerCase(Locale.ROOT);
            return StringUtils.hasText(normalized) && problemSpaceBuckets.contains(normalized);
        }
    }

    private static final class MutableWindowCompetitorRow {
        private String id;
        private String brandName;
        private boolean target;
        private final List<Double> visibilityScores = new ArrayList<>();
        private final List<Double> recommendRates = new ArrayList<>();
        private final List<Double> mentionRates = new ArrayList<>();
        private final List<Double> positiveRates = new ArrayList<>();
        private final List<Double> top3Rates = new ArrayList<>();
        private final List<Double> shareOfVoiceValues = new ArrayList<>();
        private final List<Double> averagePositions = new ArrayList<>();
        private int mentionCount;
    }

    private static final class MutableAggregatedTopicRow {
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private Integer seqNo;
        private boolean enabled;
        private int promptCount;
        private int enabledPromptCount;
        private int signalCount;
        private final LinkedHashSet<String> sampleQuestions = new LinkedHashSet<>();
    }

    private static final class MutableAggregatedClusterRow {
        private String id;
        private String title;
        private String summary;
        private String severity;
        private String problemSpaceBucket;
        private int signalCount;
        private final LinkedHashSet<String> representativeQuestions = new LinkedHashSet<>();
    }

    private record ProblemAnalysisFrame(List<WorkbenchProblemAnalysisView.TopicRowView> topicRows,
                                        List<WorkbenchProblemAnalysisView.PromptRowView> promptRows,
                                        List<WorkbenchProblemAnalysisView.ProblemClusterView> problemClusters,
                                        List<WorkbenchProblemAnalysisView.ProbeRowView> probeRows,
                                        List<WorkbenchProblemAnalysisView.EvidenceRowView> evidenceRows,
                                        List<WorkbenchProblemAnalysisView.AttemptRowView> attemptRows,
                                        List<DiagnosisProbeRecord> diagnosisProbes,
                                        List<ProbeDispatchAttempt> dispatchAttempts,
                                        List<ProbeBrandMentionRecord> brandMentions,
                                        List<ProbeEvidenceRecord> evidenceRecords) {
        private static ProblemAnalysisFrame empty() {
            return new ProblemAnalysisFrame(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    private record KnowledgeRunWindowSelection(List<KnowledgeAssetRunRepository.RunDetailView> currentRuns,
                                               List<KnowledgeAssetRunRepository.RunDetailView> previousRuns,
                                               OffsetDateTime currentWindowStartAt,
                                               OffsetDateTime currentWindowEndAt) {
    }

    private record ProblemRichSnapshot(List<PromptSignalView> prompts,
                                       Map<String, PromptSignalView> promptByKey,
                                       List<PlatformSignalView> platforms,
                                       int totalDemandIndex,
                                       int totalQueryCount) {
        private static ProblemRichSnapshot empty() {
            return new ProblemRichSnapshot(List.of(), Map.of(), List.of(), 0, 0);
        }

        private Map<String, PlatformSignalView> platformsByName() {
            LinkedHashMap<String, PlatformSignalView> index = new LinkedHashMap<>();
            for (PlatformSignalView item : platforms) {
                index.put(item.platform(), item);
            }
            return index;
        }
    }

    private record ProblemVolumeSlice(DiagnosisReportRecord report,
                                      ProblemRichSnapshot snapshot) {
    }

    private record PromptSignalView(String promptKey,
                                    String promptId,
                                    String topic,
                                    Integer seqNo,
                                    String promptText,
                                    String problemSpaceBucket,
                                    String intentKey,
                                    String intentLabel,
                                    String subIntentLabel,
                                    String audienceSegment,
                                    String budgetSegment,
                                    int demandIndex,
                                    int attemptCount,
                                    int queryCount,
                                    int evidenceCount,
                                    int mentionCount,
                                    Double visibilityScore,
                                    Double averagePosition,
                                    Double citationShare,
                                    double coverageScore,
                                    boolean targetBrandCited,
                                    String competitorLeadBrand,
                                    List<String> citedBrandNames,
                                    int competitionScore,
                                    int relevanceScore,
                                    int opportunityScore,
                                    String loopStatus,
                                    String loopAction,
                                    String gapType,
                                    Map<String, Integer> queryCounts,
                                    Map<String, Integer> platformQueryCounts) {
    }

    private record PlatformSignalView(String platform, int queryCount, int attemptCount) {
        private double avgQueriesPerExecution() {
            if (attemptCount <= 0) {
                return 0.0d;
            }
            return BigDecimal.valueOf(queryCount * 1.0d / attemptCount)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    private record CompetitorMetricSnapshot(Double mentionRate,
                                            Double recommendRate,
                                            Double top3Rate) {
        private static CompetitorMetricSnapshot zero() {
            return new CompetitorMetricSnapshot(0.0d, 0.0d, 0.0d);
        }
    }

    private record FirstBatchMetricTruth(int effectiveAnswerCount,
                                         int mentionHitCount,
                                         int recommendHitCount,
                                         int top3HitCount,
                                         double mentionRate,
                                         double recommendRate,
                                         double top3Rate,
                                         double visibilityScore) {
    }

    private final class CompetitorMetricAccumulator {
        private final Set<String> mentionedProbeKeys = new LinkedHashSet<>();
        private final Set<String> recommendedProbeKeys = new LinkedHashSet<>();
        private final Set<String> top3ProbeKeys = new LinkedHashSet<>();

        private void add(String probeKey, boolean recommended, Integer positionIndex) {
            mentionedProbeKeys.add(probeKey);
            if (recommended) {
                recommendedProbeKeys.add(probeKey);
            }
            if (positionIndex != null && positionIndex <= 3) {
                top3ProbeKeys.add(probeKey);
            }
        }

        private CompetitorMetricSnapshot snapshot(int denominator) {
            if (denominator <= 0) {
                return new CompetitorMetricSnapshot(null, null, null);
            }
            return new CompetitorMetricSnapshot(
                    round2(mentionedProbeKeys.size() * 100.0d / denominator),
                    round2(recommendedProbeKeys.size() * 100.0d / denominator),
                    round2(top3ProbeKeys.size() * 100.0d / denominator)
            );
        }
    }

    private record BrandSpaceContext(String userId,
                                     String organizationId,
                                     BrandScope scope,
                                     Brand brand,
                                     DiagnosisTask latestTask,
                                     DiagnosisReportRecord latestReport) {
    }

    private record CitationEvidenceSnapshot(DiagnosisReportRecord report,
                                            List<ProbeEvidenceRecord> evidence) {
    }

    private record CitationEvidenceBasis(DiagnosisReportRecord currentReport,
                                         List<ProbeEvidenceRecord> currentEvidence,
                                         DiagnosisReportRecord previousReport,
                                         List<ProbeEvidenceRecord> previousEvidence) {
    }

    private record KnowledgeWorkbenchSelection(KnowledgeRunWindowSelection selection,
                                               List<DiagnosisReportRecord> windowReports,
                                               List<DiagnosisReportRecord> filteredReports) {
    }

    private record SourceAggregation(WorkbenchCitationSourcesView.SourceSummaryView summary,
                                     List<WorkbenchCitationSourcesView.CategorySliceView> categorySlices,
                                     List<WorkbenchCitationSourcesView.DomainRowView> domainRows,
                                     List<WorkbenchCitationSourcesView.UrlRowView> urlRows,
                                     WorkbenchCitationSourcesView.OwnershipView ownership,
                                     List<WorkbenchCitationSourcesView.DomainDetailRowView> domainDetails,
                                     List<String> missingFields) {
    }

    private record SourceSiteGrouping(String key, String label, String representativeDomain) {
    }

    private record PromptLookup(String promptId,
                                String topicId,
                                String topicName,
                                String problemSpaceBucket,
                                String promptText) {
    }

    private record RunPromptBridge(List<PromptLookup> orderedLookups,
                                   Integer targetProbeCount) {
        private static RunPromptBridge empty() {
            return new RunPromptBridge(List.of(), null);
        }

        private boolean hasPromptRows() {
            return orderedLookups != null && !orderedLookups.isEmpty();
        }

        private boolean supportsSequenceLinking(int actualProbeCount) {
            if (orderedLookups == null || orderedLookups.isEmpty() || actualProbeCount <= 0) {
                return false;
            }
            return orderedLookups.size() == actualProbeCount;
        }

        private PromptLookup lookupBySequence(Integer seqNo) {
            if (seqNo == null || seqNo <= 0 || orderedLookups == null || seqNo > orderedLookups.size()) {
                return null;
            }
            return orderedLookups.get(seqNo - 1);
        }

        private List<WorkbenchProblemAnalysisView.PromptRowView> toPromptRows() {
            if (orderedLookups == null || orderedLookups.isEmpty()) {
                return List.of();
            }
            List<WorkbenchProblemAnalysisView.PromptRowView> rows = new ArrayList<>();
            for (int index = 0; index < orderedLookups.size(); index++) {
                PromptLookup lookup = orderedLookups.get(index);
                rows.add(WorkbenchProblemAnalysisView.PromptRowView.builder()
                        .promptId(lookup.promptId())
                        .topicId(lookup.topicId())
                        .topicName(lookup.topicName())
                        .problemSpaceBucket(lookup.problemSpaceBucket())
                        .seqNo(index + 1)
                        .promptText(lookup.promptText())
                        .enabled(true)
                        .probeCount(0)
                        .evidenceCount(0)
                        .hasRawAnswer(false)
                        .latestProbeStatus(null)
                        .build());
            }
            return List.copyOf(rows);
        }
    }

    private record ProblemSpaceBucketDefinition(String key,
                                                String label,
                                                String description) {
    }

    private record EvidenceBucket(String url, String domain, String title, String type, int count, Set<String> probeIds) {
        private double averagePerProbe() {
            int probeCount = (int) probeIds.stream().filter(StringUtils::hasText).count();
            if (probeCount <= 0) {
                return count;
            }
            return count * 1.0d / probeCount;
        }
    }

    private static final class MutableEvidenceBucket {
        private String url;
        private String domain;
        private String title;
        private String type;
        private int count;
        private final LinkedHashSet<String> probeIds = new LinkedHashSet<>();
        private String representativeDomain;
        private int representativeDomainCount;
    }

    private static final class MutableCitationDomainDetail {
        private String groupKey;
        private String domainId;
        private String domain;
        private String type;
        private String ownershipKey;
        private int used;
        private final LinkedHashSet<String> probeIds = new LinkedHashSet<>();
        private final LinkedHashMap<String, MutableCitationUrlDetail> urls = new LinkedHashMap<>();
    }

    private static final class MutableCitationUrlDetail {
        private String urlKey;
        private String urlId;
        private String url;
        private String title;
        private int used;
    }

    private static final class PositionAccumulator {
        private int count;
        private int total;

        void add(Integer position) {
            if (position == null) {
                return;
            }
            this.total += position;
            this.count += 1;
        }

        double average() {
            if (count <= 0) {
                return 0.0d;
            }
            return BigDecimal.valueOf((double) total / count)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    private static final class MutableDerivedPrompt {
        private String promptId;
        private String topicId;
        private String topicName;
        private String problemSpaceBucket;
        private Integer seqNo;
        private String promptText;
    }

    private static final class MutablePromptSignal {
        private String promptKey;
        private String promptId;
        private String topic;
        private Integer seqNo;
        private String promptText;
        private String problemSpaceBucket;
        private Double visibilityScore;
        private Double averagePosition;
        private Double citationShare;
        private int evidenceCount;
        private int attemptCount;
        private int queryCount;
        private int mentionCount;
        private int targetMentionCount;
        private boolean targetBrandCited;
        private boolean hasRawAnswer;
        private int demandIndex;
        private int competitionScore;
        private int relevanceScore;
        private int opportunityScore;
        private double coverageScore;
        private String intentKey;
        private String intentLabel;
        private String subIntentLabel;
        private String audienceSegment;
        private String budgetSegment;
        private String competitorLeadBrand;
        private String loopStatus;
        private String loopAction;
        private String gapType;
        private List<String> citedBrandNames = List.of();
        private final LinkedHashMap<String, Integer> problemSpaceBucketCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> intentCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> subIntentCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> audienceCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> budgetCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> competitorMentions = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> queryCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> platformQueryCounts = new LinkedHashMap<>();
    }

    private static final class MutablePlatformSignal {
        private String platform;
        private int queryCount;
        private int attemptCount;
    }

    private final class MutableBrandTrendBucket {
        private final String label;
        private final String generatedAt;
        private String reportId;
        private String runId;
        private final List<Double> visibilityScores = new ArrayList<>();
        private final List<Double> recommendRates = new ArrayList<>();
        private final List<Double> mentionRates = new ArrayList<>();
        private final List<Double> top3Rates = new ArrayList<>();

        private MutableBrandTrendBucket(String label, String generatedAt) {
            this.label = label;
            this.generatedAt = generatedAt;
        }

        private void add(WorkbenchBrandPerformanceView.TrendPointView point) {
            this.reportId = point.getReportId();
            this.runId = point.getRunId();
            this.visibilityScores.add(point.getVisibilityScore());
            this.recommendRates.add(point.getRecommendRate());
            this.mentionRates.add(point.getMentionRate());
            this.top3Rates.add(point.getTop3Rate());
        }

        private WorkbenchBrandPerformanceView.TrendPointView toPoint() {
            return WorkbenchBrandPerformanceView.TrendPointView.builder()
                    .reportId(reportId)
                    .runId(runId)
                    .label(label)
                    .generatedAt(generatedAt)
                    .visibilityScore(averageValues(visibilityScores))
                    .recommendRate(averageValues(recommendRates))
                    .mentionRate(averageValues(mentionRates))
                    .top3Rate(averageValues(top3Rates))
                    .build();
        }
    }

    private final class MutableCitationTrendBucket {
        private final String label;
        private final String generatedAt;
        private String sliceKey;
        private boolean latest;
        private int totalCitations;
        private final List<Double> ownedShares = new ArrayList<>();
        private final List<Double> thirdPartyShares = new ArrayList<>();
        private final List<Double> competitorShares = new ArrayList<>();
        private final LinkedHashMap<String, MutableTrendDomainAccumulator> topDomains = new LinkedHashMap<>();

        private MutableCitationTrendBucket(String label, String generatedAt) {
            this.label = label;
            this.generatedAt = generatedAt;
        }

        private void add(WorkbenchCitationSourcesView.TrendPointView point) {
            this.sliceKey = point.getSliceKey();
            this.latest = this.latest || Boolean.TRUE.equals(point.getIsLatest());
            this.totalCitations += safeInt(point.getTotalCitations());
            this.ownedShares.add(point.getOwnedShare());
            this.thirdPartyShares.add(point.getThirdPartyShare());
            this.competitorShares.add(point.getCompetitorShare());
            if (point.getTopDomains() == null) {
                return;
            }
            for (WorkbenchCitationSourcesView.TrendDomainSliceView item : point.getTopDomains()) {
                if (item == null || !StringUtils.hasText(item.getDomain())) {
                    continue;
                }
                topDomains.computeIfAbsent(item.getDomain(), ignored -> new MutableTrendDomainAccumulator(item)).add(item);
            }
        }

        private WorkbenchCitationSourcesView.TrendPointView toPoint() {
            return WorkbenchCitationSourcesView.TrendPointView.builder()
                    .sliceKey(sliceKey)
                    .label(label)
                    .generatedAt(generatedAt)
                    .totalCitations(totalCitations)
                    .ownedShare(averageValues(ownedShares))
                    .thirdPartyShare(averageValues(thirdPartyShares))
                    .competitorShare(averageValues(competitorShares))
                    .isLatest(latest)
                    .topDomains(topDomains.values().stream()
                            .map(MutableTrendDomainAccumulator::toView)
                            .sorted(Comparator.comparing(
                                    WorkbenchCitationSourcesView.TrendDomainSliceView::getUsed,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            ))
                            .limit(3)
                            .toList())
                    .build();
        }
    }

    private static final class MutableTrendDomainAccumulator {
        private final String domain;
        private final String type;
        private final String ownershipKey;
        private final String ownershipLabel;
        private int used;
        private final List<Double> shares = new ArrayList<>();

        private MutableTrendDomainAccumulator(WorkbenchCitationSourcesView.TrendDomainSliceView seed) {
            this.domain = seed.getDomain();
            this.type = seed.getType();
            this.ownershipKey = seed.getOwnershipKey();
            this.ownershipLabel = seed.getOwnershipLabel();
        }

        private void add(WorkbenchCitationSourcesView.TrendDomainSliceView item) {
            this.used += item.getUsed() == null ? 0 : item.getUsed();
            if (item.getShare() != null) {
                this.shares.add(item.getShare());
            }
        }

        private WorkbenchCitationSourcesView.TrendDomainSliceView toView() {
            return WorkbenchCitationSourcesView.TrendDomainSliceView.builder()
                    .domain(domain)
                    .used(used)
                    .share(shares.isEmpty()
                            ? null
                            : BigDecimal.valueOf(shares.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d))
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue())
                    .type(type)
                    .ownershipKey(ownershipKey)
                    .ownershipLabel(ownershipLabel)
                    .build();
        }
    }

    private static final class MutableVolumeTopicSlice {
        private String topicName;
        private String problemSpaceBucket;
        private int demandCount;
        private String leadPromptText;
        private int leadPromptDemand;
    }

    private record OverviewInsightCopy(String evidenceSummary,
                                       String nextStepLabel,
                                       String nextStepText,
                                       String nextStepRouteKey) {
    }
}

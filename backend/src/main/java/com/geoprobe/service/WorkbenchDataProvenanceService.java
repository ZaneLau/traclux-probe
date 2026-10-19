package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.workbench.WorkbenchDataProvenanceCardView;
import com.geoprobe.dto.workbench.WorkbenchDataProvenanceQuery;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanRecordItem;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordItemMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.QuotaLedgerMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkbenchDataProvenanceService {

    private static final String CONTRACT_VERSION = "workbench_data_provenance_card_b1";
    private static final int DETAIL_LIMIT = 20;
    private static final int DEFAULT_SAMPLE_PAGE_SIZE = 20;
    private static final int MAX_SAMPLE_PAGE_SIZE = 50;
    private static final ZoneId PRODUCT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Set<String> FIRST_BATCH_METRICS = Set.of(
            "visibilityScore",
            "mentionRate",
            "recommendRate",
            "top3Rate"
    );

    private final ServiceAccessService serviceAccessService;
    private final BrandScopeMapper brandScopeMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final ProbePlanRecordMapper probePlanRecordMapper;
    private final ProbePlanRecordItemMapper probePlanRecordItemMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    private final QuotaLedgerMapper quotaLedgerMapper;
    private final ObjectProvider<KnowledgeAssetEvidenceRepository> knowledgeAssetEvidenceRepositoryProvider;

    public WorkbenchDataProvenanceCardView getCard(String userId, WorkbenchDataProvenanceQuery query) {
        BrandScope scope = loadScope(userId, query == null ? null : query.brandSpaceId());
        DiagnosisTask task = loadTask(scope.getId(), query == null ? null : query.runId());
        ProbePlanRecord planRecord = loadPlanRecord(scope.getId(), task);
        Map<String, Object> runtimeMetadata = safeMap(task.getTaskMetadataJson());
        Map<String, Object> binding = readProbePlanBinding(task.getTaskOrderJson());
        List<ProbePlanRecordItem> planItems = loadPlanItems(planRecord, query);
        List<String> selectedProbeIds = resolveSelectedProbeIds(query, planItems);
        List<DiagnosisProbeRecord> probes = loadProbes(task.getId(), selectedProbeIds);
        if (!selectedProbeIds.isEmpty() && probes.isEmpty()) {
            probes = loadProbes(task.getId(), List.of());
            selectedProbeIds = resolveConcreteProbeIds(probes);
        }
        List<ProbeDispatchAttempt> attempts = loadAttempts(task.getId(), selectedProbeIds);
        List<ProbeEvidenceRecord> citations = loadCitations(task.getId(), selectedProbeIds);
        List<ProbeBrandMentionRecord> mentions = loadMentions(task.getId(), selectedProbeIds);
        List<QuotaLedger> ledgers = loadLedgers(task.getId(), selectedProbeIds);
        WorkbenchDataProvenanceCardView.PlanView plan = buildPlan(planRecord, runtimeMetadata, binding, planItems);
        WorkbenchDataProvenanceCardView.EvidenceView evidence = buildEvidence(probes, attempts, citations, mentions, selectedProbeIds);
        WorkbenchDataProvenanceCardView.SamplePageView samplePage = buildSamplePage(scope, query);
        WorkbenchDataProvenanceCardView.ResultExplanationView resultExplanation = buildResultExplanation(query, plan, evidence, samplePage);

        return WorkbenchDataProvenanceCardView.builder()
                .contractVersion(CONTRACT_VERSION)
                .title("数据溯源卡")
                .brandSpaceId(scope.getId())
                .anchor(buildAnchor(query))
                .run(buildRun(task))
                .resultExplanation(resultExplanation)
                .plan(plan)
                .evidence(evidence)
                .samplePage(samplePage)
                .billing(buildBilling(ledgers))
                .build();
    }

    private BrandScope loadScope(String userId, String brandSpaceId) {
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
        return scope;
    }

    private DiagnosisTask loadTask(String brandSpaceId, String runId) {
        DiagnosisTask task;
        if (StringUtils.hasText(runId)) {
            task = diagnosisTaskMapper.selectById(runId.trim());
            if (task == null || !Objects.equals(task.getBrandScopeId(), brandSpaceId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "运行记录不存在或无权访问");
            }
            return task;
        }
        task = diagnosisTaskMapper.selectOne(new LambdaQueryWrapper<DiagnosisTask>()
                .eq(DiagnosisTask::getBrandScopeId, brandSpaceId)
                .orderByDesc(DiagnosisTask::getCompletedAt)
                .orderByDesc(DiagnosisTask::getCreatedAt)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "品牌空间暂无可溯源运行记录");
        }
        return task;
    }

    private ProbePlanRecord loadPlanRecord(String brandSpaceId, DiagnosisTask task) {
        Map<String, Object> metadata = safeMap(task.getTaskMetadataJson());
        String recordId = readString(metadata, "planRecordId");
        if (!StringUtils.hasText(recordId)) {
            recordId = readString(readProbePlanBinding(task.getTaskOrderJson()), "planRecordId");
        }
        if (StringUtils.hasText(recordId)) {
            ProbePlanRecord record = probePlanRecordMapper.selectById(recordId);
            if (record != null && Objects.equals(record.getBrandScopeId(), brandSpaceId)) {
                return record;
            }
        }
        return probePlanRecordMapper.selectOne(new LambdaQueryWrapper<ProbePlanRecord>()
                .eq(ProbePlanRecord::getBrandScopeId, brandSpaceId)
                .eq(ProbePlanRecord::getTriggeredRunId, task.getId())
                .orderByDesc(ProbePlanRecord::getCreatedAt)
                .last("limit 1"));
    }

    private List<ProbePlanRecordItem> loadPlanItems(ProbePlanRecord planRecord, WorkbenchDataProvenanceQuery query) {
        if (planRecord == null || !StringUtils.hasText(planRecord.getId())) {
            return List.of();
        }
        LambdaQueryWrapper<ProbePlanRecordItem> wrapper = new LambdaQueryWrapper<ProbePlanRecordItem>()
                .eq(ProbePlanRecordItem::getRecordId, planRecord.getId());
        if (query != null && StringUtils.hasText(query.probeId())) {
            wrapper.eq(ProbePlanRecordItem::getProbeId, query.probeId().trim());
        } else if (query != null && StringUtils.hasText(query.topicId())) {
            wrapper.eq(ProbePlanRecordItem::getTopicId, query.topicId().trim());
        }
        return probePlanRecordItemMapper.selectList(wrapper
                .orderByAsc(ProbePlanRecordItem::getSeqNoSnapshot)
                .last("limit " + DETAIL_LIMIT));
    }

    private List<String> resolveSelectedProbeIds(WorkbenchDataProvenanceQuery query, List<ProbePlanRecordItem> planItems) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (query != null && StringUtils.hasText(query.probeId())) {
            ids.add(query.probeId().trim());
        }
        for (ProbePlanRecordItem item : planItems) {
            if (StringUtils.hasText(item.getProbeId())) {
                ids.add(item.getProbeId());
            }
        }
        return List.copyOf(ids);
    }

    private List<String> resolveConcreteProbeIds(List<DiagnosisProbeRecord> probes) {
        if (probes == null || probes.isEmpty()) {
            return List.of();
        }
        return probes.stream()
                .map(DiagnosisProbeRecord::getId)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<DiagnosisProbeRecord> loadProbes(String runId, List<String> probeIds) {
        LambdaQueryWrapper<DiagnosisProbeRecord> wrapper = new LambdaQueryWrapper<DiagnosisProbeRecord>()
                .eq(DiagnosisProbeRecord::getDiagnosisTaskId, runId);
        applyProbeFilter(wrapper, DiagnosisProbeRecord::getId, probeIds);
        return diagnosisProbeRecordMapper.selectList(wrapper
                .orderByAsc(DiagnosisProbeRecord::getSeqNo)
                .last("limit " + DETAIL_LIMIT));
    }

    private List<ProbeDispatchAttempt> loadAttempts(String runId, List<String> probeIds) {
        LambdaQueryWrapper<ProbeDispatchAttempt> wrapper = new LambdaQueryWrapper<ProbeDispatchAttempt>()
                .eq(ProbeDispatchAttempt::getDiagnosisTaskId, runId);
        applyProbeFilter(wrapper, ProbeDispatchAttempt::getProbeId, probeIds);
        return probeDispatchAttemptMapper.selectList(wrapper
                .orderByDesc(ProbeDispatchAttempt::getSelectedFinal)
                .orderByDesc(ProbeDispatchAttempt::getCreatedAt)
                .last("limit " + DETAIL_LIMIT));
    }

    private List<ProbeEvidenceRecord> loadCitations(String runId, List<String> probeIds) {
        LambdaQueryWrapper<ProbeEvidenceRecord> wrapper = new LambdaQueryWrapper<ProbeEvidenceRecord>()
                .eq(ProbeEvidenceRecord::getDiagnosisTaskId, runId);
        applyProbeFilter(wrapper, ProbeEvidenceRecord::getProbeId, probeIds);
        return probeEvidenceRecordMapper.selectList(wrapper
                .orderByDesc(ProbeEvidenceRecord::getCreatedAt)
                .last("limit " + DETAIL_LIMIT));
    }

    private List<ProbeBrandMentionRecord> loadMentions(String runId, List<String> probeIds) {
        LambdaQueryWrapper<ProbeBrandMentionRecord> wrapper = new LambdaQueryWrapper<ProbeBrandMentionRecord>()
                .eq(ProbeBrandMentionRecord::getDiagnosisTaskId, runId);
        applyProbeFilter(wrapper, ProbeBrandMentionRecord::getProbeId, probeIds);
        return probeBrandMentionRecordMapper.selectList(wrapper
                .orderByAsc(ProbeBrandMentionRecord::getPositionIndex)
                .last("limit " + DETAIL_LIMIT));
    }

    private List<QuotaLedger> loadLedgers(String runId, List<String> probeIds) {
        LambdaQueryWrapper<QuotaLedger> wrapper = new LambdaQueryWrapper<QuotaLedger>()
                .eq(QuotaLedger::getDiagnosisTaskId, runId);
        if (probeIds != null && !probeIds.isEmpty()) {
            wrapper.and(nested -> nested.in(QuotaLedger::getProbeId, probeIds).or().isNull(QuotaLedger::getProbeId));
        }
        return quotaLedgerMapper.selectList(wrapper
                .orderByDesc(QuotaLedger::getCreatedAt)
                .last("limit " + DETAIL_LIMIT));
    }

    private WorkbenchDataProvenanceCardView.AnchorView buildAnchor(WorkbenchDataProvenanceQuery query) {
        return WorkbenchDataProvenanceCardView.AnchorView.builder()
                .anchorType(query == null ? null : trimToNull(query.anchorType()))
                .anchorKey(query == null ? null : trimToNull(query.anchorKey()))
                .metricKey(query == null ? null : trimToNull(query.metricKey()))
                .metricLabel(query == null ? null : trimToNull(query.metricLabel()))
                .metricValue(query == null ? null : trimToNull(query.metricValue()))
                .timeWindowLabel(buildTimeWindowLabel(query))
                .topicId(query == null ? null : trimToNull(query.topicId()))
                .topicName(query == null ? null : trimToNull(query.topicName()))
                .probeId(query == null ? null : trimToNull(query.probeId()))
                .build();
    }

    private WorkbenchDataProvenanceCardView.ResultExplanationView buildResultExplanation(
            WorkbenchDataProvenanceQuery query,
            WorkbenchDataProvenanceCardView.PlanView plan,
            WorkbenchDataProvenanceCardView.EvidenceView evidence,
            WorkbenchDataProvenanceCardView.SamplePageView samplePage) {
        if (query == null || !StringUtils.hasText(query.metricKey())) {
            return null;
        }
        String metricKey = query.metricKey().trim();
        String metricLabel = firstText(query.metricLabel(), metricDefaultLabel(metricKey));
        FirstBatchMetricTruth firstBatchTruth = resolveFirstBatchMetricTruth(metricKey, samplePage);
        String metricValue = firstText(firstBatchTruth == null ? null : formatMetricValue(metricKey, firstBatchTruth), query.metricValue(), "--");
        String timeWindowLabel = buildTimeWindowLabel(query);
        if (!isOfficialFirstBatchQuery(query)) {
            PendingMetricStatus pendingStatus = resolvePendingMetricStatus(query, metricKey);
            return WorkbenchDataProvenanceCardView.ResultExplanationView.builder()
                    .status(pendingStatus.status())
                    .statusLabel(pendingStatus.label())
                    .statusNote(pendingStatus.note())
                    .metricLabel(metricLabel)
                    .metricValue(metricValue)
                    .timeWindowLabel(timeWindowLabel)
                    .anchorLabel(buildAnchorLabel(query))
                    .meaning(pendingStatus.meaning())
                    .calculation(null)
                    .limitationNote(pendingStatus.note())
                    .composition(List.of())
                    .build();
        }
        List<WorkbenchDataProvenanceCardView.CompositionItemView> composition = buildComposition(query, metricKey, plan, evidence, samplePage);
        return WorkbenchDataProvenanceCardView.ResultExplanationView.builder()
                .status("connected")
                .statusLabel("口径已接通")
                .statusNote("当前指标使用回答级样本统计。")
                .metricLabel(metricLabel)
                .metricValue(metricValue)
                .timeWindowLabel(timeWindowLabel)
                .anchorLabel(buildAnchorLabel(query))
                .meaning(metricMeaning(metricKey, metricLabel))
                .calculation(metricCalculation(metricKey, metricLabel))
                .limitationNote(buildLimitationNote(query, timeWindowLabel))
                .composition(composition)
                .build();
    }

    private List<WorkbenchDataProvenanceCardView.CompositionItemView> buildComposition(
            WorkbenchDataProvenanceQuery query,
            String metricKey,
            WorkbenchDataProvenanceCardView.PlanView plan,
            WorkbenchDataProvenanceCardView.EvidenceView evidence,
            WorkbenchDataProvenanceCardView.SamplePageView samplePage) {
        List<WorkbenchDataProvenanceCardView.CompositionItemView> items = new ArrayList<>();
        FirstBatchMetricTruth firstBatchTruth = resolveFirstBatchMetricTruth(metricKey, samplePage);
        if (firstBatchTruth != null) {
            switch (metricKey) {
                case "visibilityScore" -> {
                    addRateFactor(items, "提及率", firstBatchTruth.mentionRate(), 35,
                            firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.mentionHitCount(), "mentionRate");
                    addRateFactor(items, "推荐率", firstBatchTruth.recommendRate(), 40,
                            firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.recommendHitCount(), "recommendRate");
                    addRateFactor(items, "Top3 出现率", firstBatchTruth.top3Rate(), 25,
                            firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.top3HitCount(), "top3Rate");
                }
                case "mentionRate" -> addRatio(items, "提及样本", firstBatchTruth.mentionRate(),
                        firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.mentionHitCount(), "mentionRate");
                case "recommendRate" -> addRatio(items, "推荐样本", firstBatchTruth.recommendRate(),
                        firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.recommendHitCount(), "recommendRate");
                case "top3Rate" -> addRatio(items, "Top3 样本", firstBatchTruth.top3Rate(),
                        firstBatchTruth.effectiveAnswerCount(), firstBatchTruth.top3HitCount(), "top3Rate");
                default -> {
                }
            }
            return items;
        }
        int sampleSize = firstPositive(query.contextSampleSize(), evidence == null ? null : evidence.getProbeCount(), plan == null ? null : plan.getProbeCount());
        switch (metricKey) {
            case "visibilityScore" -> {
                addRateFactor(items, "提及率", query.contextMentionRate(), 35, sampleSize, query.contextMentionCount(), "mentionRate");
                addRateFactor(items, "推荐率", query.contextRecommendRate(), 40, sampleSize, null, "recommendRate");
                addRateFactor(items, "Top3 出现率", query.contextTop3Rate(), 25, sampleSize, null, "top3Rate");
            }
            case "mentionRate" -> addRatio(items, "提及样本", query.contextMentionRate(), sampleSize, query.contextMentionCount(), "mentionRate");
            case "recommendRate" -> addRatio(items, "推荐样本", query.contextRecommendRate(), sampleSize, null, "recommendRate");
            case "top3Rate" -> addRatio(items, "Top3 样本", query.contextTop3Rate(), sampleSize, null, "top3Rate");
            case "citationShare", "ownedShare", "thirdPartyShare" -> addCountShare(items, "当前来源引用", query.contextCitationCount(), query.contextTotalCitationCount(), query.contextCitationShare(), metricKey);
            case "citationCount", "totalCitations" -> addValue(items, "引用 URL", query.contextCitationCount(), "条", "citationCount");
            case "demandShare" -> addNumericShare(items, "当前切片需求", query.contextDemandVolume(), query.contextDemandTotal(), metricKey);
            case "demandVolume" -> addValue(items, "当前切片需求", query.contextDemandVolume(), null, "demandVolume");
            case "demandTotal" -> addValue(items, "需求汇总", query.contextDemandTotal(), null, "demandTotal");
            case "activeTopicCount" -> addValue(items, "活跃主题", query.contextActiveTopicCount(), "个", "activeTopicCount");
            case "promptCount" -> addValue(items, "问题/问法", query.contextPromptCount(), "个", "promptCount");
            case "totalProjection" -> addValue(items, "需求估算", query.contextTotalProjection(), null, "totalProjection");
            case "coverage" -> addCountShare(items, "已覆盖问题", query.contextPromptLoopCoveredCount(), query.contextPromptLoopTotalCount(), null, "coverage");
            case "gapCount" -> addValue(items, "未覆盖问题", query.contextPromptLoopGapCount(), "个", "gapCount");
            case "highOpportunityCount" -> addValue(items, "高机会缺口", query.contextPromptLoopHighOpportunityCount(), "个", "highOpportunityCount");
            default -> {
                if (sampleSize > 0) {
                    addValue(items, "可统计样本", sampleSize, "条", "sampleSize");
                }
            }
        }
        return items;
    }

    private void addRateFactor(List<WorkbenchDataProvenanceCardView.CompositionItemView> items,
                               String label,
                               Double rate,
                               int weight,
                               int denominator,
                               Integer numerator,
                               String sourceMetricKey) {
        if (rate == null && numerator == null) {
            return;
        }
        Double resolvedRate = rate;
        if (resolvedRate == null && numerator != null && denominator > 0) {
            resolvedRate = numerator * 100.0 / denominator;
        }
        double normalizedRate = normalizePercent(resolvedRate);
        double score = normalizedRate * weight / 100.0;
        items.add(WorkbenchDataProvenanceCardView.CompositionItemView.builder()
                .label(label)
                .value(formatPercent(normalizedRate))
                .description("权重 " + weight + "%，计入 " + formatDecimal(score))
                .formulaKey("weighted_visibility_factor")
                .rateValue(round2(normalizedRate))
                .weightPercent(weight)
                .contributionValue(round2(score))
                .numerator(formatNullableNumber(firstPositiveNullable(numerator, estimateNumerator(normalizedRate, denominator))))
                .denominator(denominator > 0 ? String.valueOf(denominator) : null)
                .sourceMetricKey(sourceMetricKey)
                .source("回答级样本统计")
                .build());
    }

    private void addRatio(List<WorkbenchDataProvenanceCardView.CompositionItemView> items,
                          String label,
                          Double rate,
                          int denominator,
                          Integer numerator,
                          String sourceMetricKey) {
        if (rate == null && numerator == null) {
            return;
        }
        Double resolvedRate = rate;
        if (resolvedRate == null && numerator != null && denominator > 0) {
            resolvedRate = numerator * 100.0 / denominator;
        }
        double normalizedRate = normalizePercent(resolvedRate);
        items.add(WorkbenchDataProvenanceCardView.CompositionItemView.builder()
                .label(label)
                .value(formatPercent(normalizedRate))
                .description("当前样本内命中占比")
                .formulaKey("sample_ratio")
                .rateValue(round2(normalizedRate))
                .numerator(formatNullableNumber(firstPositiveNullable(numerator, estimateNumerator(normalizedRate, denominator))))
                .denominator(denominator > 0 ? String.valueOf(denominator) : null)
                .sourceMetricKey(sourceMetricKey)
                .source("回答级样本统计")
                .build());
    }

    private void addCountShare(List<WorkbenchDataProvenanceCardView.CompositionItemView> items,
                               String label,
                               Integer numerator,
                               Integer denominator,
                               Double rate,
                               String sourceMetricKey) {
        if (numerator == null && denominator == null && rate == null) {
            return;
        }
        items.add(WorkbenchDataProvenanceCardView.CompositionItemView.builder()
                .label(label)
                .value(rate == null ? null : formatPercent(normalizePercent(rate)))
                .description("当前数量 / 当前窗口总量")
                .formulaKey("count_share")
                .numerator(formatNullableNumber(numerator))
                .denominator(formatNullableNumber(denominator))
                .sourceMetricKey(sourceMetricKey)
                .source("当前筛选窗口")
                .build());
    }

    private void addNumericShare(List<WorkbenchDataProvenanceCardView.CompositionItemView> items,
                                 String label,
                                 Double numerator,
                                 Double denominator,
                                 String sourceMetricKey) {
        if (numerator == null && denominator == null) {
            return;
        }
        items.add(WorkbenchDataProvenanceCardView.CompositionItemView.builder()
                .label(label)
                .value(numerator != null && denominator != null && denominator > 0 ? formatPercent(numerator * 100.0 / denominator) : null)
                .description("当前需求量 / 当前窗口总需求量")
                .formulaKey("numeric_share")
                .numerator(formatNullableNumber(numerator))
                .denominator(formatNullableNumber(denominator))
                .sourceMetricKey(sourceMetricKey)
                .source("当前筛选窗口")
                .build());
    }

    private void addValue(List<WorkbenchDataProvenanceCardView.CompositionItemView> items,
                          String label,
                          Number value,
                          String unit,
                          String sourceMetricKey) {
        if (value == null) {
            return;
        }
        items.add(WorkbenchDataProvenanceCardView.CompositionItemView.builder()
                .label(label)
                .value(formatNumber(value) + (StringUtils.hasText(unit) ? " " + unit : ""))
                .description("当前筛选窗口内后端汇总值")
                .formulaKey("direct_count")
                .numerator(formatNumber(value))
                .sourceMetricKey(sourceMetricKey)
                .source("当前筛选窗口")
                .build());
    }

    private String metricCalculation(String metricKey, String metricLabel) {
        return switch (metricKey) {
            case "visibilityScore" -> "AI 可见度 = 提及率 × 35% + 推荐率 × 40% + Top3 出现率 × 25%。";
            case "mentionRate" -> "提及率 = 提及命中回答数 / 有效回答数。";
            case "recommendRate" -> "推荐率 = 明确推荐本品牌的有效回答数 / 有效回答数。";
            case "top3Rate" -> "Top3 出现率 = 本品牌进入候选/推荐序列前三的有效回答数 / 有效回答数。";
            case "positiveRate" -> "正向率 = 当前筛选窗口内正向提及样本数 / 有情绪判断的品牌提及样本数。";
            case "citationCount", "totalCitations" -> "引用总量 = 当前筛选窗口内模型回答引用到的 URL 证据条数。";
            case "citationShare" -> "引用占比 = 该来源引用次数 / 当前筛选窗口引用总量。";
            case "ownedShare" -> "自有引用占比 = 品牌自有或可控来源引用次数 / 当前筛选窗口引用总量。";
            case "thirdPartyShare" -> "第三方引用占比 = 非自有来源引用次数 / 当前筛选窗口引用总量。";
            case "demandTotal" -> "需求总量 = 当前筛选窗口内问题需求估算量的汇总。";
            case "activeTopicCount" -> "活跃主题 = 当前筛选窗口内有需求或样本命中的主题数。";
            case "demandVolume" -> "需求量级 = 当前切片或主题在筛选窗口内的需求估算量。";
            case "demandShare" -> "需求占比 = 当前问题或主题需求量 / 当前筛选窗口需求总量。";
            case "promptCount" -> "问题数 = 当前筛选范围内可展示的问题或问法数量。";
            case "totalProjection" -> "总需求量 = 当前筛选范围内问题需求估算量之和。";
            case "coverage" -> "被引覆盖率 = 已有引用证据覆盖的问题数 / 当前筛选范围问题数。";
            case "gapCount" -> "缺口词 = 当前筛选范围内未被引用证据覆盖的问题数。";
            case "highOpportunityCount" -> "高价值机会 = 当前筛选范围内需求较高且引用覆盖不足的问题数。";
            default -> metricLabel + " = 当前筛选窗口内后端返回的同名指标值。";
        };
    }

    private String metricMeaning(String metricKey, String metricLabel) {
        return switch (metricKey) {
            case "visibilityScore" -> "衡量品牌在 AI 回答中被看见、被推荐、并进入靠前位置的综合表现。";
            case "mentionRate" -> "衡量品牌是否进入回答内容。";
            case "recommendRate" -> "衡量品牌是否被模型明确推荐。";
            case "top3Rate" -> "衡量品牌在候选品牌里的靠前程度。";
            case "citationShare", "ownedShare", "thirdPartyShare" -> "衡量当前来源类型在引用证据中的占比。";
            case "citationCount", "totalCitations" -> "衡量当前窗口内可追溯引用证据的数量。";
            case "demandTotal", "demandVolume", "demandShare" -> "衡量当前问题空间的需求规模。";
            case "coverage", "gapCount", "highOpportunityCount" -> "衡量现有引用证据对问题需求的覆盖情况。";
            default -> "解释你点击的“" + metricLabel + "”来自哪个当前筛选窗口和哪些可追溯样本。";
        };
    }

    private String buildLimitationNote(WorkbenchDataProvenanceQuery query, String timeWindowLabel) {
        String scope = firstText(query == null ? null : query.platform(), query == null ? null : query.domain(), "当前品牌空间");
        return "仅解释" + timeWindowLabel + "、" + scope + "内已入库且可解析的回答；失败、空回答和无效回答不进入比例分母。";
    }

    private String buildAnchorLabel(WorkbenchDataProvenanceQuery query) {
        if (query == null) {
            return null;
        }
        return firstText(query.topicName(), query.platform(), query.domain(), query.anchorKey());
    }

    private String buildTimeWindowLabel(WorkbenchDataProvenanceQuery query) {
        if (query == null) {
            return "当前筛选窗口";
        }
        if (StringUtils.hasText(query.dateFrom()) && StringUtils.hasText(query.dateTo())) {
            return query.dateFrom().trim() + " 至 " + query.dateTo().trim();
        }
        String timePreset = trimToNull(query.timePreset());
        if (timePreset == null) {
            return firstText(query.windowKey(), "当前筛选窗口");
        }
        return switch (timePreset) {
            case "today" -> "今天";
            case "yesterday" -> "昨天";
            case "7d", "last_7d", "recent_7d", "last_7_days" -> "近 7 天";
            case "14d", "last_14d", "recent_14d", "last_14_days" -> "近 14 天";
            case "30d", "last_30d", "recent_30d", "last_30_days" -> "近 30 天";
            case "90d", "last_90d", "recent_90d", "last_90_days" -> "近 90 天";
            case "custom" -> firstText(query.windowKey(), "自定义时间范围");
            default -> "当前筛选窗口";
        };
    }

    private String metricDefaultLabel(String metricKey) {
        return switch (metricKey) {
            case "visibilityScore" -> "AI 可见度";
            case "mentionRate" -> "提及率";
            case "recommendRate" -> "推荐率";
            case "top3Rate" -> "Top3 出现率";
            case "positiveRate" -> "正向率";
            case "citationCount", "totalCitations" -> "引用总量";
            case "citationShare" -> "引用占比";
            case "ownedShare" -> "自有引用占比";
            case "thirdPartyShare" -> "第三方引用占比";
            case "demandTotal" -> "需求总量";
            case "activeTopicCount" -> "活跃主题";
            case "demandVolume" -> "需求量级";
            case "demandShare" -> "需求占比";
            case "promptCount" -> "问题数";
            case "totalProjection" -> "总需求量";
            case "coverage" -> "被引覆盖率";
            case "gapCount" -> "缺口词";
            case "highOpportunityCount" -> "高价值机会";
            default -> "当前结果";
        };
    }

    private WorkbenchDataProvenanceCardView.RunView buildRun(DiagnosisTask task) {
        return WorkbenchDataProvenanceCardView.RunView.builder()
                .runId(task.getId())
                .status(toTaskStatus(task.getStatusCode()))
                .generatedAt(toText(firstTime(task.getCompletedAt(), task.getUpdatedAt(), task.getCreatedAt())))
                .model(task.getTargetModelProduct())
                .engineVersion(task.getEngineVersion())
                .targetProbeCount(task.getTargetProbeCount())
                .completedProbeCount(task.getSamplingCompletedCount())
                .failedProbeCount(task.getSamplingFailedCount())
                .build();
    }

    private WorkbenchDataProvenanceCardView.PlanView buildPlan(ProbePlanRecord record,
                                                               Map<String, Object> runtimeMetadata,
                                                               Map<String, Object> binding,
                                                               List<ProbePlanRecordItem> items) {
        String planVersionId = record == null ? readString(runtimeMetadata, "planVersionId") : record.getPlanVersionId();
        String planRecordId = record == null ? readString(runtimeMetadata, "planRecordId") : record.getId();
        Integer versionNo = firstInt(readInt(runtimeMetadata, "probePlanVersionNo"), readInt(binding, "versionNo"));
        Integer recordNo = record == null
                ? firstInt(readInt(runtimeMetadata, "probePlanRecordNo"), readInt(binding, "recordNo"))
                : record.getRecordNo();
        Integer estimatedCredits = record == null
                ? readInt(binding, "enabledProbeCount")
                : record.getImmediateEstimatedCredits();

        return WorkbenchDataProvenanceCardView.PlanView.builder()
                .planVersionId(planVersionId)
                .planRecordId(planRecordId)
                .planVersionNo(versionNo)
                .planRecordNo(recordNo)
                .changeTypeCode(record == null ? readString(runtimeMetadata, "runTypeCode") : record.getChangeTypeCode())
                .executeModeCode(record == null ? null : record.getExecuteModeCode())
                .estimatedCredits(estimatedCredits)
                .recurringEstimatedCreditsPerCycle(record == null ? null : record.getRecurringEstimatedCreditsPerCycle())
                .topicCount(countDistinctTopics(items))
                .probeCount(items.size())
                .sampleProbes(items.stream()
                        .limit(DETAIL_LIMIT)
                        .map(this::toPlanItemView)
                        .toList())
                .build();
    }

    private WorkbenchDataProvenanceCardView.PlanItemView toPlanItemView(ProbePlanRecordItem item) {
        return WorkbenchDataProvenanceCardView.PlanItemView.builder()
                .probeId(item.getProbeId())
                .topicId(item.getTopicId())
                .seqNo(item.getSeqNoSnapshot())
                .promptText(item.getProbeTextSnapshot())
                .actionCode(item.getActionCode())
                .executeModeCode(item.getExecuteModeCode())
                .build();
    }

    private WorkbenchDataProvenanceCardView.EvidenceView buildEvidence(List<DiagnosisProbeRecord> probes,
                                                                       List<ProbeDispatchAttempt> attempts,
                                                                       List<ProbeEvidenceRecord> citations,
                                                                       List<ProbeBrandMentionRecord> mentions,
                                                                       List<String> selectedProbeIds) {
        return WorkbenchDataProvenanceCardView.EvidenceView.builder()
                .probeCount(countDistinctProbeIds(probes, citations, mentions))
                .selectedProbeCount(selectedProbeIds == null ? 0 : selectedProbeIds.size())
                .attemptCount(attempts.size())
                .citationCount(citations.size())
                .brandMentionCount(mentions.size())
                .probes(probes.stream().map(this::toProbeView).toList())
                .citations(citations.stream().map(this::toCitationView).toList())
                .brandMentions(mentions.stream().map(this::toBrandMentionView).toList())
                .build();
    }

    private WorkbenchDataProvenanceCardView.SamplePageView buildSamplePage(BrandScope scope,
                                                                           WorkbenchDataProvenanceQuery query) {
        if (scope == null || query == null || !isOfficialFirstBatchQuery(query)) {
            return null;
        }
        KnowledgeAssetEvidenceRepository repository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
        if (repository == null) {
            return WorkbenchDataProvenanceCardView.SamplePageView.builder()
                    .sourceStatus("unavailable")
                    .sourceStatusLabel("样本层未接通")
                    .sourceNote("当前环境未启用知识资产样本仓，无法分页查看回答样本。")
                    .page(resolveSamplePage(query))
                    .pageSize(resolveSamplePageSize(query))
                    .pageCount(0)
                    .totalCount(0)
                    .effectiveAnswerCount(0)
                    .excludedAnswerCount(0)
                    .mentionHitCount(0)
                    .recommendHitCount(0)
                    .top3HitCount(0)
                    .rows(List.of())
                    .build();
        }
        WindowRange windowRange = resolveWindowRange(query);
        int page = resolveSamplePage(query);
        int pageSize = resolveSamplePageSize(query);
        KnowledgeAssetEvidenceRepository.MetricSampleRowsResult result = repository.listMetricSampleRows(
                scope.getId(),
                scope.getScopeName(),
                windowRange.startAt(),
                windowRange.endAt(),
                (page - 1) * pageSize,
                pageSize
        );
        int totalCount = safeInt(result.totalCount());
        int pageCount = totalCount <= 0 ? 0 : (int) Math.ceil(totalCount * 1.0d / pageSize);
        SampleSourceStatus sourceStatus = resolveSampleSourceStatus(query, result);
        return WorkbenchDataProvenanceCardView.SamplePageView.builder()
                .sourceStatus(sourceStatus.status())
                .sourceStatusLabel(sourceStatus.label())
                .sourceNote(sourceStatus.note())
                .page(page)
                .pageSize(pageSize)
                .pageCount(pageCount)
                .totalCount(totalCount)
                .effectiveAnswerCount(result.effectiveAnswerCount())
                .excludedAnswerCount(result.excludedAnswerCount())
                .mentionHitCount(result.mentionHitCount())
                .recommendHitCount(result.recommendHitCount())
                .top3HitCount(result.top3HitCount())
                .rows(result.rows().stream().map(this::toAnswerSampleView).toList())
                .build();
    }

    private FirstBatchMetricTruth resolveFirstBatchMetricTruth(String metricKey,
                                                               WorkbenchDataProvenanceCardView.SamplePageView samplePage) {
        if (!FIRST_BATCH_METRICS.contains(trimToNull(metricKey)) || samplePage == null) {
            return null;
        }
        int denominator = safeInt(samplePage.getEffectiveAnswerCount());
        if (denominator <= 0) {
            return null;
        }
        int mentionHitCount = safeInt(samplePage.getMentionHitCount());
        int recommendHitCount = safeInt(samplePage.getRecommendHitCount());
        int top3HitCount = safeInt(samplePage.getTop3HitCount());
        double mentionRate = percentage(mentionHitCount, denominator);
        double recommendRate = percentage(recommendHitCount, denominator);
        double top3Rate = percentage(top3HitCount, denominator);
        double visibilityScore = round2(mentionRate * 0.35d + recommendRate * 0.40d + top3Rate * 0.25d);
        return new FirstBatchMetricTruth(
                denominator,
                mentionHitCount,
                recommendHitCount,
                top3HitCount,
                mentionRate,
                recommendRate,
                top3Rate,
                visibilityScore
        );
    }

    private boolean isOfficialFirstBatchQuery(WorkbenchDataProvenanceQuery query) {
        if (query == null || !FIRST_BATCH_METRICS.contains(trimToNull(query.metricKey()))) {
            return false;
        }
        if (!"dashboard_metric".equals(trimToNull(query.anchorType()))) {
            return false;
        }
        return !StringUtils.hasText(query.topicId())
                && !StringUtils.hasText(query.probeId())
                && !StringUtils.hasText(query.platform())
                && !StringUtils.hasText(query.domain());
    }

    private PendingMetricStatus resolvePendingMetricStatus(WorkbenchDataProvenanceQuery query, String metricKey) {
        String anchorType = trimToNull(query == null ? null : query.anchorType());
        if (FIRST_BATCH_METRICS.contains(trimToNull(metricKey))) {
            return new PendingMetricStatus(
                    "second_batch",
                    "第二批：维度口径待接入",
                    "这个指标已经有总览口径，但当前入口带有模型、平台、Topic、竞品或其他维度；后端还没有返回该维度下的回答级样本过滤，所以暂不展示正式公式和样本。",
                    "这一版先保护数据真相：只在首页当前表现展示完整溯源，维度卡片等样本过滤接通后再开放。"
            );
        }
        if ("citationShare".equals(metricKey) || "citationCount".equals(metricKey) || "totalCitations".equals(metricKey)) {
            return new PendingMetricStatus(
                    "second_batch",
                    "第二批：引用证据待接入",
                    "引用类指标需要按 URL、域名和回答证据重新聚合；当前入口暂不使用通用探针列表替代引用事实表。",
                    "后续接入引用证据集合后，再展示来源次数、总引用次数和可下钻 URL。"
            );
        }
        if ("positiveRate".equals(metricKey)
                || "demandTotal".equals(metricKey)
                || "activeTopicCount".equals(metricKey)
                || "demandVolume".equals(metricKey)
                || "demandShare".equals(metricKey)
                || "promptCount".equals(metricKey)
                || "totalProjection".equals(metricKey)
                || "coverage".equals(metricKey)
                || "gapCount".equals(metricKey)
                || "highOpportunityCount".equals(metricKey)) {
            return new PendingMetricStatus(
                    "not_in_scope",
                    "本期不做正式溯源",
                    "这个入口依赖需求估算、情绪、覆盖关系或机会规则，5.0.4 本批次不把它伪装成已验证数据。",
                    "页面可以继续展示业务指标，但数字穿透会等规则和后端对象稳定后再开放。"
            );
        }
        return new PendingMetricStatus(
                "pending",
                "口径待接入",
                "当前入口还没有稳定的数据溯源定义。",
                "需要先补齐后端对象、计算口径和样本规则，再开放正式穿透。"
        );
    }

    private String formatMetricValue(String metricKey, FirstBatchMetricTruth truth) {
        if (truth == null) {
            return null;
        }
        return switch (metricKey) {
            case "visibilityScore" -> formatDecimal(truth.visibilityScore());
            case "mentionRate" -> formatPercent(truth.mentionRate());
            case "recommendRate" -> formatPercent(truth.recommendRate());
            case "top3Rate" -> formatPercent(truth.top3Rate());
            default -> null;
        };
    }

    private WorkbenchDataProvenanceCardView.AnswerSampleView toAnswerSampleView(
            KnowledgeAssetEvidenceRepository.MetricSampleRowView row) {
        return WorkbenchDataProvenanceCardView.AnswerSampleView.builder()
                .rowNo(row.rowNo())
                .runId(row.sourceTaskId())
                .sourceTaskId(row.sourceTaskId())
                .generatedAt(toText(KnowledgeAssetTimeSupport.toProductLocalDateTime(row.observedAt())))
                .probeId(row.probeId())
                .sourceProbeId(row.sourceProbeId())
                .attemptId(row.attemptId())
                .probeRef(row.probeRef())
                .seqNo(row.seqNo())
                .topicId(row.topicCatalogId())
                .topicName(row.topicName())
                .promptText(row.promptText())
                .answerSnippet(truncate(row.answerPreview(), 180))
                .provider(row.provider())
                .modelId(row.modelId())
                .effectiveAnswer(row.effectiveAnswer())
                .excludedReason(row.excludedReason())
                .mentioned(row.mentioned())
                .recommended(row.recommended())
                .top3(row.top3())
                .recommendationRank(row.recommendationRank())
                .detailRouteKey("probe_analysis")
                .detailProbeId(firstText(row.sourceProbeId(), row.probeId()))
                .detailAttemptId(row.attemptId())
                .build();
    }

    private SampleSourceStatus resolveSampleSourceStatus(WorkbenchDataProvenanceQuery query,
                                                         KnowledgeAssetEvidenceRepository.MetricSampleRowsResult result) {
        if (result == null || safeInt(result.totalCount()) <= 0) {
            return new SampleSourceStatus(
                    "empty",
                    "暂无样本",
                    "当前筛选窗口没有可展示的回答样本。"
            );
        }
        List<String> mismatches = new ArrayList<>();
        Integer contextSampleSize = query.contextSampleSize();
        if (contextSampleSize != null && Math.abs(contextSampleSize - safeInt(result.effectiveAnswerCount())) > 1) {
            mismatches.add("有效回答数");
        }
        String metricKey = trimToNull(query.metricKey());
        if ("visibilityScore".equals(metricKey) || "mentionRate".equals(metricKey)) {
            compareHitCount(mismatches, "提及命中", query.contextMentionRate(), contextSampleSize, result.mentionHitCount());
        }
        if ("visibilityScore".equals(metricKey) || "recommendRate".equals(metricKey)) {
            compareHitCount(mismatches, "推荐命中", query.contextRecommendRate(), contextSampleSize, result.recommendHitCount());
        }
        if ("visibilityScore".equals(metricKey) || "top3Rate".equals(metricKey)) {
            compareHitCount(mismatches, "Top3 命中", query.contextTop3Rate(), contextSampleSize, result.top3HitCount());
        }
        if (!mismatches.isEmpty()) {
            return new SampleSourceStatus(
                    "metric_context_mismatch",
                    "样本已接通，聚合口径待统一",
                    "当前列表来自回答级样本；上方数字仍沿用现有窗口聚合口径，" + String.join("、", mismatches) + "与回答级样本不完全一致。"
            );
        }
        return new SampleSourceStatus(
                "connected",
                "样本已接通",
                "当前列表按同一品牌空间和时间窗口返回回答样本，支持分页查看。"
        );
    }

    private void compareHitCount(List<String> mismatches,
                                 String label,
                                 Double rate,
                                 Integer denominator,
                                 Integer actualCount) {
        if (rate == null || denominator == null || denominator <= 0 || actualCount == null) {
            return;
        }
        int expected = estimateNumerator(normalizePercent(rate), denominator);
        if (Math.abs(expected - actualCount) > 1) {
            mismatches.add(label);
        }
    }

    private WindowRange resolveWindowRange(WorkbenchDataProvenanceQuery query) {
        LocalDate today = LocalDate.now(PRODUCT_ZONE_ID);
        LocalDate start = parseDate(query == null ? null : query.dateFrom());
        LocalDate end = parseDate(query == null ? null : query.dateTo());
        if (start == null || end == null) {
            String preset = trimToNull(query == null ? null : query.timePreset());
            switch (preset == null ? "" : preset) {
                case "today" -> {
                    start = today;
                    end = today;
                }
                case "yesterday" -> {
                    start = today.minusDays(1);
                    end = today.minusDays(1);
                }
                case "7d", "last_7d", "recent_7d", "last_7_days" -> {
                    start = today.minusDays(6);
                    end = today;
                }
                case "30d", "last_30d", "recent_30d", "last_30_days" -> {
                    start = today.minusDays(29);
                    end = today;
                }
                case "90d", "last_90d", "recent_90d", "last_90_days" -> {
                    start = today.minusDays(89);
                    end = today;
                }
                default -> {
                    start = today.minusDays(13);
                    end = today;
                }
            }
        }
        if (end.isBefore(start)) {
            LocalDate swapped = start;
            start = end;
            end = swapped;
        }
        return new WindowRange(
                start.atStartOfDay(PRODUCT_ZONE_ID).toOffsetDateTime(),
                end.atTime(LocalTime.MAX).atZone(PRODUCT_ZONE_ID).toOffsetDateTime()
        );
    }

    private LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private int resolveSamplePage(WorkbenchDataProvenanceQuery query) {
        Integer page = query == null ? null : query.samplePage();
        return page == null || page < 1 ? 1 : page;
    }

    private int resolveSamplePageSize(WorkbenchDataProvenanceQuery query) {
        Integer pageSize = query == null ? null : query.samplePageSize();
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_SAMPLE_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_SAMPLE_PAGE_SIZE);
    }

    private WorkbenchDataProvenanceCardView.ProbeView toProbeView(DiagnosisProbeRecord probe) {
        return WorkbenchDataProvenanceCardView.ProbeView.builder()
                .probeId(probe.getId())
                .probeRef(probe.getProbeRef())
                .seqNo(probe.getSeqNo())
                .promptText(probe.getRenderText())
                .status(toProbeStatus(probe.getStatusCode()))
                .build();
    }

    private WorkbenchDataProvenanceCardView.CitationView toCitationView(ProbeEvidenceRecord evidence) {
        return WorkbenchDataProvenanceCardView.CitationView.builder()
                .evidenceId(evidence.getId())
                .probeId(evidence.getProbeId())
                .title(evidence.getTitle())
                .domain(evidence.getDomain())
                .url(evidence.getUrl())
                .sourceType(evidence.getSourceTypeCode())
                .capturedAt(toText(firstTime(evidence.getRetrievedAt(), evidence.getCreatedAt())))
                .build();
    }

    private WorkbenchDataProvenanceCardView.BrandMentionView toBrandMentionView(ProbeBrandMentionRecord mention) {
        return WorkbenchDataProvenanceCardView.BrandMentionView.builder()
                .probeId(mention.getProbeId())
                .brandName(mention.getBrandName())
                .mentioned(mention.getMentionedFlag())
                .recommended(mention.getRecommendedFlag())
                .positionIndex(mention.getPositionIndex())
                .sentimentCode(mention.getSentimentCode())
                .build();
    }

    private WorkbenchDataProvenanceCardView.BillingView buildBilling(List<QuotaLedger> ledgers) {
        Long latestBalance = ledgers.isEmpty() ? null : ledgers.get(0).getBalanceAfter();
        return WorkbenchDataProvenanceCardView.BillingView.builder()
                .ledgerCount(ledgers.size())
                .consumedCredits(ledgers.stream()
                        .map(QuotaLedger::getProbeDelta)
                        .filter(Objects::nonNull)
                        .filter(delta -> delta < 0)
                        .mapToLong(delta -> -delta)
                        .sum())
                .latestBalanceAfter(latestBalance)
                .ledgers(ledgers.stream().map(this::toLedgerView).toList())
                .build();
    }

    private WorkbenchDataProvenanceCardView.LedgerView toLedgerView(QuotaLedger ledger) {
        return WorkbenchDataProvenanceCardView.LedgerView.builder()
                .ledgerId(ledger.getId())
                .ledgerTypeCode(ledger.getLedgerTypeCode())
                .probeDelta(ledger.getProbeDelta())
                .balanceAfter(ledger.getBalanceAfter())
                .sourceTypeCode(ledger.getSourceTypeCode())
                .createdAt(toText(ledger.getCreatedAt()))
                .memo(ledger.getMemo())
                .build();
    }

    private <T> void applyProbeFilter(LambdaQueryWrapper<T> wrapper,
                                      com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> column,
                                      List<String> probeIds) {
        if (probeIds != null && !probeIds.isEmpty()) {
            wrapper.in(column, probeIds);
        }
    }

    private Map<String, Object> readProbePlanBinding(Map<String, Object> taskOrderJson) {
        Object businessHints = safeMap(taskOrderJson).get("businessHints");
        Object binding = businessHints instanceof Map<?, ?> hints ? hints.get("probePlanBinding") : null;
        return safeMap(binding);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private String readString(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInt(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer firstInt(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private int firstPositive(Integer... values) {
        if (values == null) {
            return 0;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return 0;
    }

    private Integer firstPositiveNullable(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private double normalizePercent(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return 0;
        }
        return value <= 1.0 && value >= 0 ? value * 100.0 : value;
    }

    private double percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return round2(numerator * 100.0d / denominator);
    }

    private double round2(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.round(value * 100.0d) / 100.0d;
    }

    private Integer estimateNumerator(double percent, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return (int) Math.round(percent * denominator / 100.0);
    }

    private String formatPercent(double value) {
        return formatDecimal(value) + "%";
    }

    private String formatNullableNumber(Number value) {
        return value == null ? null : formatNumber(value);
    }

    private String formatNumber(Number value) {
        if (value == null) {
            return null;
        }
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric)) {
            return null;
        }
        if (Math.abs(numeric - Math.rint(numeric)) < 0.000001) {
            return String.valueOf((long) Math.rint(numeric));
        }
        return formatDecimal(numeric);
    }

    private String formatDecimal(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String toText(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    private LocalDateTime firstTime(LocalDateTime... values) {
        if (values == null) {
            return null;
        }
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int countDistinctTopics(List<ProbePlanRecordItem> items) {
        Set<String> topicIds = new LinkedHashSet<>();
        for (ProbePlanRecordItem item : items) {
            if (StringUtils.hasText(item.getTopicId())) {
                topicIds.add(item.getTopicId());
            }
        }
        return topicIds.size();
    }

    private int countDistinctProbeIds(List<DiagnosisProbeRecord> probes,
                                      List<ProbeEvidenceRecord> citations,
                                      List<ProbeBrandMentionRecord> mentions) {
        Set<String> probeIds = new LinkedHashSet<>();
        for (DiagnosisProbeRecord probe : probes) {
            if (StringUtils.hasText(probe.getId())) {
                probeIds.add(probe.getId());
            }
        }
        for (ProbeEvidenceRecord citation : citations) {
            if (StringUtils.hasText(citation.getProbeId())) {
                probeIds.add(citation.getProbeId());
            }
        }
        for (ProbeBrandMentionRecord mention : mentions) {
            if (StringUtils.hasText(mention.getProbeId())) {
                probeIds.add(mention.getProbeId());
            }
        }
        return probeIds.size();
    }

    private String toTaskStatus(Integer statusCode) {
        if (statusCode == null) {
            return "unknown";
        }
        return switch (statusCode) {
            case 0 -> "pending";
            case 1 -> "running";
            case 2 -> "completed";
            case 3 -> "failed";
            case 4 -> "cancelled";
            default -> "unknown";
        };
    }

    private String toProbeStatus(Integer statusCode) {
        if (statusCode == null) {
            return "unknown";
        }
        return switch (statusCode) {
            case 0 -> "pending";
            case 1 -> "running";
            case 2 -> "completed";
            case 3 -> "failed";
            default -> "unknown";
        };
    }

    private record WindowRange(OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    private record SampleSourceStatus(String status, String label, String note) {
    }

    private record PendingMetricStatus(String status, String label, String meaning, String note) {
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
}

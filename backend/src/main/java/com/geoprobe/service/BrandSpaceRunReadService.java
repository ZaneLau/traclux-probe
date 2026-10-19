package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.BrandSpaceRunDetailView;
import com.geoprobe.dto.brandspace.BrandSpaceRunHistoryView;
import com.geoprobe.dto.brandspace.BrandSpaceRunPlanTraceView;
import com.geoprobe.dto.brandspace.RunEventPageView;
import com.geoprobe.dto.brandspace.RunEventView;
import com.geoprobe.dto.brandspace.RunView;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeDispatchAttempt;
import com.geoprobe.entity.v1.ProbeEvidenceRecord;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanVersion;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunEventRepository;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeDispatchAttemptMapper;
import com.geoprobe.mapper.v1.ProbeEvidenceRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanVersionMapper;
import com.geoprobe.mapper.v1.QuotaLedgerMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BrandSpaceRunReadService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int DEFAULT_WINDOW_DAYS = 14;
    private static final int MAX_HISTORY_ROWS = 200;
    private static final int MAX_EVENT_ROWS = 300;
    private static final String LEDGER_CONSUME = "CONSUME";

    private final ServiceAccessService serviceAccessService;
    private final BrandScopeMapper brandScopeMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisEventRecordMapper diagnosisEventRecordMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final ProbeDispatchAttemptMapper probeDispatchAttemptMapper;
    private final ProbeEvidenceRecordMapper probeEvidenceRecordMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final ProbePlanRecordMapper probePlanRecordMapper;
    private final ProbePlanVersionMapper probePlanVersionMapper;
    private final QuotaLedgerMapper quotaLedgerMapper;
    private final ObjectProvider<KnowledgeAssetRunEventRepository> knowledgeAssetRunEventRepositoryProvider;
    private final RunReadService runReadService;

    public BrandSpaceRunHistoryView listRuns(String userId,
                                             String brandSpaceId,
                                             String dateFrom,
                                             String dateTo) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId);
        DateWindow dateWindow = resolveDateWindow(dateFrom, dateTo);
        List<DiagnosisTask> tasks = diagnosisTaskMapper.selectList(new LambdaQueryWrapper<DiagnosisTask>()
                .eq(DiagnosisTask::getBrandScopeId, context.scope().getId())
                .ge(DiagnosisTask::getCreatedAt, dateWindow.fromDateTime())
                .lt(DiagnosisTask::getCreatedAt, dateWindow.toExclusive())
                .orderByDesc(DiagnosisTask::getCreatedAt)
                .orderByDesc(DiagnosisTask::getUpdatedAt)
                .last("limit " + MAX_HISTORY_ROWS));
        if (tasks == null || tasks.isEmpty()) {
            return BrandSpaceRunHistoryView.builder()
                    .brandSpaceId(context.scope().getId())
                    .brandName(resolveBrandName(context.scope(), null))
                    .dateFrom(dateWindow.dateFrom().format(DATE_FORMATTER))
                    .dateTo(dateWindow.dateTo().format(DATE_FORMATTER))
                    .totalRuns(0)
                    .items(List.of())
                    .build();
        }

        List<String> runIds = tasks.stream()
                .map(DiagnosisTask::getId)
                .filter(StringUtils::hasText)
                .toList();
        Map<String, Integer> probeCountByRunId = countProbesByRunId(runIds);
        Map<String, Integer> evidenceCountByRunId = countEvidenceByRunId(runIds);
        Map<String, DiagnosisReportRecord> reportByRunId = loadLatestReportsByRunId(context.scope().getId(), runIds);

        List<BrandSpaceRunHistoryView.RunListItemView> items = tasks.stream()
                .map(task -> toRunHistoryItem(task, reportByRunId.get(task.getId()), probeCountByRunId, evidenceCountByRunId))
                .toList();
        return BrandSpaceRunHistoryView.builder()
                .brandSpaceId(context.scope().getId())
                .brandName(resolveBrandName(context.scope(), tasks.getFirst()))
                .dateFrom(dateWindow.dateFrom().format(DATE_FORMATTER))
                .dateTo(dateWindow.dateTo().format(DATE_FORMATTER))
                .totalRuns(items.size())
                .items(items)
                .build();
    }

    public BrandSpaceRunDetailView getRunDetail(String userId,
                                                String brandSpaceId,
                                                String runId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId);
        RunView runView = runReadService.getRunForBrandSpace(context.organizationId(), context.scope().getId(), runId);
        RunEventPageView eventPage = runReadService.getRunEventsForBrandSpace(
                context.organizationId(),
                context.scope().getId(),
                runId,
                null,
                MAX_EVENT_ROWS
        );
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId.trim());
        if (task == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        DiagnosisReportRecord report = loadLatestReportByRunId(task.getId(), context.scope().getId());
        Map<String, Integer> probeCountByRunId = countProbesByRunId(List.of(task.getId()));
        Map<String, Integer> evidenceCountByRunId = countEvidenceByRunId(List.of(task.getId()));
        Map<String, Object> taskOrder = defaultMap(task.getTaskOrderJson());
        Map<String, Object> businessHints = readObjectMap(taskOrder.get("businessHints"));
        Map<String, Object> scheduleDecision = readObjectMap(businessHints.get("scheduleDecision"));
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(taskOrder);

        return BrandSpaceRunDetailView.builder()
                .brandSpaceId(context.scope().getId())
                .brandName(resolveBrandName(context.scope(), task))
                .runId(task.getId())
                .overview(buildOverview(task, report, executionPlan, probeCountByRunId, evidenceCountByRunId, runView))
                .planTrace(buildPlanTrace(task, executionPlan))
                .scheduleDecision(buildScheduleDecision(executionPlan))
                .executionRecord(buildExecutionRecord(executionPlan, task, probeCountByRunId, eventPage))
                .rawRecord(buildRawRecord(task, report, scheduleDecision, eventPage))
                .build();
    }

    public BrandSpaceRunDetailView getRunDetailForAdmin(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "运行记录不存在");
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId.trim());
        if (task == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        BrandScope scope = StringUtils.hasText(task.getBrandScopeId())
                ? brandScopeMapper.selectById(task.getBrandScopeId().trim())
                : null;
        RunView runView = runReadService.getRunForAdmin(runId.trim());
        RunEventPageView eventPage = runReadService.getRunEventsForAdmin(runId.trim(), null, MAX_EVENT_ROWS);
        DiagnosisReportRecord report = loadLatestReportByRunId(task.getId(), task.getBrandScopeId());
        Map<String, Integer> probeCountByRunId = countProbesByRunId(List.of(task.getId()));
        Map<String, Integer> evidenceCountByRunId = countEvidenceByRunId(List.of(task.getId()));
        Map<String, Object> taskOrder = defaultMap(task.getTaskOrderJson());
        Map<String, Object> businessHints = readObjectMap(taskOrder.get("businessHints"));
        Map<String, Object> scheduleDecision = readObjectMap(businessHints.get("scheduleDecision"));
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(taskOrder);

        return BrandSpaceRunDetailView.builder()
                .brandSpaceId(task.getBrandScopeId())
                .brandName(resolveBrandName(scope, task))
                .runId(task.getId())
                .overview(buildOverview(task, report, executionPlan, probeCountByRunId, evidenceCountByRunId, runView))
                .planTrace(buildPlanTrace(task, executionPlan))
                .scheduleDecision(buildScheduleDecision(executionPlan))
                .executionRecord(buildExecutionRecord(executionPlan, task, probeCountByRunId, eventPage))
                .rawRecord(buildRawRecord(task, report, scheduleDecision, eventPage))
                .build();
    }

    private BrandSpaceRunHistoryView.RunListItemView toRunHistoryItem(DiagnosisTask task,
                                                                      DiagnosisReportRecord report,
                                                                      Map<String, Integer> probeCountByRunId,
                                                                      Map<String, Integer> evidenceCountByRunId) {
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = readExecutionPlan(task);
        LocalDateTime anchor = resolveRunAnchor(task, report);
        String triggerMode = executionPlan == null ? null : executionPlan.triggerMode();
        String status = mapRunStatus(task);
        return BrandSpaceRunHistoryView.RunListItemView.builder()
                .runId(task.getId())
                .runDate(formatDate(anchor))
                .startedAt(formatDateTime(task.getStartedAt()))
                .completedAt(formatDateTime(task.getCompletedAt()))
                .updatedAt(formatDateTime(resolveUpdatedAt(task, report)))
                .triggerMode(triggerMode)
                .triggerLabel(mapTriggerLabel(triggerMode))
                .status(status)
                .statusLabel(mapStatusLabel(status))
                .plannedProbeCount(resolvePlannedProbeCount(task, executionPlan))
                .completedProbeCount(resolveCompletedProbeCount(task, probeCountByRunId.get(task.getId())))
                .failedProbeCount(defaultInt(task.getSamplingFailedCount()))
                .coverageTopicCount(executionPlan == null ? 0 : executionPlan.selectedTopics().size())
                .evidenceCount(evidenceCountByRunId.getOrDefault(task.getId(), 0))
                .schedulingReason(executionPlan == null ? null : executionPlan.humanReason())
                .planTrace(buildPlanTrace(task, executionPlan))
                .build();
    }

    private BrandSpaceRunDetailView.OverviewView buildOverview(DiagnosisTask task,
                                                               DiagnosisReportRecord report,
                                                               BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                               Map<String, Integer> probeCountByRunId,
                                                               Map<String, Integer> evidenceCountByRunId,
                                                               RunView runView) {
        String status = resolveOverviewStatus(task, runView);
        LocalDateTime anchor = resolveRunAnchor(task, report);
        LocalDateTime updatedAt = resolveUpdatedAt(task, report);
        return BrandSpaceRunDetailView.OverviewView.builder()
                .runDate(formatDate(anchor))
                .startedAt(resolveOverviewStartedAt(task, runView))
                .completedAt(resolveOverviewCompletedAt(task, runView))
                .updatedAt(formatDateTime(updatedAt))
                .durationSeconds(resolveDurationSeconds(task, updatedAt))
                .triggerMode(executionPlan == null ? null : executionPlan.triggerMode())
                .triggerLabel(mapTriggerLabel(executionPlan == null ? null : executionPlan.triggerMode()))
                .status(status)
                .statusLabel(mapStatusLabel(status))
                .plannedProbeCount(resolvePlannedProbeCount(task, executionPlan))
                .completedProbeCount(resolveCompletedProbeCount(task, probeCountByRunId.get(task.getId())))
                .failedProbeCount(defaultInt(task.getSamplingFailedCount()))
                .evidenceCount(evidenceCountByRunId.getOrDefault(task.getId(), 0))
                .coverageTopicCount(executionPlan == null ? 0 : executionPlan.selectedTopics().size())
                .reportGeneratedAt(formatDateTime(report == null ? null : report.getGeneratedAt()))
                .failureMessage(defaultIfBlank(task.getFailureMessage(), null))
                .build();
    }

    private BrandSpaceRunDetailView.ScheduleDecisionView buildScheduleDecision(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null) {
            return BrandSpaceRunDetailView.ScheduleDecisionView.builder()
                    .snapshotAvailable(false)
                    .selectedTopics(List.of())
                    .deferredTopics(List.of())
                    .build();
        }
        return BrandSpaceRunDetailView.ScheduleDecisionView.builder()
                .snapshotAvailable(executionPlan.targetProbeCount() > 0)
                .coverageStrategy(executionPlan.coverageStrategy())
                .schedulingReason(resolveUserFacingScheduleReason(executionPlan))
                .coldStart(executionPlan.coldStart())
                .justEdited(executionPlan.justEdited())
                .weeklyTargetTopicCount(executionPlan.weeklyTargetTopicCount())
                .weeklyCoveredTopicCount(executionPlan.weeklyCoveredTopicCount())
                .weeklyGapTopicCount(executionPlan.weeklyGapTopicCount())
                .weeklyCoverageRatio(executionPlan.weeklyCoverageRatio())
                .selectedTopics(toTopicDecisionViews(executionPlan.selectedTopics()))
                .deferredTopics(toTopicDecisionViews(executionPlan.deferredTopics()))
                .build();
    }

    private BrandSpaceRunDetailView.ExecutionRecordView buildExecutionRecord(BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                                             DiagnosisTask task,
                                                                             Map<String, Integer> probeCountByRunId,
                                                                             RunEventPageView eventPage) {
        List<DiagnosisProbeRecord> probes = loadRunProbes(task == null ? null : task.getId());
        Map<String, TopicPromptBundle> promptBundleByTopic = buildPromptBundleByTopic(executionPlan, probes);
        List<BrandSpaceRunDetailView.ProbeExecutionView> probeExecutions = buildProbeExecutions(task, probes, executionPlan);
        List<BrandSpaceRunDetailView.TopicExecutionView> topicExecutions = new ArrayList<>();
        if (executionPlan != null && !executionPlan.selectedTopics().isEmpty()) {
            for (BrandSpaceSchedulingService.TopicDecision topicDecision : executionPlan.selectedTopics()) {
                TopicPromptBundle promptBundle = resolveTopicPromptBundle(promptBundleByTopic, topicDecision.topicId(), topicDecision.topicName());
                topicExecutions.add(BrandSpaceRunDetailView.TopicExecutionView.builder()
                        .topicId(topicDecision.topicId())
                        .topicName(topicDecision.topicName())
                        .plannedProbeCount(topicDecision.allocatedProbeCount())
                        .promptCount(promptBundle == null ? 0 : promptBundle.promptCount())
                        .promptSamples(promptBundle == null ? List.of() : promptBundle.promptSamples())
                        .priorityScore(topicDecision.priorityScore())
                        .reasonTags(topicDecision.reasonTags())
                        .build());
            }
        } else {
            for (TopicPromptBundle bundle : promptBundleByTopic.values()) {
                topicExecutions.add(BrandSpaceRunDetailView.TopicExecutionView.builder()
                        .topicId(bundle.topicId())
                        .topicName(bundle.topicName())
                        .plannedProbeCount(null)
                        .promptCount(bundle.promptCount())
                        .promptSamples(bundle.promptSamples())
                        .priorityScore(null)
                        .reasonTags(List.of())
                        .build());
            }
        }
        return BrandSpaceRunDetailView.ExecutionRecordView.builder()
                .selectedTopicCount(executionPlan == null ? 0 : executionPlan.selectedTopics().size())
                .deferredTopicCount(executionPlan == null ? 0 : executionPlan.deferredTopics().size())
                .completedProbeCount(resolveCompletedProbeCount(task, probeCountByRunId.get(task.getId())))
                .failedProbeCount(defaultInt(task.getSamplingFailedCount()))
                .topicExecutions(topicExecutions)
                .probeExecutions(probeExecutions)
                .milestones(toMilestones(eventPage == null ? List.of() : eventPage.getEvents()))
                .build();
    }

    private BrandSpaceRunDetailView.RawRecordView buildRawRecord(DiagnosisTask task,
                                                                 DiagnosisReportRecord report,
                                                                 Map<String, Object> scheduleDecision,
                                                                 RunEventPageView eventPage) {
        return BrandSpaceRunDetailView.RawRecordView.builder()
                .taskOrder(BrandSpaceTaskOrderSupport.normalizeTaskOrderForRead(
                        defaultMap(task == null ? null : task.getTaskOrderJson())
                ))
                .scheduleDecision(BrandSpaceTaskOrderSupport.normalizeRuntimeMapForRead(scheduleDecision))
                .reportSummary(defaultMap(report == null ? null : report.getSummaryJson()))
                .events(eventPage == null || eventPage.getEvents() == null ? List.of() : eventPage.getEvents())
                .failureMessage(task == null ? null : defaultIfBlank(task.getFailureMessage(), null))
                .build();
    }

    private String resolveUserFacingScheduleReason(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null) {
            return null;
        }
        int selectedTopicCount = executionPlan.selectedTopics() == null ? 0 : executionPlan.selectedTopics().size();
        int deferredTopicCount = executionPlan.deferredTopics() == null ? 0 : executionPlan.deferredTopics().size();
        if ("create_initial_run".equals(defaultIfBlank(executionPlan.triggerMode(), ""))) {
            if (deferredTopicCount > 0) {
                return String.format("这次是新品牌首跑，先执行 %d 个主题；其余 %d 个主题留待后续运行。", selectedTopicCount, deferredTopicCount);
            }
            return String.format("这次是新品牌首跑，已按当前配置执行 %d 个主题。", selectedTopicCount);
        }
        if (executionPlan.justEdited()) {
            if (deferredTopicCount > 0) {
                return String.format("这次优先验证你刚更新的配置，先执行 %d 个主题；其余 %d 个主题留待后续运行。", selectedTopicCount, deferredTopicCount);
            }
            return String.format("这次优先验证你刚更新的配置，已执行 %d 个主题。", selectedTopicCount);
        }
        if (deferredTopicCount > 0) {
            return String.format("这次按当前配置先执行 %d 个主题；其余 %d 个主题留待后续运行。", selectedTopicCount, deferredTopicCount);
        }
        return String.format("这次按当前配置执行 %d 个主题。", selectedTopicCount);
    }

    private BrandSpaceRunPlanTraceView buildPlanTrace(DiagnosisTask task,
                                                      BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (task == null) {
            return null;
        }
        Map<String, Object> taskMetadata = defaultMap(task.getTaskMetadataJson());
        Map<String, Object> taskOrder = defaultMap(task.getTaskOrderJson());
        Map<String, Object> taskOrderMetadata = readObjectMap(taskOrder.get("taskMetadata"));
        Map<String, Object> businessHints = readObjectMap(taskOrder.get("businessHints"));
        Map<String, Object> binding = readObjectMap(businessHints.get("probePlanBinding"));
        ProbePlanRecord planRecord = loadPlanRecordForRun(task);
        ProbePlanVersion planVersion = loadPlanVersion(planRecord);
        List<QuotaLedger> consumeLedgers = loadConsumeLedgers(task.getId());
        QuotaLedger latestLedger = consumeLedgers.isEmpty() ? null : consumeLedgers.getFirst();
        Map<String, Object> ledgerMetadata = defaultMap(latestLedger == null ? null : latestLedger.getLedgerMetadataJson());
        Integer plannedProbeCount = resolvePlannedProbeCount(task, executionPlan);
        CreditEstimate estimatedCredits = resolveEstimatedCredits(binding, planRecord, plannedProbeCount);
        CreditCharge chargedCredits = resolveChargedCredits(consumeLedgers);
        Integer coveredTopicCount = executionPlan == null ? 0 : executionPlan.selectedTopics().size();
        return BrandSpaceRunPlanTraceView.builder()
                .planVersionId(firstText(
                        asText(taskMetadata.get("planVersionId")),
                        asText(taskOrderMetadata.get("planVersionId")),
                        asText(binding.get("planVersionId")),
                        planRecord == null ? null : planRecord.getPlanVersionId(),
                        latestLedger == null ? null : latestLedger.getPlanVersionId()))
                .planVersionNo(firstNonNull(
                        toInteger(taskMetadata.get("probePlanVersionNo")),
                        toInteger(taskOrderMetadata.get("probePlanVersionNo")),
                        toInteger(binding.get("versionNo")),
                        planVersion == null ? null : planVersion.getVersionNo(),
                        toInteger(ledgerMetadata.get("probePlanVersionNo"))))
                .planRecordId(firstText(
                        asText(taskMetadata.get("planRecordId")),
                        asText(taskOrderMetadata.get("planRecordId")),
                        asText(binding.get("planRecordId")),
                        planRecord == null ? null : planRecord.getId(),
                        latestLedger == null ? null : latestLedger.getPlanRecordId()))
                .planRecordNo(firstNonNull(
                        toInteger(taskMetadata.get("probePlanRecordNo")),
                        toInteger(taskOrderMetadata.get("probePlanRecordNo")),
                        toInteger(binding.get("recordNo")),
                        planRecord == null ? null : planRecord.getRecordNo(),
                        toInteger(ledgerMetadata.get("probePlanRecordNo"))))
                .executeModeCode(resolveExecuteModeCode(planRecord, taskMetadata, taskOrderMetadata, binding, executionPlan))
                .estimatedCredits(estimatedCredits.amount())
                .chargedCredits(chargedCredits.amount())
                .creditDelta(resolveCreditDelta(estimatedCredits.amount(), chargedCredits.amount()))
                .creditReconciliationStatus(resolveCreditReconciliationStatus(estimatedCredits.amount(), chargedCredits.amount()))
                .estimatedCreditsSourceCode(estimatedCredits.sourceCode())
                .chargedCreditsSourceCode(chargedCredits.sourceCode())
                .coveredTopicCount(coveredTopicCount)
                .plannedProbeCount(plannedProbeCount)
                .build();
    }

    private ProbePlanRecord loadPlanRecordForRun(DiagnosisTask task) {
        if (task == null || !StringUtils.hasText(task.getBrandScopeId()) || !StringUtils.hasText(task.getId())) {
            return null;
        }
        return probePlanRecordMapper.selectOne(new LambdaQueryWrapper<ProbePlanRecord>()
                .eq(ProbePlanRecord::getBrandScopeId, task.getBrandScopeId().trim())
                .eq(ProbePlanRecord::getTriggeredRunId, task.getId().trim())
                .orderByDesc(ProbePlanRecord::getCreatedAt)
                .last("limit 1"));
    }

    private ProbePlanVersion loadPlanVersion(ProbePlanRecord planRecord) {
        if (planRecord == null || !StringUtils.hasText(planRecord.getPlanVersionId())) {
            return null;
        }
        return probePlanVersionMapper.selectById(planRecord.getPlanVersionId().trim());
    }

    private List<QuotaLedger> loadConsumeLedgers(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        List<QuotaLedger> ledgers = quotaLedgerMapper.selectList(new LambdaQueryWrapper<QuotaLedger>()
                .eq(QuotaLedger::getDiagnosisTaskId, runId.trim())
                .eq(QuotaLedger::getLedgerTypeCode, LEDGER_CONSUME)
                .orderByDesc(QuotaLedger::getCreatedAt));
        return ledgers == null ? List.of() : ledgers;
    }

    private CreditEstimate resolveEstimatedCredits(Map<String, Object> binding,
                                                   ProbePlanRecord planRecord,
                                                   Integer plannedProbeCount) {
        Integer bindingProbeCount = toInteger(binding.get("enabledProbeCount"));
        if (bindingProbeCount != null) {
            return new CreditEstimate(bindingProbeCount, "PROBE_PLAN_BINDING");
        }
        Integer planRecordCredits = planRecord == null ? null : planRecord.getImmediateEstimatedCredits();
        if (planRecordCredits != null) {
            return new CreditEstimate(planRecordCredits, "PROBE_PLAN_RECORD");
        }
        if (plannedProbeCount != null) {
            return new CreditEstimate(plannedProbeCount, "RUN_PLANNED_PROBE_COUNT");
        }
        return new CreditEstimate(null, null);
    }

    private CreditCharge resolveChargedCredits(List<QuotaLedger> ledgers) {
        if (ledgers == null || ledgers.isEmpty()) {
            return new CreditCharge(null, null);
        }
        long total = ledgers.stream()
                .filter(Objects::nonNull)
                .map(QuotaLedger::getProbeDelta)
                .filter(Objects::nonNull)
                .mapToLong(Math::abs)
                .sum();
        Integer amount = total > 0 && total <= Integer.MAX_VALUE ? (int) total : null;
        return new CreditCharge(amount, amount == null ? null : "QUOTA_LEDGER_CONSUME");
    }

    private String resolveExecuteModeCode(ProbePlanRecord planRecord,
                                          Map<String, Object> taskMetadata,
                                          Map<String, Object> taskOrderMetadata,
                                          Map<String, Object> binding,
                                          BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        String explicitMode = firstText(
                planRecord == null ? null : planRecord.getExecuteModeCode(),
                asText(taskMetadata.get("executeModeCode")),
                asText(taskOrderMetadata.get("executeModeCode")),
                asText(binding.get("executeModeCode"))
        );
        if (StringUtils.hasText(explicitMode)) {
            return explicitMode.trim().toUpperCase();
        }

        String runTypeCode = firstText(asText(taskMetadata.get("runTypeCode")), asText(taskOrderMetadata.get("runTypeCode")));
        if ("SCHEDULED".equalsIgnoreCase(runTypeCode)) {
            return "RECURRING";
        }
        if ("MANUAL_ONCE".equalsIgnoreCase(runTypeCode) || "CONTROLLED_RERUN".equalsIgnoreCase(runTypeCode)) {
            return "ONE_TIME";
        }

        String triggerSource = firstText(asText(taskMetadata.get("triggerSource")), asText(taskOrderMetadata.get("triggerSource")));
        if ("brand_space_schedule".equalsIgnoreCase(triggerSource)) {
            return "RECURRING";
        }
        if ("brand_space_manual_execute_once".equalsIgnoreCase(triggerSource)
                || "brand_space_controlled_rerun".equalsIgnoreCase(triggerSource)) {
            return "ONE_TIME";
        }
        String triggerMode = executionPlan == null ? null : executionPlan.triggerMode();
        if (StringUtils.hasText(triggerMode)) {
            String normalizedTrigger = triggerMode.trim().toLowerCase();
            if (normalizedTrigger.contains("manual") || normalizedTrigger.contains("rerun")) {
                return "ONE_TIME";
            }
            if (normalizedTrigger.contains("scheduled_tracking")) {
                return "RECURRING";
            }
        }
        return null;
    }

    private Integer resolveCreditDelta(Integer estimatedCredits, Integer chargedCredits) {
        if (estimatedCredits == null || chargedCredits == null) {
            return null;
        }
        return chargedCredits - estimatedCredits;
    }

    private String resolveCreditReconciliationStatus(Integer estimatedCredits, Integer chargedCredits) {
        if (estimatedCredits == null && chargedCredits == null) {
            return "UNAVAILABLE";
        }
        if (estimatedCredits == null || chargedCredits == null) {
            return "PENDING";
        }
        return Objects.equals(estimatedCredits, chargedCredits) ? "MATCHED" : "DIFFERENT";
    }

    private record CreditEstimate(Integer amount, String sourceCode) {
    }

    private record CreditCharge(Integer amount, String sourceCode) {
    }

    private List<BrandSpaceRunDetailView.TopicDecisionView> toTopicDecisionViews(
            List<BrandSpaceSchedulingService.TopicDecision> topicDecisions) {
        if (topicDecisions == null || topicDecisions.isEmpty()) {
            return List.of();
        }
        return topicDecisions.stream()
                .filter(Objects::nonNull)
                .map(item -> BrandSpaceRunDetailView.TopicDecisionView.builder()
                        .topicId(item.topicId())
                        .topicName(item.topicName())
                        .plannedProbeCount(item.allocatedProbeCount())
                        .priorityScore(item.priorityScore())
                        .reasonTags(item.reasonTags())
                        .build())
                .toList();
    }

    private List<BrandSpaceRunDetailView.MilestoneView> toMilestones(List<RunEventView> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<BrandSpaceRunDetailView.MilestoneView> milestones = new ArrayList<>();
        for (RunEventView event : events) {
            if (event == null || !StringUtils.hasText(event.getType())) {
                continue;
            }
            milestones.add(BrandSpaceRunDetailView.MilestoneView.builder()
                    .type(event.getType())
                    .label(mapEventLabel(event.getType()))
                    .createdAt(event.getCreatedAt())
                    .summary(buildEventSummary(event))
                    .build());
        }
        return List.copyOf(milestones);
    }

    private String buildEventSummary(RunEventView event) {
        if (event == null || event.getPayload() == null || event.getPayload().isEmpty()) {
            return null;
        }
        Map<String, Object> payload = event.getPayload();
        return switch (defaultIfBlank(event.getType(), "")) {
            case "step_changed" -> defaultIfBlank(asText(payload.get("currentStepLabel")), asText(payload.get("rawEventType")));
            case "probe_generated" -> defaultIfBlank(asText(payload.get("renderedPrompt")), asText(payload.get("status")));
            case "model_called" -> defaultIfBlank(asText(payload.get("modelId")), asText(payload.get("status")));
            case "citations_collected" -> {
                Integer evidenceCount = toInteger(payload.get("evidenceCount"));
                yield evidenceCount == null ? defaultIfBlank(asText(payload.get("qualityGatePassed")), null)
                        : "收集到 " + evidenceCount + " 条证据";
            }
            case "aggregation_completed" -> defaultIfBlank(asText(payload.get("reportId")), null);
            case "run_failed" -> defaultIfBlank(asText(payload.get("failureMessage")), "运行失败");
            default -> null;
        };
    }

    private String mapEventLabel(String type) {
        return switch (defaultIfBlank(type, "")) {
            case "run_started" -> "运行开始";
            case "step_changed" -> "阶段变化";
            case "probe_generated" -> "探针生成";
            case "model_called" -> "模型调用";
            case "citations_collected" -> "证据收集";
            case "aggregation_completed" -> "聚合完成";
            case "run_completed" -> "运行完成";
            case "run_failed" -> "运行失败";
            default -> "运行事件";
        };
    }

    private Map<String, TopicPromptBundle> buildPromptBundleByTopic(BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                                    List<DiagnosisProbeRecord> probes) {
        LinkedHashMap<String, TopicPromptBundle> bundles = new LinkedHashMap<>();
        if (executionPlan != null && executionPlan.executionPrompts() != null && !executionPlan.executionPrompts().isEmpty()) {
            for (BrandSpaceSchedulingService.ExecutionPromptPlan promptPlan : executionPlan.executionPrompts()) {
                if (promptPlan == null) {
                    continue;
                }
                String topicId = defaultIfBlank(promptPlan.topicId(), null);
                String topicName = defaultIfBlank(promptPlan.topicName(), "未命名主题");
                String promptText = defaultIfBlank(promptPlan.promptText(), null);
                String key = topicKey(topicId, topicName);
                TopicPromptBundle existing = bundles.computeIfAbsent(key,
                        ignored -> new TopicPromptBundle(topicId, topicName, new ArrayList<>()));
                if (StringUtils.hasText(promptText) && existing.prompts().stream().noneMatch(promptText::equals)) {
                    existing.prompts().add(promptText);
                }
            }
            return bundles.isEmpty() ? Map.of() : Map.copyOf(bundles);
        }
        if (probes == null || probes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> selectedTopicNameById = buildSelectedTopicNameById(executionPlan);
        for (DiagnosisProbeRecord probe : probes) {
            if (probe == null) {
                continue;
            }
            String topicId = defaultIfBlank(resolveProbeTopicId(probe), null);
            String topicName = firstText(
                    topicId == null ? null : selectedTopicNameById.get(topicId),
                    "未命名主题"
            );
            String promptText = defaultIfBlank(probe.getRenderText(), null);
            String key = topicKey(topicId, topicName);
            TopicPromptBundle existing = bundles.computeIfAbsent(key,
                    ignored -> new TopicPromptBundle(topicId, topicName, new ArrayList<>()));
            if (StringUtils.hasText(promptText) && existing.prompts().stream().noneMatch(promptText::equals)) {
                existing.prompts().add(promptText);
            }
        }
        return bundles.isEmpty() ? Map.of() : Map.copyOf(bundles);
    }

    private TopicPromptBundle resolveTopicPromptBundle(Map<String, TopicPromptBundle> bundles,
                                                       String topicId,
                                                       String topicName) {
        if (bundles == null || bundles.isEmpty()) {
            return null;
        }
        TopicPromptBundle exact = bundles.get(topicKey(topicId, topicName));
        if (exact != null) {
            return exact;
        }
        String normalizedTopicName = defaultIfBlank(topicName, null);
        if (!StringUtils.hasText(normalizedTopicName)) {
            return null;
        }
        for (TopicPromptBundle bundle : bundles.values()) {
            if (bundle == null) {
                continue;
            }
            if (normalizedTopicName.equals(defaultIfBlank(bundle.topicName(), null))) {
                return bundle;
            }
        }
        return null;
    }

    private List<BrandSpaceRunDetailView.ProbeExecutionView> buildProbeExecutions(DiagnosisTask task,
                                                                                   List<DiagnosisProbeRecord> probes,
                                                                                   BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (task == null || !StringUtils.hasText(task.getId()) || probes == null || probes.isEmpty()) {
            return List.of();
        }
        Map<Integer, ExecutionPromptBinding> executionPromptBySeqNo = indexExecutionPromptBySeqNo(executionPlan);
        Map<String, ExecutionPromptBinding> executionPromptByPromptId = indexExecutionPromptByPromptId(executionPlan);
        Map<String, TopicAllocationRecord> topicAllocationBySeqNo = buildTopicAllocationBySeqNo(executionPlan);
        Map<String, String> selectedTopicNameById = buildSelectedTopicNameById(executionPlan);
        Map<String, ProbeDispatchAttempt> latestAttemptByProbeId = loadLatestAttemptByProbeId(task.getId());
        Map<String, ProbeResultEventRecord> resultEventByProbeId = loadLatestProbeResultEventByProbeId(task.getId());
        Map<String, Integer> evidenceCountByProbeId = countEvidenceByProbeId(task.getId());
        List<BrandSpaceRunDetailView.ProbeExecutionView> rows = new ArrayList<>();
        for (DiagnosisProbeRecord probe : probes) {
            if (probe == null) {
                continue;
            }
            ExecutionPromptBinding executionPromptBinding = resolveExecutionPromptBinding(
                    probe,
                    executionPromptBySeqNo,
                    executionPromptByPromptId
            );
            TopicAllocationRecord topicAllocationRecord = probe.getSeqNo() == null ? null : topicAllocationBySeqNo.get(String.valueOf(probe.getSeqNo()));
            ProbeDispatchAttempt latestAttempt = latestAttemptByProbeId.get(defaultIfBlank(probe.getId(), ""));
            ProbeResultEventRecord resultEvent = resultEventByProbeId.get(defaultIfBlank(probe.getId(), null));
            Map<String, Object> payload = resultEvent == null ? Map.of() : defaultMap(resultEvent.payloadJson());
            Integer payloadEvidenceCount = readList(payload.get("searchEvidence")).size();
            Integer evidenceCount = payloadEvidenceCount > 0
                    ? payloadEvidenceCount
                    : evidenceCountByProbeId.getOrDefault(defaultIfBlank(probe.getId(), ""), 0);
            String status = resolveProbeExecutionStatus(probe.getStatusCode(), latestAttempt == null ? null : latestAttempt.getStatusCode(), payload);
            String explicitTopicId = resolveProbeTopicId(probe);
            String explicitPromptId = resolveProbePromptId(probe);
            rows.add(BrandSpaceRunDetailView.ProbeExecutionView.builder()
                    .probeId(probe.getId())
                    .probeRef(probe.getProbeRef())
                    .seqNo(probe.getSeqNo())
                    .topicId(firstText(
                            explicitTopicId,
                            topicAllocationRecord == null ? null : topicAllocationRecord.topicId(),
                            executionPromptBinding == null ? null : executionPromptBinding.topicId()))
                    .topicName(firstText(
                            explicitTopicId == null ? null : selectedTopicNameById.get(explicitTopicId),
                            topicAllocationRecord == null ? null : topicAllocationRecord.topicName(),
                            executionPromptBinding == null ? null : executionPromptBinding.topicName()))
                    .promptId(firstText(
                            explicitPromptId,
                            executionPromptBinding == null ? null : executionPromptBinding.promptId()))
                    .renderedPrompt(firstText(
                            defaultIfBlank(asText(payload.get("renderedPrompt")), null),
                            probe.getRenderText(),
                            executionPromptBinding == null ? null : executionPromptBinding.promptText()))
                    .modelId(firstText(
                            latestAttempt == null ? null : latestAttempt.getModelId(),
                            latestAttempt == null ? null : latestAttempt.getProvider(),
                            defaultIfBlank(asText(payload.get("modelId")), null)))
                    .status(status)
                    .statusLabel(mapProbeStatusLabel(status))
                    .evidenceCount(evidenceCount)
                    .latencyMs(firstNonNull(
                            latestAttempt == null ? null : latestAttempt.getLatencyMs(),
                            toInteger(payload.get("latencyMs"))))
                    .createdAt(formatDateTime(firstNonNull(
                            latestAttempt == null ? null : latestAttempt.getCreatedAt(),
                            resultEvent == null ? null : resultEvent.createdAt(),
                            probe.getCreatedAt())))
                    .provisional(resultEvent == null ? null : resultEvent.provisional())
                    .build());
        }
        return List.copyOf(rows);
    }

    private ExecutionPromptBinding resolveExecutionPromptBinding(DiagnosisProbeRecord probe,
                                                                 Map<Integer, ExecutionPromptBinding> executionPromptBySeqNo,
                                                                 Map<String, ExecutionPromptBinding> executionPromptByPromptId) {
        if (probe == null) {
            return null;
        }
        String explicitPromptId = resolveProbePromptId(probe);
        if (StringUtils.hasText(explicitPromptId)) {
            ExecutionPromptBinding executionByPromptId = executionPromptByPromptId.get(explicitPromptId.trim());
            if (executionByPromptId != null) {
                return executionByPromptId;
            }
        }
        Integer promptSequence = resolveProbePromptSequence(probe);
        if (promptSequence != null) {
            ExecutionPromptBinding executionBySequence = executionPromptBySeqNo.get(promptSequence);
            if (executionBySequence != null) {
                return executionBySequence;
            }
        }
        if (probe.getSeqNo() == null) {
            return null;
        }
        return executionPromptBySeqNo.get(probe.getSeqNo());
    }

    private Map<Integer, ExecutionPromptBinding> indexExecutionPromptBySeqNo(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.executionPrompts().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Integer, ExecutionPromptBinding> records = new LinkedHashMap<>();
        for (BrandSpaceSchedulingService.ExecutionPromptPlan executionPrompt : executionPlan.executionPrompts()) {
            if (executionPrompt == null || executionPrompt.seqNo() == null) {
                continue;
            }
            records.put(executionPrompt.seqNo(), new ExecutionPromptBinding(
                    defaultIfBlank(executionPrompt.promptId(), null),
                    defaultIfBlank(executionPrompt.topicId(), null),
                    defaultIfBlank(executionPrompt.topicName(), null),
                    defaultIfBlank(executionPrompt.promptText(), null)
            ));
        }
        return records.isEmpty() ? Map.of() : Map.copyOf(records);
    }

    private Map<String, ExecutionPromptBinding> indexExecutionPromptByPromptId(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.executionPrompts().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ExecutionPromptBinding> records = new LinkedHashMap<>();
        for (BrandSpaceSchedulingService.ExecutionPromptPlan executionPrompt : executionPlan.executionPrompts()) {
            if (executionPrompt == null || !StringUtils.hasText(executionPrompt.promptId())) {
                continue;
            }
            records.put(executionPrompt.promptId().trim(), new ExecutionPromptBinding(
                    executionPrompt.promptId().trim(),
                    defaultIfBlank(executionPrompt.topicId(), null),
                    defaultIfBlank(executionPrompt.topicName(), null),
                    defaultIfBlank(executionPrompt.promptText(), null)
            ));
        }
        return records.isEmpty() ? Map.of() : Map.copyOf(records);
    }

    private Map<String, TopicAllocationRecord> buildTopicAllocationBySeqNo(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null) {
            return Map.of();
        }
        if (executionPlan.executionPrompts() != null && !executionPlan.executionPrompts().isEmpty()) {
            LinkedHashMap<String, TopicAllocationRecord> mappings = new LinkedHashMap<>();
            for (BrandSpaceSchedulingService.ExecutionPromptPlan executionPrompt : executionPlan.executionPrompts()) {
                if (executionPrompt == null || executionPrompt.seqNo() == null) {
                    continue;
                }
                mappings.put(String.valueOf(executionPrompt.seqNo()), new TopicAllocationRecord(
                        executionPrompt.topicId(),
                        executionPrompt.topicName()
                ));
            }
            if (!mappings.isEmpty()) {
                return Map.copyOf(mappings);
            }
        }
        if (executionPlan.selectedTopics() == null || executionPlan.selectedTopics().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, TopicAllocationRecord> mappings = new LinkedHashMap<>();
        int currentSeqNo = 1;
        for (BrandSpaceSchedulingService.TopicDecision topicDecision : executionPlan.selectedTopics()) {
            if (topicDecision == null || topicDecision.allocatedProbeCount() <= 0) {
                continue;
            }
            for (int index = 0; index < topicDecision.allocatedProbeCount(); index++) {
                mappings.put(String.valueOf(currentSeqNo++), new TopicAllocationRecord(
                        topicDecision.topicId(),
                        topicDecision.topicName()
                ));
            }
        }
        return mappings.isEmpty() ? Map.of() : Map.copyOf(mappings);
    }

    private Map<String, String> buildSelectedTopicNameById(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.selectedTopics() == null || executionPlan.selectedTopics().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (BrandSpaceSchedulingService.TopicDecision topicDecision : executionPlan.selectedTopics()) {
            if (topicDecision == null || !StringUtils.hasText(topicDecision.topicId())) {
                continue;
            }
            names.put(topicDecision.topicId(), topicDecision.topicName());
        }
        return names.isEmpty() ? Map.of() : Map.copyOf(names);
    }

    private List<DiagnosisProbeRecord> loadRunProbes(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        List<DiagnosisProbeRecord> probes = diagnosisProbeRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisProbeRecord>()
                .eq(DiagnosisProbeRecord::getDiagnosisTaskId, runId)
                .orderByAsc(DiagnosisProbeRecord::getSeqNo)
                .orderByAsc(DiagnosisProbeRecord::getCreatedAt));
        return probes == null || probes.isEmpty() ? List.of() : List.copyOf(probes);
    }

    private String resolveProbePromptId(DiagnosisProbeRecord probe) {
        Map<String, Object> renderMetadata = defaultMap(probe == null ? null : probe.getRenderMetadataJson());
        Map<String, Object> triggeredRules = defaultMap(probe == null ? null : probe.getTriggeredRulesJson());
        return firstText(
                defaultIfBlank(asText(renderMetadata.get("promptId")), defaultIfBlank(asText(renderMetadata.get("sourcePromptId")), null)),
                defaultIfBlank(asText(triggeredRules.get("promptId")), defaultIfBlank(asText(triggeredRules.get("sourcePromptId")), null))
        );
    }

    private String resolveProbeTopicId(DiagnosisProbeRecord probe) {
        Map<String, Object> renderMetadata = defaultMap(probe == null ? null : probe.getRenderMetadataJson());
        Map<String, Object> triggeredRules = defaultMap(probe == null ? null : probe.getTriggeredRulesJson());
        return firstText(
                defaultIfBlank(asText(renderMetadata.get("topicId")), defaultIfBlank(asText(renderMetadata.get("sourceTopicId")), null)),
                defaultIfBlank(asText(triggeredRules.get("topicId")), defaultIfBlank(asText(triggeredRules.get("sourceTopicId")), null))
        );
    }

    private Integer resolveProbePromptSequence(DiagnosisProbeRecord probe) {
        Map<String, Object> renderMetadata = defaultMap(probe == null ? null : probe.getRenderMetadataJson());
        Map<String, Object> triggeredRules = defaultMap(probe == null ? null : probe.getTriggeredRulesJson());
        return firstNonNull(
                toInteger(renderMetadata.get("promptSequence")),
                toInteger(triggeredRules.get("promptSequence"))
        );
    }

    private Map<String, ProbeResultEventRecord> loadLatestProbeResultEventByProbeId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        KnowledgeAssetRunEventRepository runEventRepository = knowledgeAssetRunEventRepositoryProvider.getIfAvailable();
        if (runEventRepository != null) {
            List<KnowledgeAssetRunEventRepository.RunEventView> eventViews =
                    runEventRepository.listBySourceTaskIdAndEventType(runId.trim(), "probe_result");
            if (eventViews != null && !eventViews.isEmpty()) {
                LinkedHashMap<String, ProbeResultEventRecord> eventByProbeId = new LinkedHashMap<>();
                for (KnowledgeAssetRunEventRepository.RunEventView eventView : eventViews) {
                    if (eventView == null) {
                        continue;
                    }
                    String probeId = defaultIfBlank(asText(defaultMap(eventView.payloadJson()).get("probeId")), null);
                    if (!StringUtils.hasText(probeId)) {
                        continue;
                    }
                    eventByProbeId.put(probeId, new ProbeResultEventRecord(
                            eventView.provisional(),
                            defaultMap(eventView.payloadJson()),
                            eventView.eventCreatedAt()
                    ));
                }
                if (!eventByProbeId.isEmpty()) {
                    return Map.copyOf(eventByProbeId);
                }
            }
        }
        List<DiagnosisEventRecord> events = diagnosisEventRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisEventRecord>()
                .eq(DiagnosisEventRecord::getDiagnosisTaskId, runId)
                .eq(DiagnosisEventRecord::getEventType, "probe_result")
                .orderByAsc(DiagnosisEventRecord::getSeq));
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ProbeResultEventRecord> eventByProbeId = new LinkedHashMap<>();
        for (DiagnosisEventRecord event : events) {
            if (event == null) {
                continue;
            }
            String probeId = defaultIfBlank(asText(defaultMap(event.getPayloadJson()).get("probeId")), null);
            if (!StringUtils.hasText(probeId)) {
                continue;
            }
            eventByProbeId.put(probeId, new ProbeResultEventRecord(
                    event.getProvisional(),
                    defaultMap(event.getPayloadJson()),
                    event.getCreatedAt()
            ));
        }
        return eventByProbeId.isEmpty() ? Map.of() : Map.copyOf(eventByProbeId);
    }

    private Map<String, ProbeDispatchAttempt> loadLatestAttemptByProbeId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        List<ProbeDispatchAttempt> attempts = probeDispatchAttemptMapper.selectList(new LambdaQueryWrapper<ProbeDispatchAttempt>()
                .eq(ProbeDispatchAttempt::getDiagnosisTaskId, runId)
                .orderByDesc(ProbeDispatchAttempt::getSelectedFinal)
                .orderByDesc(ProbeDispatchAttempt::getCreatedAt)
                .orderByDesc(ProbeDispatchAttempt::getAttemptNo));
        if (attempts == null || attempts.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, ProbeDispatchAttempt> latestByProbeId = new LinkedHashMap<>();
        for (ProbeDispatchAttempt attempt : attempts) {
            if (attempt == null || !StringUtils.hasText(attempt.getProbeId())) {
                continue;
            }
            latestByProbeId.putIfAbsent(attempt.getProbeId().trim(), attempt);
        }
        return latestByProbeId.isEmpty() ? Map.of() : Map.copyOf(latestByProbeId);
    }

    private Map<String, Integer> countEvidenceByProbeId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (ProbeEvidenceRecord record : probeEvidenceRecordMapper.selectList(new LambdaQueryWrapper<ProbeEvidenceRecord>()
                .eq(ProbeEvidenceRecord::getDiagnosisTaskId, runId))) {
            if (record == null || !StringUtils.hasText(record.getProbeId())) {
                continue;
            }
            counts.merge(record.getProbeId().trim(), 1, Integer::sum);
        }
        return counts.isEmpty() ? Map.of() : Map.copyOf(counts);
    }

    private Map<String, Integer> countProbesByRunId(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (DiagnosisProbeRecord record : diagnosisProbeRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisProbeRecord>()
                .in(DiagnosisProbeRecord::getDiagnosisTaskId, runIds))) {
            if (record == null || !StringUtils.hasText(record.getDiagnosisTaskId())) {
                continue;
            }
            counts.merge(record.getDiagnosisTaskId(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private Map<String, Integer> countEvidenceByRunId(List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (ProbeEvidenceRecord record : probeEvidenceRecordMapper.selectList(new LambdaQueryWrapper<ProbeEvidenceRecord>()
                .in(ProbeEvidenceRecord::getDiagnosisTaskId, runIds))) {
            if (record == null || !StringUtils.hasText(record.getDiagnosisTaskId())) {
                continue;
            }
            counts.merge(record.getDiagnosisTaskId(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private Map<String, DiagnosisReportRecord> loadLatestReportsByRunId(String brandSpaceId, List<String> runIds) {
        if (!StringUtils.hasText(brandSpaceId) || runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, DiagnosisReportRecord> reports = new LinkedHashMap<>();
        List<DiagnosisReportRecord> reportRows = diagnosisReportRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisReportRecord>()
                .eq(DiagnosisReportRecord::getBrandScopeId, brandSpaceId)
                .in(DiagnosisReportRecord::getDiagnosisTaskId, runIds));
        for (DiagnosisReportRecord record : reportRows) {
            if (record == null || !StringUtils.hasText(record.getDiagnosisTaskId())) {
                continue;
            }
            reports.merge(record.getDiagnosisTaskId(), record, this::pickLatestReport);
        }
        return Map.copyOf(reports);
    }

    private DiagnosisReportRecord loadLatestReportByRunId(String runId, String brandSpaceId) {
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(brandSpaceId)) {
            return null;
        }
        List<DiagnosisReportRecord> rows = diagnosisReportRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisReportRecord>()
                .eq(DiagnosisReportRecord::getBrandScopeId, brandSpaceId)
                .eq(DiagnosisReportRecord::getDiagnosisTaskId, runId));
        return rows == null || rows.isEmpty()
                ? null
                : rows.stream().filter(Objects::nonNull).max(this::compareReportRecency).orElse(null);
    }

    private DiagnosisReportRecord pickLatestReport(DiagnosisReportRecord left, DiagnosisReportRecord right) {
        return compareReportRecency(left, right) >= 0 ? left : right;
    }

    private int compareReportRecency(DiagnosisReportRecord left, DiagnosisReportRecord right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return Comparator
                .comparing(DiagnosisReportRecord::getGeneratedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(DiagnosisReportRecord::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(DiagnosisReportRecord::getId, Comparator.nullsLast(String::compareTo))
                .compare(left, right);
    }

    private BrandSpaceSchedulingService.ExecutionPlan readExecutionPlan(DiagnosisTask task) {
        if (task == null) {
            return null;
        }
        return BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(defaultMap(task.getTaskOrderJson()));
    }

    private BrandSpaceAccessContext requireBrandSpaceContext(String userId, String brandSpaceId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(brandSpaceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        BrandScope scope = brandScopeMapper.selectById(brandSpaceId.trim());
        if (scope == null || !StringUtils.hasText(organizationId) || !Objects.equals(organizationId, scope.getOrganizationId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
        }
        return new BrandSpaceAccessContext(organizationId, scope);
    }

    private DateWindow resolveDateWindow(String rawDateFrom, String rawDateTo) {
        LocalDate today = LocalDate.now();
        LocalDate dateTo = parseDate(rawDateTo);
        LocalDate dateFrom = parseDate(rawDateFrom);
        if (dateFrom == null && dateTo == null) {
            dateTo = today;
            dateFrom = today.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        } else if (dateFrom == null) {
            dateFrom = dateTo.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        } else if (dateTo == null) {
            dateTo = today;
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期范围不合法");
        }
        return new DateWindow(
                dateFrom,
                dateTo,
                dateFrom.atStartOfDay(),
                dateTo.plusDays(1L).atStartOfDay()
        );
    }

    private LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日期格式必须为 YYYY-MM-DD");
        }
    }

    private String resolveBrandName(BrandScope scope, DiagnosisTask task) {
        return firstText(
                scope == null ? null : scope.getScopeName(),
                task == null ? null : task.getBrandNameSnapshot(),
                "品牌空间"
        );
    }

    private LocalDateTime resolveRunAnchor(DiagnosisTask task, DiagnosisReportRecord report) {
        if (task == null) {
            return report == null ? null : report.getGeneratedAt();
        }
        if (task.getStartedAt() != null) {
            return task.getStartedAt();
        }
        if (task.getCreatedAt() != null) {
            return task.getCreatedAt();
        }
        return report == null ? null : report.getGeneratedAt();
    }

    private LocalDateTime resolveUpdatedAt(DiagnosisTask task, DiagnosisReportRecord report) {
        if (task == null) {
            return report == null ? null : report.getGeneratedAt();
        }
        if (task.getCompletedAt() != null) {
            return task.getCompletedAt();
        }
        if (task.getUpdatedAt() != null) {
            return task.getUpdatedAt();
        }
        return report == null ? task.getCreatedAt() : firstNonNull(report.getGeneratedAt(), task.getCreatedAt());
    }

    private Long resolveDurationSeconds(DiagnosisTask task, LocalDateTime updatedAt) {
        if (task == null || task.getStartedAt() == null || updatedAt == null) {
            return null;
        }
        return Math.max(0L, Duration.between(task.getStartedAt(), updatedAt).getSeconds());
    }

    private Integer resolvePlannedProbeCount(DiagnosisTask task, BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan != null && executionPlan.targetProbeCount() > 0) {
            return executionPlan.targetProbeCount();
        }
        return task == null ? 0 : defaultInt(task.getTargetProbeCount());
    }

    private Integer resolveCompletedProbeCount(DiagnosisTask task, Integer probeCountFallback) {
        if (task != null && task.getSamplingCompletedCount() != null) {
            return Math.max(0, task.getSamplingCompletedCount());
        }
        return probeCountFallback == null ? 0 : Math.max(0, probeCountFallback);
    }

    private String resolveOverviewStatus(DiagnosisTask task, RunView runView) {
        String persistedStatus = mapRunStatus(task);
        if (isTerminalRunStatus(persistedStatus)) {
            return persistedStatus;
        }
        return firstText(runView == null ? null : runView.getStatus(), persistedStatus);
    }

    private String resolveOverviewStartedAt(DiagnosisTask task, RunView runView) {
        String persisted = formatDateTime(task == null ? null : task.getStartedAt());
        return firstText(persisted, runView == null ? null : runView.getStartedAt());
    }

    private String resolveOverviewCompletedAt(DiagnosisTask task, RunView runView) {
        String persisted = formatDateTime(task == null ? null : task.getCompletedAt());
        if (StringUtils.hasText(persisted)) {
            return persisted;
        }
        return runView == null ? null : runView.getCompletedAt();
    }

    private String mapRunStatus(DiagnosisTask task) {
        if (task == null || task.getStatusCode() == null) {
            return null;
        }
        return switch (task.getStatusCode()) {
            case 0, 5 -> "created";
            case 1 -> "running";
            case 2 -> "completed";
            case 3 -> "failed";
            case 4 -> "cancelled";
            default -> "partial";
        };
    }

    private boolean isTerminalRunStatus(String status) {
        return "completed".equals(status)
                || "failed".equals(status)
                || "cancelled".equals(status)
                || "partial".equals(status);
    }

    private String mapStatusLabel(String status) {
        return switch (defaultIfBlank(status, "")) {
            case "created" -> "待开始";
            case "running" -> "运行中";
            case "completed" -> "已完成";
            case "failed" -> "运行失败";
            case "cancelled" -> "已取消";
            case "partial" -> "部分完成";
            default -> "未知状态";
        };
    }

    private String mapTriggerLabel(String triggerMode) {
        return switch (defaultIfBlank(triggerMode, "")) {
            case "create_initial_run" -> "首次运行";
            case "scheduled_tracking", "scheduled_tracking_manual_acceptance" -> "日常跟踪";
            case "interactive_preview" -> "创建预览";
            default -> "未标记触发方式";
        };
    }

    private String resolveProbeExecutionStatus(Integer probeStatusCode,
                                               Integer attemptStatusCode,
                                               Map<String, Object> payload) {
        String payloadStatus = defaultIfBlank(asText(payload.get("status")), null);
        if ("failed".equalsIgnoreCase(payloadStatus) || "error".equalsIgnoreCase(payloadStatus)) {
            return "failed";
        }
        if (attemptStatusCode != null) {
            return mapProbeStatusCode(attemptStatusCode);
        }
        if (probeStatusCode != null) {
            return mapProbeStatusCode(probeStatusCode);
        }
        if (StringUtils.hasText(payloadStatus)) {
            String normalized = payloadStatus.trim().toLowerCase();
            return switch (normalized) {
                case "success", "succeeded", "completed", "complete" -> "succeeded";
                case "running", "pending", "queued", "created" -> "running";
                case "failed", "error" -> "failed";
                default -> normalized;
            };
        }
        return null;
    }

    private String mapProbeStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        return switch (statusCode) {
            case 1 -> "succeeded";
            case 2 -> "failed";
            case 0, 4 -> "running";
            default -> "partial";
        };
    }

    private String mapProbeStatusLabel(String status) {
        return switch (defaultIfBlank(status, "").toLowerCase()) {
            case "succeeded", "success", "completed", "complete" -> "成功";
            case "failed", "error" -> "失败";
            case "running" -> "运行中";
            case "queued", "pending", "created" -> "待执行";
            case "partial" -> "部分完成";
            default -> "未记录";
        };
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private Map<String, Object> defaultMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }

    private Map<String, Object> readObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source) || source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = entry.getKey() == null ? null : String.valueOf(entry.getKey()).trim();
            if (StringUtils.hasText(key) && entry.getValue() != null) {
                result.put(key, entry.getValue());
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private List<Object> readList(Object raw) {
        if (!(raw instanceof List<?> source) || source.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            if (item != null) {
                result.add(item);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
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

    private int defaultInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
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

    private String topicKey(String topicId, String topicName) {
        return StringUtils.hasText(topicId) ? topicId.trim() : defaultIfBlank(topicName, "未命名主题");
    }

    private record BrandSpaceAccessContext(String organizationId, BrandScope scope) {
    }

    private record DateWindow(LocalDate dateFrom,
                              LocalDate dateTo,
                              LocalDateTime fromDateTime,
                              LocalDateTime toExclusive) {
    }

    private record TopicPromptBundle(String topicId, String topicName, List<String> prompts) {
        int promptCount() {
            return prompts == null ? 0 : prompts.size();
        }

        List<String> promptSamples() {
            if (prompts == null || prompts.isEmpty()) {
                return List.of();
            }
            return prompts.stream().limit(3).toList();
        }
    }

    private record ExecutionPromptBinding(String promptId,
                                          String topicId,
                                          String topicName,
                                          String promptText) {
    }

    private record ProbeResultEventRecord(Boolean provisional,
                                          Map<String, Object> payloadJson,
                                          LocalDateTime createdAt) {
    }

    private record TopicAllocationRecord(String topicId,
                                         String topicName) {
    }
}

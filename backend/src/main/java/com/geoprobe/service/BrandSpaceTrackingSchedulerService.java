package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.LlmRunAdmissionBlockedException;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandSpaceTrackingSchedulerService {

    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_QUEUED = 5;
    private static final String TRACKING_MODE_PAUSED = "PAUSED";
    private static final String TASK_SOURCE = "brand_space_schedule";
    private static final String TASK_SOURCE_REPLAY = "brand_space_schedule_replay";
    private static final String TASK_SOURCE_MANUAL_ONCE = "brand_space_manual_execute_once";
    private static final ZoneId SCHEDULER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter REPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

    private final BrandScopeMapper brandScopeMapper;
    private final BrandMapper brandMapper;
    private final OrganizationMembershipMapper organizationMembershipMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    private final BrandSpaceConfigService brandSpaceConfigService;
    private final BrandSpaceSchedulingService brandSpaceSchedulingService;
    private final BrandSpaceRunConfigService brandSpaceRunConfigService;
    private final BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    private final BrandSpaceExecutionGateService brandSpaceExecutionGateService;
    private final DiagnosisService diagnosisService;
    private final DiagnosisTaskReconciliationService diagnosisTaskReconciliationService;
    private final ProbePlanService probePlanService;
    private final LegacyCreditMirrorService legacyCreditMirrorService;

    @Scheduled(cron = "${app.brand-space.tracking.cron:0 */15 * * * *}",
            zone = "${app.brand-space.tracking.zone:Asia/Shanghai}")
    public void runScheduledSweep() {
        scheduleDueBrandSpaces();
    }

    void scheduleDueBrandSpaces() {
        diagnosisTaskReconciliationService.reconcileOpenTasks("brand_space_schedule");
        LocalDate today = LocalDate.now(SCHEDULER_ZONE);
        List<BrandScope> scopes = brandScopeMapper.selectList(new LambdaQueryWrapper<BrandScope>()
                .and(wrapper -> wrapper
                        .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ACTIVE)
                        .or()
                        .isNull(BrandScope::getScopeStatusCode))
                .orderByAsc(BrandScope::getUpdatedAt)
                .orderByAsc(BrandScope::getCreatedAt)
                .last("limit 50"));
        if (scopes.isEmpty()) {
            return;
        }

        Map<String, Brand> brandsById = loadBrandsById(scopes);
        Map<String, DiagnosisTask> latestTasksByScopeId = loadLatestTasksByScopeId(scopes);
        Map<String, DiagnosisReportRecord> latestReportsByScopeId = loadLatestReportsByScopeId(scopes);
        Map<String, BrandSpaceConfigService.ConfigSummarySnapshot> configSummariesByScopeId =
                brandSpaceConfigService.summarizeConfigs(extractScopeIds(scopes));
        Map<String, Integer> activeBrandCountByGroup = buildActiveBrandCountByGroup(scopes, configSummariesByScopeId);

        List<BrandScope> dueScopes = scopes.stream()
                .filter(scope -> isTrackingEnabled(scope, configSummariesByScopeId.get(scope.getId())))
                .filter(scope -> !hasRunToday(
                        latestTasksByScopeId.get(scope.getId()),
                        latestReportsByScopeId.get(scope.getId()),
                        today
                ))
                .filter(scope -> !isGenerating(latestTasksByScopeId.get(scope.getId())))
                .sorted(Comparator.comparing(scope -> resolveLastActivity(
                        latestTasksByScopeId.get(scope.getId()),
                        latestReportsByScopeId.get(scope.getId())
                ), Comparator.nullsFirst(LocalDateTime::compareTo)))
                .toList();

        List<PreparedScope> preparedScopes = new java.util.ArrayList<>();
        for (BrandScope scope : dueScopes) {
            try {
                PreparedScope prepared = prepareScope(
                        scope,
                        brandsById.get(scope.getBrandId()),
                        latestTasksByScopeId.get(scope.getId()),
                        today
                );
                if (prepared != null) {
                    preparedScopes.add(prepared);
                }
            } catch (Exception ex) {
                log.error("Brand-space scheduled run failed during prepare. brandSpaceId={}, brandId={}", scope.getId(), scope.getBrandId(), ex);
            }
        }

        Map<String, List<PreparedScope>> groups = new java.util.LinkedHashMap<>();
        for (PreparedScope prepared : preparedScopes) {
            String groupKey = resolveGroupKey(prepared.scope(), prepared.userId());
            groups.computeIfAbsent(groupKey, ignored -> new java.util.ArrayList<>()).add(prepared);
        }
        for (Map.Entry<String, List<PreparedScope>> entry : groups.entrySet()) {
            schedulePreparedGroup(entry.getValue(), activeBrandCountByGroup.getOrDefault(entry.getKey(), entry.getValue().size()));
        }
    }

    ReplayScheduledRunResult replayBrandSpace(String brandSpaceId) {
        return replayBrandSpace(brandSpaceId, LocalDate.now(SCHEDULER_ZONE));
    }

    ReplayScheduledRunResult replayBrandSpace(String brandSpaceId, LocalDate today) {
        diagnosisTaskReconciliationService.reconcileOpenTasks("brand_space_schedule_replay");
        if (!StringUtils.hasText(brandSpaceId) || today == null) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "missing_scope_or_date");
        }
        BrandScope targetScope = brandScopeMapper.selectById(brandSpaceId);
        if (targetScope == null) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "brand_space_not_found");
        }
        Brand brand = StringUtils.hasText(targetScope.getBrandId()) ? brandMapper.selectById(targetScope.getBrandId()) : null;
        DiagnosisTask latestTask = loadLatestTask(targetScope.getId());
        PreparedScope targetPrepared = prepareScope(targetScope, brand, latestTask, today);
        if (targetPrepared == null) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "brand_space_not_schedulable");
        }
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = buildConfiguredExecutionPlan(targetPrepared, false);
        if (executionPlan == null || !executionPlan.shouldExecute()) {
            log.info("Replay brand-space run skipped by execution plan. brandSpaceId={}, requestDate={}, reason={}",
                    brandSpaceId,
                    today,
                    executionPlan == null ? "execution_plan_missing" : executionPlan.humanReason());
            return ReplayScheduledRunResult.skipped(
                    brandSpaceId,
                    null,
                    null,
                    executionPlan == null ? "execution_plan_missing" : "execution_plan_skipped"
            );
        }
        if (!hasSufficientCredits(targetPrepared, executionPlan, "replay")) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "credits_insufficient");
        }
        String requestId = buildReplayRequestId(brandSpaceId, today);
        String runId;
        try {
            runId = schedulePreparedScope(targetPrepared, executionPlan, requestId, TASK_SOURCE_REPLAY);
        } catch (LlmRunAdmissionBlockedException ex) {
            log.warn("Replay brand-space run blocked by reward snapshot gate. brandSpaceId={}, requestId={}, reason={}",
                    brandSpaceId, requestId, ex.getMessage());
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, requestId, "reward_quota_snapshot_unavailable");
        }
        return ReplayScheduledRunResult.created(
                brandSpaceId,
                runId,
                requestId,
                executionPlan.targetProbeCount()
        );
    }

    ReplayScheduledRunResult executeBrandSpaceOnce(String brandSpaceId) {
        diagnosisTaskReconciliationService.reconcileOpenTasks(TASK_SOURCE_MANUAL_ONCE);
        LocalDate today = LocalDate.now(SCHEDULER_ZONE);
        if (!StringUtils.hasText(brandSpaceId)) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "missing_scope");
        }
        BrandScope scope = brandScopeMapper.selectById(brandSpaceId);
        if (scope == null) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "brand_space_not_found");
        }
        Brand brand = StringUtils.hasText(scope.getBrandId()) ? brandMapper.selectById(scope.getBrandId()) : null;
        DiagnosisTask latestTask = loadLatestTask(scope.getId());
        if (isGenerating(latestTask)) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "brand_space_is_generating");
        }
        PreparedScope prepared = prepareScope(scope, brand, latestTask, today);
        if (prepared == null) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "brand_space_not_schedulable");
        }
        BrandSpaceSchedulingService.ExecutionPlan executionPlan = buildConfiguredExecutionPlan(prepared, true);
        if (executionPlan == null || !executionPlan.shouldExecute()) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "execution_plan_missing");
        }
        if (!hasSufficientCredits(prepared, executionPlan, "manual_execute_once")) {
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, null, "credits_insufficient");
        }
        String requestId = buildManualOnceRequestId(brandSpaceId, today);
        String runId;
        try {
            runId = schedulePreparedScope(prepared, executionPlan, requestId, TASK_SOURCE_MANUAL_ONCE);
        } catch (LlmRunAdmissionBlockedException ex) {
            log.warn("Manual brand-space run blocked by reward snapshot gate. brandSpaceId={}, requestId={}, reason={}",
                    brandSpaceId, requestId, ex.getMessage());
            return ReplayScheduledRunResult.skipped(brandSpaceId, null, requestId, "reward_quota_snapshot_unavailable");
        }
        return ReplayScheduledRunResult.created(
                brandSpaceId,
                runId,
                requestId,
                executionPlan.targetProbeCount()
        );
    }

    private PreparedScope prepareScope(BrandScope scope, Brand brand, DiagnosisTask latestTask, LocalDate today) {
        String userId = resolveActorUserId(scope);
        if (!StringUtils.hasText(userId)) {
            log.warn("Skip brand-space scheduled run because no active member can be resolved. brandSpaceId={}", scope.getId());
            return null;
        }
        String brandName = firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName());
        BrandSpaceConfigView configView = brandSpaceConfigService.loadConfigView(
                scope.getId(),
                scope.getBrandId(),
                brandName,
                scope.getConfigGeneration()
        );
        List<String> enabledPromptTexts = resolveEnabledPromptTexts(configView);
        if (enabledPromptTexts.isEmpty()) {
            log.info("Skip brand-space scheduled run because config has no enabled prompts. brandSpaceId={}", scope.getId());
            return null;
        }
        BrandSpaceExecutionGateService.AuthoritySnapshot authority;
        try {
            authority = brandSpaceExecutionGateService.requireScheduledAuthority(userId, scope);
        } catch (RuntimeException ex) {
            log.warn("Skip brand-space scheduled run because authority check failed. brandSpaceId={}, reason={}",
                    scope.getId(),
                    ex.getMessage());
            return null;
        }
        return new PreparedScope(scope, brand, latestTask, configView, authority, userId, today);
    }

    private void schedulePreparedGroup(List<PreparedScope> preparedScopes, int activeBrandCount) {
        if (preparedScopes == null || preparedScopes.isEmpty()) {
            return;
        }
        for (PreparedScope prepared : preparedScopes) {
            BrandSpaceSchedulingService.ExecutionPlan executionPlan = buildConfiguredExecutionPlan(prepared, false);
            if (executionPlan == null || !executionPlan.shouldExecute()) {
                log.info("Brand-space scheduled run skipped by execution plan. brandSpaceId={}, brandId={}, reason={}",
                        prepared.scope().getId(),
                        prepared.scope().getBrandId(),
                        executionPlan == null ? "execution_plan_missing" : executionPlan.humanReason());
                continue;
            }
            if (!hasSufficientCredits(prepared, executionPlan, "scheduled_tracking")) {
                log.info("Brand-space scheduled run skipped by insufficient credits. brandSpaceId={}, brandId={}, targetProbeCount={}",
                        prepared.scope().getId(),
                        prepared.scope().getBrandId(),
                        executionPlan.targetProbeCount());
                continue;
            }
            try {
                scheduleOne(prepared, executionPlan);
            } catch (LlmRunAdmissionBlockedException ex) {
                log.warn("Brand-space scheduled run blocked by reward snapshot gate. brandSpaceId={}, brandId={}, reason={}",
                        prepared.scope().getId(),
                        prepared.scope().getBrandId(),
                        ex.getMessage());
            } catch (Exception ex) {
                log.error("Brand-space scheduled run failed. brandSpaceId={}, brandId={}",
                        prepared.scope().getId(),
                        prepared.scope().getBrandId(),
                        ex);
            }
        }
    }

    private BrandSpaceSchedulingService.ExecutionPlan buildConfiguredExecutionPlan(PreparedScope prepared,
                                                                                   boolean manualExecuteOnce) {
        if (prepared == null || prepared.scope() == null || prepared.configView() == null) {
            return null;
        }
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(
                        prepared.scope(),
                        resolveEnabledPromptCount(prepared.configView())
                );
        return brandSpaceSchedulingService.planCurrentConfigRun(
                prepared.scope().getId(),
                prepared.configView(),
                prepared.latestTask(),
                prepared.authority().serviceAccess(),
                currentRunConfig.targetProbeCount(),
                manualExecuteOnce
        );
    }

    private boolean hasSufficientCredits(PreparedScope prepared,
                                         BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                         String scene) {
        if (prepared == null || executionPlan == null) {
            return false;
        }
        BrandSpaceRunCreditPolicy.CreditQuote quote = brandSpaceRunCreditPolicy.quote(
                prepared.userId(),
                executionPlan.targetProbeCount(),
                prepared.authority().serviceAccess()
        );
        if (quote.sufficient()) {
            return true;
        }
        log.info("Skip brand-space run because credits are insufficient. brandSpaceId={}, scene={}, targetProbeCount={}, estimatedCredits={}, remainingCredits={}",
                prepared.scope() == null ? null : prepared.scope().getId(),
                scene,
                executionPlan.targetProbeCount(),
                quote.estimatedCredits(),
                quote.creditsRemaining());
        return false;
    }

    private void scheduleOne(PreparedScope prepared, BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        String requestId = buildRequestId(prepared.scope().getId(), prepared.today());
        schedulePreparedScope(prepared, executionPlan, requestId, TASK_SOURCE);
    }

    private String schedulePreparedScope(PreparedScope prepared,
                                         BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                         String requestId,
                                         String taskSource) {
        BrandScope scope = prepared.scope();
        Brand brand = prepared.brand();
        DiagnosisTask latestTask = prepared.latestTask();
        brandSpaceExecutionGateService.assertPlannedProbeCountAllowed(
                executionPlan.targetProbeCount(),
                prepared.authority().serviceAccess(),
                TASK_SOURCE_MANUAL_ONCE.equals(taskSource) ? TASK_SOURCE_MANUAL_ONCE : "brand_space_schedule"
        );
        Map<String, Object> frontDeskTaskOrder = buildFrontDeskTaskOrder(
                scope, brand, latestTask, prepared.configView(), prepared.today(), prepared.authority(), requestId, taskSource, executionPlan
        );
        CreateDiagnosisFromTaskOrderRequest request = new CreateDiagnosisFromTaskOrderRequest();
        request.setRequestId(requestId);
        request.setFrontDeskTaskOrder(frontDeskTaskOrder);
        MainChainRunTicket runTicket = new MainChainRunTicket(
                diagnosisService.createDiagnosis(prepared.userId(), request).getId()
        );
        bindRunToCurrentProbePlan(prepared, executionPlan, runTicket.runId(), taskSource);
        log.info("Scheduled brand-space run created. brandSpaceId={}, targetProbeCount={}, requestId={}",
                scope.getId(), executionPlan.targetProbeCount(), request.getRequestId());
        return runTicket == null ? null : runTicket.runId();
    }

    private void bindRunToCurrentProbePlan(PreparedScope prepared,
                                           BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                           String runId,
                                           String taskSource) {
        if (prepared == null || prepared.scope() == null || !StringUtils.hasText(runId)) {
            return;
        }
        BrandScope scope = prepared.scope();
        Brand brand = prepared.brand();
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(
                        scope,
                        executionPlan == null ? null : executionPlan.targetProbeCount()
                );
        ProbePlanService.SnapshotCommand command = new ProbePlanService.SnapshotCommand(
                scope.getOrganizationId(),
                scope.getBrandId(),
                scope.getId(),
                firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName()),
                prepared.userId(),
                runId,
                executionPlan == null ? null : executionPlan.targetProbeCount(),
                currentRunConfig.targetModelProduct(),
                brandSpaceRunCreditPolicy.resolveCreditsPerProbe(prepared.authority().serviceAccess())
        );
        ProbePlanService.SnapshotRef planSnapshot;
        if (TASK_SOURCE_MANUAL_ONCE.equals(taskSource)) {
            planSnapshot = probePlanService.recordManualRunPlan(command);
        } else if (TASK_SOURCE_REPLAY.equals(taskSource)) {
            planSnapshot = probePlanService.recordControlledRerunPlan(command);
        } else {
            planSnapshot = probePlanService.recordScheduledRunPlan(command);
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        if (task == null) {
            log.warn("Skip probe-plan run binding because diagnosis task shell is missing. brandSpaceId={}, runId={}",
                    scope.getId(), runId);
            return;
        }
        task.setTaskOrderJson(attachProbePlanBinding(task.getTaskOrderJson(), planSnapshot));
        task.setTaskMetadataJson(attachProbePlanRuntimeMetadata(
                task.getTaskMetadataJson(),
                planSnapshot,
                TASK_SOURCE_MANUAL_ONCE.equals(taskSource) ? "MANUAL_ONCE"
                        : TASK_SOURCE_REPLAY.equals(taskSource) ? "CONTROLLED_RERUN" : "SCHEDULED",
                taskSource
        ));
        diagnosisTaskMapper.updateById(task);
        legacyCreditMirrorService.attachDiagnosisTaskAttribution(task);
    }

    private Map<String, Object> attachProbePlanBinding(Map<String, Object> taskOrder,
                                                       ProbePlanService.SnapshotRef planSnapshot) {
        if (planSnapshot == null) {
            return taskOrder;
        }
        Map<String, Object> mutableTaskOrder = mutableCopy(taskOrder);
        Map<String, Object> businessHints = mutableCopy(readMap(mutableTaskOrder.get("businessHints")));
        businessHints.put("probePlanBinding", buildProbePlanBinding(planSnapshot));
        mutableTaskOrder.put("businessHints", businessHints);

        Map<String, Object> taskMetadata = mutableCopy(readMap(mutableTaskOrder.get("taskMetadata")));
        taskMetadata.putAll(buildProbePlanRuntimeMetadata(planSnapshot, null, null));
        mutableTaskOrder.put("taskMetadata", taskMetadata);
        return mutableTaskOrder;
    }

    private Map<String, Object> attachProbePlanRuntimeMetadata(Map<String, Object> metadata,
                                                               ProbePlanService.SnapshotRef planSnapshot,
                                                               String runTypeCode,
                                                               String triggerSource) {
        Map<String, Object> mutableMetadata = mutableCopy(metadata);
        if (planSnapshot != null) {
            mutableMetadata.putAll(buildProbePlanRuntimeMetadata(planSnapshot, runTypeCode, triggerSource));
        }
        return mutableMetadata;
    }

    private Map<String, Object> buildProbePlanBinding(ProbePlanService.SnapshotRef planSnapshot) {
        LinkedHashMap<String, Object> binding = new LinkedHashMap<>();
        binding.put("contractVersion", "probe_plan_b1");
        binding.put("planVersionId", planSnapshot.planVersionId());
        binding.put("planRecordId", planSnapshot.planRecordId());
        binding.put("versionNo", planSnapshot.versionNo());
        binding.put("recordNo", planSnapshot.recordNo());
        binding.put("enabledProbeCount", planSnapshot.enabledProbeCount());
        return binding;
    }

    private Map<String, Object> buildProbePlanRuntimeMetadata(ProbePlanService.SnapshotRef planSnapshot,
                                                              String runTypeCode,
                                                              String triggerSource) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("billingSnapshotVersion", "probe_plan_b1");
        metadata.put("planVersionId", planSnapshot.planVersionId());
        metadata.put("planRecordId", planSnapshot.planRecordId());
        metadata.put("probePlanVersionNo", planSnapshot.versionNo());
        metadata.put("probePlanRecordNo", planSnapshot.recordNo());
        if (StringUtils.hasText(runTypeCode)) {
            metadata.put("runTypeCode", runTypeCode);
        }
        if (StringUtils.hasText(triggerSource)) {
            metadata.put("triggerSource", triggerSource);
        }
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private Map<String, Object> mutableCopy(Map<String, Object> value) {
        return value == null || value.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private Map<String, Object> buildFrontDeskTaskOrder(BrandScope scope,
                                                        Brand brand,
                                                        DiagnosisTask latestTask,
                                                        BrandSpaceConfigView configView,
                                                        LocalDate today,
                                                        BrandSpaceExecutionGateService.AuthoritySnapshot authority,
                                                        String requestId,
                                                        String taskSource,
                                                        BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(
                        scope,
                        executionPlan == null ? null : executionPlan.targetProbeCount()
                );
        return BrandSpaceTaskOrderSupport.buildScheduledTaskOrder(
                scope,
                brand,
                resolveScheduledCompetitors(scope, latestTask),
                configView,
                currentRunConfig.targetModelProduct(),
                new BrandSpaceTaskOrderSupport.ContractContext(
                        taskSource,
                        taskSource,
                        TASK_SOURCE_MANUAL_ONCE.equals(taskSource) ? "manual_execution" : "scheduled_tracking",
                        TASK_SOURCE_MANUAL_ONCE.equals(taskSource) ? "manual_once_run" : "active_scheduled_run",
                        requestId,
                        authority == null ? null : authority.organizationId(),
                        authority == null ? null : authority.membershipId(),
                        brand == null ? null : brand.getId(),
                        scope == null ? null : scope.getId(),
                        scope == null ? null : scope.getConfigGeneration(),
                        scope == null ? null : scope.getTrackingMode(),
                        today,
                        authority == null ? null : authority.serviceAccess()
                ),
                executionPlan
        );
    }

    private List<String> resolveScheduledCompetitors(BrandScope scope, DiagnosisTask latestTask) {
        if (BrandSpaceBasicProfileSupport.hasStoredCompetitors(scope)) {
            return BrandSpaceBasicProfileSupport.readStoredCompetitors(scope);
        }
        return List.of();
    }

    private String buildRequestId(String brandSpaceId, LocalDate today) {
        String compactId = brandSpaceId == null ? "unknown" : brandSpaceId.replace("-", "");
        return "bs-schedule-" + compactId + "-" + DATE_FORMATTER.format(today);
    }

    private String buildReplayRequestId(String brandSpaceId, LocalDate today) {
        String compactId = brandSpaceId == null ? "unknown" : brandSpaceId.replace("-", "");
        return "bs-replay-" + compactId + "-" + DATE_FORMATTER.format(today)
                + "-" + REPLAY_TIME_FORMATTER.format(LocalDateTime.now(SCHEDULER_ZONE));
    }

    private String buildManualOnceRequestId(String brandSpaceId, LocalDate today) {
        String compactId = brandSpaceId == null ? "unknown" : brandSpaceId.replace("-", "");
        return "bs-manual-" + compactId + "-" + DATE_FORMATTER.format(today)
                + "-" + REPLAY_TIME_FORMATTER.format(LocalDateTime.now(SCHEDULER_ZONE));
    }

    private DiagnosisTask loadLatestTask(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return null;
        }
        List<DiagnosisTask> tasks = diagnosisTaskMapper.selectList(new LambdaQueryWrapper<DiagnosisTask>()
                .eq(DiagnosisTask::getBrandScopeId, brandScopeId.trim())
                .orderByDesc(DiagnosisTask::getUpdatedAt)
                .orderByDesc(DiagnosisTask::getCreatedAt)
                .last("limit 1"));
        return tasks == null || tasks.isEmpty() ? null : tasks.getFirst();
    }

    private Integer resolveEnabledPromptCount(BrandSpaceConfigView configView) {
        if (configView == null || configView.getPrompts() == null) {
            return null;
        }
        int enabledPromptCount = (int) configView.getPrompts().stream()
                .filter(prompt -> prompt != null)
                .filter(prompt -> Boolean.TRUE.equals(prompt.getEnabled()))
                .filter(prompt -> StringUtils.hasText(prompt.getText()))
                .count();
        return enabledPromptCount > 0 ? enabledPromptCount : null;
    }

    private String resolveActorUserId(BrandScope scope) {
        if (scope == null) {
            return null;
        }
        if (StringUtils.hasText(scope.getCreatedByMembershipId())) {
            OrganizationMembership membership = organizationMembershipMapper.selectById(scope.getCreatedByMembershipId().trim());
            if (membership != null
                    && membership.getStatusCode() != null
                    && membership.getStatusCode() == MEMBERSHIP_STATUS_ACTIVE
                    && StringUtils.hasText(membership.getUserId())) {
                return membership.getUserId().trim();
            }
        }
        return null;
    }

    private Map<String, Integer> buildActiveBrandCountByGroup(List<BrandScope> scopes,
                                                              Map<String, BrandSpaceConfigService.ConfigSummarySnapshot> configSummariesByScopeId) {
        Map<String, Integer> result = new HashMap<>();
        if (scopes == null || scopes.isEmpty()) {
            return result;
        }
        for (BrandScope scope : scopes) {
            if (!isTrackingEnabled(scope, configSummariesByScopeId.get(scope.getId()))) {
                continue;
            }
            String groupKey = StringUtils.hasText(scope.getOrganizationId())
                    ? scope.getOrganizationId().trim()
                    : resolveGroupKey(scope, resolveActorUserId(scope));
            result.merge(groupKey, 1, Integer::sum);
        }
        return result;
    }

    private String resolveGroupKey(BrandScope scope, String userId) {
        if (scope != null && StringUtils.hasText(scope.getOrganizationId())) {
            return scope.getOrganizationId().trim();
        }
        return "user:" + firstText(userId, "unknown");
    }

    private boolean isTrackingEnabled(BrandScope scope,
                                      BrandSpaceConfigService.ConfigSummarySnapshot configSummary) {
        if (scope == null || BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            return false;
        }
        if (TRACKING_MODE_PAUSED.equalsIgnoreCase(String.valueOf(scope.getTrackingMode()))) {
            return false;
        }
        return configSummary != null && configSummary.enabledPromptCount() > 0;
    }

    private boolean hasRunToday(DiagnosisTask latestTask,
                                DiagnosisReportRecord latestReport,
                                LocalDate today) {
        if (today == null) {
            return false;
        }
        LocalDateTime latestTaskTime = resolveTaskActivity(latestTask);
        if (latestTaskTime != null && latestTaskTime.toLocalDate().isEqual(today)) {
            return true;
        }
        return latestReport != null
                && latestReport.getGeneratedAt() != null
                && latestReport.getGeneratedAt().toLocalDate().isEqual(today);
    }

    private boolean isGenerating(DiagnosisTask latestTask) {
        if (latestTask == null || latestTask.getStatusCode() == null) {
            return false;
        }
        int statusCode = latestTask.getStatusCode();
        return statusCode == STATUS_PENDING || statusCode == STATUS_RUNNING || statusCode == STATUS_QUEUED;
    }

    private LocalDateTime resolveLastActivity(DiagnosisTask latestTask, DiagnosisReportRecord latestReport) {
        LocalDateTime taskTime = resolveTaskActivity(latestTask);
        if (taskTime != null) {
            return taskTime;
        }
        return latestReport == null ? null : latestReport.getGeneratedAt();
    }

    private LocalDateTime resolveTaskActivity(DiagnosisTask latestTask) {
        if (latestTask == null) {
            return null;
        }
        if (latestTask.getCompletedAt() != null) {
            return latestTask.getCompletedAt();
        }
        if (latestTask.getStartedAt() != null) {
            return latestTask.getStartedAt();
        }
        return latestTask.getCreatedAt();
    }

    private Map<String, Brand> loadBrandsById(List<BrandScope> scopes) {
        Set<String> brandIds = scopes.stream()
                .filter(Objects::nonNull)
                .map(BrandScope::getBrandId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Brand> brandsById = new HashMap<>();
        for (Brand brand : brandMapper.selectBatchIds(brandIds)) {
            if (brand != null && StringUtils.hasText(brand.getId())) {
                brandsById.put(brand.getId(), brand);
            }
        }
        return brandsById;
    }

    private Map<String, DiagnosisTask> loadLatestTasksByScopeId(List<BrandScope> scopes) {
        List<String> scopeIds = extractScopeIds(scopes);
        if (scopeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisTask> latestTasksByScopeId = new HashMap<>();
        for (DiagnosisTask task : diagnosisTaskMapper.selectLatestByBrandScopeIds(scopeIds)) {
            if (task != null && StringUtils.hasText(task.getBrandScopeId())) {
                latestTasksByScopeId.put(task.getBrandScopeId(), task);
            }
        }
        return latestTasksByScopeId;
    }

    private Map<String, DiagnosisReportRecord> loadLatestReportsByScopeId(List<BrandScope> scopes) {
        List<String> scopeIds = extractScopeIds(scopes);
        Map<String, List<BrandScope>> scopesByOrg = scopes.stream()
                .filter(scope -> StringUtils.hasText(scope.getOrganizationId()) && StringUtils.hasText(scope.getId()))
                .collect(Collectors.groupingBy(BrandScope::getOrganizationId));
        if (scopeIds.isEmpty() || scopesByOrg.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisReportRecord> latestReportsByScopeId = new HashMap<>(loadLatestKnowledgeReportsByScopeId(scopeIds));
        for (Map.Entry<String, List<BrandScope>> entry : scopesByOrg.entrySet()) {
            String organizationId = entry.getKey();
            List<String> organizationScopeIds = extractScopeIds(entry.getValue()).stream()
                    .filter(scopeId -> !latestReportsByScopeId.containsKey(scopeId))
                    .toList();
            if (organizationScopeIds.isEmpty()) {
                continue;
            }
            for (DiagnosisReportRecord report : diagnosisReportRecordMapper
                    .selectLatestHeadsByBrandScopeIds(organizationId, organizationScopeIds)) {
                if (report != null && StringUtils.hasText(report.getBrandScopeId())) {
                    latestReportsByScopeId.put(report.getBrandScopeId(), report);
                }
            }
        }
        return latestReportsByScopeId;
    }

    private Map<String, DiagnosisReportRecord> loadLatestKnowledgeReportsByScopeId(List<String> scopeIds) {
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || scopeIds == null || scopeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisReportRecord> reportsByScopeId = new HashMap<>();
        for (KnowledgeAssetRunRepository.RunDetailView runDetail : repository.findLatestRunDetailsByScopeIds(scopeIds)) {
            if (runDetail == null || !StringUtils.hasText(runDetail.brandScopeId()) || runDetail.observedAt() == null) {
                continue;
            }
            reportsByScopeId.put(runDetail.brandScopeId(), DiagnosisReportRecord.builder()
                    .id(firstText(runDetail.sourceReportId(), runDetail.runId()))
                    .diagnosisTaskId(runDetail.runId())
                    .brandScopeId(runDetail.brandScopeId())
                    .organizationId(runDetail.organizationId())
                    .generatedAt(toLocalDateTime(runDetail.observedAt()))
                    .createdAt(toLocalDateTime(runDetail.observedAt()))
                    .build());
        }
        return reportsByScopeId;
    }

    private List<String> extractScopeIds(List<BrandScope> scopes) {
        return scopes.stream()
                .filter(Objects::nonNull)
                .map(BrandScope::getId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> resolveEnabledPromptTexts(BrandSpaceConfigView configView) {
        if (configView == null || configView.getPrompts() == null) {
            return List.of();
        }
        return configView.getPrompts().stream()
                .filter(Objects::nonNull)
                .filter(prompt -> Boolean.TRUE.equals(prompt.getEnabled()))
                .map(BrandSpaceConfigView.PromptView::getText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
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

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return KnowledgeAssetTimeSupport.toProductLocalDateTime(value);
    }

    private record PreparedScope(BrandScope scope,
                                 Brand brand,
                                 DiagnosisTask latestTask,
                                 BrandSpaceConfigView configView,
                                 BrandSpaceExecutionGateService.AuthoritySnapshot authority,
                                 String userId,
                                 LocalDate today) {
    }

    record ReplayScheduledRunResult(String brandSpaceId,
                                    String runId,
                                    String requestId,
                                    Integer targetProbeCount,
                                    boolean skipped,
                                    String reason) {

        static ReplayScheduledRunResult created(String brandSpaceId,
                                                String runId,
                                                String requestId,
                                                Integer targetProbeCount) {
            return new ReplayScheduledRunResult(
                    brandSpaceId,
                    runId,
                    requestId,
                    targetProbeCount,
                    false,
                    null
            );
        }

        static ReplayScheduledRunResult skipped(String brandSpaceId,
                                                String runId,
                                                String requestId,
                                                String reason) {
            return new ReplayScheduledRunResult(
                    brandSpaceId,
                    runId,
                    requestId,
                    null,
                    true,
                    reason
            );
        }
    }
}

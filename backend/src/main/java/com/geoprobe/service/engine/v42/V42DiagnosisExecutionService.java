package com.geoprobe.service.engine.v42;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.V42DiagnosisRequest;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.service.CreditService;
import com.geoprobe.service.DiagnosisBillingService;
import com.geoprobe.service.DiagnosisProbeEventService;
import com.geoprobe.service.DiagnosisTaskNativeRuntimeService;
import com.geoprobe.service.RedisSupportService;
import com.geoprobe.service.shared.BrandAliasService;
import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import com.geoprobe.service.shared.DiagnosisAliasConfigService;
import com.geoprobe.service.shared.DiagnosisCancellationRegistry;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class V42DiagnosisExecutionService {

    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int CREDIT_COST_PER_DIAGNOSIS = 1;
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);
    private static final int TOTAL_STEPS = V42ProgressStateSupport.TOTAL_STEPS;
    private static final String BILLING_STATE_REFUNDED = "refunded";
    private static final String BILLING_STATE_SERVICE_ACCESS_MANAGED = "service_access_managed";
    private static final String NODE3_TOKEN_BUDGET_EXCEEDED = "TOKEN_BUDGET_EXCEEDED";

    private final DiagnosisMapper diagnosisMapper;
    private final BrandAliasService brandAliasService;
    private final DiagnosisAliasConfigService diagnosisAliasConfigService;
    private final DiagnosisCancellationRegistry diagnosisCancellationRegistry;
    private final DiagnosisProbeEventService diagnosisProbeEventService;
    private final V42TargetProbeCountPolicy v42TargetProbeCountPolicy;
    private final V42DiagnosisOrchestrator v42DiagnosisOrchestrator;
    private final V42ReportProjectionService v42ReportProjectionService;
    private final ObjectMapper objectMapper;
    private final RedisSupportService redisSupportService;
    private final VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;
    @Autowired(required = false)
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Autowired(required = false)
    private CreditService creditService;
    @Autowired(required = false)
    private DiagnosisBillingService diagnosisBillingService;

    @Qualifier("diagnosisExecutionExecutor")
    private final ThreadPoolTaskExecutor diagnosisExecutionExecutor;

    public void enqueue(V42ExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");
        Runnable task = () -> executeInternal(plan);
        try {
            Future<?> future = diagnosisExecutionExecutor.submit(task);
            diagnosisCancellationRegistry.registerRunningTask(plan.diagnosisId(), future);
        } catch (RuntimeException ex) {
            log.warn("V4.2 executor rejected task, fallback to current thread. diagnosisId={}, reason={}", plan.diagnosisId(), ex.getMessage());
            task.run();
        }
    }

    private void executeInternal(V42ExecutionPlan plan) {
        try {
            ExecutionDiagnosisContext diagnosisContext = resolveExecutionDiagnosis(plan);
            Diagnosis diagnosis = diagnosisContext.diagnosis();
            if (diagnosis == null) {
                log.warn("V4.2 diagnosis does not exist, diagnosisId={}", plan.diagnosisId());
                return;
            }
            diagnosisCancellationRegistry.throwIfCancellationRequested(plan.diagnosisId());
            markRunning(plan.diagnosisId());
            int expectedSamples = resolveExpectedSampleCount(diagnosis, plan, plan.diagnosisId(), "v42_execution");
            FrontDeskTaskOrder taskOrder = plan.taskOrder();
            volcengineRewardRunAdmissionService.openMainChainLease(
                    plan.diagnosisId(),
                    taskOrder,
                    "diagnosis_execution diagnosisId=%s".formatted(plan.diagnosisId())
            );
            writeProgress(plan.diagnosisId(), "NODE0_STATE_SPACE", 0, 5.0d, "正在推断品类状态空间...",
                    0, expectedSamples, expectedSamples, 0, 0, false);
            V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors =
                    V42CategoryAnchorResolver.reconcile(
                            plan.category(),
                            plan.categoryAnchors(),
                            plan.fallbackAnchorMode()
                    );

            DiagnosisAliasConfigData aliasConfig = resolveAliasConfig(plan);
            diagnosisCancellationRegistry.throwIfCancellationRequested(plan.diagnosisId());

            EngineTaskContextPackage contextPackage = reconcileContextPackage(plan.contextPackage(), resolvedCategoryAnchors);
            V42DiagnosisResult result = v42DiagnosisOrchestrator.execute(new V42DiagnosisRequest(
                    plan.diagnosisId(),
                    buildReportId(plan.diagnosisId()),
                    plan.engineVersion(),
                    taskOrder,
                    contextPackage,
                    aliasConfig.brandAliases(),
                    aliasConfig.competitorAliases()
            ), update -> writeProgress(
                    plan.diagnosisId(),
                    update.currentStep(),
                    update.stepsCompleted(),
                    update.percent(),
                    update.currentStepLabel(),
                    update.samplingCompleted() == null ? 0 : update.samplingCompleted(),
                    update.samplingTotal() == null ? 0 : update.samplingTotal(),
                    expectedSamples,
                    update.samplingTotal() == null ? 0 : update.samplingTotal(),
                    Math.max(0, expectedSamples - (update.samplingTotal() == null ? 0 : update.samplingTotal())),
                    (update.samplingTotal() == null ? 0 : update.samplingTotal()) < expectedSamples
            ), buildRealtimeExecutionObserver(plan.diagnosisId()));
            diagnosisCancellationRegistry.throwIfCancellationRequested(plan.diagnosisId());
            int actualSampleSize = result.report().sampleSize();
            if (actualSampleSize != expectedSamples) {
                throw new IllegalStateException("V4.2 未按前台任务单完成探针交付，要求=" + expectedSamples + "，实际=" + actualSampleSize);
            }
            int successfulSamples = Math.toIntExact(result.task().successfulProbes());
            int missingSamples = Math.max(0, expectedSamples - actualSampleSize);
            boolean samplingVariantShortfall = missingSamples > 0;
            writeProgress(
                    plan.diagnosisId(),
                    "REPORT_PROJECTION",
                    10,
                    98.0d,
                    "引擎报告已装配，正在生成下游报告视图...",
                    successfulSamples,
                    actualSampleSize,
                    expectedSamples,
                    actualSampleSize,
                    missingSamples,
                    samplingVariantShortfall
            );

            Diagnosis current = diagnosisContext.legacyBacked()
                    ? diagnosisMapper.selectById(plan.diagnosisId())
                    : diagnosis;
            Map<String, Object> existingMeta = readExistingMeta(current, plan);
            Map<String, Object> reportData = v42ReportProjectionService.buildReportData(current, result, aliasConfig, existingMeta);
            Map<String, Object> summaryMirror = v42ReportProjectionService.buildSummaryKpiMirror(result);

            LocalDateTime completedAt = LocalDateTime.now();
            if (diagnosisContext.legacyBacked()) {
                diagnosisMapper.updateById(Diagnosis.builder()
                        .id(plan.diagnosisId())
                        .status(STATUS_COMPLETED)
                        .completedAt(completedAt)
                        .errorMessage(null)
                        .summaryKpis(summaryMirror)
                        .stabilityScore(BigDecimal.valueOf(result.report().kpiSummary().stabilityScore()).setScale(2, java.math.RoundingMode.HALF_UP))
                        .seoResult(reportData.get("seoVsGeo"))
                        .competitorRanking(reportData.get("competitorRanking"))
                        .associatedKeywords(reportData.get("associatedKeywords"))
                        .reportData(reportData)
                        .totalApiCalls(result.report().sampleSize())
                        .build());
            }
            Diagnosis completedDiagnosis = diagnosisContext.legacyBacked()
                    ? diagnosisMapper.selectById(plan.diagnosisId())
                    : buildCompletedDiagnosisView(diagnosis, completedAt, reportData);
            if (diagnosisTaskNativeRuntimeService != null && completedDiagnosis != null) {
                diagnosisTaskNativeRuntimeService.onDiagnosisCompleted(completedDiagnosis, result, reportData);
            }
            writeProgress(
                    plan.diagnosisId(),
                    "COMPLETED",
                    TOTAL_STEPS,
                    100.0d,
                    "诊断完成，报告已可查看",
                    successfulSamples,
                    actualSampleSize,
                    expectedSamples,
                    actualSampleSize,
                    missingSamples,
                    samplingVariantShortfall
            );
        } catch (CancellationException ex) {
            handleCancellation(plan);
        } catch (Exception ex) {
            handleFailure(plan, ex);
        } finally {
            volcengineRewardRunAdmissionService.closeLease(plan.diagnosisId());
            diagnosisCancellationRegistry.clear(plan.diagnosisId());
        }
    }

    private DiagnosisAliasConfigData resolveAliasConfig(V42ExecutionPlan plan) {
        DiagnosisAliasConfigData generated = null;
        try {
            generated = brandAliasService.generateAliasConfig(
                    plan.diagnosisId(),
                    plan.brandName(),
                    plan.category(),
                    plan.competitors()
            );
        } catch (RuntimeException ex) {
            log.warn("Generate alias config failed, diagnosisId={}, reason={}", plan.diagnosisId(), ex.getMessage());
        }

        DiagnosisAliasConfigData merged = mergeAliasConfig(
                generated == null ? new DiagnosisAliasConfigData(List.of(), Map.of()) : generated,
                new DiagnosisAliasConfigData(plan.brandAliases(), plan.competitorAliases())
        );
        DiagnosisAliasConfigData resolved = V42DeterministicAliasResolver.resolve(
                plan.brandName(),
                plan.competitors(),
                merged
        );
        diagnosisAliasConfigService.persist(plan.diagnosisId(), resolved);
        return resolved;
    }

    private DiagnosisAliasConfigData mergeAliasConfig(DiagnosisAliasConfigData generated,
                                                      DiagnosisAliasConfigData overrides) {
        List<String> brandAliases = mergeAliasList(
                overrides == null ? List.of() : overrides.brandAliases(),
                generated == null ? List.of() : generated.brandAliases()
        );
        Map<String, List<String>> competitorAliases = new LinkedHashMap<>();
        if (generated != null && generated.competitorAliases() != null) {
            competitorAliases.putAll(generated.competitorAliases());
        }
        if (overrides != null && overrides.competitorAliases() != null) {
            overrides.competitorAliases().forEach((key, value) ->
                    competitorAliases.put(key, mergeAliasList(value, competitorAliases.getOrDefault(key, List.of()))));
        }
        return new DiagnosisAliasConfigData(brandAliases, Map.copyOf(competitorAliases));
    }

    private List<String> mergeAliasList(List<String> primary, List<String> secondary) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            primary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (secondary != null) {
            secondary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private EngineTaskContextPackage reconcileContextPackage(EngineTaskContextPackage contextPackage,
                                                             V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors) {
        List<CategoryAnchorSignal> anchorSignals = buildCategoryAnchorSignals(
                resolvedCategoryAnchors.anchors(),
                resolvedCategoryAnchors.source()
        );
        return new EngineTaskContextPackage(
                contextPackage.brandName(),
                contextPackage.category(),
                contextPackage.competitors(),
                resolvedCategoryAnchors.anchors(),
                anchorSignals,
                resolvedCategoryAnchors.fallbackAnchorMode(),
                contextPackage.brandBrief(),
                contextPackage.categorySceneMap(),
                contextPackage.descriptiveEntrypoints(),
                contextPackage.competitorPlaceholders(),
                contextPackage.personaExpressionTendencies(),
                contextPackage.targetModelContextSignals(),
                reconcileGroundedSignals(contextPackage, anchorSignals),
                contextPackage.missingContextItems(),
                mergeObjectMaps(
                        contextPackage.extensibleSignals(),
                        mapOfNonNull(
                                "categoryAnchorSource", resolvedCategoryAnchors.source(),
                                "contextAssemblyStage", "diagnosis_execution_service"
                        )
                )
        );
    }

    private List<CategoryAnchorSignal> buildCategoryAnchorSignals(List<String> anchors, String source) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }
        return anchors.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(anchor -> new CategoryAnchorSignal(anchor, source, 1.0d))
                .toList();
    }

    private List<GroundedContextSignal> reconcileGroundedSignals(EngineTaskContextPackage contextPackage,
                                                                 List<CategoryAnchorSignal> anchorSignals) {
        if (contextPackage.groundedSignals().isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, GroundedContextSignal> merged = new LinkedHashMap<>();
        contextPackage.groundedSignals().stream()
                .filter(Objects::nonNull)
                .filter(signal -> !MinusOneContextSlots.CATEGORY_HYPOTHESIS.equals(signal.slot()))
                .forEach(signal -> merged.put(signal.dedupeKey(), signal));
        anchorSignals.stream()
                .map(signal -> new GroundedContextSignal(
                        MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                        signal.anchor(),
                        signal.source(),
                        null,
                        signal.confidence(),
                        List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
                ))
                .forEach(signal -> merged.put(signal.dedupeKey(), signal));
        return List.copyOf(merged.values());
    }

    private Map<String, Object> mergeObjectMaps(Map<String, Object> primary, Map<String, Object> secondary) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return Map.of();
        }
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (primary != null) {
            merged.putAll(primary);
        }
        if (secondary != null) {
            secondary.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
    }

    private Map<String, Object> mapOfNonNull(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues 必须成对出现");
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object rawKey = keyValues[index];
            Object rawValue = keyValues[index + 1];
            if (!(rawKey instanceof String key) || !StringUtils.hasText(key) || rawValue == null) {
                continue;
            }
            values.put(key.trim(), rawValue);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    private void markRunning(String diagnosisId) {
        LocalDateTime startedAt = LocalDateTime.now();
        Diagnosis legacyDiagnosis = diagnosisMapper.selectById(diagnosisId);
        if (legacyDiagnosis != null) {
            diagnosisMapper.updateById(Diagnosis.builder()
                    .id(diagnosisId)
                    .status(STATUS_RUNNING)
                    .startedAt(startedAt)
                    .completedAt(null)
                    .errorMessage(null)
                    .build());
        }
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onTaskRunning(diagnosisId, startedAt);
        }
    }

    private void handleCancellation(V42ExecutionPlan plan) {
        String diagnosisId = plan == null ? null : plan.diagnosisId();
        Diagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        Diagnosis effectiveDiagnosis = diagnosis != null ? diagnosis : resolveTaskOnlyDiagnosis(diagnosisId, plan);
        if (effectiveDiagnosis == null || Objects.equals(effectiveDiagnosis.getStatus(), STATUS_CANCELLED)) {
            return;
        }
        LocalDateTime completedAt = LocalDateTime.now();
        if (diagnosis != null) {
            diagnosisMapper.updateById(Diagnosis.builder()
                    .id(diagnosisId)
                    .status(STATUS_CANCELLED)
                    .completedAt(completedAt)
                    .errorMessage("用户取消")
                    .build());
        }
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onTaskTerminal(diagnosisId, STATUS_CANCELLED, "用户取消", completedAt);
        }
        Integer expectedSamples = resolveExpectedSampleCountForProgress(effectiveDiagnosis, plan, diagnosisId, "cancel_progress");
        if (expectedSamples != null && expectedSamples > 0) {
            writeProgress(diagnosisId, "CANCELLED", 0, 0.0d, "诊断已取消",
                    0, expectedSamples, expectedSamples, 0, 0, false);
        }
    }

    private void handleFailure(V42ExecutionPlan plan, Exception ex) {
        String diagnosisId = plan == null ? null : plan.diagnosisId();
        log.error("V4.2 diagnosis execution failed, diagnosisId={}", diagnosisId, ex);
        Diagnosis current = diagnosisMapper.selectById(diagnosisId);
        Diagnosis effectiveDiagnosis = current != null ? current : resolveTaskOnlyDiagnosis(diagnosisId, plan);
        if (effectiveDiagnosis == null || Objects.equals(effectiveDiagnosis.getStatus(), STATUS_CANCELLED)) {
            return;
        }
        String failureMessage = sanitizeError(ex);
        LocalDateTime completedAt = LocalDateTime.now();
        if (current != null) {
            diagnosisMapper.updateById(Diagnosis.builder()
                    .id(diagnosisId)
                    .status(STATUS_FAILED)
                    .completedAt(completedAt)
                    .errorMessage(failureMessage)
                    .build());
        }
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onTaskTerminal(diagnosisId, STATUS_FAILED, failureMessage, completedAt);
            if (ex instanceof Node3SamplingViabilityException viabilityException) {
                diagnosisTaskNativeRuntimeService.onDiagnosisSamplingFailed(effectiveDiagnosis, viabilityException.taskSnapshot());
            }
        }
        refundCreditWhenNode3BudgetFullyBlocks(effectiveDiagnosis, ex);
        Integer expectedSamples = resolveExpectedSampleCountForProgress(effectiveDiagnosis, plan, diagnosisId, "failure_progress");
        if (expectedSamples != null && expectedSamples > 0) {
            FailureProgressState progressState = resolveFailureProgressState(diagnosisId, expectedSamples);
            writeProgress(diagnosisId, "FAILED", progressState.stepsCompleted(), progressState.percent(), "诊断执行失败",
                    progressState.samplingCompleted(),
                    progressState.samplingTotal(),
                    progressState.samplingExpectedTotal(),
                    progressState.samplingGeneratedTotal(),
                    progressState.samplingMissingTotal(),
                    progressState.samplingVariantShortfall());
        }
    }

    private void refundCreditWhenNode3BudgetFullyBlocks(Diagnosis diagnosis, Exception ex) {
        if (!(ex instanceof Node3SamplingViabilityException viabilityException)
                || diagnosis == null
                || creditService == null
                || !StringUtils.hasText(diagnosis.getUserId())
                || isServiceAccessManagedBilling(diagnosis)
                || (diagnosis.getIsTrial() != null && diagnosis.getIsTrial() == 1)) {
            return;
        }
        V42DiagnosisResult.Task task = viabilityException.taskSnapshot();
        if (task == null
                || task.successfulProbes() > 0
                || task.failedProbes() <= 0
                || task.probeTrace().stream().anyMatch(probe ->
                probe == null || !NODE3_TOKEN_BUDGET_EXCEEDED.equalsIgnoreCase(probe.failureReason()))) {
            return;
        }
        int refundCredits = diagnosisBillingService == null
                ? CREDIT_COST_PER_DIAGNOSIS
                : diagnosisBillingService.readChargedCredits(diagnosis.getId(), diagnosis);
        creditService.refundCredits(diagnosis.getUserId(), diagnosis.getId(), refundCredits);
        if (diagnosisBillingService != null) {
            diagnosisBillingService.markDiagnosisBillingState(diagnosis.getId(), BILLING_STATE_REFUNDED);
        }
    }

    private boolean isServiceAccessManagedBilling(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return false;
        }
        Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
        });
        if (reportData == null || reportData.isEmpty()) {
            return false;
        }
        Map<String, Object> meta = objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
        });
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        Object billingState = meta.get("billingState");
        return billingState instanceof String text && BILLING_STATE_SERVICE_ACCESS_MANAGED.equalsIgnoreCase(text.trim());
    }

    private FailureProgressState resolveFailureProgressState(String diagnosisId, int expectedSamples) {
        DiagnosisProgressSnapshot snapshot = diagnosisTaskNativeRuntimeService == null
                ? null
                : diagnosisTaskNativeRuntimeService.readProgressSnapshot(diagnosisId);
        V42ProgressStateSupport.FailureProgressState recovered = V42ProgressStateSupport.recoverFailureProgress(
                snapshot,
                snapshot == null ? 0 : positiveOrZero(snapshot.getSamplingCompleted()),
                snapshot == null ? 0 : positiveOrZero(snapshot.getSamplingGeneratedTotal()),
                expectedSamples
        );
        return new FailureProgressState(
                recovered.stepsCompleted(),
                recovered.percent(),
                recovered.samplingCompleted(),
                Math.max(recovered.samplingExpectedTotal(), recovered.samplingGeneratedTotal()),
                recovered.samplingExpectedTotal(),
                recovered.samplingGeneratedTotal(),
                recovered.samplingMissingTotal(),
                recovered.samplingVariantShortfall()
        );
    }

    private int positiveOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private Map<String, Object> readExistingMeta(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return Map.of();
        }
        try {
            Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
            });
            if (reportData == null || reportData.get("meta") == null) {
                return Map.of();
            }
            Map<String, Object> meta = objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
            });
            return meta == null ? Map.of() : meta;
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private Map<String, Object> readExistingMeta(Diagnosis diagnosis, V42ExecutionPlan plan) {
        Map<String, Object> legacyMeta = readExistingMeta(diagnosis);
        if (!legacyMeta.isEmpty()) {
            return legacyMeta;
        }
        return buildTaskNativeMeta(plan == null ? null : plan.diagnosisId(), plan);
    }

    private V42ExecutionObserver buildRealtimeExecutionObserver(String diagnosisId) {
        return new V42ExecutionObserver() {
            @Override
            public void onStageCompleted(V42ExecutionObserver.StageTrace trace) {
                if (diagnosisTaskNativeRuntimeService != null) {
                    diagnosisTaskNativeRuntimeService.onStageCompleted(diagnosisId, trace);
                }
                appendRealtimeEvent(diagnosisId, new RealtimeEventTrace(
                        "engine_log",
                        true,
                        buildStageLogPayload(trace)
                ));
            }

            @Override
            public void onExecutionFailed(V42ExecutionObserver.FailureTrace trace) {
                if (diagnosisTaskNativeRuntimeService != null) {
                    diagnosisTaskNativeRuntimeService.onExecutionFailed(diagnosisId, trace);
                }
                appendRealtimeEvent(diagnosisId, new RealtimeEventTrace(
                        "engine_log",
                        false,
                        buildFailureLogPayload(trace)
                ));
            }

            @Override
            public void onRealtimeEvent(RealtimeEventTrace trace) {
                appendRealtimeEvent(diagnosisId, trace);
            }
        };
    }

    private void appendRealtimeEvent(String diagnosisId, V42ExecutionObserver.RealtimeEventTrace trace) {
        if (!StringUtils.hasText(diagnosisId) || trace == null) {
            return;
        }
        diagnosisProbeEventService.append(diagnosisId, trace);
    }

    private void writeProgress(String diagnosisId,
                               String currentStep,
                               int stepsCompleted,
                               double percent,
                               String currentStepLabel,
                               int samplingCompleted,
                               int samplingTotal,
                               int samplingExpectedTotal,
                               int samplingGeneratedTotal,
                               int samplingMissingTotal,
                               boolean samplingVariantShortfall) {
        if (!StringUtils.hasText(diagnosisId)) {
            return;
        }
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("currentStep", currentStep);
        progress.put("currentStepLabel", currentStepLabel);
        progress.put("stepsCompleted", stepsCompleted);
        progress.put("stepsTotal", TOTAL_STEPS);
        progress.put("samplingCompleted", samplingCompleted);
        progress.put("samplingTotal", samplingTotal);
        progress.put("samplingExpectedTotal", samplingExpectedTotal);
        progress.put("samplingGeneratedTotal", samplingGeneratedTotal);
        progress.put("samplingMissingTotal", samplingMissingTotal);
        progress.put("samplingVariantShortfall", samplingVariantShortfall);
        progress.put("percent", percent);
        redisSupportService.setJson(progressKey(diagnosisId), progress, PROGRESS_TTL, "v42 diagnosis progress");
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onProgress(
                    diagnosisId,
                    currentStep,
                    currentStepLabel,
                    stepsCompleted,
                    TOTAL_STEPS,
                    samplingCompleted,
                    samplingExpectedTotal,
                    samplingGeneratedTotal,
                    samplingMissingTotal,
                    samplingVariantShortfall,
                    percent
            );
        }
    }

    private int resolveExpectedSampleCount(Diagnosis diagnosis,
                                           V42ExecutionPlan plan,
                                           String diagnosisId,
                                           String scene) {
        Map<String, Object> meta = readExistingMeta(diagnosis, plan);
        if (!meta.isEmpty()) {
            try {
                return v42TargetProbeCountPolicy.resolveRequiredFromDiagnosisMeta(meta, scene);
            } catch (RuntimeException ex) {
                Map<String, Object> taskOrderSnapshot = readTaskOrderSnapshot(diagnosisId, plan);
                if (taskOrderSnapshot.isEmpty()) {
                    throw ex;
                }
                return v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, scene + "_task_native");
            }
        }
        Map<String, Object> taskOrderSnapshot = readTaskOrderSnapshot(diagnosisId, plan);
        if (!taskOrderSnapshot.isEmpty()) {
            return v42TargetProbeCountPolicy.resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, scene + "_task_native");
        }
        String normalizedDiagnosisId = StringUtils.hasText(diagnosisId)
                ? diagnosisId.trim()
                : diagnosis == null ? null : diagnosis.getId();
        throw new IllegalStateException("V4.2 缺少前台任务单探针数快照。diagnosisId=" + normalizedDiagnosisId);
    }

    private Integer resolveExpectedSampleCountForProgress(Diagnosis diagnosis,
                                                          V42ExecutionPlan plan,
                                                          String diagnosisId,
                                                          String scene) {
        try {
            return resolveExpectedSampleCount(diagnosis, plan, diagnosisId, scene);
        } catch (RuntimeException ex) {
            log.error("V4.2 progress strict failure without expectedSamples, diagnosisId={}, scene={}, reason={}",
                    StringUtils.hasText(diagnosisId) ? diagnosisId : diagnosis == null ? null : diagnosis.getId(),
                    scene,
                    ex.getMessage());
            return null;
        }
    }

    private ExecutionDiagnosisContext resolveExecutionDiagnosis(V42ExecutionPlan plan) {
        Diagnosis legacyDiagnosis = diagnosisMapper.selectById(plan.diagnosisId());
        if (legacyDiagnosis != null) {
            return new ExecutionDiagnosisContext(legacyDiagnosis, true);
        }
        Diagnosis taskOnlyDiagnosis = resolveTaskOnlyDiagnosis(plan.diagnosisId(), plan);
        return new ExecutionDiagnosisContext(taskOnlyDiagnosis, false);
    }

    private Diagnosis resolveTaskOnlyDiagnosis(String diagnosisId, V42ExecutionPlan plan) {
        if (diagnosisTaskNativeRuntimeService == null || !StringUtils.hasText(diagnosisId)) {
            return null;
        }
        DiagnosisTask task = diagnosisTaskNativeRuntimeService.readDiagnosisTask(diagnosisId);
        if (task == null) {
            return null;
        }
        FrontDeskTaskOrder taskOrder = plan != null && plan.taskOrder() != null
                ? plan.taskOrder()
                : readSnapshot(task.getTaskOrderJson(), FrontDeskTaskOrder.class);
        List<String> competitors = plan != null && plan.competitors() != null && !plan.competitors().isEmpty()
                ? List.copyOf(plan.competitors())
                : taskOrder == null || taskOrder.competitors() == null ? List.of() : List.copyOf(taskOrder.competitors());
        return Diagnosis.builder()
                .id(task.getId())
                .userId(task.getRequestedByUserId())
                .brandName(firstNonBlank(task.getBrandNameSnapshot(), plan == null ? null : plan.brandName()))
                .category(firstNonBlank(task.getCategorySnapshot(), plan == null ? null : plan.category()))
                .brandWebsite(firstNonBlank(task.getOfficialWebsiteSnapshot(), taskOrder == null ? null : taskOrder.brandWebsite()))
                .userPrompt(readTaskOrderUserPrompt(taskOrder))
                .competitorsInput(competitors)
                .status(task.getStatusCode())
                .source(task.getSourceCode())
                .reportData(Map.of("meta", buildTaskNativeMeta(task.getId(), plan)))
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    private Diagnosis buildCompletedDiagnosisView(Diagnosis diagnosis,
                                                  LocalDateTime completedAt,
                                                  Map<String, Object> reportData) {
        if (diagnosis == null) {
            return null;
        }
        return Diagnosis.builder()
                .id(diagnosis.getId())
                .userId(diagnosis.getUserId())
                .brandName(diagnosis.getBrandName())
                .category(diagnosis.getCategory())
                .brandWebsite(diagnosis.getBrandWebsite())
                .userPrompt(diagnosis.getUserPrompt())
                .competitorsInput(diagnosis.getCompetitorsInput())
                .status(STATUS_COMPLETED)
                .source(diagnosis.getSource())
                .reportData(reportData)
                .createdAt(diagnosis.getCreatedAt())
                .startedAt(diagnosis.getStartedAt())
                .completedAt(completedAt)
                .build();
    }

    private Map<String, Object> buildTaskNativeMeta(String diagnosisId, V42ExecutionPlan plan) {
        DiagnosisTask task = diagnosisTaskNativeRuntimeService == null ? null : diagnosisTaskNativeRuntimeService.readDiagnosisTask(diagnosisId);
        Map<String, Object> taskOrderSnapshot = readTaskOrderSnapshot(diagnosisId, plan);
        return mapOfNonNull(
                "engineVersion", firstNonBlank(plan == null ? null : plan.engineVersion(), task == null ? null : task.getEngineVersion()),
                "taskOrderSnapshot", taskOrderSnapshot.isEmpty() ? null : taskOrderSnapshot,
                "categoryAnchors", plan == null || plan.categoryAnchors() == null || plan.categoryAnchors().isEmpty() ? null : plan.categoryAnchors(),
                "fallbackAnchorMode", plan == null ? null : plan.fallbackAnchorMode(),
                "brandAliases", plan == null || plan.brandAliases() == null || plan.brandAliases().isEmpty() ? null : plan.brandAliases(),
                "competitorAliases", plan == null || plan.competitorAliases() == null || plan.competitorAliases().isEmpty() ? null : plan.competitorAliases()
        );
    }

    private Map<String, Object> readTaskOrderSnapshot(String diagnosisId, V42ExecutionPlan plan) {
        if (plan != null && plan.taskOrder() != null) {
            return toMap(plan.taskOrder());
        }
        if (diagnosisTaskNativeRuntimeService == null || !StringUtils.hasText(diagnosisId)) {
            return Map.of();
        }
        Map<String, Object> snapshot = diagnosisTaskNativeRuntimeService.readTaskOrderSnapshot(diagnosisId);
        return snapshot == null ? Map.of() : snapshot;
    }

    private String readTaskOrderUserPrompt(FrontDeskTaskOrder taskOrder) {
        if (taskOrder == null || taskOrder.businessHints() == null || taskOrder.businessHints().isEmpty()) {
            return null;
        }
        return asText(taskOrder.businessHints().get("userPrompt"));
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            Map<String, Object> converted = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
            });
            return converted == null ? Map.of() : converted;
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private <T> T readSnapshot(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, targetType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String text ? text : Objects.toString(value, null);
    }

    private String firstNonBlank(String... values) {
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

    private String progressKey(String diagnosisId) {
        return "diagnosis:" + diagnosisId + ":progress";
    }

    private String buildReportId(String diagnosisId) {
        String normalized = diagnosisId == null ? "" : diagnosisId.replace("-", "");
        return "rpt_" + normalized;
    }

    private String sanitizeError(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "V4.2 引擎执行失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Map<String, Object> buildStageLogPayload(V42ExecutionObserver.StageTrace trace) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", trace.stage());
        payload.put("level", "INFO");
        payload.put("kind", "stage_completed");
        payload.put("durationMillis", trace.durationMillis());
        payload.put("summary", trace.summary());
        payload.put("message", "阶段完成: " + trace.stage() + " (" + trace.durationMillis() + "ms)");
        return payload;
    }

    private Map<String, Object> buildFailureLogPayload(V42ExecutionObserver.FailureTrace trace) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", trace.stage());
        payload.put("level", "ERROR");
        payload.put("kind", "execution_failed");
        payload.put("elapsedMillis", trace.elapsedMillis());
        payload.put("errorType", trace.errorType());
        payload.put("message", trace.message());
        return payload;
    }

    private record FailureProgressState(
            int stepsCompleted,
            double percent,
            int samplingCompleted,
            int samplingTotal,
            int samplingExpectedTotal,
            int samplingGeneratedTotal,
            int samplingMissingTotal,
            boolean samplingVariantShortfall
    ) {
    }

    private record ExecutionDiagnosisContext(
            Diagnosis diagnosis,
            boolean legacyBacked
    ) {
    }
}

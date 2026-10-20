package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisTaskReconciliationService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_QUEUED = 5;
    private static final int STATUS_EXPIRED = 6;

    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisMapper diagnosisMapper;
    private final DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    private final V42ReportProjectionService v42ReportProjectionService;
    private final ObjectMapper objectMapper;

    @Value("${app.diagnosis.reconciliation.open-task-limit:200}")
    private int openTaskLimit;

    @Value("${app.diagnosis.reconciliation.enabled:true}")
    private boolean reconciliationEnabled = true;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        if (!reconciliationEnabled) {
            log.info("Diagnosis task reconciliation skipped: disabled by configuration.");
            return;
        }
        reconcileOpenTasks("application_ready");
    }

    @Transactional(rollbackFor = Exception.class)
    public ReconciliationSummary reconcileOpenTasks(String trigger) {
        if (!reconciliationEnabled) {
            return ReconciliationSummary.empty(trigger);
        }
        if (!diagnosisTaskNativeRuntimeService.isEnabled()) {
            return ReconciliationSummary.empty(trigger);
        }
        List<DiagnosisTask> openTasks = diagnosisTaskMapper.selectList(new LambdaQueryWrapper<DiagnosisTask>()
                .in(DiagnosisTask::getStatusCode, List.of(STATUS_PENDING, STATUS_RUNNING, STATUS_QUEUED))
                .orderByAsc(DiagnosisTask::getUpdatedAt)
                .orderByAsc(DiagnosisTask::getCreatedAt)
                .last("limit " + Math.max(1, openTaskLimit)));
        if (openTasks.isEmpty()) {
            return ReconciliationSummary.empty(trigger);
        }
        Map<String, Diagnosis> diagnosesById = diagnosisMapper.selectBatchIds(
                        openTasks.stream().map(DiagnosisTask::getId).filter(StringUtils::hasText).distinct().toList()
                ).stream()
                .filter(Objects::nonNull)
                .filter(diagnosis -> StringUtils.hasText(diagnosis.getId()))
                .collect(Collectors.toMap(Diagnosis::getId, Function.identity()));
        ReconciliationSummary summary = new ReconciliationSummary(trigger, openTasks.size(), 0, 0, 0, 0);
        for (DiagnosisTask openTask : openTasks) {
            summary = reconcileOne(openTask, diagnosesById.get(openTask.getId()), summary);
        }
        if (summary.recoveredCompleted() > 0 || summary.recoveredTerminal() > 0 || summary.skippedUnsupported() > 0) {
            log.info("Diagnosis task reconciliation finished. trigger={}, scanned={}, recoveredCompleted={}, recoveredTerminal={}, skippedMissingDiagnosis={}, skippedUnsupported={}",
                    summary.trigger(),
                    summary.scanned(),
                    summary.recoveredCompleted(),
                    summary.recoveredTerminal(),
                    summary.skippedMissingDiagnosis(),
                    summary.skippedUnsupported());
        }
        return summary;
    }

    private ReconciliationSummary reconcileOne(DiagnosisTask openTask,
                                               Diagnosis diagnosis,
                                               ReconciliationSummary summary) {
        if (openTask == null || !StringUtils.hasText(openTask.getId())) {
            return summary;
        }
        if (shouldRecoverRunning(openTask)) {
            diagnosisTaskNativeRuntimeService.onTaskRunning(
                    openTask.getId(),
                    firstNonNull(openTask.getStartedAt(), openTask.getUpdatedAt(), LocalDateTime.now())
            );
            openTask.setStatusCode(STATUS_RUNNING);
        }
        if (diagnosis == null) {
            return summary.withSkippedMissingDiagnosis(summary.skippedMissingDiagnosis() + 1);
        }
        Integer legacyStatus = diagnosis.getStatus();
        if (Objects.equals(legacyStatus, STATUS_COMPLETED)) {
            return reconcileCompleted(openTask, diagnosis, summary);
        }
        if (Objects.equals(legacyStatus, STATUS_FAILED)
                || Objects.equals(legacyStatus, STATUS_CANCELLED)
                || Objects.equals(legacyStatus, STATUS_EXPIRED)) {
            LocalDateTime completedAt = firstNonNull(diagnosis.getCompletedAt(), openTask.getCompletedAt(), LocalDateTime.now());
            diagnosisTaskNativeRuntimeService.onTaskTerminal(
                    openTask.getId(),
                    legacyStatus,
                    resolveTerminalMessage(legacyStatus, diagnosis.getErrorMessage()),
                    completedAt
            );
            return summary.withRecoveredTerminal(summary.recoveredTerminal() + 1);
        }
        return summary;
    }

    private boolean shouldRecoverRunning(DiagnosisTask openTask) {
        Integer statusCode = openTask.getStatusCode();
        if (!(Objects.equals(statusCode, STATUS_PENDING) || Objects.equals(statusCode, STATUS_QUEUED))) {
            return false;
        }
        DiagnosisProgressSnapshot snapshot = diagnosisTaskNativeRuntimeService.readProgressSnapshot(openTask.getId());
        if (snapshot == null) {
            return false;
        }
        if (snapshot.getSamplingCompleted() != null && snapshot.getSamplingCompleted() > 0) {
            return true;
        }
        if (snapshot.getSamplingGeneratedTotal() != null && snapshot.getSamplingGeneratedTotal() > 0) {
            return true;
        }
        if (snapshot.getPercent() != null && snapshot.getPercent().doubleValue() > 0.0d) {
            return true;
        }
        String currentStepCode = snapshot.getCurrentStepCode();
        if (!StringUtils.hasText(currentStepCode)) {
            return false;
        }
        String normalizedStep = currentStepCode.trim().toUpperCase();
        return !"CREATED".equals(normalizedStep)
                && !"QUEUED".equals(normalizedStep)
                && !"PENDING".equals(normalizedStep);
    }

    private ReconciliationSummary reconcileCompleted(DiagnosisTask openTask,
                                                     Diagnosis diagnosis,
                                                     ReconciliationSummary summary) {
        if (!v42ReportProjectionService.supports(diagnosis)) {
            return summary.withSkippedUnsupported(summary.skippedUnsupported() + 1);
        }
        V42DiagnosisResult.Task task = v42ReportProjectionService.readTask(diagnosis);
        V42DiagnosisResult.Report report = v42ReportProjectionService.readReport(diagnosis);
        if (task == null || report == null) {
            return summary.withSkippedUnsupported(summary.skippedUnsupported() + 1);
        }
        Map<String, Object> reportData = objectMapper.convertValue(
                diagnosis.getReportData(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        if (reportData == null || reportData.isEmpty()) {
            return summary.withSkippedUnsupported(summary.skippedUnsupported() + 1);
        }
        Diagnosis completionView = Diagnosis.builder()
                .id(diagnosis.getId())
                .completedAt(firstNonNull(diagnosis.getCompletedAt(), report.completedAt(), task.completedAt(), LocalDateTime.now()))
                .build();
        diagnosisTaskNativeRuntimeService.onDiagnosisCompleted(completionView, new V42DiagnosisResult(task, report), reportData);
        return summary.withRecoveredCompleted(summary.recoveredCompleted() + 1);
    }

    private String resolveTerminalMessage(Integer legacyStatus, String errorMessage) {
        if (StringUtils.hasText(errorMessage)) {
            return errorMessage.trim();
        }
        if (Objects.equals(legacyStatus, STATUS_FAILED)) {
            return "历史诊断已失败，运行单自动收尾";
        }
        if (Objects.equals(legacyStatus, STATUS_CANCELLED)) {
            return "历史诊断已取消，运行单自动收尾";
        }
        if (Objects.equals(legacyStatus, STATUS_EXPIRED)) {
            return "历史诊断已过期，运行单自动收尾";
        }
        return "历史诊断已结束，运行单自动收尾";
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
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

    public record ReconciliationSummary(String trigger,
                                        int scanned,
                                        int recoveredCompleted,
                                        int recoveredTerminal,
                                        int skippedMissingDiagnosis,
                                        int skippedUnsupported) {

        static ReconciliationSummary empty(String trigger) {
            return new ReconciliationSummary(trigger, 0, 0, 0, 0, 0);
        }

        ReconciliationSummary withRecoveredCompleted(int value) {
            return new ReconciliationSummary(trigger, scanned, value, recoveredTerminal, skippedMissingDiagnosis, skippedUnsupported);
        }

        ReconciliationSummary withRecoveredTerminal(int value) {
            return new ReconciliationSummary(trigger, scanned, recoveredCompleted, value, skippedMissingDiagnosis, skippedUnsupported);
        }

        ReconciliationSummary withSkippedMissingDiagnosis(int value) {
            return new ReconciliationSummary(trigger, scanned, recoveredCompleted, recoveredTerminal, value, skippedUnsupported);
        }

        ReconciliationSummary withSkippedUnsupported(int value) {
            return new ReconciliationSummary(trigger, scanned, recoveredCompleted, recoveredTerminal, skippedMissingDiagnosis, value);
        }
    }
}

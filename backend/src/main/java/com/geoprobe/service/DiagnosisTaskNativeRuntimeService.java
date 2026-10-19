package com.geoprobe.service;

import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.service.engine.v42.V42ExecutionObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagnosisTaskNativeRuntimeService {

    private final DiagnosisTaskRuntimePersistenceService diagnosisTaskRuntimePersistenceService;

    public boolean isEnabled() {
        return diagnosisTaskRuntimePersistenceService.isEnabled();
    }

    public DiagnosisProgressSnapshot readProgressSnapshot(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readProgressSnapshot(diagnosisId);
    }

    public DiagnosisTask readDiagnosisTask(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readDiagnosisTask(diagnosisId);
    }

    public Map<String, Object> readTaskOrderSnapshot(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskOrderSnapshot(diagnosisId);
    }

    public Map<String, Object> readTaskMetadataSnapshot(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskMetadataSnapshot(diagnosisId);
    }

    public Map<String, Object> readTaskContextSnapshot(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskContextSnapshot(diagnosisId);
    }

    public Map<String, Object> readTaskCreateDiagnosisPayloadSnapshot(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskCreateDiagnosisPayloadSnapshot(diagnosisId);
    }

    public Map<String, Object> readTaskBillingMetadata(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskBillingMetadata(diagnosisId);
    }

    public void upsertTaskBillingMetadata(String diagnosisId, Map<String, Object> billingMetadata) {
        diagnosisTaskRuntimePersistenceService.upsertTaskBillingMetadata(diagnosisId, billingMetadata);
    }

    public Map<String, Object> readTaskAliasConfigMetadata(String diagnosisId) {
        return diagnosisTaskRuntimePersistenceService.readTaskAliasConfigMetadata(diagnosisId);
    }

    public void upsertTaskAliasConfigMetadata(String diagnosisId, Map<String, Object> aliasConfigMetadata) {
        diagnosisTaskRuntimePersistenceService.upsertTaskAliasConfigMetadata(diagnosisId, aliasConfigMetadata);
    }

    public DiagnosisService.ProbeEventPage readProbeEvents(String diagnosisId, Long afterSeq, Integer limit) {
        return diagnosisTaskRuntimePersistenceService.readProbeEvents(diagnosisId, afterSeq, limit);
    }

    public DiagnosisService.ProgressStreamSnapshot buildProgressStreamSnapshot(Diagnosis diagnosis,
                                                                              DiagnosisProgressResponse.Progress progress,
                                                                              String stage,
                                                                              int stageIndex,
                                                                              String stageLabel,
                                                                              Integer etaSeconds) {
        return diagnosisTaskRuntimePersistenceService.buildProgressStreamSnapshot(
                diagnosis,
                progress,
                stage,
                stageIndex,
                stageLabel,
                etaSeconds
        );
    }

    public DiagnosisOpsMetricsResponse buildOpsMetrics(String diagnosisId, Integer legacyStatus, boolean refunded) {
        return diagnosisTaskRuntimePersistenceService.buildOpsMetrics(diagnosisId, legacyStatus, refunded);
    }

    public DiagnosisTask ensureTaskShell(String diagnosisId,
                                         String userId,
                                         String brandName,
                                         String category,
                                         String brandWebsite,
                                         Integer sourceCode,
                                         Integer statusCode,
                                         FrontDeskTaskOrder taskOrder,
                                         Object contextPackage,
                                         String engineVersion,
                                         Map<String, Object> createDiagnosisPayloadSnapshot) {
        return diagnosisTaskRuntimePersistenceService.ensureTaskShell(
                diagnosisId,
                userId,
                brandName,
                category,
                brandWebsite,
                sourceCode,
                statusCode,
                taskOrder,
                contextPackage,
                engineVersion,
                createDiagnosisPayloadSnapshot
        );
    }

    public void onTaskCreated(Diagnosis diagnosis,
                              FrontDeskTaskOrder taskOrder,
                              Object contextPackage,
                              String engineVersion,
                              Map<String, Object> createDiagnosisPayloadSnapshot) {
        diagnosisTaskRuntimePersistenceService.mirrorTaskCreated(
                diagnosis,
                taskOrder,
                contextPackage,
                engineVersion,
                createDiagnosisPayloadSnapshot
        );
    }

    public void removeTaskShell(String diagnosisId) {
        diagnosisTaskRuntimePersistenceService.removeTaskShell(diagnosisId);
    }

    public void onTaskRunning(String diagnosisId, LocalDateTime startedAt) {
        diagnosisTaskRuntimePersistenceService.onTaskRunning(diagnosisId, startedAt);
    }

    public void onTaskTerminal(String diagnosisId, Integer statusCode, String failureMessage, LocalDateTime completedAt) {
        diagnosisTaskRuntimePersistenceService.onTaskTerminal(diagnosisId, statusCode, failureMessage, completedAt);
    }

    public void onProgress(String diagnosisId,
                           String currentStep,
                           String currentStepLabel,
                           int currentStepIndex,
                           int totalSteps,
                           int samplingCompleted,
                           int samplingTotal,
                           int samplingExpectedTotal,
                           int samplingFailedTotal,
                           boolean fallbackUsed,
                           double progressPercent) {
        diagnosisTaskRuntimePersistenceService.onProgress(
                diagnosisId,
                currentStep,
                currentStepLabel,
                currentStepIndex,
                totalSteps,
                samplingCompleted,
                samplingTotal,
                samplingExpectedTotal,
                samplingFailedTotal,
                fallbackUsed,
                progressPercent
        );
    }

    public void onEvent(String diagnosisId, DiagnosisService.ProbeEventItem item) {
        diagnosisTaskRuntimePersistenceService.onEvent(diagnosisId, item);
    }

    public void onStageCompleted(String diagnosisId, V42ExecutionObserver.StageTrace trace) {
        diagnosisTaskRuntimePersistenceService.onStageCompleted(diagnosisId, trace);
    }

    public void onExecutionFailed(String diagnosisId, V42ExecutionObserver.FailureTrace trace) {
        diagnosisTaskRuntimePersistenceService.onExecutionFailed(diagnosisId, trace);
    }

    public void onDiagnosisCompleted(Diagnosis diagnosis,
                                     V42DiagnosisResult result,
                                     Map<String, Object> reportData) {
        diagnosisTaskRuntimePersistenceService.onDiagnosisCompleted(diagnosis, result, reportData);
    }

    public void onDiagnosisSamplingFailed(Diagnosis diagnosis, V42DiagnosisResult.Task taskResult) {
        diagnosisTaskRuntimePersistenceService.onDiagnosisSamplingFailed(diagnosis, taskResult);
    }
}

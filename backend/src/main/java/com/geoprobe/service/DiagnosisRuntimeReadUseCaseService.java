package com.geoprobe.service;

import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DiagnosisRuntimeReadUseCaseService {

    private final DiagnosisAccessService diagnosisAccessService;
    private final DiagnosisRuntimeQueryService diagnosisRuntimeQueryService;

    public DiagnosisProgressResponse getDiagnosisProgress(String userId, String diagnosisId) {
        Diagnosis diagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        return diagnosisRuntimeQueryService.buildProgressResponse(diagnosis);
    }

    public DiagnosisService.ProgressStreamSnapshot getProgressStreamSnapshot(String userId, String diagnosisId) {
        Diagnosis diagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        return diagnosisRuntimeQueryService.buildProgressStreamSnapshot(diagnosis);
    }

    public DiagnosisService.ProgressStreamFrame getProgressStreamFrame(String userId,
                                                                       String diagnosisId,
                                                                       Long afterSeq,
                                                                       Integer limit) {
        Diagnosis diagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        return diagnosisRuntimeQueryService.buildProgressStreamFrame(diagnosis, afterSeq, limit);
    }

    public DiagnosisService.ProbeEventPage getProbeEvents(String userId, String diagnosisId, Long afterSeq, Integer limit) {
        diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        return diagnosisRuntimeQueryService.readProbeEvents(diagnosisId, afterSeq, limit);
    }

    public DiagnosisOpsMetricsResponse getOpsMetrics(String userId, String diagnosisId) {
        Diagnosis diagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        return diagnosisRuntimeQueryService.buildOpsMetrics(userId, diagnosisId, diagnosis);
    }
}

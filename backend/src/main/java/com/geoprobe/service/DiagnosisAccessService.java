package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiagnosisAccessService {

    private final DiagnosisMapper diagnosisMapper;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    @Autowired(required = false)
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;

    public Diagnosis getByIdAndUserId(String diagnosisId, String userId) {
        Diagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis != null) {
            if (!Objects.equals(diagnosis.getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return diagnosis;
        }
        Diagnosis taskDiagnosis = readTaskDiagnosisView(diagnosisId, null);
        if (taskDiagnosis == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        if (!Objects.equals(taskDiagnosis.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return taskDiagnosis;
    }

    public Diagnosis getRuntimeReadByIdAndUserId(String diagnosisId, String userId) {
        Diagnosis taskDiagnosis = readFormalTaskDiagnosisByUser(diagnosisId, userId);
        if (taskDiagnosis != null) {
            return taskDiagnosis;
        }
        if (!shouldFallbackToLegacyFormalRead()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        return getByIdAndUserId(diagnosisId, userId);
    }

    public Diagnosis findRuntimeReadByIdAndUserId(String diagnosisId, String userId) {
        if (!StringUtils.hasText(diagnosisId) || !StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return getRuntimeReadByIdAndUserId(diagnosisId, userId);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.DIAGNOSIS_NOT_FOUND || ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return null;
            }
            throw ex;
        }
    }

    public Diagnosis getReportReadByIdAndUserId(String diagnosisId, String userId) {
        Diagnosis taskDiagnosis = readFormalTaskDiagnosisByUser(diagnosisId, userId);
        if (taskDiagnosis != null) {
            return taskDiagnosis;
        }
        if (!shouldFallbackToLegacyFormalRead()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        return getByIdAndUserId(diagnosisId, userId);
    }

    public Diagnosis getByIdOrThrow(String diagnosisId) {
        Diagnosis diagnosis = diagnosisMapper.selectById(diagnosisId);
        if (diagnosis != null) {
            return diagnosis;
        }
        Diagnosis taskDiagnosis = readTaskDiagnosisView(diagnosisId, null);
        if (taskDiagnosis == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        return taskDiagnosis;
    }

    private Diagnosis readFormalTaskDiagnosisByUser(String diagnosisId, String userId) {
        if (!shouldUseTaskNativeFormalRead()) {
            return null;
        }
        Diagnosis taskDiagnosis = readTaskDiagnosisView(diagnosisId, null);
        if (taskDiagnosis == null) {
            return null;
        }
        if (!Objects.equals(taskDiagnosis.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return taskDiagnosis;
    }

    private boolean shouldUseTaskNativeFormalRead() {
        return isTaskNativeRuntimeEnabled();
    }

    private boolean shouldFallbackToLegacyFormalRead() {
        return !isTaskNativeRuntimeEnabled();
    }

    private boolean isTaskNativeRuntimeEnabled() {
        return diagnosisTaskNativeRuntimeService != null && diagnosisTaskNativeRuntimeService.isEnabled();
    }

    private Diagnosis readTaskDiagnosisView(String diagnosisId, Map<String, Object> reportProjection) {
        if (!isTaskNativeRuntimeEnabled() || !StringUtils.hasText(diagnosisId)) {
            return null;
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(diagnosisId);
        if (task == null) {
            return null;
        }
        return toDiagnosisView(task, reportProjection);
    }

    @SuppressWarnings("unchecked")
    private Diagnosis toDiagnosisView(DiagnosisTask task, Map<String, Object> reportProjection) {
        Map<String, Object> taskOrder = task.getTaskOrderJson();
        Object competitorsValue = taskOrder == null ? null : taskOrder.get("competitors");
        List<String> competitors = competitorsValue instanceof List<?> raw
                ? raw.stream().filter(Objects::nonNull).map(String::valueOf).filter(StringUtils::hasText).map(String::trim).toList()
                : List.of();
        String userPrompt = null;
        if (taskOrder != null) {
            Object businessHintsValue = taskOrder.get("businessHints");
            if (businessHintsValue instanceof Map<?, ?> hintsMap) {
                Object rawPrompt = hintsMap.get("userPrompt");
                if (rawPrompt instanceof String prompt && StringUtils.hasText(prompt)) {
                    userPrompt = prompt.trim();
                }
            }
        }
        return Diagnosis.builder()
                .id(task.getId())
                .userId(task.getRequestedByUserId())
                .brandName(task.getBrandNameSnapshot())
                .category(task.getCategorySnapshot())
                .brandWebsite(task.getOfficialWebsiteSnapshot())
                .userPrompt(userPrompt)
                .competitorsInput(competitors)
                .status(task.getStatusCode())
                .source(task.getSourceCode())
                .reportData(reportProjection == null || reportProjection.isEmpty() ? null : reportProjection)
                .totalLlmCostYuan(task.getTotalCostYuan())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .errorMessage(task.getFailureMessage())
                .build();
    }
}

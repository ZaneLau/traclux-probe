package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.RunMapper;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisRuntimeQueryServiceTest {

    @Mock
    private RunMapper runMapper;

    @Mock
    private LlmCallLogMapper llmCallLogMapper;

    @Mock
    private CreditTransactionMapper creditTransactionMapper;

    @Mock
    private DiagnosisProbeEventService diagnosisProbeEventService;

    @Mock
    private V42ReportProjectionService v42ReportProjectionService;

    @Mock
    private RedisSupportService redisSupportService;

    private DiagnosisRuntimeQueryService diagnosisRuntimeQueryService;

    @BeforeEach
    void setUp() {
        diagnosisRuntimeQueryService = new DiagnosisRuntimeQueryService(
                runMapper,
                llmCallLogMapper,
                creditTransactionMapper,
                diagnosisProbeEventService,
                new V42TargetProbeCountPolicy(),
                v42ReportProjectionService,
                new ObjectMapper(),
                redisSupportService
        );
    }

    @Test
    void shouldUseTruthfulRunningProgressWhenRedisMissing() {
        Diagnosis diagnosis = buildDiagnosis("diag-running", 1, 50, 10, 40, true);
        V42DiagnosisResult.Task task = buildTask("task-running", 10, 8, 2);

        when(redisSupportService.getJson(eq("diagnosis:diag-running:progress"), any(TypeReference.class), eq("diagnosis progress snapshot")))
                .thenReturn(null);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(task);

        DiagnosisProgressResponse response = diagnosisRuntimeQueryService.buildProgressResponse(diagnosis);

        assertEquals("SAMPLING", response.getProgress().getCurrentStep());
        assertEquals("运行中，等待实时进度信号；当前仅展示可证实采样结果", response.getProgress().getCurrentStepLabel());
        assertEquals(8, response.getProgress().getSamplingCompleted());
        assertEquals(50, response.getProgress().getSamplingExpectedTotal());
        assertEquals(10, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(40, response.getProgress().getSamplingMissingTotal());
        assertEquals(20.0d, response.getProgress().getPercent());
    }

    @Test
    void shouldUseTruthfulCompletedProgressWhenRedisMissing() {
        Diagnosis diagnosis = buildDiagnosis("diag-completed", 2, 50, 50, 0, false);
        V42DiagnosisResult.Task task = buildTask("task-completed", 50, 50, 0);

        when(redisSupportService.getJson(eq("diagnosis:diag-completed:progress"), any(TypeReference.class), eq("diagnosis progress snapshot")))
                .thenReturn(null);
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(task);

        DiagnosisProgressResponse response = diagnosisRuntimeQueryService.buildProgressResponse(diagnosis);

        assertEquals("COMPLETED", response.getProgress().getCurrentStep());
        assertEquals(50, response.getProgress().getSamplingCompleted());
        assertEquals(50, response.getProgress().getSamplingExpectedTotal());
        assertEquals(50, response.getProgress().getSamplingGeneratedTotal());
        assertEquals(0, response.getProgress().getSamplingMissingTotal());
        assertEquals(100.0d, response.getProgress().getPercent());
    }

    private Diagnosis buildDiagnosis(String diagnosisId,
                                     int status,
                                     int targetProbeCount,
                                     int generatedSamples,
                                     int missingSamples,
                                     boolean shortfall) {
        return Diagnosis.builder()
                .id(diagnosisId)
                .brandName("测试品牌")
                .status(status)
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .reportData(Map.of(
                        "meta", Map.of(
                                "taskOrderSnapshot", Map.of(
                                        "businessHints", Map.of(
                                                "sampling", Map.of("targetProbeCount", targetProbeCount)
                                        )
                                ),
                                "generatedSamples", generatedSamples,
                                "missingSamples", missingSamples,
                                "samplingVariantShortfall", shortfall
                        )
                ))
                .build();
    }

    private V42DiagnosisResult.Task buildTask(String taskId,
                                              int sampleSize,
                                              int successCount,
                                              int failedCount) {
        List<V42DiagnosisResult.ProbeTrace> probeTrace = java.util.stream.IntStream.range(0, sampleSize)
                .mapToObj(index -> new V42DiagnosisResult.ProbeTrace(
                        "probe-" + index,
                        BucketType.ORTHOGONAL,
                        Map.of("D1", "initiation"),
                        Map.of("D1", "刚开始看"),
                        ProbeExposurePolicy.BRAND_HIDDEN,
                        "测试问题？",
                        ProbeDispatchStatus.SUCCESS,
                        ProbeType.OPEN_DISCOVERY,
                        false,
                        "测试回答",
                        10,
                        "short",
                        null,
                        null
                ))
                .toList();
        return new V42DiagnosisResult.Task(
                taskId,
                "report-" + taskId,
                "light_cone_4_3",
                "RUNNING",
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now(),
                sampleSize,
                "doubao",
                successCount,
                failedCount,
                RealtimeSearchSummary.disabled("doubao"),
                probeTrace
        );
    }
}

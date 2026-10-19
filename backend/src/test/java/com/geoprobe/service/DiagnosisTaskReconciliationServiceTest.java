package com.geoprobe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.DashboardCharts;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.InsightItem;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node3UsageCostSummary;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import com.geoprobe.dto.engine.v42.V42DiagnosisResult;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisTaskReconciliationServiceTest {

    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;
    @Mock
    private DiagnosisMapper diagnosisMapper;
    @Mock
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Mock
    private V42ReportProjectionService v42ReportProjectionService;

    @InjectMocks
    private DiagnosisTaskReconciliationService reconciliationService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(reconciliationService, "openTaskLimit", 20);
        ReflectionTestUtils.setField(reconciliationService, "objectMapper", objectMapper);
    }

    @Test
    void shouldRecoverCompletedTaskFromLegacyDiagnosis() {
        DiagnosisTask openTask = DiagnosisTask.builder()
                .id("diag-1")
                .statusCode(1)
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-1")
                .status(2)
                .completedAt(LocalDateTime.of(2026, 5, 13, 22, 44, 17))
                .reportData(Map.of(
                        "meta", Map.of("engineVersion", "4.2"),
                        "task", Map.of("taskId", "diag-1"),
                        "report", Map.of("reportId", "report-1")
                ))
                .build();
        V42DiagnosisResult.Task task = sampleTask();
        V42DiagnosisResult.Report report = sampleReport();

        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(openTask));
        when(diagnosisMapper.selectBatchIds(List.of("diag-1"))).thenReturn(List.of(diagnosis));
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(true);
        when(v42ReportProjectionService.readTask(diagnosis)).thenReturn(task);
        when(v42ReportProjectionService.readReport(diagnosis)).thenReturn(report);

        DiagnosisTaskReconciliationService.ReconciliationSummary summary =
                reconciliationService.reconcileOpenTasks("test");

        ArgumentCaptor<Diagnosis> diagnosisCaptor = ArgumentCaptor.forClass(Diagnosis.class);
        ArgumentCaptor<V42DiagnosisResult> resultCaptor = ArgumentCaptor.forClass(V42DiagnosisResult.class);
        verify(diagnosisTaskNativeRuntimeService).onDiagnosisCompleted(diagnosisCaptor.capture(), resultCaptor.capture(), any());
        assertEquals("diag-1", diagnosisCaptor.getValue().getId());
        assertEquals(diagnosis.getCompletedAt(), diagnosisCaptor.getValue().getCompletedAt());
        assertEquals("diag-1", resultCaptor.getValue().task().taskId());
        assertEquals(1, summary.recoveredCompleted());
        assertEquals(0, summary.recoveredTerminal());
    }

    @Test
    void shouldCloseTerminalShellWhenLegacyDiagnosisAlreadyFailed() {
        DiagnosisTask openTask = DiagnosisTask.builder()
                .id("diag-2")
                .statusCode(1)
                .completedAt(LocalDateTime.of(2026, 5, 13, 22, 0, 0))
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-2")
                .status(3)
                .errorMessage("采样阶段失败")
                .completedAt(LocalDateTime.of(2026, 5, 13, 22, 1, 0))
                .build();

        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(openTask));
        when(diagnosisMapper.selectBatchIds(List.of("diag-2"))).thenReturn(List.of(diagnosis));

        DiagnosisTaskReconciliationService.ReconciliationSummary summary =
                reconciliationService.reconcileOpenTasks("test");

        verify(diagnosisTaskNativeRuntimeService).onTaskTerminal("diag-2", 3, "采样阶段失败", diagnosis.getCompletedAt());
        verify(diagnosisTaskNativeRuntimeService, never()).onDiagnosisCompleted(any(), any(), any());
        assertEquals(0, summary.recoveredCompleted());
        assertEquals(1, summary.recoveredTerminal());
    }

    @Test
    void shouldSkipUnsupportedCompletedDiagnosisWithoutProjectionPayload() {
        DiagnosisTask openTask = DiagnosisTask.builder()
                .id("diag-3")
                .statusCode(1)
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-3")
                .status(2)
                .reportData(Map.of("meta", Map.of("engineVersion", "4.2")))
                .build();

        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(openTask));
        when(diagnosisMapper.selectBatchIds(List.of("diag-3"))).thenReturn(List.of(diagnosis));
        when(v42ReportProjectionService.supports(diagnosis)).thenReturn(false);

        DiagnosisTaskReconciliationService.ReconciliationSummary summary =
                reconciliationService.reconcileOpenTasks("test");

        verify(diagnosisTaskNativeRuntimeService, never()).onDiagnosisCompleted(any(), any(), any());
        assertEquals(1, summary.skippedUnsupported());
    }

    @Test
    void shouldRecoverRunningTaskWhenProgressSnapshotShowsExecutionStarted() {
        DiagnosisTask openTask = DiagnosisTask.builder()
                .id("diag-4")
                .statusCode(0)
                .updatedAt(LocalDateTime.of(2026, 5, 14, 10, 5, 0))
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-4")
                .status(1)
                .build();
        com.geoprobe.entity.v1.DiagnosisProgressSnapshot snapshot =
                com.geoprobe.entity.v1.DiagnosisProgressSnapshot.builder()
                        .diagnosisTaskId("diag-4")
                        .currentStepCode("NODE3_SAMPLING")
                        .currentStepLabel("正在向 AI 发送探测请求（12/50）...")
                        .percent(new BigDecimal("46.72"))
                        .samplingCompleted(12)
                        .samplingGeneratedTotal(12)
                        .build();

        when(diagnosisTaskNativeRuntimeService.isEnabled()).thenReturn(true);
        when(diagnosisTaskMapper.selectList(any())).thenReturn(List.of(openTask));
        when(diagnosisMapper.selectBatchIds(List.of("diag-4"))).thenReturn(List.of(diagnosis));
        when(diagnosisTaskNativeRuntimeService.readProgressSnapshot("diag-4")).thenReturn(snapshot);

        DiagnosisTaskReconciliationService.ReconciliationSummary summary =
                reconciliationService.reconcileOpenTasks("test");

        verify(diagnosisTaskNativeRuntimeService).onTaskRunning("diag-4", openTask.getUpdatedAt());
        verify(diagnosisTaskNativeRuntimeService, never()).onDiagnosisCompleted(any(), any(), any());
        verify(diagnosisTaskNativeRuntimeService, never()).onTaskTerminal(any(), any(), any(), any());
        assertEquals(0, summary.recoveredCompleted());
        assertEquals(0, summary.recoveredTerminal());
        assertEquals(0, summary.skippedMissingDiagnosis());
        assertEquals(0, summary.skippedUnsupported());
    }

    private V42DiagnosisResult.Task sampleTask() {
        V42DiagnosisResult.ProbeTrace probeTrace = new V42DiagnosisResult.ProbeTrace(
                "probe-1",
                BucketType.ORTHOGONAL,
                Map.of("D1", "用户身份"),
                Map.of("D1", "用户身份"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "美厨物语和竞品哪个好",
                ProbeDispatchStatus.SUCCESS,
                ProbeType.OPEN_DISCOVERY,
                true,
                "回答文本",
                32,
                "medium",
                "brand",
                null
        );
        return new V42DiagnosisResult.Task(
                "diag-1",
                "report-1",
                "4.2",
                "COMPLETED",
                LocalDateTime.of(2026, 5, 13, 22, 30, 0),
                LocalDateTime.of(2026, 5, 13, 22, 44, 17),
                1,
                "doubao",
                1,
                0,
                RealtimeSearchSummary.disabled("doubao"),
                new Node3UsageCostSummary(
                        10,
                        20,
                        30,
                        0,
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000"),
                        new BigDecimal("0.000000")
                ),
                List.of(probeTrace),
                List.of()
        );
    }

    private V42DiagnosisResult.Report sampleReport() {
        LengthBucketKpi shortBucket = new LengthBucketKpi("Short", 1, 1, 0, 1.0d, 0.0d, 1.0d);
        LengthStratifiedKpi lengthKpi = new LengthStratifiedKpi(shortBucket, shortBucket, shortBucket);
        BucketKpi bucketKpi = new BucketKpi(BucketType.ORTHOGONAL, 1, 1, 1, 0, 1.0d, 0.0d, 1.0d, 1.0d, 1.0d);
        KpiSummary kpiSummary = new KpiSummary(
                1.0d,
                0.0d,
                1.0d,
                lengthKpi,
                1.0d,
                1.0d,
                80.0d,
                bucketKpi,
                bucketKpi,
                bucketKpi,
                Map.of("品牌选择", 1.0d),
                Map.of("品牌选择", 1.0d),
                Map.of(),
                1
        );
        CommercialInsights insights = new CommercialInsights(
                "品牌在核心主题上优势明确",
                List.of(
                        new InsightItem("优势1", "提及率100%，推荐率100%", "必须继续强化品牌选择内容覆盖"),
                        new InsightItem("优势2", "正向率100%，样本量1", "立即沉淀当前高表现问法"),
                        new InsightItem("优势3", "稳定度80，样本量1", "启动主题扩展验证以确认稳定性")
                ),
                "LLM"
        );
        DefenseCompletenessSummary defense = new DefenseCompletenessSummary(
                1.0d,
                1,
                List.of(DefenseCompletenessResult.missing("probe-1"))
        );
        DashboardCharts charts = new DashboardCharts(
                new DashboardCharts.RadarChartData(
                        "radar",
                        List.of("品牌选择"),
                        "美厨物语",
                        List.of(1.0d),
                        Map.of()
                ),
                new DashboardCharts.ScatterChartData(
                        "scatter",
                        "distance",
                        "recommendRate",
                        List.of(new DashboardCharts.ScatterPoint("美厨物语", 1.0d, 1.0d, true, 1.0d, 1))
                ),
                new DashboardCharts.SankeyChartData(
                        "sankey",
                        List.of(
                                new DashboardCharts.SankeyNode("intent-1", "品牌选择", "intent"),
                                new DashboardCharts.SankeyNode("candidate-1", "美厨物语", "candidate"),
                                new DashboardCharts.SankeyNode("recommend-1", "推荐", "recommend")
                        ),
                        List.of(
                                new DashboardCharts.SankeyLink("intent-1", "candidate-1", 1),
                                new DashboardCharts.SankeyLink("candidate-1", "recommend-1", 1)
                        )
                ),
                new DashboardCharts.StackedBarChartData(
                        "stacked_bar",
                        List.of("品牌选择"),
                        List.of(new DashboardCharts.MetricSeries("orthogonal", "正交", List.of(1.0d), 1))
                )
        );
        return new V42DiagnosisResult.Report(
                "diag-1",
                "report-1",
                "4.2",
                LocalDateTime.of(2026, 5, 13, 22, 30, 0),
                LocalDateTime.of(2026, 5, 13, 22, 44, 17),
                1,
                "doubao",
                "方法说明",
                "免责声明",
                kpiSummary,
                insights,
                defense,
                charts,
                List.of(new V42DiagnosisResult.DisplayNameMapping("brand_choice", "品牌选择")),
                List.of()
        );
    }
}

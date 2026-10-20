package com.geoprobe.service;

import com.geoprobe.ai.ModelCapability;
import com.geoprobe.ai.ModelCapabilityRegistry;
import com.geoprobe.common.DiagnosisEdition;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.service.shared.DiagnosisConfig;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisSnapshotServiceTest {

    @Test
    void shouldFallbackThresholdToZeroWhenConfiguredAsInfinite() {
        DiagnosisMapSupport diagnosisMapSupport = mock(DiagnosisMapSupport.class);
        when(diagnosisMapSupport.toSnapshotMap(any())).thenReturn(Map.of());
        ModelCapabilityRegistry capabilityRegistry = mock(ModelCapabilityRegistry.class);
        when(capabilityRegistry.resolve(any(), any())).thenReturn(new ModelCapability(true, true, true, true));
        DiagnosisSnapshotService snapshotService = new DiagnosisSnapshotService(diagnosisMapSupport, capabilityRegistry);

        ReflectionTestUtils.setField(snapshotService, "samplingStrictThreshold", Double.POSITIVE_INFINITY);
        ReflectionTestUtils.setField(snapshotService, "samplingFloorThreshold", Double.NEGATIVE_INFINITY);

        DiagnosisConfig diagnosisConfig = DiagnosisEdition.STANDARD.toConfig();
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "佳帮手",
                "厨房置物架",
                List.of("美之高"),
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of()
        );
        EngineTaskContextPackage contextPackage = new EngineTaskContextPackage(
                "佳帮手",
                "厨房置物架",
                List.of("美之高"),
                List.of("收纳"),
                List.of(new CategoryAnchorSignal("收纳", "manual", 1.0d)),
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );

        Map<String, Object> reportData = snapshotService.buildInitialReportData(
                "req-1",
                "佳帮手",
                "厨房置物架",
                DiagnosisEdition.STANDARD,
                diagnosisConfig,
                "4.2",
                List.of("收纳"),
                false,
                "manual",
                List.of(),
                Map.of(),
                taskOrder,
                contextPackage,
                Map.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) reportData.get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> configSnapshot = (Map<String, Object>) meta.get("diagnosisConfigSnapshot");
        assertEquals(0.0d, configSnapshot.get("samplingStrictThreshold"));
        assertEquals(0.0d, configSnapshot.get("samplingFloorThreshold"));
        assertEquals(90L, configSnapshot.get("extractionModelTimeoutSeconds"));
        assertEquals(12L, configSnapshot.get("aliasLlmTimeoutSeconds"));
    }

    @Test
    void shouldFallbackToTaskOrderMetadataWhenCanonicalPayloadMetaIsMissing() {
        DiagnosisMapSupport diagnosisMapSupport = mock(DiagnosisMapSupport.class);
        ModelCapabilityRegistry capabilityRegistry = mock(ModelCapabilityRegistry.class);
        when(capabilityRegistry.resolve(any(), any())).thenReturn(new ModelCapability(true, true, true, true));
        DiagnosisSnapshotService snapshotService = new DiagnosisSnapshotService(diagnosisMapSupport, capabilityRegistry);

        Map<String, Object> taskOrderSnapshot = Map.of(
                "brandName", "海尔",
                "category", "洗衣机",
                "taskMetadata", Map.of(
                        "requestId", "monitor-demo-preview-req",
                        "edition", "STANDARD",
                        "engineVersion", "4.2"
                )
        );

        Map<String, Object> payloadSnapshot = snapshotService.buildCanonicalCreateDiagnosisPayloadSnapshot(
                taskOrderSnapshot,
                Map.of(),
                Map.of()
        );

        assertEquals("monitor-demo-preview-req", payloadSnapshot.get("requestId"));
        assertEquals("STANDARD", payloadSnapshot.get("edition"));
        assertEquals("4.2", payloadSnapshot.get("engineVersion"));
        assertEquals(taskOrderSnapshot, payloadSnapshot.get("frontDeskTaskOrder"));
    }
}

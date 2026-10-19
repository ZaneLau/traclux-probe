package com.geoprobe.service.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.service.DiagnosisTaskNativeRuntimeService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisAliasConfigServiceTest {

    @Test
    void shouldPreferTaskNativeAliasConfigWhenAvailable() {
        DiagnosisMapper diagnosisMapper = mock(DiagnosisMapper.class);
        DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService = mock(DiagnosisTaskNativeRuntimeService.class);
        DiagnosisAliasConfigService service = new DiagnosisAliasConfigService(
                diagnosisMapper,
                diagnosisTaskNativeRuntimeService,
                new ObjectMapper()
        );

        when(diagnosisTaskNativeRuntimeService.readTaskAliasConfigMetadata("diag-alias")).thenReturn(Map.of(
                "brandAliases", List.of("任务别名"),
                "competitorAliases", Map.of("Insta360", List.of("影石任务别名")),
                "aliasConfigVersion", "1"
        ));

        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-alias")
                .reportData(Map.of("meta", Map.of(
                        "brandAliases", List.of("旧别名"),
                        "competitorAliases", Map.of("Insta360", List.of("旧竞品别名"))
                )))
                .build();

        DiagnosisAliasConfigData config = service.read(diagnosis);

        assertEquals(List.of("任务别名"), config.brandAliases());
        assertEquals(List.of("影石任务别名"), config.competitorAliases().get("Insta360"));
    }

    @Test
    void shouldFallbackToLegacyAliasConfigWhenTaskNativeSidecarMissing() {
        DiagnosisMapper diagnosisMapper = mock(DiagnosisMapper.class);
        DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService = mock(DiagnosisTaskNativeRuntimeService.class);
        DiagnosisAliasConfigService service = new DiagnosisAliasConfigService(
                diagnosisMapper,
                diagnosisTaskNativeRuntimeService,
                new ObjectMapper()
        );

        when(diagnosisTaskNativeRuntimeService.readTaskAliasConfigMetadata("diag-legacy")).thenReturn(Map.of());

        Diagnosis diagnosis = Diagnosis.builder()
                .id("diag-legacy")
                .reportData(Map.of("meta", Map.of(
                        "brandAliases", List.of("小米之家", "Mi Home"),
                        "competitorAliases", Map.of("华为", List.of("HUAWEI"))
                )))
                .build();

        DiagnosisAliasConfigData config = service.read(diagnosis);

        assertEquals(List.of("小米之家", "Mi Home"), config.brandAliases());
        assertEquals(List.of("HUAWEI"), config.competitorAliases().get("华为"));
    }

    @Test
    void shouldPersistAliasConfigToTaskNativeAndLegacyMeta() {
        DiagnosisMapper diagnosisMapper = mock(DiagnosisMapper.class);
        DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService = mock(DiagnosisTaskNativeRuntimeService.class);
        DiagnosisAliasConfigService service = new DiagnosisAliasConfigService(
                diagnosisMapper,
                diagnosisTaskNativeRuntimeService,
                new ObjectMapper()
        );

        AtomicReference<Map<String, Object>> reportDataRef = new AtomicReference<>(legacyReportData());
        AtomicReference<Map<String, Object>> taskAliasRef = new AtomicReference<>(Map.of());
        when(diagnosisMapper.selectById("diag-persist")).thenAnswer(invocation -> Diagnosis.builder()
                .id("diag-persist")
                .reportData(reportDataRef.get())
                .build());
        when(diagnosisMapper.updateById(any(Diagnosis.class))).thenAnswer(invocation -> {
            Diagnosis updating = invocation.getArgument(0, Diagnosis.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> updatedReportData = (Map<String, Object>) updating.getReportData();
            reportDataRef.set(updatedReportData);
            return 1;
        });
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> updatedAliasMetadata = invocation.getArgument(1, Map.class);
            taskAliasRef.set(updatedAliasMetadata);
            return null;
        }).when(diagnosisTaskNativeRuntimeService).upsertTaskAliasConfigMetadata(eq("diag-persist"), any());

        service.persist("diag-persist", new DiagnosisAliasConfigData(
                List.of("任务品牌别名"),
                Map.of("Insta360", List.of("任务竞品别名"))
        ));

        assertEquals(List.of("任务品牌别名"), taskAliasRef.get().get("brandAliases"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> taskCompetitorAliases = (Map<String, List<String>>) taskAliasRef.get().get("competitorAliases");
        assertEquals(List.of("任务竞品别名"), taskCompetitorAliases.get("Insta360"));
        assertTrue(taskAliasRef.get().containsKey("aliasUpdatedAt"));

        @SuppressWarnings("unchecked")
        Map<String, Object> legacyMeta = (Map<String, Object>) reportDataRef.get().get("meta");
        assertEquals(List.of("任务品牌别名"), legacyMeta.get("brandAliases"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> legacyCompetitorAliases = (Map<String, List<String>>) legacyMeta.get("competitorAliases");
        assertEquals(List.of("任务竞品别名"), legacyCompetitorAliases.get("Insta360"));
        assertEquals("1", legacyMeta.get("aliasConfigVersion"));

        @SuppressWarnings("unchecked")
        Map<String, Object> diagnosisConfigSnapshot = (Map<String, Object>) legacyMeta.get("diagnosisConfigSnapshot");
        assertEquals(List.of("任务品牌别名"), diagnosisConfigSnapshot.get("brandAliases"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> snapshotCompetitorAliases = (Map<String, List<String>>) diagnosisConfigSnapshot.get("competitorAliases");
        assertEquals(List.of("任务竞品别名"), snapshotCompetitorAliases.get("Insta360"));
    }

    private static Map<String, Object> legacyReportData() {
        Map<String, Object> diagnosisConfigSnapshot = new LinkedHashMap<>();
        diagnosisConfigSnapshot.put("brandAliases", List.of("旧品牌别名"));
        diagnosisConfigSnapshot.put("competitorAliases", Map.of("Insta360", List.of("旧竞品别名")));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("brandAliases", List.of("旧品牌别名"));
        meta.put("competitorAliases", Map.of("Insta360", List.of("旧竞品别名")));
        meta.put("diagnosisConfigSnapshot", diagnosisConfigSnapshot);
        Map<String, Object> reportData = new LinkedHashMap<>();
        reportData.put("meta", meta);
        return reportData;
    }
}

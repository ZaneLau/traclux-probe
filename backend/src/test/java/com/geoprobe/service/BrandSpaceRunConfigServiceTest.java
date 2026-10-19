package com.geoprobe.service;

import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceRunConfigServiceTest {

    @Mock
    private BrandScopeMapper brandScopeMapper;

    @InjectMocks
    private BrandSpaceRunConfigService brandSpaceRunConfigService;

    @Test
    void shouldResolveStoredRunConfigAndDeriveAutoRunFromTrackingMode() {
        BrandScope scope = BrandScope.builder()
                .id("bs_1")
                .trackingMode("PAUSED")
                .defaultModelPolicyJson(Map.of(
                        "brandSpaceRunConfig",
                        Map.of(
                                "targetProbeCount", 32,
                                "targetModelProduct", "doubao-pro"
                        )
                ))
                .build();

        BrandSpaceRunConfigService.CurrentRunConfig config =
                brandSpaceRunConfigService.resolveCurrentRunConfig(scope, 8);

        assertEquals(32, config.targetProbeCount());
        assertEquals("doubao-pro", config.targetModelProduct());
        assertFalse(config.autoRunEnabled());
    }

    @Test
    void shouldPersistNormalizedRunConfigAndTrackingMode() {
        BrandScope scope = BrandScope.builder()
                .id("bs_2")
                .trackingMode("ACTIVE")
                .defaultModelPolicyJson(new LinkedHashMap<>())
                .build();
        when(brandScopeMapper.selectById("bs_2")).thenReturn(scope);

        BrandSpaceRunConfigService.CurrentRunConfig saved =
                brandSpaceRunConfigService.saveCurrentRunConfig("bs_2", 120, " ", false, 6);

        assertEquals(100, saved.targetProbeCount());
        assertEquals("doubao", saved.targetModelProduct());
        assertFalse(saved.autoRunEnabled());

        ArgumentCaptor<BrandScope> scopeCaptor = ArgumentCaptor.forClass(BrandScope.class);
        verify(brandScopeMapper).updateById(scopeCaptor.capture());
        BrandScope persisted = scopeCaptor.getValue();
        assertEquals("PAUSED", persisted.getTrackingMode());
        Object rawRunConfig = persisted.getDefaultModelPolicyJson().get("brandSpaceRunConfig");
        assertTrue(rawRunConfig instanceof Map<?, ?>);
        Map<?, ?> runConfig = (Map<?, ?>) rawRunConfig;
        assertEquals(100, runConfig.get("targetProbeCount"));
        assertEquals("doubao", runConfig.get("targetModelProduct"));
    }
}

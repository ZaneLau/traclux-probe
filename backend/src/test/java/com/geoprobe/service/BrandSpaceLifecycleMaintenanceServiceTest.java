package com.geoprobe.service;

import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceLifecycleMaintenanceServiceTest {

    @Mock
    private BrandScopeMapper brandScopeMapper;
    @Mock
    private BrandSpaceConfigService brandSpaceConfigService;

    @InjectMocks
    private BrandSpaceLifecycleMaintenanceService lifecycleMaintenanceService;

    @Test
    void shouldPurgeRecoverableConfigAfterInternalRestoreWindowExpires() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 27, 20, 0);
        BrandScope archivedScope = BrandScope.builder()
                .id("scope-1")
                .scopeStatusCode(0)
                .archivedAt(now.minusDays(181))
                .retentionUntil(now.minusDays(151))
                .internalRestoreUntil(now.minusDays(1))
                .build();
        when(brandScopeMapper.selectList(any())).thenReturn(List.of(archivedScope));

        BrandSpaceLifecycleMaintenanceService.LifecycleSweepResult result = lifecycleMaintenanceService.runSweep(now);

        assertEquals(0, result.normalizedScopes());
        assertEquals(1, result.purgedScopes());
        verify(brandSpaceConfigService).purgeRecoverableConfig("scope-1");

        ArgumentCaptor<BrandScope> scopeCaptor = ArgumentCaptor.forClass(BrandScope.class);
        verify(brandScopeMapper).updateById(scopeCaptor.capture());
        assertNotNull(scopeCaptor.getValue().getPurgedAt());
    }

    @Test
    void shouldBackfillMissingRestoreWindowsBeforePurge() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 27, 20, 0);
        BrandScope archivedScope = BrandScope.builder()
                .id("scope-2")
                .scopeStatusCode(0)
                .archivedAt(now.minusDays(12))
                .build();
        when(brandScopeMapper.selectList(any())).thenReturn(List.of(archivedScope));

        BrandSpaceLifecycleMaintenanceService.LifecycleSweepResult result = lifecycleMaintenanceService.runSweep(now);

        assertEquals(1, result.normalizedScopes());
        assertEquals(0, result.purgedScopes());
        verify(brandSpaceConfigService, never()).purgeRecoverableConfig("scope-2");

        ArgumentCaptor<BrandScope> scopeCaptor = ArgumentCaptor.forClass(BrandScope.class);
        verify(brandScopeMapper).updateById(scopeCaptor.capture());
        assertNotNull(scopeCaptor.getValue().getRetentionUntil());
        assertNotNull(scopeCaptor.getValue().getInternalRestoreUntil());
    }
}

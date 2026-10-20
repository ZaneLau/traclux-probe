package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceExecutionGateServiceTest {

    @Mock
    private ServiceAccessService serviceAccessService;
    @Mock
    private BrandScopeMapper brandScopeMapper;

    @InjectMocks
    private BrandSpaceExecutionGateService gateService;

    @Test
    void requirePreviewAuthorityShouldReturnResolvedAuthoritySnapshot() {
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .planName("公测版")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
        doNothing().when(serviceAccessService).requireAccessible("user-1");
        when(serviceAccessService.getServiceAccess("user-1")).thenReturn(access);
        when(serviceAccessService.resolveOrganizationId("user-1")).thenReturn("org-1");
        when(serviceAccessService.resolveMembershipId("user-1", "org-1")).thenReturn("membership-1");

        BrandSpaceExecutionGateService.AuthoritySnapshot authority = gateService.requirePreviewAuthority("user-1", 10);

        assertEquals("user-1", authority.userId());
        assertEquals("org-1", authority.organizationId());
        assertEquals("membership-1", authority.membershipId());
        assertEquals("trial", authority.serviceAccess().getStatus());
        verify(brandScopeMapper, never()).selectCount(any());
    }

    @Test
    void requirePreviewAuthorityShouldNotRejectPromptCountOverLegacyDailyPoolBridge() {
        ServiceAccessView access = ServiceAccessView.builder()
                .status("trial")
                .limits(ServiceAccessView.Limits.builder()
                        .promptLimitPerBrandSpace(20)
                        .build())
                .build();
        doNothing().when(serviceAccessService).requireAccessible("user-2");
        when(serviceAccessService.getServiceAccess("user-2")).thenReturn(access);
        when(serviceAccessService.resolveOrganizationId("user-2")).thenReturn("org-2");
        when(serviceAccessService.resolveMembershipId("user-2", "org-2")).thenReturn("membership-2");

        BrandSpaceExecutionGateService.AuthoritySnapshot authority = gateService.requirePreviewAuthority("user-2", 11);

        assertEquals("user-2", authority.userId());
        assertEquals("org-2", authority.organizationId());
        assertEquals("membership-2", authority.membershipId());
    }

    @Test
    void requireCreateAuthorityShouldRejectWhenBrandSpaceLimitReached() {
        ServiceAccessView access = ServiceAccessView.builder()
                .status("active")
                .planName("专业版")
                .limits(ServiceAccessView.Limits.builder()
                        .brandSpaceLimit(1)
                        .promptLimitPerBrandSpace(50)
                        .build())
                .build();
        doNothing().when(serviceAccessService).requireAccessible("user-3");
        when(serviceAccessService.getServiceAccess("user-3")).thenReturn(access);
        when(serviceAccessService.resolveOrganizationId("user-3")).thenReturn("org-3");
        when(serviceAccessService.resolveMembershipId("user-3", "org-3")).thenReturn("membership-3");
        when(brandScopeMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateService.requireCreateAuthority("user-3", 10)
        );

        assertEquals("当前套餐最多支持 1 个活跃品牌空间，请先归档或升级套餐", exception.getMessage());
    }

    @Test
    void requireScheduledAuthorityShouldRejectPausedScope() {
        BrandScope pausedScope = BrandScope.builder()
                .id("scope-paused")
                .trackingMode("PAUSED")
                .build();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> gateService.requireScheduledAuthority("user-4", pausedScope, 3)
        );

        assertEquals("品牌空间已暂停自动追踪", exception.getMessage());
    }
}

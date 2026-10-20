package com.geoprobe.service;

import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.User;
import com.geoprobe.entity.v1.Organization;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.entity.v1.PlanCatalog;
import com.geoprobe.entity.v1.SubscriptionRecord;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import com.geoprobe.mapper.v1.OrganizationMapper;
import com.geoprobe.mapper.v1.PlanCatalogMapper;
import com.geoprobe.mapper.v1.SubscriptionRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAccessServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private OrganizationMembershipMapper organizationMembershipMapper;
    @Mock
    private SubscriptionRecordMapper subscriptionRecordMapper;
    @Mock
    private PlanCatalogMapper planCatalogMapper;

    @InjectMocks
    private ServiceAccessService serviceAccessService;

    @Test
    void shouldExposePromptLimitAndEnabledModelCountForSubscription() {
        when(userMapper.selectById("user-1")).thenReturn(User.builder()
                .id("user-1")
                .enabled(1)
                .defaultOrganizationId("org-1")
                .build());
        when(organizationMapper.selectById("org-1")).thenReturn(Organization.builder()
                .id("org-1")
                .statusCode(1)
                .build());
        when(subscriptionRecordMapper.selectList(any())).thenReturn(List.of(SubscriptionRecord.builder()
                .id("sub-1")
                .organizationId("org-1")
                .planId("plan-team")
                .statusCode(0)
                .effectiveFrom(LocalDateTime.now().minusDays(1))
                .effectiveTo(LocalDateTime.now().plusDays(30))
                .billingPolicySnapshotJson(Map.of("chargeUnit", "PROBE"))
                .build()));
        when(planCatalogMapper.selectById("plan-team")).thenReturn(PlanCatalog.builder()
                .id("plan-team")
                .planCode("team")
                .displayName("高级版")
                .dailyProbeQuota(100)
                .modelLimit(null)
                .build());

        ServiceAccessView view = serviceAccessService.getServiceAccess("user-1");

        assertEquals("active", view.getStatus());
        assertEquals("高级版", view.getPlanName());
        assertEquals(300, view.getLimits().getPromptLimitPerBrandSpace());
        assertEquals(3, view.getLimits().getEnabledModelCount());
        assertEquals(9000, view.getLimits().getMonthlyRunLimit());
    }

    @Test
    void shouldResolveSharedOrganizationFromDefaultOrganizationBeforeOwnerFallback() {
        when(userMapper.selectById("seat-user")).thenReturn(User.builder()
                .id("seat-user")
                .enabled(1)
                .defaultOrganizationId("org-shared")
                .build());
        when(organizationMapper.selectById("org-shared")).thenReturn(Organization.builder()
                .id("org-shared")
                .statusCode(1)
                .build());
        when(subscriptionRecordMapper.selectList(any())).thenReturn(List.of(SubscriptionRecord.builder()
                .id("sub-shared")
                .organizationId("org-shared")
                .planId("plan-basic")
                .statusCode(0)
                .effectiveFrom(LocalDateTime.now().minusDays(1))
                .effectiveTo(LocalDateTime.now().plusDays(7))
                .billingPolicySnapshotJson(Map.of("chargeUnit", "PROBE"))
                .build()));
        when(planCatalogMapper.selectById("plan-basic")).thenReturn(PlanCatalog.builder()
                .id("plan-basic")
                .planCode("basic")
                .displayName("基础版")
                .dailyProbeQuota(50)
                .modelLimit(1)
                .build());

        ServiceAccessView view = serviceAccessService.getServiceAccess("seat-user");

        assertEquals("active", view.getStatus());
        assertEquals("基础版", view.getPlanName());
        assertEquals(50, view.getLimits().getPromptLimitPerBrandSpace());
        assertEquals(1, view.getLimits().getEnabledModelCount());
    }
}

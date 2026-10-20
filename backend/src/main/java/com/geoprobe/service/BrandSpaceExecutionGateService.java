package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BrandSpaceExecutionGateService {

    private static final String TRACKING_MODE_PAUSED = "PAUSED";

    private final ServiceAccessService serviceAccessService;
    private final BrandScopeMapper brandScopeMapper;

    public AuthoritySnapshot requirePreviewAuthority(String userId, int promptProbeCount) {
        AuthoritySnapshot authority = requireAccessibleAuthority(userId);
        assertPromptCountAllowed(promptProbeCount, authority.serviceAccess(), "brand_space_preview");
        return authority;
    }

    public AuthoritySnapshot requireCreateAuthority(String userId, int promptProbeCount) {
        AuthoritySnapshot authority = requireAccessibleAuthority(userId);
        assertPromptCountAllowed(promptProbeCount, authority.serviceAccess(), "brand_space_create_initial_run");
        assertBrandSpaceLimitNotReached(authority.organizationId(), authority.serviceAccess());
        return authority;
    }

    public AuthoritySnapshot requireScheduledAuthority(String userId,
                                                       BrandScope scope,
                                                       int promptProbeCount) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已归档，不能继续自动追踪");
        }
        if (TRACKING_MODE_PAUSED.equalsIgnoreCase(String.valueOf(scope.getTrackingMode()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已暂停自动追踪");
        }
        AuthoritySnapshot authority = requireAccessibleAuthority(userId);
        assertPromptCountAllowed(promptProbeCount, authority.serviceAccess(), "brand_space_schedule");
        return authority;
    }

    public AuthoritySnapshot requirePreviewAuthority(String userId) {
        return requireAccessibleAuthority(userId);
    }

    public AuthoritySnapshot requireCreateAuthority(String userId) {
        AuthoritySnapshot authority = requireAccessibleAuthority(userId);
        assertBrandSpaceLimitNotReached(authority.organizationId(), authority.serviceAccess());
        return authority;
    }

    public AuthoritySnapshot requireScheduledAuthority(String userId, BrandScope scope) {
        if (scope == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已归档，不能继续自动追踪");
        }
        if (TRACKING_MODE_PAUSED.equalsIgnoreCase(String.valueOf(scope.getTrackingMode()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已暂停自动追踪");
        }
        return requireAccessibleAuthority(userId);
    }

    public void assertPlannedProbeCountAllowed(int targetProbeCount,
                                               ServiceAccessView serviceAccess,
                                               String scene) {
        assertPromptCountAllowed(targetProbeCount, serviceAccess, scene);
    }

    private AuthoritySnapshot requireAccessibleAuthority(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String normalizedUserId = userId.trim();
        serviceAccessService.requireAccessible(normalizedUserId);
        ServiceAccessView serviceAccess = serviceAccessService.getServiceAccess(normalizedUserId);
        String organizationId = serviceAccessService.resolveOrganizationId(normalizedUserId);
        String membershipId = StringUtils.hasText(organizationId)
                ? serviceAccessService.resolveMembershipId(normalizedUserId, organizationId)
                : null;
        return new AuthoritySnapshot(normalizedUserId, organizationId, membershipId, serviceAccess);
    }

    private void assertPromptCountAllowed(int promptProbeCount,
                                          ServiceAccessView serviceAccess,
                                          String scene) {
        if (promptProbeCount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间至少需要 1 条启用 prompt/probe");
        }
        ServiceAccessView.Limits limits = serviceAccess == null ? null : serviceAccess.getLimits();
        Integer promptLimitPerBrandSpace = limits == null ? null : limits.getPromptLimitPerBrandSpace();
        if (promptLimitPerBrandSpace != null && promptLimitPerBrandSpace > 0 && promptProbeCount > promptLimitPerBrandSpace) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "启用的 prompt/probe 数不能超过当前套餐单品牌上限 " + promptLimitPerBrandSpace + " 条，scene=" + scene
            );
        }
    }

    private void assertBrandSpaceLimitNotReached(String organizationId,
                                                 ServiceAccessView serviceAccess) {
        if (!StringUtils.hasText(organizationId)) {
            return;
        }
        ServiceAccessView.Limits limits = serviceAccess == null ? null : serviceAccess.getLimits();
        Integer brandSpaceLimit = limits == null ? null : limits.getBrandSpaceLimit();
        if (brandSpaceLimit == null || brandSpaceLimit <= 0) {
            return;
        }
        Long activeCount = brandScopeMapper.selectCount(new LambdaQueryWrapper<BrandScope>()
                .eq(BrandScope::getOrganizationId, organizationId.trim())
                .and(wrapper -> wrapper
                        .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ACTIVE)
                        .or()
                        .isNull(BrandScope::getScopeStatusCode)));
        long currentCount = activeCount == null ? 0L : activeCount;
        if (currentCount >= brandSpaceLimit) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "当前套餐最多支持 " + brandSpaceLimit + " 个活跃品牌空间，请先归档或升级套餐"
            );
        }
    }

    public record AuthoritySnapshot(String userId,
                                    String organizationId,
                                    String membershipId,
                                    ServiceAccessView serviceAccess) {
    }
}

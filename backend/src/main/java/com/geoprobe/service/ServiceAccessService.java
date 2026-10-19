package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceAccessService {

    private static final int USER_ENABLED = 1;
    private static final int ORG_TYPE_PERSONAL = 0;
    private static final int ORG_STATUS_ACTIVE = 1;
    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;
    private static final int SUBSCRIPTION_STATUS_ACTIVE = 0;
    private static final int DEFAULT_BRAND_SPACE_LIMIT = 10;
    private static final int DEFAULT_MONTHLY_RUN_LIMIT = 100;
    private static final int DEFAULT_PROMPT_LIMIT_PER_BRAND_SPACE = 200;
    private static final int DEFAULT_DAILY_PROMPT_POOL = 100;
    private static final int DEFAULT_ENABLED_MODEL_COUNT = 1;
    private static final int DEFAULT_CREDITS_PER_PROBE = 1;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMembershipMapper organizationMembershipMapper;
    private final SubscriptionRecordMapper subscriptionRecordMapper;
    private final PlanCatalogMapper planCatalogMapper;

    public ServiceAccessView getServiceAccess(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId.trim());
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!intEquals(user.getEnabled(), USER_ENABLED)) {
            return buildInactiveAccess("inactive", "当前账号未开通服务");
        }
        String organizationId = resolveOrganizationId(userId);
        if (!StringUtils.hasText(organizationId)) {
            return buildTrialAccess();
        }
        List<SubscriptionRecord> subscriptions = subscriptionRecordMapper.selectList(new LambdaQueryWrapper<SubscriptionRecord>()
                .eq(SubscriptionRecord::getOrganizationId, organizationId)
                .orderByDesc(SubscriptionRecord::getEffectiveTo)
                .orderByDesc(SubscriptionRecord::getCreatedAt));
        LocalDateTime now = LocalDateTime.now();
        SubscriptionRecord active = subscriptions.stream()
                .filter(item -> intEquals(item.getStatusCode(), SUBSCRIPTION_STATUS_ACTIVE))
                .filter(item -> item.getEffectiveTo() == null || !item.getEffectiveTo().isBefore(now))
                .max(Comparator.comparing(item -> item.getEffectiveTo() == null ? LocalDateTime.MAX : item.getEffectiveTo()))
                .orElse(null);
        if (active != null) {
            return buildSubscriptionAccess(active, "active", null);
        }
        SubscriptionRecord latest = subscriptions.stream()
                .max(Comparator.comparing(item -> item.getEffectiveTo() == null ? LocalDateTime.MIN : item.getEffectiveTo()))
                .orElse(null);
        if (latest != null) {
            return buildSubscriptionAccess(latest, "expired", "当前账号未开通服务");
        }
        return buildTrialAccess();
    }

    public void requireAccessible(String userId) {
        ServiceAccessView access = getServiceAccess(userId);
        if (!"active".equals(access.getStatus()) && !"trial".equals(access.getStatus())) {
            throw new BusinessException(
                    ErrorCode.SERVICE_ACCESS_REQUIRED,
                    StringUtils.hasText(access.getReason()) ? access.getReason() : ErrorCode.SERVICE_ACCESS_REQUIRED.getMessage()
            );
        }
    }

    public String resolveOrganizationId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        String normalizedUserId = userId.trim();
        User user = userMapper.selectById(normalizedUserId);
        if (user == null) {
            return null;
        }

        Organization defaultOrganization = findActiveOrganizationById(user.getDefaultOrganizationId());
        if (defaultOrganization != null) {
            return defaultOrganization.getId();
        }

        List<OrganizationMembership> memberships = organizationMembershipMapper.selectList(new LambdaQueryWrapper<OrganizationMembership>()
                .eq(OrganizationMembership::getUserId, normalizedUserId)
                .eq(OrganizationMembership::getStatusCode, MEMBERSHIP_STATUS_ACTIVE)
                .orderByDesc(OrganizationMembership::getUpdatedAt)
                .orderByDesc(OrganizationMembership::getCreatedAt));
        if (!memberships.isEmpty()) {
            List<String> organizationIds = memberships.stream()
                    .map(OrganizationMembership::getOrganizationId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (!organizationIds.isEmpty()) {
                Map<String, Organization> organizationsById = organizationMapper.selectList(new LambdaQueryWrapper<Organization>()
                                .in(Organization::getId, organizationIds)
                                .eq(Organization::getStatusCode, ORG_STATUS_ACTIVE))
                        .stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(Organization::getId, organization -> organization));
                for (OrganizationMembership membership : memberships) {
                    if (organizationsById.containsKey(membership.getOrganizationId())) {
                        return membership.getOrganizationId();
                    }
                }
            }
        }

        Organization organization = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOwnerUserId, normalizedUserId)
                .eq(Organization::getOrganizationTypeCode, ORG_TYPE_PERSONAL)
                .eq(Organization::getStatusCode, ORG_STATUS_ACTIVE)
                .last("limit 1"));
        return organization == null ? null : organization.getId();
    }

    public String resolveMembershipId(String userId, String organizationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(organizationId)) {
            return null;
        }
        OrganizationMembership membership = organizationMembershipMapper.selectOne(new LambdaQueryWrapper<OrganizationMembership>()
                .eq(OrganizationMembership::getUserId, userId.trim())
                .eq(OrganizationMembership::getOrganizationId, organizationId.trim())
                .eq(OrganizationMembership::getStatusCode, MEMBERSHIP_STATUS_ACTIVE)
                .orderByDesc(OrganizationMembership::getUpdatedAt)
                .orderByDesc(OrganizationMembership::getCreatedAt)
                .last("limit 1"));
        return membership == null ? null : membership.getId();
    }

    private Organization findActiveOrganizationById(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return null;
        }
        Organization organization = organizationMapper.selectById(organizationId.trim());
        if (organization == null || !intEquals(organization.getStatusCode(), ORG_STATUS_ACTIVE)) {
            return null;
        }
        return organization;
    }

    private ServiceAccessView buildTrialAccess() {
        return ServiceAccessView.builder()
                .status("trial")
                .planName("公测版")
                .limits(defaultLimits(DEFAULT_DAILY_PROMPT_POOL, DEFAULT_ENABLED_MODEL_COUNT))
                .reason(null)
                .build();
    }

    private ServiceAccessView buildInactiveAccess(String status, String reason) {
        return ServiceAccessView.builder()
                .status(status)
                .planName("公测版")
                .limits(defaultLimits(DEFAULT_DAILY_PROMPT_POOL, DEFAULT_ENABLED_MODEL_COUNT))
                .reason(reason)
                .build();
    }

    private ServiceAccessView buildSubscriptionAccess(SubscriptionRecord subscription,
                                                      String status,
                                                      String reason) {
        PlanCatalog plan = subscription == null || !StringUtils.hasText(subscription.getPlanId())
                ? null
                : planCatalogMapper.selectById(subscription.getPlanId());
        Map<String, Object> policy = subscription == null ? Map.of() : defaultMap(subscription.getBillingPolicySnapshotJson());
        int enabledModelCount = resolveEnabledModelCount(plan, policy);
        int dailyPromptPool = resolveDailyPromptPool(subscription, plan, policy, enabledModelCount);
        int creditsPerProbe = resolveCreditsPerProbe(policy);
        return ServiceAccessView.builder()
                .status(status)
                .planName(plan == null || !StringUtils.hasText(plan.getDisplayName()) ? "公测版" : plan.getDisplayName())
                .expiresAt(subscription == null || subscription.getEffectiveTo() == null
                        ? null
                        : DATE_TIME_FORMATTER.format(subscription.getEffectiveTo()))
                .limits(ServiceAccessView.Limits.builder()
                        .brandSpaceLimit(readInt(policy.get("brandSpaceLimit"), DEFAULT_BRAND_SPACE_LIMIT))
                        .monthlyRunLimit(resolveMonthlyRunLimit(dailyPromptPool))
                        .promptLimitPerBrandSpace(readInt(policy.get("promptLimitPerBrandSpace"), dailyPromptPool))
                        .enabledModelCount(enabledModelCount)
                        .creditsPerProbe(creditsPerProbe)
                        .build())
                .reason(reason)
                .build();
    }

    private ServiceAccessView.Limits defaultLimits(int dailyPromptPool, int enabledModelCount) {
        return ServiceAccessView.Limits.builder()
                .brandSpaceLimit(DEFAULT_BRAND_SPACE_LIMIT)
                .monthlyRunLimit(resolveMonthlyRunLimit(dailyPromptPool))
                .promptLimitPerBrandSpace(dailyPromptPool > 0 ? dailyPromptPool : DEFAULT_PROMPT_LIMIT_PER_BRAND_SPACE)
                .enabledModelCount(enabledModelCount)
                .creditsPerProbe(DEFAULT_CREDITS_PER_PROBE)
                .build();
    }

    private int resolveMonthlyRunLimit(int dailyPromptPool) {
        if (dailyPromptPool <= 0) {
            return DEFAULT_MONTHLY_RUN_LIMIT;
        }
        return dailyPromptPool * 30;
    }

    private int resolveDailyPromptPool(SubscriptionRecord subscription,
                                       PlanCatalog plan,
                                       Map<String, Object> policy,
                                       int enabledModelCount) {
        Integer policyValue = readInt(policy.get("dailyPromptPool"), null);
        if (policyValue != null) {
            return policyValue;
        }

        Integer policyDailyQuota = readInt(policy.get("dailyProbeQuota"), null);
        if (policyDailyQuota != null && policyDailyQuota > 0) {
            return policyDailyQuota * Math.max(enabledModelCount, 1);
        }

        Integer subscriptionQuota = subscription == null ? null : subscription.getDailyProbeQuota();
        if (subscriptionQuota != null && subscriptionQuota > 0) {
            return subscriptionQuota * Math.max(enabledModelCount, 1);
        }

        Integer planQuota = plan == null ? null : plan.getDailyProbeQuota();
        if (planQuota != null && planQuota > 0) {
            return planQuota * Math.max(enabledModelCount, 1);
        }

        return DEFAULT_DAILY_PROMPT_POOL;
    }

    private int resolveCreditsPerProbe(Map<String, Object> policy) {
        Integer policyValue = readInt(firstNonNull(policy.get("creditsPerProbe"), policy.get("defaultCreditsPerProbe")), null);
        if (policyValue != null && policyValue > 0) {
            return policyValue;
        }
        return DEFAULT_CREDITS_PER_PROBE;
    }

    private int resolveEnabledModelCount(PlanCatalog plan, Map<String, Object> policy) {
        Integer policyValue = readInt(firstNonNull(policy.get("enabledModelCount"), policy.get("modelLimit")), null);
        if (policyValue != null && policyValue > 0) {
            return policyValue;
        }

        Integer planLimit = plan == null ? null : plan.getModelLimit();
        if (planLimit != null && planLimit > 0) {
            return planLimit;
        }

        String planCode = plan == null ? null : plan.getPlanCode();
        if (!StringUtils.hasText(planCode)) {
            return DEFAULT_ENABLED_MODEL_COUNT;
        }
        return switch (planCode.trim().toLowerCase(Locale.ROOT)) {
            case "team", "advanced", "pro" -> 3;
            default -> DEFAULT_ENABLED_MODEL_COUNT;
        };
    }

    private Map<String, Object> defaultMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private Integer readInt(Object value, Integer defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private Object firstNonNull(Object left, Object right) {
        return left != null ? left : right;
    }
}

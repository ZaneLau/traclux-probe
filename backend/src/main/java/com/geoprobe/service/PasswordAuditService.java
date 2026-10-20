package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.User;
import com.geoprobe.entity.v1.ComplianceAuditLog;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.ComplianceAuditLogMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordAuditService {

    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;

    private final UserMapper userMapper;
    private final OrganizationMembershipMapper organizationMembershipMapper;
    private final ComplianceAuditLogMapper complianceAuditLogMapper;

    public void recordUserPasswordWrite(String targetUserId,
                                        String username,
                                        String auditTypeCode,
                                        String source) {
        if (!StringUtils.hasText(targetUserId)) {
            log.warn("Skip password audit because target user id is blank. auditTypeCode={}, source={}",
                    auditTypeCode,
                    source);
            return;
        }

        User user = userMapper.selectById(targetUserId.trim());
        if (user == null) {
            log.warn("Skip password audit because target user does not exist. targetUserId={}, auditTypeCode={}",
                    targetUserId,
                    auditTypeCode);
            return;
        }

        OrganizationMembership membership = findActiveMembership(user);
        String organizationId = StringUtils.hasText(user.getDefaultOrganizationId())
                ? user.getDefaultOrganizationId().trim()
                : membership == null ? null : membership.getOrganizationId();
        if (!StringUtils.hasText(organizationId)) {
            log.warn("Skip password audit because target user has no organization context. targetUserId={}, auditTypeCode={}",
                    targetUserId,
                    auditTypeCode);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("riskLevel", "HIGH");
        payload.put("source", source);
        payload.put("username", StringUtils.hasText(username) ? username.trim() : user.getUsername());
        payload.put("hashFormat", "BCRYPT");
        payload.put("containsSecret", false);

        complianceAuditLogMapper.insert(ComplianceAuditLog.builder()
                .id(UUID.randomUUID().toString())
                .organizationId(organizationId)
                .actorUserId(user.getId())
                .actorMembershipId(membership == null ? null : membership.getId())
                .auditTypeCode(auditTypeCode)
                .targetTypeCode("USER")
                .targetId(user.getId())
                .payloadJson(payload)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private OrganizationMembership findActiveMembership(User user) {
        LambdaQueryWrapper<OrganizationMembership> query = new LambdaQueryWrapper<OrganizationMembership>()
                .eq(OrganizationMembership::getUserId, user.getId())
                .eq(OrganizationMembership::getStatusCode, MEMBERSHIP_STATUS_ACTIVE)
                .orderByDesc(OrganizationMembership::getUpdatedAt)
                .orderByDesc(OrganizationMembership::getCreatedAt)
                .last("LIMIT 1");
        if (StringUtils.hasText(user.getDefaultOrganizationId())) {
            query.eq(OrganizationMembership::getOrganizationId, user.getDefaultOrganizationId().trim());
        }
        OrganizationMembership membership = organizationMembershipMapper.selectOne(query);
        if (membership == null && StringUtils.hasText(user.getDefaultOrganizationId())) {
            membership = organizationMembershipMapper.selectOne(new LambdaQueryWrapper<OrganizationMembership>()
                    .eq(OrganizationMembership::getUserId, user.getId())
                    .eq(OrganizationMembership::getStatusCode, MEMBERSHIP_STATUS_ACTIVE)
                    .orderByDesc(OrganizationMembership::getUpdatedAt)
                    .orderByDesc(OrganizationMembership::getCreatedAt)
                    .last("LIMIT 1"));
        }
        return membership;
    }
}

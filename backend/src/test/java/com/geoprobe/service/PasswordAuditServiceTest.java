package com.geoprobe.service;

import com.geoprobe.entity.User;
import com.geoprobe.entity.v1.ComplianceAuditLog;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.ComplianceAuditLogMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordAuditServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private OrganizationMembershipMapper organizationMembershipMapper;
    @Mock
    private ComplianceAuditLogMapper complianceAuditLogMapper;

    private PasswordAuditService service;

    @BeforeEach
    void setUp() {
        service = new PasswordAuditService(
                userMapper,
                organizationMembershipMapper,
                complianceAuditLogMapper
        );
    }

    @Test
    void shouldWritePasswordAuditWithoutSecretMaterial() {
        when(userMapper.selectById("user-1")).thenReturn(User.builder()
                .id("user-1")
                .username("alice")
                .defaultOrganizationId("org-1")
                .build());
        when(organizationMembershipMapper.selectOne(any())).thenReturn(OrganizationMembership.builder()
                .id("membership-1")
                .organizationId("org-1")
                .userId("user-1")
                .statusCode(1)
                .build());

        service.recordUserPasswordWrite(
                "user-1",
                "alice",
                "USER_PASSWORD_REGISTERED",
                "REGISTER"
        );

        ArgumentCaptor<ComplianceAuditLog> captor = ArgumentCaptor.forClass(ComplianceAuditLog.class);
        verify(complianceAuditLogMapper).insert(captor.capture());
        ComplianceAuditLog log = captor.getValue();
        assertNotNull(log.getId());
        assertEquals("org-1", log.getOrganizationId());
        assertEquals("user-1", log.getActorUserId());
        assertEquals("membership-1", log.getActorMembershipId());
        assertEquals("USER_PASSWORD_REGISTERED", log.getAuditTypeCode());
        assertEquals("USER", log.getTargetTypeCode());
        assertEquals("user-1", log.getTargetId());
        assertEquals("HIGH", log.getPayloadJson().get("riskLevel"));
        assertEquals("REGISTER", log.getPayloadJson().get("source"));
        assertEquals("BCRYPT", log.getPayloadJson().get("hashFormat"));
        assertEquals(false, log.getPayloadJson().get("containsSecret"));
        assertFalse(log.getPayloadJson().containsKey("password"));
    }

    @Test
    void shouldSkipPasswordAuditWhenOrganizationContextIsMissing() {
        when(userMapper.selectById("user-1")).thenReturn(User.builder()
                .id("user-1")
                .username("alice")
                .build());
        when(organizationMembershipMapper.selectOne(any())).thenReturn(null);

        service.recordUserPasswordWrite(
                "user-1",
                "alice",
                "USER_PASSWORD_REGISTERED",
                "REGISTER"
        );

        verify(complianceAuditLogMapper, never()).insert(any(ComplianceAuditLog.class));
    }
}

package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "compliance_audit_logs", autoResultMap = true)
public class ComplianceAuditLog {

    @TableId
    private String id;

    private String organizationId;
    private String actorUserId;
    private String actorMembershipId;
    private String auditTypeCode;
    private String targetTypeCode;
    private String targetId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payloadJson;

    private LocalDateTime createdAt;
}

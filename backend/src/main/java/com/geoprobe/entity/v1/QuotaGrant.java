package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName(value = "quota_grants", autoResultMap = true)
public class QuotaGrant {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String quotaAccountId;
    private String grantTypeCode;
    private String sourceTypeCode;
    private String sourceId;
    private String planId;
    private Long totalProbeQuota;
    private Long usedProbeQuota;
    private Long reservedProbeQuota;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private LocalDateTime expiresAt;
    private Boolean rolloverAllowed;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> grantMetadataJson;

    private LocalDateTime createdAt;
}

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
@TableName(value = "subscriptions", autoResultMap = true)
public class SubscriptionRecord {

    @TableId
    private String id;

    private String organizationId;
    private String planId;

    @TableField(exist = false)
    private String sourceOrderId;

    private Integer statusCode;
    private Integer seatLimit;
    private Integer modelLimit;

    @TableField(exist = false)
    private Integer dailyProbeQuota;

    private String quotaScopeCode;
    private Integer historyRetentionDays;

    @TableField("started_at")
    private LocalDateTime effectiveFrom;

    @TableField("expires_at")
    private LocalDateTime effectiveTo;

    private Integer renewalModeCode;

    @TableField(exist = false)
    private LocalDateTime cancelledAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> featureFlagsJson;

    @TableField(value = "quota_policy_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> billingPolicySnapshotJson;

    @TableField(value = "grant_generation_policy_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> grantGenerationPolicyJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

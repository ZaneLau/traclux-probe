package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "plan_catalog", autoResultMap = true)
public class PlanCatalog {

    @TableId
    private String id;

    private String planCode;
    private String displayName;
    private Integer billingModeCode;

    @TableField("price_amount")
    private BigDecimal listPriceYuan;

    private String currencyCode;
    private Integer seatLimit;
    private Integer modelLimit;
    private Integer dailyProbeQuota;
    private String quotaScopeCode;
    private Integer historyRetentionDays;

    private Integer validityDays;

    @TableField("status_code")
    private Integer activeFlag;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> featureFlagsJson;

    @TableField(value = "quota_policy_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> billingPolicyJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

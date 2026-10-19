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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "billing_credit_rule_configs", autoResultMap = true)
public class BillingCreditRuleConfig {

    @TableId(type = IdType.INPUT)
    private String id;

    private String ruleCode;
    private String displayName;
    private String pricingModeCode;
    private Integer defaultCreditsPerProbe;
    private Integer minCreditsPerProbe;
    private Integer maxCreditsPerProbe;
    private BigDecimal defaultGrossMarginRate;
    private Integer statusCode;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ruleJson;

    private String updatedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

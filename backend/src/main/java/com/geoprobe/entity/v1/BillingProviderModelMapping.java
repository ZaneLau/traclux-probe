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
@TableName(value = "billing_provider_model_mappings", autoResultMap = true)
public class BillingProviderModelMapping {

    @TableId(type = IdType.INPUT)
    private String id;

    private String providerCode;
    private String providerName;
    private String modelId;
    private String modelDisplayName;
    private String frontendModelKey;
    private String frontendModelLabel;
    private String capabilityCode;
    private Integer defaultCreditsPerProbe;
    private BigDecimal internalCostYuanPerProbe;
    private BigDecimal grossMarginRate;
    private Integer statusCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;

    private String updatedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

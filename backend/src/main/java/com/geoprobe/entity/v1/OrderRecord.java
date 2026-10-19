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
@TableName(value = "orders", autoResultMap = true)
public class OrderRecord {

    @TableId
    private String id;

    private String organizationId;
    private String purchaserUserId;
    private String purchaserMembershipId;
    private String planId;
    private String orderNo;
    private Integer orderTypeCode;

    @TableField("amount")
    private BigDecimal orderAmountYuan;

    private String currencyCode;
    private Integer statusCode;
    private String pricingPolicyVersion;
    private Integer grantedProbeQuota;

    @TableField("grant_expires_at")
    private LocalDateTime expiresAt;

    @TableField("ext_trade_no")
    private String extTradeNo;

    private LocalDateTime paidAt;

    @TableField(exist = false, typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> orderSnapshotJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

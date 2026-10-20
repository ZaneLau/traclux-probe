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
@TableName(value = "refunds", autoResultMap = true)
public class RefundRecord {

    @TableId
    private String id;

    private String orderId;
    private String paymentId;
    private String organizationId;
    private BigDecimal refundAmountYuan;
    private Integer statusCode;
    private String reasonCode;
    private String providerRefundNo;
    private LocalDateTime refundedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> refundMetadataJson;

    private LocalDateTime createdAt;
}

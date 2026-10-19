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
@TableName(value = "payments", autoResultMap = true)
public class PaymentRecord {

    @TableId
    private String id;

    private String orderId;

    @TableField(exist = false)
    private String organizationId;

    @TableField("provider_code")
    private Integer providerCode;

    @TableField(exist = false)
    private String paymentChannelCode;

    private String providerTradeNo;

    @TableField("paid_amount")
    private BigDecimal paidAmountYuan;

    @TableField(exist = false)
    private String currencyCode;

    private Integer statusCode;
    private LocalDateTime paidAt;

    @TableField(value = "callback_payload_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> providerPayloadJson;

    private LocalDateTime createdAt;
}

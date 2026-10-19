package com.geoprobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credit_transactions")
public class CreditTransaction {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    // 1=PURCHASE, 2=CONSUME, 3=REFUND, 4=GRANT
    private Integer type;

    private Integer amount;
    private String orderId;
    private String diagnosisId;
    private LocalDateTime createdAt;
}

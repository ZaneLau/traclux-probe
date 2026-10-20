package com.geoprobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("llm_call_logs")
public class LlmCallLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisId;

    // 1=CREATIVE, 2=EXTRACTION, 3=SAMPLING
    private Integer module;

    private String provider;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal costYuan;
    private Integer latencyMs;

    // 1=SUCCESS, 2=FAILED, 3=TIMEOUT
    private Integer status;

    private String errorMessage;
    private LocalDateTime createdAt;
}

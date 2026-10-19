package com.geoprobe.entity;

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
@TableName(value = "runs", autoResultMap = true)
public class Run {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisId;
    private String promptId;
    private String clusterId;
    private String promptText;
    private BigDecimal temperature;
    private String model;
    private String rawAnswer;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object parsedResult;

    // 0=PENDING, 1=SUCCESS, 2=FAILED, 3=TIMEOUT
    private Integer status;

    private Integer latencyMs;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> tokenCount;

    private String errorMessage;
    private LocalDateTime createdAt;
}

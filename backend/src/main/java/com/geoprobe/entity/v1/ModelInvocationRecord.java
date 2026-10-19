package com.geoprobe.entity.v1;

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
@TableName("model_invocations")
public class ModelInvocationRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String probeId;
    private String dispatchAttemptId;
    private String nodeCode;
    private String provider;
    private String modelId;
    private String endpointRef;
    private String pluginCode;
    private String priceCatalogId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer latencyMs;
    private BigDecimal modelCostYuan;
    private BigDecimal pluginCostYuan;
    private BigDecimal totalCostYuan;
    private Integer statusCode;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
}

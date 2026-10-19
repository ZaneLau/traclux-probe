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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "diagnosis_reports", autoResultMap = true)
public class DiagnosisReportRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String organizationId;
    private String brandScopeId;
    private String brandId;
    private String categorySnapshot;
    private String targetModelProduct;
    private Integer reportStatusCode;
    private Integer sampleSize;
    private BigDecimal spontaneousMentionRate;
    private BigDecimal confirmationResponseRate;
    private BigDecimal defenseCompleteness;
    private BigDecimal citationRate;
    private BigDecimal abvIndex;
    private String abvGrade;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> dimensionPenetrationJson;

    private BigDecimal brandDriftScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> brandDriftDetailsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> pathologyLabelsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> topInsightsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> summaryJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> chartsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> reportProjectionJson;

    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
}

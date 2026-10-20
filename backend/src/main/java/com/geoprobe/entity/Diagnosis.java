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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "diagnoses", autoResultMap = true)
public class Diagnosis {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String brandName;
    private String category;
    private String brandWebsite;
    private String userPrompt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> competitorsInput;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> negativeTerms;

    // 0=PENDING, 1=RUNNING, 2=COMPLETED, 3=FAILED, 4=CANCELLED, 5=QUEUED, 6=EXPIRED
    private Integer status;

    // 0=EXPERIENCE, 1=STANDARD, 2=DEEP
    private Integer edition;

    private Integer isTrial;
    // 0=MANUAL, 1=TRACKING
    private Integer source;
    private String trackingId;
    private String shareToken;
    private LocalDateTime shareExpiresAt;

    @TableField("variants_per_core_prompt")
    private Integer variantsPerCorePrompt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object intentClusters;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object promptMatrix;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> summaryKpis;

    private BigDecimal stabilityScore;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object seoResult;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object competitorRanking;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object associatedKeywords;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object reportData;

    private BigDecimal totalLlmCostYuan;
    private Integer totalApiCalls;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    private String errorMessage;
}

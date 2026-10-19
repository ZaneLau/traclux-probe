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
@TableName(value = "diagnosis_tasks", autoResultMap = true)
public class DiagnosisTask {

    @TableId(type = IdType.INPUT)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private String requestedByUserId;
    private String requestedByMembershipId;
    private String billingQuotaAccountId;
    private String rerunRootTaskId;
    private String rerunOfTaskId;
    private Integer rerunSeqNo;
    private String planId;
    private Integer sourceCode;
    private Integer targetProbeCount;
    private Boolean realtimeSearchEnabled;
    private String targetModelProduct;
    private String engineVersion;
    private Integer billingModeCode;
    private String billingPolicyVersion;
    private Integer statusCode;
    private BigDecimal progressPercent;
    private Integer samplingGeneratedCount;
    private Integer samplingCompletedCount;
    private Integer samplingFailedCount;
    private Integer samplingSkippedCount;
    private BigDecimal totalCostYuan;
    private Integer searchRequestedCount;
    private Integer searchSatisfiedCount;
    private String locale;
    private String language;
    private String diagnosisGoal;
    private String brandNameSnapshot;
    private String categorySnapshot;
    private String officialWebsiteSnapshot;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> taskOrderJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> taskMetadataJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> chargePolicySnapshotJson;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String failureReasonCode;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

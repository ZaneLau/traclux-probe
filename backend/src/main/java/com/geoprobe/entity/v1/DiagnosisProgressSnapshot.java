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
@TableName("diagnosis_progress_snapshots")
public class DiagnosisProgressSnapshot {

    @TableId(type = IdType.INPUT)
    private String diagnosisTaskId;

    private String organizationId;
    private String brandScopeId;
    private String currentStepCode;
    private String currentStepLabel;
    private Integer stepsCompleted;
    private Integer stepsTotal;
    private Integer samplingCompleted;
    private Integer samplingExpectedTotal;
    private Integer samplingGeneratedTotal;
    private Integer samplingMissingTotal;
    private Boolean samplingVariantShortfall;
    private BigDecimal percent;
    private Long lastEventSeq;
    private Integer searchRequestedCount;
    private Integer searchSatisfiedCount;
    private Integer failedProbeCount;
    private BigDecimal totalCostYuan;
    private LocalDateTime updatedAt;
}

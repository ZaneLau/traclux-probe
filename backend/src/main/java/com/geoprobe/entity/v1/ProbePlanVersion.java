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

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "probe_plan_versions", autoResultMap = true)
public class ProbePlanVersion {

    @TableId(type = IdType.INPUT)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private Integer versionNo;
    private Integer statusCode;
    private Boolean isCurrent;
    private Integer configGenerationSnapshot;
    private Integer activeRecurringProbeCount;
    private Integer estimatedImmediateCredits;
    private Integer estimatedRecurringCreditsPerCycle;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshotJson;

    private String createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
@TableName(value = "probe_plan_records", autoResultMap = true)
public class ProbePlanRecord {

    @TableId(type = IdType.INPUT)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private String planVersionId;
    private Integer recordNo;
    private String changeTypeCode;
    private String executeModeCode;
    private Integer statusCode;
    private Integer immediateEstimatedCredits;
    private Integer recurringEstimatedCreditsPerCycle;
    private String triggeredRunId;
    private LocalDateTime effectiveFromCycleAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> summaryJson;

    private String createdByUserId;
    private LocalDateTime createdAt;
}

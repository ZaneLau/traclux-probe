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
@TableName(value = "diagnosis_node_runs", autoResultMap = true)
public class DiagnosisNodeRun {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String nodeCode;
    private String graphVersion;
    private Integer attemptNo;
    private Integer statusCode;
    private String dependencySignature;
    private String workerRef;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSummaryJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputSummaryJson;

    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer durationMs;
    private LocalDateTime createdAt;
}

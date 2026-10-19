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
@TableName(value = "diagnosis_probes", autoResultMap = true)
public class DiagnosisProbeRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String probeRef;
    private Integer seqNo;
    private String bucketTypeCode;
    private String probeTypeCode;
    private String d1Identity;
    private String d2Context;
    private String d3PainPoint;
    private String d4Jtbd;
    private String d5Knowledge;
    private String d6Constraints;
    private String surfaceIntent;
    private String interactionPosture;
    private String conversationMode;
    private String renderText;
    private Integer renderSourceCode;
    private Boolean qualityGatePassed;
    private Boolean fallbackUsed;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> renderMetadataJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> triggeredRulesJson;

    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

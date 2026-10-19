package com.geoprobe.entity.v1;

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
@TableName(value = "diagnosis_node_artifacts", autoResultMap = true)
public class DiagnosisNodeArtifact {

    @TableId
    private String id;

    private String diagnosisTaskId;
    private String nodeRunId;
    private String nodeCode;
    private String artifactTypeCode;
    private String storageTypeCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payloadJson;

    private String objectUri;
    private String contentHash;
    private Long sizeBytes;
    private LocalDateTime createdAt;
}

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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "probe_dispatch_attempts", autoResultMap = true)
public class ProbeDispatchAttempt {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String probeId;
    private Integer roundNo;
    private Integer attemptNo;
    private String provider;
    private String modelId;
    private String endpointRef;
    private String routingIntentCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> routingCandidatesJson;

    private Boolean searchRequested;
    private Boolean searchSatisfied;
    private Boolean searchFallbackFlag;
    private Integer searchCallCount;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> searchQueriesJson;

    private String rawResponseText;
    private String thinkContent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> citationsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseMetadataJson;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Integer latencyMs;
    private Integer statusCode;
    private String failureReasonCode;
    private Boolean selectedFinal;
    private LocalDateTime createdAt;
}

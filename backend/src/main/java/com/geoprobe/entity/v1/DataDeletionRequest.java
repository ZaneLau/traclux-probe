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
@TableName(value = "data_deletion_requests", autoResultMap = true)
public class DataDeletionRequest {

    @TableId
    private String id;

    private String organizationId;
    private String requestedByMembershipId;
    private String approvedByMembershipId;
    private String targetTypeCode;
    private String targetId;
    private String requestReason;
    private Integer statusCode;
    private String retentionExceptionCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultSummaryJson;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
}

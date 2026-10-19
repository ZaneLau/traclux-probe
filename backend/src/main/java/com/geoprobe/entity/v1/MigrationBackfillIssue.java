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
@TableName(value = "migration_backfill_issues", autoResultMap = true)
public class MigrationBackfillIssue {

    @TableId
    private String id;

    private String issueTypeCode;
    private Integer severityCode;
    private String legacyTableName;
    private String legacyId;
    private String diagnosisId;
    private String organizationId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payloadJson;

    private Boolean resolvedFlag;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}

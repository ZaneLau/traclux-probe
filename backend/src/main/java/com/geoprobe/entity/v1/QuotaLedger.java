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
@TableName(value = "quota_ledgers", autoResultMap = true)
public class QuotaLedger {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String quotaAccountId;
    private String quotaGrantId;
    private String ledgerTypeCode;
    private Long probeDelta;
    private Long balanceAfter;
    private String sourceTypeCode;
    private String sourceId;
    private String diagnosisTaskId;
    private String probeId;
    private String organizationId;
    private String brandScopeId;
    private String planVersionId;
    private String planRecordId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> pricingRuleSnapshotJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ledgerMetadataJson;

    private String idempotencyKey;
    private String memo;
    private LocalDateTime createdAt;
}

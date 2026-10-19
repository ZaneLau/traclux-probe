package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
@TableName(value = "brand_scopes", autoResultMap = true)
public class BrandScope {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String organizationId;
    private String brandId;
    private String createdByMembershipId;
    private String scopeName;
    private String categorySnapshot;
    private String regionCode;
    private String locale;
    private String language;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String primaryDomain;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultModelPolicyJson;

    private String trackingMode;
    private Integer configGeneration;
    private Integer scopeStatusCode;
    private LocalDateTime archivedAt;
    private String archivedByMembershipId;
    private LocalDateTime retentionUntil;
    private LocalDateTime internalRestoreUntil;
    private LocalDateTime purgedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

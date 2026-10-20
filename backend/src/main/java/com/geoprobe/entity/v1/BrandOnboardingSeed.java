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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "brand_onboarding_seeds", autoResultMap = true)
public class BrandOnboardingSeed {

    @TableId
    private String id;

    private String organizationId;
    private String brandId;
    private String sourceUrl;
    private String sourceDomain;
    private Integer sourceTypeCode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> officialTermsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> brandAliasesJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> seedSnapshotJson;

    private String extractionVersion;
    private String approvedByMembershipId;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}

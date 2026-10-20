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
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "brands", autoResultMap = true)
public class Brand {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String organizationId;
    private String createdByMembershipId;
    private String brandName;
    private String brandNameEn;
    private String categoryName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String canonicalDomain;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String officialWebsite;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> officialDomainsJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> brandAliasesJson;

    private String brandDescription;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("web_surfaces")
public class WebSurface {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private String createdByMembershipId;
    private Integer surfaceTypeCode;
    private String host;
    private String pathPrefix;
    private String fullUrl;
    private Boolean officialFlag;
    private Boolean isPrimary;
    private String regionCode;
    private String language;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

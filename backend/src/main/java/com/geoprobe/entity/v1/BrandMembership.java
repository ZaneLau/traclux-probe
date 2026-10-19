package com.geoprobe.entity.v1;

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
@TableName("brand_memberships")
public class BrandMembership {

    @TableId
    private String id;

    private String organizationId;
    private String brandId;
    private String membershipId;
    private Integer accessRoleCode;
    private Integer statusCode;
    private LocalDateTime createdAt;
}

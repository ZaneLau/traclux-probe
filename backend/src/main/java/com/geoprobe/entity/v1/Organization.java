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
@TableName("organizations")
public class Organization {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String ownerUserId;
    private String organizationName;
    private Integer organizationTypeCode;
    private Integer billingOwnerTypeCode;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

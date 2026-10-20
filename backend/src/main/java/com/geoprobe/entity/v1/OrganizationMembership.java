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
@TableName("organization_memberships")
public class OrganizationMembership {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String organizationId;
    private String userId;
    private Integer roleCode;
    private Integer seatTypeCode;
    private Integer statusCode;
    private String invitedByUserId;
    private LocalDateTime invitedAt;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

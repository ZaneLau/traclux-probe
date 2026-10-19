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
@TableName("quota_accounts")
public class QuotaAccount {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String organizationId;
    private String subjectTypeCode;
    private String subjectUserId;
    private String subjectMembershipId;
    private Integer statusCode;
    private Long availableProbeQuota;
    private Long reservedProbeQuota;
    private Long consumedProbeQuota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

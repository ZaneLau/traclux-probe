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
@TableName("data_export_jobs")
public class DataExportJob {

    @TableId
    private String id;

    private String organizationId;
    private String requestedByMembershipId;
    private String exportScopeCode;
    private String targetTypeCode;
    private String targetId;
    private String formatCode;
    private Integer statusCode;
    private String objectUri;
    private String checksum;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}

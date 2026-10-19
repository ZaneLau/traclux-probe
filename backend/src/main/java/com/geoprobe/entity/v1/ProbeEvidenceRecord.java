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
@TableName("probe_evidences")
public class ProbeEvidenceRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String dispatchAttemptId;
    private String diagnosisTaskId;
    private String probeId;
    private String sourceTypeCode;
    private String provider;
    private String domain;
    private String url;
    private String title;
    private String snippet;
    private String searchQuery;
    private LocalDateTime retrievedAt;
    private LocalDateTime createdAt;
}

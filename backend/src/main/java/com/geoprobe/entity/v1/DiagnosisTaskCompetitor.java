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
@TableName("diagnosis_task_competitors")
public class DiagnosisTaskCompetitor {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private Integer seqNo;
    private String competitorBrandId;
    private String competitorNameSnapshot;
    private LocalDateTime createdAt;
}

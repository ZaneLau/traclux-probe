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
@TableName("probe_plan_record_items")
public class ProbePlanRecordItem {

    @TableId(type = IdType.INPUT)
    private String id;

    private String recordId;
    private String planVersionId;
    private String probeId;
    private String topicId;
    private String actionCode;
    private String executeModeCode;
    private Integer seqNoSnapshot;
    private String probeTextSnapshot;
    private Integer sourceCodeSnapshot;
    private LocalDateTime createdAt;
}

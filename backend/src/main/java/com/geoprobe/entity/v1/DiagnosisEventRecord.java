package com.geoprobe.entity.v1;

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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "diagnosis_events", autoResultMap = true)
public class DiagnosisEventRecord {

    @TableId(value = "seq", type = IdType.INPUT)
    private Long seq;

    private String diagnosisTaskId;
    private String eventType;
    private Boolean provisional;

    @TableField(value = "payload_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payloadJson;

    private LocalDateTime createdAt;
}

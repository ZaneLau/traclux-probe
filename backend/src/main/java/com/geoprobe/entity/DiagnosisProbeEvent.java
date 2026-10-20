package com.geoprobe.entity;

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
@TableName(value = "diagnosis_probe_events", autoResultMap = true)
public class DiagnosisProbeEvent {

    @TableId(value = "seq", type = IdType.INPUT)
    private Long seq;

    private String diagnosisId;
    private String eventType;
    private Boolean provisional;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    private LocalDateTime createdAt;
}

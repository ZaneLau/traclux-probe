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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "probe_brand_mentions", autoResultMap = true)
public class ProbeBrandMentionRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String diagnosisTaskId;
    private String probeId;
    private String dispatchAttemptId;
    private String brandId;
    private String brandName;
    private Integer brandRoleCode;
    private Integer mentionLevelCode;
    private Boolean mentionedFlag;
    private Boolean recommendedFlag;
    private Integer sentimentCode;
    private Integer positionIndex;
    private BigDecimal confidenceScore;
    private String evidenceSpan;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> mentionMetadataJson;

    private LocalDateTime createdAt;
}

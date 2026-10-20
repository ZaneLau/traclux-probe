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
@TableName("brand_space_prompts")
public class BrandSpacePrompt {

    @TableId(type = IdType.INPUT)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private String topicId;
    private Integer seqNo;
    private String promptText;
    private String regionCode;
    private String locale;
    private String language;
    private Integer sourceCode;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

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
@TableName("brand_space_topics")
public class BrandSpaceTopic {

    @TableId(type = IdType.INPUT)
    private String id;

    private String organizationId;
    private String brandId;
    private String brandScopeId;
    private Integer seqNo;
    private String name;
    private String description;
    private Integer sourceCode;
    private Integer statusCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

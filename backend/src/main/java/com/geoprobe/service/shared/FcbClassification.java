package com.geoprobe.service.shared;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FCB 分类结果对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcbClassification {

    // 品类名称
    private String category;
    // 品类整体介入度：HIGH / LOW
    private Object involvement;
    // PARAMETER 维度是否有效
    @JsonAlias({"parameter_valid", "parameterValid"})
    private Object parameterValid;
    // EXPERIENCE 维度是否有效
    @JsonAlias({"experience_valid", "experienceValid"})
    private Object experienceValid;
    // TRUST 维度是否有效
    @JsonAlias({"trust_valid", "trustValid"})
    private Object trustValid;
    // 品类筛选维度
    @JsonAlias({"category_attributes", "categoryAttributes"})
    private List<String> categoryAttributes;
    // 竞品列表
    private List<String> competitors;
    // 品类内“非品牌区分型”关键词（用于门禁过滤）
    @JsonAlias({"non_discriminating_keywords", "nonDiscriminatingKeywords"})
    private List<String> nonDiscriminatingKeywords;

    public String getInvolvement() {
        return involvement == null ? null : String.valueOf(involvement);
    }

    public boolean isParameterValid() {
        return Boolean.TRUE.equals(parameterValid);
    }

    public boolean isExperienceValid() {
        return Boolean.TRUE.equals(experienceValid);
    }

    public boolean isTrustValid() {
        return Boolean.TRUE.equals(trustValid);
    }
}

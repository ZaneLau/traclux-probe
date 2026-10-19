package com.geoprobe.service.engine.v42;

import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

final class W7Node2TopicProjectionHeuristics {

    private static final List<String> OFFICIAL_TRUST_TOKENS = List.of("官网", "官方", "可信来源", "来源", "资质", "信任");
    private static final List<String> COMPARISON_TOKENS = List.of("对比", "比较", "差别", "区别", "差在哪", "怎么选");
    private static final List<String> FIRST_DECISION_TOKENS = List.of("第一次", "入门", "先看哪几个点", "先从哪几个点", "基础决策");
    private static final List<String> VALUE_TOKENS = List.of("性价比", "预算", "值不值", "价格", "划算", "贵不贵", "同价位");
    private static final List<String> CAPACITY_TOKENS = List.of("容量", "空间", "小户型", "出租屋", "台面", "占地方");
    private static final List<String> SCENARIO_TOKENS = List.of("适合什么人", "适合谁", "什么场景", "哪类家庭", "使用场景");
    private static final List<String> RECOMMEND_TOKENS = List.of("推荐", "主动推荐", "不指定品牌", "不点名", "替代");
    private static final List<String> DECISION_BLOCK_TOKENS = List.of("顾虑", "劝退", "阻力", "误解", "犹豫", "卡住");
    private static final List<String> DURABILITY_TOKENS = List.of("做工", "耐用", "长期使用", "耐不耐用");
    private static final List<String> EXPERIENCE_TOKENS = List.of("口碑", "体验", "优缺点", "风险点", "翻车", "差评");

    private W7Node2TopicProjectionHeuristics() {
    }

    static LightConeV43TopicStateProjectionSupport.TopicStateProjection deriveTopicProjection(String sourceProbeText) {
        String label = inferTopicLabel(sourceProbeText);
        if (!StringUtils.hasText(label)) {
            return null;
        }
        return new LightConeV43TopicStateProjectionSupport.TopicStateProjection(
                "topic-" + Math.abs(label.hashCode()),
                LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION,
                label,
                "general_visibility",
                Map.of("D1", 0.55d, "D2", 0.60d, "D3", 0.55d, "D4", 0.45d, "D5", 0.50d, "D6", 0.55d),
                List.of(),
                1,
                1,
                0.4d,
                0.3d,
                "w7_harness_inference",
                List.of(),
                Map.of()
        );
    }

    static boolean hasOfficialIntent(String sourceProbeText) {
        return containsAnyToken(sourceProbeText, OFFICIAL_TRUST_TOKENS);
    }

    private static String inferTopicLabel(String sourceProbeText) {
        if (!StringUtils.hasText(sourceProbeText)) {
            return null;
        }
        String normalized = sourceProbeText.trim();
        if (containsAnyToken(normalized, OFFICIAL_TRUST_TOKENS)) {
            return "官网与信任线索";
        }
        if (containsAnyToken(normalized, CAPACITY_TOKENS)) {
            return "容量与空间适配";
        }
        if (containsAnyToken(normalized, SCENARIO_TOKENS)) {
            return "适用人群与场景";
        }
        if (containsAnyToken(normalized, RECOMMEND_TOKENS)) {
            return "推荐与替代";
        }
        if (containsAnyToken(normalized, DECISION_BLOCK_TOKENS)) {
            return "购买决策阻力";
        }
        if (containsAnyToken(normalized, VALUE_TOKENS)) {
            return "性价比与价格带";
        }
        if (containsAnyToken(normalized, DURABILITY_TOKENS)) {
            return "做工与耐用";
        }
        if (containsAnyToken(normalized, EXPERIENCE_TOKENS)) {
            return "真实体验与口碑风险";
        }
        if (containsAnyToken(normalized, FIRST_DECISION_TOKENS)) {
            return "第一次挑与基础决策";
        }
        if (containsAnyToken(normalized, COMPARISON_TOKENS)) {
            return "竞品对比与差异化比较";
        }
        return null;
    }

    private static boolean containsAnyToken(String content, List<String> tokens) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        for (String token : tokens) {
            if (StringUtils.hasText(token) && content.contains(token)) {
                return true;
            }
        }
        return false;
    }
}

package com.geoprobe.service.shared;

import java.util.List;

/**
 * 引擎级默认常量，避免多处维护同一份规则导致漂移。
 */
public final class EngineDefaults {

    private EngineDefaults() {
    }

    public static final List<String> NON_DISCRIMINATING_KEYWORDS = List.of(
            "是什么", "什么意思", "怎么做", "流程", "步骤",
            "注意事项", "常见问题", "需要准备什么", "多久", "在哪里",
            "怎么预约", "材料要求", "适用条件", "基础原理"
    );

    public static final List<String> NEGATIVE_TERMS = List.of(
            "不推荐", "体验差", "效果不佳", "性价比低", "价格偏高",
            "稳定性差", "问题频发", "信息不透明", "与宣传不符", "服务响应慢",
            "售后体验差", "风险较高", "质量不稳定", "使用门槛高", "不值得选择"
    );
}

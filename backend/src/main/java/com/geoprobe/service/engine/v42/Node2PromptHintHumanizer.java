package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

final class Node2PromptHintHumanizer {

    private Node2PromptHintHumanizer() {
    }

    static String humanizeMustSemantic(String semantic) {
        String[] parts = semantic.split(":", 2);
        if (parts.length != 2) {
            return semantic;
        }
        return switch (parts[0] + ":" + parts[1]) {
            case "D2:informational_directed" -> "问题里要自然表达“想尽快得到明确判断”";
            case "D2:informational_undirected" -> "问题里要自然表达“先想了解一下方向”";
            case "D2:instructional_active" -> "问题里要自然表达“想知道具体该怎么做/怎么选”，最好带出“从哪几步看起 / 按什么思路挑 / 先抓哪几块”这类动作骨架，不要只停在泛问哪个好";
            case "D2:resource_acquisition" -> "问题里要自然表达“已经临近购买/下单，想做最后确认后就定一个”，不要只停在普通比较；最好自然带出“现在买 / 买前 / 下单前 / 准备定 / 拍板前 / 直接拿”这类购买前信号之一";
            case "D3:subsistence_protection" -> "问题里要自然表达“怕踩坑、怕出问题”";
            case "D3:affection_belonging_participation" -> "问题里要自然表达“也在意别人一般怎么选”";
            case "D3:understanding_competence" -> "问题里要自然表达“也想顺便弄明白差别”";
            case "D3:identity_freedom" -> "问题里要自然表达“又不想选得太普通”";
            case "D3:creation_idleness" -> "问题里要自然表达“最好轻松、省心、别太折腾”";
            default -> semantic;
        };
    }

    static String humanizeSoftHint(String hint) {
        String[] parts = hint.split(":", 2);
        if (parts.length != 2) {
            return hint;
        }
        String abstractCode = parts[1].split("=", 2)[0];
        return switch (parts[0]) {
            case "D1" -> "认知阶段只影响问法姿态，不要直接自报状态：" + humanizeD1Hint(abstractCode);
            case "D4" -> "用信息密度和铺垫多少体现耐心与负荷，不要直接说自己很累或很急";
            case "D5" -> "用决策窗口长短体现时间压力，不要直接说“急着用/马上要喝”";
            case "D6" -> "用开放度和是否期待追问体现互动模式：" + humanizeD6Hint(abstractCode);
            case "VARIATION" -> parts[1];
            case "TOPIC" -> "这轮主问题不要跑偏，优先围绕这个 topic 展开：" + parts[1];
            case "SCENE" -> "可落在这样的真实场景：" + parts[1];
            case "DECISION" -> "用户可能会在意：" + parts[1];
            case "DESCRIBE" -> "可以贴近这种真人说法：" + parts[1];
            case "COMPARE" -> "允许自然拿这些对象做比较：" + parts[1];
            case "EVIDENCE" -> "这轮主问题要明确落到这个具体判断对象上，不要退回泛泛选购：" + parts[1];
            case "CHANNEL" -> "渠道或获取方式可自然带上：" + parts[1];
            case "MEMORY" -> "允许使用模糊记忆线索，但别像广告：" + parts[1];
            default -> hint;
        };
    }

    static String humanizeSurfaceIntent(String surfaceIntent) {
        if (!StringUtils.hasText(surfaceIntent)) {
            return "先把问题问清楚";
        }
        return switch (surfaceIntent) {
            case "browse_for_options" -> "先看看有哪些值得关注的选项";
            case "seek_clear_answer" -> "想尽快得到一个清楚判断";
            case "ask_for_actionable_steps" -> "想知道下一步到底该怎么选";
            case "confirm_and_buy" -> "已经接近决策，想确认后直接买";
            case "high_frequency_decision_query" -> "像高频消费里常见的默认决策问题";
            case "brand_stress_test" -> "带着怀疑或质疑来问";
            case "brand_exclusion_test" -> "有意测试不提品牌时 AI 会怎么走";
            default -> "先把问题问清楚";
        };
    }

    static String humanizeInteractionPosture(String interactionPosture) {
        if (!StringUtils.hasText(interactionPosture)) {
            return "正常口语请教";
        }
        return switch (interactionPosture) {
            case "confirmation_seeking" -> "心里已有偏向，问法不会完全开放";
            case "stressed_urgent" -> "表达更直接、更少铺垫，优先要结果";
            case "confused_entry" -> "信息还很少，问法更像先找入口";
            case "deliberate_structuring" -> "已经在比较，问题会更有结构";
            case "exploratory_browsing" -> "边看边试探，还没完全下决心";
            case "comparative_browsing" -> "默认会拿几个常见选项比较";
            case "skeptical_challenging" -> "带着质疑和挑刺来问";
            default -> "正常口语请教";
        };
    }

    static String humanizeConversationMode(String conversationMode) {
        if (!StringUtils.hasText(conversationMode)) {
            return "一句自然提问";
        }
        return switch (conversationMode) {
            case "single_turn_search_like" -> "像手机里随手问一句";
            case "constraint_driven_query" -> "会带点条件和限制";
            case "back_and_forth_consultation" -> "像准备和 AI 来回聊几句";
            case "search_like_prompt" -> "更像大众常见的搜索式提问";
            case "challenge_prompt" -> "更像故意拧着问、试探反应";
            default -> "一句自然提问";
        };
    }

    static String humanizeBrandFraming(String brandFraming) {
        if (!StringUtils.hasText(brandFraming)) {
            return "默认不主动暴露品牌";
        }
        return switch (brandFraming) {
            case "explicit_brand_reference" -> "可以自然提到品牌";
            case "hidden_brand_discovery" -> "不要主动提品牌，只问真实问题";
            default -> "默认不主动暴露品牌";
        };
    }

    static String humanizeDegradation(String degradationLevel) {
        if (!StringUtils.hasText(degradationLevel)) {
            return "正常口语";
        }
        return switch (degradationLevel) {
            case "high" -> "可以更短、更直接，但不能像状态说明或关键词堆叠";
            case "medium" -> "可以少铺垫、略碎一点，但仍要自然可读";
            case "low" -> "尽量自然完整";
            default -> "正常口语";
        };
    }

    static String humanizeD1Hint(String abstractCode) {
        return switch (abstractCode) {
            case "initiation" -> "像刚起步，还不知道先看什么";
            case "exploration" -> "像边看边试，未形成明确结论";
            case "formulation" -> "像已经有几条判断标准，问题会更聚焦";
            case "verification" -> "像心里已有答案，想确认这个方向对不对";
            default -> "保持自然";
        };
    }

    static String humanizeD6Hint(String abstractCode) {
        return switch (abstractCode) {
            case "recognition" -> "更像一句随手问的问题";
            case "specification" -> "更像带条件的筛选问题";
            case "dialogic" -> "更像默认 AI 会继续帮他想下去";
            default -> "保持自然";
        };
    }
}

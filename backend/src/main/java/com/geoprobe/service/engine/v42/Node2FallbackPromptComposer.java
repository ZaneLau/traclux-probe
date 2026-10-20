package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

class Node2FallbackPromptComposer {

    String fallbackCoreQuestion(String subject,
                                ProbeExposurePolicy exposurePolicy,
                                RealizationPlan realizationPlan,
                                String supportClause) {
        if (realizationPlan == null) {
            return subject + "到底怎么选更靠谱";
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        String interactionPosture = realizationPlan.simulationIntentProfile().interactionPosture();
        String d1 = stateCode(realizationPlan, "D1");
        String d3 = stateCode(realizationPlan, "D3");
        String d5 = stateCode(realizationPlan, "D5");
        String evidenceFocused = fallbackEvidenceFocusedStem(subject, exposurePolicy, realizationPlan, d3);
        String topicFocused = fallbackTopicFocusedStem(subject, realizationPlan, "core");
        if (StringUtils.hasText(evidenceFocused)
                && ("seek_clear_answer".equals(surfaceIntent) || "confirm_and_buy".equals(surfaceIntent))) {
            return appendSupportClause(evidenceFocused, supportClause);
        }
        if (StringUtils.hasText(topicFocused)) {
            return appendSupportClause(topicFocused, supportClause);
        }
        return switch (surfaceIntent) {
            case "browse_for_options" -> appendSupportClause(subject + "有哪些值得先看", supportClause);
            case "seek_clear_answer" -> appendSupportClause(fallbackSeekClearAnswerStem(subject, exposurePolicy, conversationMode, d3), supportClause);
            case "ask_for_actionable_steps" -> appendSupportClause(fallbackActionableCoreStem(subject, exposurePolicy, conversationMode, d3, d5), supportClause);
            case "confirm_and_buy" -> appendSupportClause(
                    fallbackConfirmAndBuyStem(subject, exposurePolicy, conversationMode, interactionPosture, d1, d3, d5),
                    supportClause
            );
            default -> appendSupportClause(subject + "到底怎么选更靠谱", supportClause);
        };
    }

    String fallbackAngleQuestion(String subject,
                                 ProbeExposurePolicy exposurePolicy,
                                 RealizationPlan realizationPlan,
                                 String supportClause) {
        if (realizationPlan == null) {
            return subject + "选哪个更合适";
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String d1 = stateCode(realizationPlan, "D1");
        String d3 = stateCode(realizationPlan, "D3");
        String d5 = stateCode(realizationPlan, "D5");
        String evidenceFocused = fallbackEvidenceFocusedStem(subject, exposurePolicy, realizationPlan, d3);
        String topicFocused = fallbackTopicFocusedStem(subject, realizationPlan, "angle");
        if (StringUtils.hasText(evidenceFocused)
                && ("seek_clear_answer".equals(surfaceIntent) || "confirm_and_buy".equals(surfaceIntent))) {
            return appendSupportClause(evidenceFocused, supportClause);
        }
        if (StringUtils.hasText(topicFocused)) {
            return appendSupportClause(topicFocused, supportClause);
        }
        return switch (realizationPlan.simulationIntentProfile().conversationMode()) {
            case "back_and_forth_consultation" -> appendSupportClause(
                    switch (surfaceIntent) {
                        case "confirm_and_buy" -> fallbackConfirmAngleStem(subject, exposurePolicy, "back_and_forth_consultation", d1, d3, d5);
                        case "seek_clear_answer" -> fallbackSeekAngleStem(subject, exposurePolicy, "back_and_forth_consultation", d3);
                        default -> "如果现在挑" + subject + "，更该从哪几处聊起";
                    },
                    supportClause
            );
            case "constraint_driven_query" -> appendSupportClause(
                    switch (surfaceIntent) {
                        case "confirm_and_buy" -> fallbackConfirmAngleStem(subject, exposurePolicy, "constraint_driven_query", d1, d3, d5);
                        case "seek_clear_answer" -> fallbackSeekAngleStem(subject, exposurePolicy, "constraint_driven_query", d3);
                        default -> fallbackConstraintAngleStem(subject, d3, d5);
                    },
                    supportClause
            );
            case "single_turn_search_like" -> "seek_clear_answer".equals(surfaceIntent)
                    ? appendSupportClause(fallbackSearchShortcutStem(subject, exposurePolicy, d3), supportClause)
                    : appendSupportClause(subject + "选哪个更合适", supportClause);
            default -> appendSupportClause(subject + "选哪个更合适", supportClause);
        };
    }

    String fallbackBrowseQuestion(String subject,
                                  ProbeExposurePolicy exposurePolicy,
                                  RealizationPlan realizationPlan,
                                  String supportClause) {
        if (realizationPlan == null) {
            return subject + "该从哪点看起";
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        String d1 = stateCode(realizationPlan, "D1");
        String d3 = stateCode(realizationPlan, "D3");
        String evidenceFocused = fallbackEvidenceFocusedStem(subject, exposurePolicy, realizationPlan, d3);
        String topicFocused = fallbackTopicFocusedStem(subject, realizationPlan, "browse");
        if (StringUtils.hasText(evidenceFocused)
                && "seek_clear_answer".equals(surfaceIntent)) {
            return appendSupportClause(evidenceFocused, supportClause);
        }
        if (StringUtils.hasText(topicFocused)) {
            return appendSupportClause(topicFocused, supportClause);
        }
        if ("confirm_and_buy".equals(surfaceIntent)) {
            return appendSupportClause(
                    fallbackConfirmBrowseStem(subject, exposurePolicy, conversationMode, d1, d3, stateCode(realizationPlan, "D5")),
                    supportClause
            );
        }
        if ("seek_clear_answer".equals(surfaceIntent)) {
            return appendSupportClause(
                    fallbackSeekBrowseStem(subject, exposurePolicy, conversationMode, d3),
                    supportClause
            );
        }
        return switch (realizationPlan.simulationIntentProfile().interactionPosture()) {
            case "confirmation_seeking" -> appendSupportClause(
                    fallbackBrowseConfirmationStem(subject, exposurePolicy, conversationMode, d3),
                    supportClause
            );
            case "confused_entry" -> appendSupportClause(subject + "到底先从哪一步看起", supportClause);
            case "deliberate_structuring" -> appendSupportClause(subject + "先从哪几项看起就够了", supportClause);
            case "stressed_urgent" -> exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                    ? appendSupportClause("如果时间不多，先从" + subject + "哪几点看起会更稳", supportClause)
                    : appendSupportClause("如果时间不多，先从" + subject + "哪几点看起会更省心", supportClause);
            default -> appendSupportClause(subject + "有哪些值得先看", supportClause);
        };
    }

    String fallbackShortQuestion(String subject,
                                 ProbeExposurePolicy exposurePolicy,
                                 RealizationPlan realizationPlan,
                                 String supportClause) {
        if (realizationPlan == null) {
            return subject + "哪个更靠谱";
        }
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        String interactionPosture = realizationPlan.simulationIntentProfile().interactionPosture();
        String d1 = stateCode(realizationPlan, "D1");
        String d3 = stateCode(realizationPlan, "D3");
        String d5 = stateCode(realizationPlan, "D5");
        String evidenceFocused = fallbackEvidenceFocusedStem(subject, exposurePolicy, realizationPlan, d3);
        String topicFocused = fallbackTopicFocusedStem(subject, realizationPlan, "short");
        if (StringUtils.hasText(evidenceFocused)
                && "seek_clear_answer".equals(realizationPlan.simulationIntentProfile().surfaceIntent())) {
            return appendSupportClause(evidenceFocused, supportClause);
        }
        if (StringUtils.hasText(topicFocused)) {
            return appendSupportClause(topicFocused, supportClause);
        }
        return switch (realizationPlan.simulationIntentProfile().surfaceIntent()) {
            case "confirm_and_buy" -> appendSupportClause(
                    fallbackConfirmAndBuyShortStem(subject, exposurePolicy, conversationMode, interactionPosture, d1, d3, d5),
                    supportClause
            );
            case "ask_for_actionable_steps" -> appendSupportClause(fallbackActionStepShortStem(subject, exposurePolicy, conversationMode, d3), supportClause);
            case "seek_clear_answer" -> appendSupportClause(fallbackSeekClearAnswerShortStem(subject, exposurePolicy, conversationMode, d3), supportClause);
            default -> appendSupportClause(subject + "更该从哪点看起", supportClause);
        };
    }

    String subjectPhrase(String anchor,
                         ProbeExposurePolicy exposurePolicy,
                         String brandName,
                         EngineTaskContextPackage contextPackage) {
        String normalizedAnchor = normalizeFallbackAnchor(anchor);
        String contextualSubject = selectContextualSubject(normalizedAnchor, exposurePolicy, brandName, contextPackage);
        if (StringUtils.hasText(contextualSubject)) {
            if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                    && StringUtils.hasText(brandName)
                    && !normalize(contextualSubject).contains(normalize(brandName))) {
                return brandName + "这个" + contextualSubject;
            }
            return contextualSubject;
        }
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE && StringUtils.hasText(brandName)) {
            return brandName + "这个" + normalizedAnchor;
        }
        return normalizedAnchor;
    }

    List<String> fallbackSupportClauses(RealizationPlan realizationPlan,
                                        ProbeExposurePolicy exposurePolicy) {
        String d3 = stateCode(realizationPlan, "D3");
        String surfaceIntent = realizationPlan == null ? "" : realizationPlan.simulationIntentProfile().surfaceIntent();
        List<String> baseClauses = !StringUtils.hasText(d3)
                ? List.of("")
                : switch (d3) {
            case "subsistence_protection" -> "confirm_and_buy".equals(surfaceIntent)
                    ? List.of("")
                    : List.of("", "，更不容易买错");
            case "affection_belonging_participation" -> List.of("", "，大家通常会先拿哪种");
            case "understanding_competence" -> "confirm_and_buy".equals(surfaceIntent)
                    ? List.of("")
                    : List.of("", "，哪块更容易看出区别");
            case "identity_freedom" -> exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                    ? List.of("")
                    : List.of("", "，不想太大众的话怎么挑");
            case "creation_idleness" -> List.of("", "，更省得来回比");
            default -> List.of("");
        };
        return mergeTopicSupportClause(baseClauses, fallbackTopicSupportClause(realizationPlan));
    }

    private String fallbackEvidenceFocusedStem(String subject,
                                               ProbeExposurePolicy exposurePolicy,
                                               RealizationPlan realizationPlan,
                                               String d3) {
        String evidenceCue = softHintValue(realizationPlan, "EVIDENCE");
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(evidenceCue)) {
            return null;
        }
        String focus = evidenceFocus(evidenceCue);
        String predicate = evidencePredicate(evidenceCue);
        if (!StringUtils.hasText(focus) || !StringUtils.hasText(predicate)) {
            return null;
        }
        String focusQuestion = subject + "的" + focus + predicate;
        if (ProbeExposurePolicy.BRAND_VISIBLE == exposurePolicy) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个没那么大众的，" + focusQuestion;
                case "affection_belonging_participation" -> "按大家常选的看，" + focusQuestion;
                case "understanding_competence" -> "想先看明白的话，" + focusQuestion;
                case "creation_idleness" -> "想省点事的话，" + focusQuestion;
                default -> focusQuestion;
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想挑个没那么大众的，" + focusQuestion;
            case "affection_belonging_participation" -> "按大家常选的看，" + focusQuestion;
            case "understanding_competence" -> "想先看明白的话，" + focusQuestion;
            case "creation_idleness" -> "想省点事的话，" + focusQuestion;
            default -> focusQuestion;
        };
    }

    private String fallbackTopicFocusedStem(String subject,
                                            RealizationPlan realizationPlan,
                                            String mode) {
        String topicCue = softHintValue(realizationPlan, "TOPIC");
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(topicCue)) {
            return null;
        }
        String normalized = normalize(topicCue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return switch (resolveTopicStemKey(normalized)) {
            case "buying_entry" -> switch (normalize(mode)) {
                case "browse" -> "第一次挑" + subject + "，先从哪几个点看起";
                case "short" -> subject + "先看哪点";
                default -> "第一次挑" + subject + "，到底先从哪几个决策点看起";
            };
            case "brand_comparison" -> switch (normalize(mode)) {
                case "browse" -> "想比" + subject + "和别家差别，先看哪点";
                case "short" -> subject + "和别家怎么比";
                default -> subject + "和同类品牌比，差别主要该看哪点";
            };
            case "trust_signal" -> switch (normalize(mode)) {
                case "browse" -> "想判断" + subject + "靠不靠谱，先看官网还是第三方口碑";
                case "short" -> subject + "靠不靠谱先看什么";
                default -> "判断" + subject + "靠不靠谱，官网、可信来源和口碑该先看哪边";
            };
            case "review_reputation" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "真实口碑，先翻哪类体验和差评";
                case "short" -> subject + "真实口碑怎么样";
                default -> subject + "真实体验里最常被提到的优缺点到底是什么";
            };
            case "value_for_money" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "值不值，先从价格还是性价比看起";
                case "short" -> subject + "性价比怎么样";
                default -> subject + "放在同价位里到底值不值";
            };
            case "audience_scenario" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "适合什么人什么场景，先确认哪点";
                case "short" -> subject + "适合什么人用";
                default -> subject + "到底更适合什么人、什么场景";
            };
            case "capacity_fit" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "容量和空间适配，先确认哪点";
                case "short" -> subject + "容量空间合适吗";
                default -> subject + "容量和空间适配这块到底怎么判断";
            };
            case "recommendation_substitute" -> switch (normalize(mode)) {
                case "browse" -> "如果不点名，想看" + subject + "会不会被主动推荐，先看哪点";
                case "short" -> subject + "会被主动推荐吗";
                default -> "如果不指定品牌，" + subject + "会不会被主动推荐，替代会是谁";
            };
            case "decision_friction" -> switch (normalize(mode)) {
                case "browse" -> "下单前担心" + subject + "的话，最该先排查哪点";
                case "short" -> subject + "最常见顾虑是什么";
                default -> "买" + subject + "前，最常见的顾虑和劝退点到底是什么";
            };
            case "budget_value" -> switch (normalize(mode)) {
                case "browse" -> "百元内想买" + subject + "，先看哪点更值";
                case "short" -> subject + "百元内值不值";
                default -> subject + "百元内值不值得买";
            };
            case "office_quiet" -> switch (normalize(mode)) {
                case "browse" -> "办公用想挑个安静顺手的，" + subject + "先看哪点";
                case "short" -> subject + "静音顺手吗";
                default -> subject + "办公用静音和顺手度到底怎么样";
            };
            case "wireless_stability" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "的无线双模，先确认连接哪点";
                case "short" -> subject + "无线双模稳吗";
                default -> subject + "的无线和双模连接到底稳不稳";
            };
            case "light_gaming" -> switch (normalize(mode)) {
                case "browse" -> "轻度游戏想用" + subject + "，先看哪点才知道够不够";
                case "short" -> subject + "轻度游戏够用吗";
                default -> subject + "拿来轻度游戏到底够不够用";
            };
            case "student_dorm" -> switch (normalize(mode)) {
                case "browse" -> "学生党宿舍里想用" + subject + "，先看什么更合适";
                case "short" -> subject + "宿舍用合适吗";
                default -> "学生党放宿舍用，" + subject + "到底合不合适";
            };
            case "dpi_practicality" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "的DPI调节，先确认哪点";
                case "short" -> subject + "DPI实用吗";
                default -> subject + "的DPI调节到底实不实用";
            };
            case "extra_features" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "的防水静音这些卖点，先看哪点";
                case "short" -> subject + "附加卖点靠谱吗";
                default -> subject + "的防水静音这些附加卖点到底靠不靠谱";
            };
            case "durability" -> switch (normalize(mode)) {
                case "browse" -> "想看" + subject + "耐不耐用，先确认哪点";
                case "short" -> subject + "做工耐用吗";
                default -> subject + "做工耐用这块到底稳不稳";
            };
            case "office_vs_gaming" -> switch (normalize(mode)) {
                case "browse" -> "办公和轻度游戏都想兼顾，" + subject + "先看哪点";
                case "short" -> subject + "办公游戏怎么兼顾";
                default -> "办公和轻度游戏都想兼顾，" + subject + "该怎么选";
            };
            case "same_price_tradeoff" -> switch (normalize(mode)) {
                case "browse" -> "同价位里想挑" + subject + "，先怎么取舍";
                case "short" -> subject + "同价位怎么取舍";
                default -> "同价位里，" + subject + "和别家该怎么取舍";
            };
            default -> null;
        };
    }

    private String resolveTopicStemKey(String normalizedTopicCue) {
        if (normalizedTopicCue.contains("选购场景") || normalizedTopicCue.contains("第一次挑") || normalizedTopicCue.contains("基础决策")) {
            return "buying_entry";
        }
        if (normalizedTopicCue.contains("竞品对比") || normalizedTopicCue.contains("品牌对比") || normalizedTopicCue.contains("差异化比较")) {
            return "brand_comparison";
        }
        if (normalizedTopicCue.contains("官网") || normalizedTopicCue.contains("信任") || normalizedTopicCue.contains("可信来源") || normalizedTopicCue.contains("第三方口碑") || normalizedTopicCue.contains("资质")) {
            return "trust_signal";
        }
        if (normalizedTopicCue.contains("真实体验") || normalizedTopicCue.contains("口碑") || normalizedTopicCue.contains("优缺点") || normalizedTopicCue.contains("风险点")) {
            return "review_reputation";
        }
        if (normalizedTopicCue.contains("性价比") || normalizedTopicCue.contains("价格带") || normalizedTopicCue.contains("价格")) {
            return "value_for_money";
        }
        if (normalizedTopicCue.contains("人群") || normalizedTopicCue.contains("适合什么人") || normalizedTopicCue.contains("使用场景")) {
            return "audience_scenario";
        }
        if (normalizedTopicCue.contains("容量") || normalizedTopicCue.contains("空间适配") || normalizedTopicCue.contains("小户型") || normalizedTopicCue.contains("出租屋") || normalizedTopicCue.contains("台面空间")) {
            return "capacity_fit";
        }
        if (normalizedTopicCue.contains("推荐与替代") || normalizedTopicCue.contains("主动推荐") || normalizedTopicCue.contains("不指定品牌")) {
            return "recommendation_substitute";
        }
        if (normalizedTopicCue.contains("购买决策阻力") || normalizedTopicCue.contains("顾虑") || normalizedTopicCue.contains("劝退") || normalizedTopicCue.contains("决策阻力") || normalizedTopicCue.contains("误解")) {
            return "decision_friction";
        }
        if (normalizedTopicCue.contains("值不值得") || normalizedTopicCue.contains("值得买") || normalizedTopicCue.contains("百元内")) {
            return "budget_value";
        }
        if (normalizedTopicCue.contains("防水") || normalizedTopicCue.contains("附加卖点")) {
            return "extra_features";
        }
        if (normalizedTopicCue.contains("静音") || normalizedTopicCue.contains("顺手")) {
            return "office_quiet";
        }
        if (normalizedTopicCue.contains("无线") || normalizedTopicCue.contains("双模") || normalizedTopicCue.contains("连接")) {
            return "wireless_stability";
        }
        if (normalizedTopicCue.contains("学生党") || normalizedTopicCue.contains("宿舍")) {
            return "student_dorm";
        }
        if (normalizedTopicCue.contains("dpi") || normalizedTopicCue.contains("调节") || normalizedTopicCue.contains("实用")) {
            return "dpi_practicality";
        }
        if (normalizedTopicCue.contains("做工") || normalizedTopicCue.contains("耐用") || normalizedTopicCue.contains("长期使用")) {
            return "durability";
        }
        if (normalizedTopicCue.contains("办公用户") || normalizedTopicCue.contains("轻度游戏用户")) {
            return "office_vs_gaming";
        }
        if (normalizedTopicCue.contains("轻度游戏")) {
            return "light_gaming";
        }
        if (normalizedTopicCue.contains("同价位") || normalizedTopicCue.contains("替代") || normalizedTopicCue.contains("取舍")) {
            return "same_price_tradeoff";
        }
        return "";
    }

    private String softHintValue(RealizationPlan realizationPlan, String prefix) {
        if (realizationPlan == null
                || realizationPlan.realizationConstraints() == null
                || realizationPlan.realizationConstraints().softSemanticHints() == null
                || !StringUtils.hasText(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.trim().toUpperCase();
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .filter(StringUtils::hasText)
                .map(hint -> hint.split(":", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> normalizedPrefix.equals(parts[0].trim().toUpperCase()))
                .map(parts -> parts[1].trim())
                .findFirst()
                .orElse(null);
    }

    private String evidenceFocus(String evidenceCue) {
        String normalized = normalize(evidenceCue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("附加卖点")) {
            return "防水静音这些附加卖点";
        }
        if (normalized.contains("dpi")) {
            return "DPI调节";
        }
        if ((normalized.contains("无线") || normalized.contains("双模")) && normalized.contains("连接")) {
            return "无线和双模连接";
        }
        return null;
    }

    private String evidencePredicate(String evidenceCue) {
        String normalized = normalize(evidenceCue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.contains("噱头") || normalized.contains("靠谱")) {
            return "到底靠不靠谱，值得先看吗";
        }
        if (normalized.contains("实用") || normalized.contains("功能")) {
            return "到底实不实用，值得先看吗";
        }
        if (normalized.contains("稳定") || normalized.contains("掉连") || normalized.contains("延迟")) {
            return "到底稳不稳，值得先看吗";
        }
        return null;
    }

    private List<String> mergeTopicSupportClause(List<String> baseClauses, String topicClause) {
        if (!StringUtils.hasText(topicClause)) {
            return baseClauses == null || baseClauses.isEmpty() ? List.of("") : baseClauses;
        }
        List<String> safeBaseClauses = baseClauses == null || baseClauses.isEmpty() ? List.of("") : baseClauses;
        List<String> merged = new ArrayList<>(safeBaseClauses.size() * 2);
        for (String clause : safeBaseClauses) {
            String normalizedClause = clause == null ? "" : clause;
            merged.add(normalizedClause);
            if (normalizedClause.isEmpty()) {
                merged.add(topicClause);
            } else if (!normalize(normalizedClause).contains(normalize(topicClause))) {
                merged.add(topicClause + normalizedClause);
            }
        }
        return merged.stream().distinct().toList();
    }

    private String fallbackTopicSupportClause(RealizationPlan realizationPlan) {
        String topicCue = softHintValue(realizationPlan, "TOPIC");
        if (!StringUtils.hasText(topicCue)) {
            return null;
        }
        String normalized = normalize(topicCue);
        if (normalized.contains("选购场景") || normalized.contains("第一次挑") || normalized.contains("基础决策")) {
            return "，入门先看哪几个点";
        }
        if (normalized.contains("竞品对比") || normalized.contains("品牌对比") || normalized.contains("差异化比较")) {
            return "，和别家对比先看哪点";
        }
        if (normalized.contains("官网") || normalized.contains("信任") || normalized.contains("可信来源") || normalized.contains("第三方口碑") || normalized.contains("资质")) {
            return "，官网和口碑先看哪边";
        }
        if (normalized.contains("真实体验") || normalized.contains("口碑") || normalized.contains("优缺点") || normalized.contains("风险点")) {
            return "，真实体验里最该看啥";
        }
        if (normalized.contains("性价比") || normalized.contains("价格带") || normalized.contains("价格")) {
            return "，值不值主要看哪点";
        }
        if (normalized.contains("人群") || normalized.contains("适合什么人") || normalized.contains("使用场景")) {
            return "，适合谁用先看哪点";
        }
        if (normalized.contains("容量") || normalized.contains("空间适配") || normalized.contains("小户型") || normalized.contains("出租屋") || normalized.contains("台面空间")) {
            return "，容量和空间适配先看哪点";
        }
        if (normalized.contains("推荐与替代") || normalized.contains("主动推荐") || normalized.contains("不指定品牌")) {
            return "，会不会被主动推荐重点看啥";
        }
        if (normalized.contains("购买决策阻力") || normalized.contains("顾虑") || normalized.contains("劝退") || normalized.contains("决策阻力") || normalized.contains("误解")) {
            return "，最容易劝退的是哪点";
        }
        if (normalized.contains("值不值得") || normalized.contains("值得买") || normalized.contains("百元内")) {
            return "，值不值得买主要先看哪点";
        }
        if (normalized.contains("静音") || normalized.contains("顺手")) {
            return "，静音手感先看哪点";
        }
        if (normalized.contains("无线") || normalized.contains("双模") || normalized.contains("连接")) {
            return "，连接稳定先看哪点";
        }
        if (normalized.contains("轻度游戏")) {
            return "，轻度游戏够不够用先看哪点";
        }
        if (normalized.contains("学生党") || normalized.contains("宿舍")) {
            return "，宿舍日常适配先看哪点";
        }
        if (normalized.contains("dpi") || normalized.contains("调节") || normalized.contains("实用")) {
            return "，DPI调节实不实用先看哪点";
        }
        if (normalized.contains("防水")) {
            return "，防水静音这些卖点先看哪点";
        }
        if (normalized.contains("口碑")) {
            return "，口碑这块重点看啥";
        }
        if (normalized.contains("对比") || normalized.contains("比较")) {
            return "，对比时先看哪点";
        }
        if (normalized.contains("性价比") || normalized.contains("价格")) {
            return "，性价比先看哪点";
        }
        if (normalized.contains("顾虑") || normalized.contains("阻力") || normalized.contains("劝退")) {
            return "，最容易劝退的是哪点";
        }
        if (normalized.contains("推荐") || normalized.contains("替代")) {
            return "，更容易被推荐的是哪种";
        }
        if (normalized.contains("场景")) {
            return "，场景适配先看哪点";
        }
        if (normalized.contains("材质") || normalized.contains("耐用")) {
            return "，材质耐用先看哪点";
        }
        if (normalized.contains("同价位")) {
            return "，同价位替代先看哪点";
        }
        return null;
    }

    private String normalizeFallbackAnchor(String anchor) {
        if (!StringUtils.hasText(anchor)) {
            return anchor;
        }
        String normalized = anchor.trim();
        if (normalized.endsWith("品牌")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        return StringUtils.hasText(normalized) ? normalized : anchor;
    }

    private String appendSupportClause(String stem, String supportClause) {
        if (!StringUtils.hasText(stem)) {
            return stem;
        }
        if (!StringUtils.hasText(supportClause)) {
            return stem;
        }
        String normalizedStem = normalize(stem);
        String normalizedClause = normalize(supportClause);
        if (!StringUtils.hasText(normalizedClause)) {
            return stem;
        }
        if (normalizedStem.contains(normalizedClause)) {
            return stem;
        }
        boolean strongTopicStem = containsStrongTopicStem(normalizedStem);
        if (strongTopicStem
                && ((normalizedClause.contains("官网") && normalizedStem.contains("官网"))
                || (normalizedClause.contains("口碑") && normalizedStem.contains("口碑"))
                || (normalizedClause.contains("值不值") && normalizedStem.contains("值不值"))
                || (normalizedClause.contains("真实体验") && normalizedStem.contains("真实体验"))
                || (normalizedClause.contains("适合") && normalizedStem.contains("适合"))
                || (normalizedClause.contains("容量") && normalizedStem.contains("容量"))
                || (normalizedClause.contains("推荐") && normalizedStem.contains("推荐"))
                || (normalizedClause.contains("顾虑") && normalizedStem.contains("顾虑")))) {
            return stem;
        }
        if ((normalizedClause.contains("差别") || normalizedClause.contains("区别"))
                && (normalizedStem.contains("差别")
                || normalizedStem.contains("区别")
                || normalizedStem.contains("判断")
                || normalizedStem.contains("看清")
                || normalizedStem.contains("分清")
                || normalizedStem.contains("搞明白")
                || strongTopicStem)) {
            return stem;
        }
        if ((normalizedClause.contains("大家") || normalizedClause.contains("常买"))
                && (normalizedStem.contains("大家") || normalizedStem.contains("常见") || normalizedStem.contains("常买") || normalizedStem.contains("更常被选") || strongTopicStem)) {
            return stem;
        }
        if ((normalizedClause.contains("普通") || normalizedClause.contains("大众"))
                && (normalizedStem.contains("普通")
                || normalizedStem.contains("大众")
                || normalizedStem.contains("不一样")
                || normalizedStem.contains("特别")
                || normalizedStem.contains("没那么")
                || strongTopicStem)) {
            return stem;
        }
        if ((normalizedClause.contains("省心") || normalizedClause.contains("费事"))
                && (normalizedStem.contains("省心") || normalizedStem.contains("省事") || normalizedStem.contains("轻松") || strongTopicStem)) {
            return stem;
        }
        if (normalizedClause.contains("踩坑")
                && (normalizedStem.contains("踩坑") || normalizedStem.contains("稳") || normalizedStem.contains("靠谱") || normalizedStem.contains("放心") || strongTopicStem)) {
            return stem;
        }
        if (normalizedClause.contains("买错")
                && (normalizedStem.contains("买错")
                || normalizedStem.contains("出问题")
                || normalizedStem.contains("稳")
                || normalizedStem.contains("稳妥")
                || normalizedStem.contains("靠谱")
                || strongTopicStem)) {
            return stem;
        }
        return stem + supportClause;
    }

    private boolean containsStrongTopicStem(String normalizedStem) {
        if (!StringUtils.hasText(normalizedStem)) {
            return false;
        }
        return normalizedStem.contains("官网")
                || normalizedStem.contains("可信")
                || normalizedStem.contains("靠不靠谱")
                || normalizedStem.contains("口碑")
                || normalizedStem.contains("优缺点")
                || normalizedStem.contains("值不值")
                || normalizedStem.contains("性价比")
                || normalizedStem.contains("适合什么人")
                || normalizedStem.contains("什么场景")
                || normalizedStem.contains("第一次挑")
                || normalizedStem.contains("先从哪几个点")
                || normalizedStem.contains("容量")
                || normalizedStem.contains("空间适配")
                || normalizedStem.contains("主动推荐")
                || normalizedStem.contains("替代")
                || normalizedStem.contains("顾虑")
                || normalizedStem.contains("劝退")
                || normalizedStem.contains("决策点");
    }

    private String selectContextualSubject(String anchor,
                                           ProbeExposurePolicy exposurePolicy,
                                           String brandName,
                                           EngineTaskContextPackage contextPackage) {
        if (contextPackage == null) {
            return null;
        }
        List<String> descriptiveCues = contextPackage.slotValuesForUse(
                MinusOneContextSlots.DESCRIPTIVE_CUE,
                MinusOneContextUses.NODE2_REALIZATION,
                3
        );
        for (String descriptiveCue : descriptiveCues) {
            if (!StringUtils.hasText(descriptiveCue)) {
                continue;
            }
            if (exposurePolicy == ProbeExposurePolicy.BRAND_HIDDEN
                    && StringUtils.hasText(brandName)
                    && normalize(descriptiveCue).contains(normalize(brandName))) {
                continue;
            }
            if (!containsAnchorCue(descriptiveCue, anchor, contextPackage.categoryAnchors())) {
                continue;
            }
            if (isTaxonomyCue(descriptiveCue, contextPackage.category())) {
                continue;
            }
            return descriptiveCue;
        }
        return null;
    }

    private boolean containsAnchorCue(String text,
                                      String anchor,
                                      List<String> categoryAnchors) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalizedText = normalize(text);
        if (StringUtils.hasText(anchor) && normalizedText.contains(normalize(anchor))) {
            return true;
        }
        if (categoryAnchors == null || categoryAnchors.isEmpty()) {
            return false;
        }
        for (String categoryAnchor : categoryAnchors) {
            if (StringUtils.hasText(categoryAnchor) && normalizedText.contains(normalize(categoryAnchor))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaxonomyCue(String text, String category) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        String normalizedText = normalize(text);
        if (StringUtils.hasText(category) && normalizedText.equals(normalize(category))) {
            return true;
        }
        return normalizedText.endsWith("品牌")
                || normalizedText.endsWith("品类")
                || normalizedText.endsWith("类别")
                || normalizedText.endsWith("类型")
                || normalizedText.endsWith("系列")
                || normalizedText.endsWith("产品")
                || normalizedText.endsWith("商品")
                || normalizedText.endsWith("型号")
                || normalizedText.endsWith("款式")
                || normalizedText.endsWith("选购");
    }

    private String fallbackBrowseConfirmationStem(String subject,
                                                  ProbeExposurePolicy exposurePolicy,
                                                  String conversationMode,
                                                  String d3) {
        if ("identity_freedom".equals(d3)) {
            if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
                return switch (conversationMode) {
                    case "constraint_driven_query" -> "想先看看没那么大众的，" + subject + "值不值得先看";
                    case "back_and_forth_consultation" -> "想先看看别太大众的，" + subject + "适不适合先放进来比";
                    default -> "先看看没那么大众的，" + subject + "值不值得先留意";
                };
            }
            return switch (conversationMode) {
                case "constraint_driven_query" -> "想先看看没那么大众的，" + subject + "里哪个更值得先看";
                case "back_and_forth_consultation" -> "想先看看别太大众的，" + subject + "先从哪个看起";
                default -> "先看看没那么大众的，" + subject + "有哪些值得先看";
            };
        }
        return subject + "这样选稳不稳";
    }

    private String fallbackConfirmAngleStem(String subject,
                                            ProbeExposurePolicy exposurePolicy,
                                            String conversationMode,
                                            String d1,
                                            String d3,
                                            String d5) {
        String visibleLead = visibleConfirmLead(d1, subject);
        String hiddenLead = hiddenConfirmLead(d1, subject);
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想买个没那么大众的，" + subject + "放进来比会不会更合适";
                case "affection_belonging_participation" -> visibleLead + "，先看大家一般会先拿哪个";
                case "understanding_competence" -> visibleLead + "，差别主要比哪儿会更好定";
                case "creation_idleness" -> "想省点事买" + subject + "的话，先看会不会更省心";
                default -> "constraint_driven_query".equals(conversationMode) || "anticipatory_constraint".equals(d5)
                        ? visibleLead + "，更该比哪一点才不容易出问题"
                        : visibleLead + "，主要比哪一点会更稳";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想从" + subject + "里定一个不那么大众的，哪个会更合适";
            case "affection_belonging_participation" -> hiddenLead + "，先看大家一般会先拿哪个";
            case "understanding_competence" -> hiddenLead + "，差别主要比哪儿会更好定";
            case "creation_idleness" -> "想从" + subject + "里定一个，先看哪个更省心";
            default -> "constraint_driven_query".equals(conversationMode) || "anticipatory_constraint".equals(d5)
                    ? hiddenLead + "，更该比哪一点才不容易出问题"
                    : hiddenLead + "，主要比哪一点会更稳";
        };
    }

    private String fallbackSeekAngleStem(String subject,
                                         ProbeExposurePolicy exposurePolicy,
                                         String conversationMode,
                                         String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个没那么大众的，" + subject + "值得先看吗";
                case "affection_belonging_participation" -> "按大家常选的看，" + subject + "值得先看吗";
                case "understanding_competence" -> "想先比清楚的话，" + subject + "值得先看吗，差别主要在哪";
                case "creation_idleness" -> "想省点事的话，" + subject + "值得先看吗，会不会更省心";
                default -> "constraint_driven_query".equals(conversationMode)
                        ? "只想先挑个稳一点的，" + subject + "值得先看吗，会不会更不容易出问题"
                        : "想先挑个稳一点的，" + subject + "值得先看吗，会不会更不容易出问题";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想挑个不那么大众的，" + subject + "里先朝哪个方向看会更好挑";
            case "affection_belonging_participation" -> "按大家常选的来挑，" + subject + "里先朝哪个方向看会更合适";
            case "understanding_competence" -> "想先比清楚的话，" + subject + "里差别主要该往哪儿看会更好判断";
            case "creation_idleness" -> "想省点事的话，" + subject + "里先朝哪个方向挑会更省心";
            default -> "constraint_driven_query".equals(conversationMode)
                    ? "只想先挑个稳一点的，" + subject + "里先朝哪个方向比，更不容易出问题"
                    : "想先挑个稳一点的，" + subject + "里先朝哪个方向比，更不容易出问题";
        };
    }

    private String fallbackConfirmBrowseStem(String subject,
                                             ProbeExposurePolicy exposurePolicy,
                                             String conversationMode,
                                             String d1,
                                             String d3) {
        return fallbackConfirmBrowseStem(subject, exposurePolicy, conversationMode, d1, d3, "");
    }

    private String fallbackConfirmBrowseStem(String subject,
                                             ProbeExposurePolicy exposurePolicy,
                                             String conversationMode,
                                             String d1,
                                             String d3,
                                             String d5) {
        String visibleLead = visibleConfirmLead(d1, subject);
        String hiddenLead = hiddenConfirmLead(d1, subject);
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想买个没那么大众的，" + subject + "放进来比会不会更合适";
                case "affection_belonging_participation" -> visibleLead + "，先看大家通常会先拿哪个";
                case "understanding_competence" -> visibleLead + "，差别先从哪个方向比更容易看清";
                case "creation_idleness" -> "想省点事买" + subject + "的话，先看会不会更省心";
                default -> "constraint_driven_query".equals(conversationMode)
                        ? "acute_emergency".equals(d5)
                        ? visibleLead + "，先朝哪个方向比更快定下来"
                        : "reflective".equals(d5)
                        ? visibleLead + "，主要往哪个方向比会更稳"
                        : visibleLead + "，先朝哪个方向比更不容易出问题"
                        : "acute_emergency".equals(d5)
                        ? visibleLead + "，先朝哪个方向比更快定下来"
                        : "reflective".equals(d5)
                        ? visibleLead + "，主要往哪个方向比会更稳"
                        : visibleLead + "，先朝哪个方向比更不容易出问题";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想从" + subject + "里定一个不那么大众的，哪个会更合适";
            case "affection_belonging_participation" -> hiddenLead + "，先看大家通常会先拿哪个";
            case "understanding_competence" -> hiddenLead + "，差别先从哪个方向比更容易看清";
            case "creation_idleness" -> "想从" + subject + "里定一个，先看哪个更省心";
            default -> "constraint_driven_query".equals(conversationMode)
                    ? "acute_emergency".equals(d5)
                    ? hiddenLead + "，先朝哪个方向比更快定下来"
                    : "reflective".equals(d5)
                    ? hiddenLead + "，主要往哪个方向比会更稳"
                    : hiddenLead + "，先朝哪个方向比更不容易出问题"
                    : "acute_emergency".equals(d5)
                    ? hiddenLead + "，先朝哪个方向比更快定下来"
                    : "reflective".equals(d5)
                    ? hiddenLead + "，主要往哪个方向比会更稳"
                    : hiddenLead + "，先朝哪个方向比更不容易出问题";
        };
    }

    private String fallbackSeekBrowseStem(String subject,
                                          ProbeExposurePolicy exposurePolicy,
                                          String conversationMode,
                                          String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个没那么大众的，" + subject + "先看值不值得选";
                case "affection_belonging_participation" -> "按大家常选的看，" + subject + "先看值不值得选";
                case "understanding_competence" -> "想先比清楚的话，" + subject + "先看差别主要在哪";
                case "creation_idleness" -> "想省点事的话，" + subject + "先看会不会更省心";
                default -> "constraint_driven_query".equals(conversationMode)
                        ? "只想先挑个稳一点的，" + subject + "先看会不会更不容易出问题"
                        : "想先挑个稳一点的，" + subject + "先看会不会更不容易出问题";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想挑个不那么大众的，" + subject + "里先从哪个方向挑会更合适";
            case "affection_belonging_participation" -> "按大家常选的看，" + subject + "里先从哪个方向看会更合适";
            case "understanding_competence" -> "想先比清楚的话，" + subject + "里先从哪个方向比，差别会更好分辨";
            case "creation_idleness" -> "想省点事的话，" + subject + "里先从哪个方向挑更省心";
            default -> "constraint_driven_query".equals(conversationMode)
                    ? "只想先挑个稳一点的，" + subject + "里先从哪个方向比，会更不容易出问题"
                    : "想先挑个稳一点的，" + subject + "里先从哪个方向比，会更不容易出问题";
        };
    }

    private String fallbackSeekClearAnswerStem(String subject,
                                               ProbeExposurePolicy exposurePolicy,
                                               String conversationMode,
                                               String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个没那么大众的，" + subject + "值得先看吗";
                case "affection_belonging_participation" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "大家常买的那类里，" + subject + "值得先看吗";
                    case "constraint_driven_query" -> "如果按大家常买的来挑，" + subject + "值得先看吗";
                    default -> subject + "值得先看吗，也算大家常选的那类";
                };
                case "understanding_competence" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想认真比一比的话，" + subject + "值得先看吗";
                    case "constraint_driven_query" -> "如果想先比清楚，" + subject + "值得先看吗";
                    default -> subject + "值得先看吗";
                };
                case "creation_idleness" -> "想省点事的话，" + subject + "值得先看吗";
                default -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "现在想先挑个稳一点的，" + subject + "值得先看吗";
                    case "constraint_driven_query" -> "只想挑个稳一点的，" + subject + "值得先看吗";
                    default -> subject + "值得先看吗";
                };
            };
        }
        return switch (d3) {
            case "identity_freedom" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想挑个不那么大众的，" + subject + "里哪个更值得先看";
                case "constraint_driven_query" -> "不想太大众的话，" + subject + "里哪个更值得先看";
                default -> "想挑个不那么大众的，" + subject + "里哪个更值得先看";
            };
            case "affection_belonging_participation" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "按大家常买的来挑，" + subject + "里哪个更值得先看";
                case "constraint_driven_query" -> "如果就按大家常买的来挑，" + subject + "里哪个更值得先看";
                default -> subject + "里哪个更值得先看，也更像大家常选的";
            };
            case "subsistence_protection" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想先挑个稳一点的，" + subject + "里哪个更不容易出问题";
                case "constraint_driven_query" -> "只想先挑个稳一点的，" + subject + "里哪个更不容易出问题";
                default -> subject + "里哪个更不容易出问题";
            };
            case "understanding_competence" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想先弄清差别的话，" + subject + "该先把哪块差别看清";
                case "constraint_driven_query" -> "如果想先比清楚，" + subject + "该先把哪块区别看清会更好判断";
                default -> subject + "该先把哪块区别看清会更好分辨";
            };
            case "creation_idleness" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想省点事的话，" + subject + "里哪个更省心";
                case "constraint_driven_query" -> "只想省点事的话，" + subject + "里哪个更省心";
                default -> subject + "里哪个更省心";
            };
            default -> switch (conversationMode) {
                case "back_and_forth_consultation" -> subject + "里哪个会更稳";
                case "constraint_driven_query" -> "只想挑个稳一点的，" + subject + "里哪个会更稳";
                default -> subject + "里哪个会更稳";
            };
        };
    }

    private String fallbackConfirmAndBuyStem(String subject,
                                             ProbeExposurePolicy exposurePolicy,
                                             String conversationMode,
                                             String interactionPosture,
                                             String d1,
                                             String d3,
                                             String d5) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个不那么大众的，" + subject + "值得直接拿吗";
                case "subsistence_protection" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> switch (d5) {
                        case "reflective" -> "真要买" + subject + "，先卡住哪一点更稳妥";
                        case "anticipatory_constraint" -> "准备买" + subject + "时，优先看住哪一点更稳妥";
                        default -> "真要买" + subject + "，先看住哪一点更稳妥";
                    };
                    case "constraint_driven_query" -> switch (d5) {
                        case "acute_emergency" -> "想尽快买" + subject + "，先卡哪一点更稳妥";
                        case "anticipatory_constraint" -> "准备买" + subject + "时，优先看住哪一点更稳妥";
                        default -> "真要买" + subject + "，先卡哪一点更稳妥";
                    };
                    default -> "真要买" + subject + "，先看住哪一点更稳妥";
                };
                case "understanding_competence" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> visibleConfirmLead(d1, subject) + "，先看清哪两块差别会更好定";
                    case "constraint_driven_query" -> "想尽快买" + subject + "，先看清哪块区别最关键";
                    default -> visibleConfirmLead(d1, subject) + "，先看清哪块区别最关键";
                };
                case "creation_idleness" -> "想省点事的话，" + subject + "直接拿行不行";
                default -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "真要买" + subject + "，先卡哪一点会更稳妥";
                    case "constraint_driven_query" -> "想尽快买" + subject + "，先卡哪一点会更稳妥";
                    default -> "真要买" + subject + "，先看住哪一点会更稳妥";
                };
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想买个不那么大众的，" + subject + "里哪个更值得先看";
            case "affection_belonging_participation" -> "想从" + subject + "里定一个，大家更常先拿哪个";
            case "understanding_competence" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> switch (interactionPosture) {
                    case "stressed_urgent" -> hiddenConfirmLead(d1, subject) + "，先看清哪两块差别会更好定";
                    case "deliberate_structuring" -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别最关键";
                    default -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别更关键";
                };
                case "constraint_driven_query" -> switch (d5) {
                    case "acute_emergency" -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别最关键";
                    case "anticipatory_constraint" -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别最关键";
                    default -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别更关键";
                };
                default -> hiddenConfirmLead(d1, subject) + "，先看清哪块区别最关键";
            };
            case "creation_idleness" -> "想省点事直接拿，" + subject + "里哪个更省心";
            case "subsistence_protection" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> switch (d5) {
                    case "acute_emergency" -> "真要从" + subject + "里定一个，先卡哪一点更稳妥";
                    case "anticipatory_constraint" -> "准备在" + subject + "里定一个时，先看住哪一点更稳妥";
                    default -> switch (interactionPosture) {
                        case "deliberate_structuring" -> "真要从" + subject + "里定一个，先核住哪一点更稳妥";
                        default -> "真要从" + subject + "里定一个，先看住哪一点更稳妥";
                    };
                };
                case "constraint_driven_query" -> switch (d5) {
                    case "reflective" -> "准备在" + subject + "里定一个时，先核住哪一点更稳妥";
                    case "anticipatory_constraint" -> "准备在" + subject + "里定一个时，先看住哪一点更稳妥";
                    default -> "想尽快从" + subject + "里定一个，先卡哪一点更稳妥";
                };
                default -> "真要从" + subject + "里定一个，先看住哪一点更稳妥";
            };
            default -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "真要从" + subject + "里定一个，先卡哪一点更稳妥";
                case "constraint_driven_query" -> "想尽快从" + subject + "里定一个，先卡哪一点更稳妥";
                default -> "真要从" + subject + "里定一个，先看住哪一点更稳妥";
            };
        };
    }

    private String fallbackSearchShortcutStem(String subject,
                                              ProbeExposurePolicy exposurePolicy,
                                              String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> "想挑个没那么大众的，" + subject + "值得先看吗";
                case "understanding_competence" -> subject + "值得先看吗";
                default -> subject + "值得先看吗";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> "想挑个没那么大众的，" + subject + "里哪个更值得先看";
            case "understanding_competence" -> subject + "该先把哪块区别看清会更好分辨";
            case "creation_idleness" -> subject + "里哪个更省心";
            default -> subject + "里哪个更靠谱";
        };
    }

    private String fallbackConfirmAndBuyShortStem(String subject,
                                                  ProbeExposurePolicy exposurePolicy,
                                                  String conversationMode,
                                                  String interactionPosture,
                                                  String d1,
                                                  String d3,
                                                  String d5) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想挑个不那么大众的，" + subject + "值得先看吗";
                    case "constraint_driven_query" -> "想买" + subject + "，值不值得先放进来比";
                    default -> subject + "值得先看吗，会不会太大众";
                };
                case "subsistence_protection" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想买" + subject + "，先卡哪一点更稳妥";
                    case "constraint_driven_query" -> "想买" + subject + "，先卡哪一点更稳妥";
                    default -> "买" + subject + "时先看住哪一点更稳妥";
                };
                case "creation_idleness" -> "想省点事，" + subject + "直接拿行不行";
                case "understanding_competence" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想买" + subject + "，先看清哪两块差别";
                    case "constraint_driven_query" -> "想买" + subject + "，先看清哪块区别";
                    default -> "买" + subject + "时先看清哪块区别";
                };
                default -> "constraint_driven_query".equals(conversationMode)
                        ? "想买" + subject + "，值不值得直接拿"
                        : subject + "值得直接拿吗";
            };
        }
        return switch (d3) {
            case "affection_belonging_participation" -> "真要从" + subject + "里定一个，大家通常会先拿哪个";
            case "identity_freedom" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想买个不那么大众的，" + subject + "里哪个更值得直接拿";
                case "constraint_driven_query" -> "想买个不那么大众的，" + subject + "里哪个更值得直接拿";
                default -> "想买个不那么大众的，" + subject + "里哪个更值得直接拿";
            };
            case "understanding_competence" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "从" + subject + "里定一个，先看清哪两块差别";
                case "constraint_driven_query" -> "想从" + subject + "里定一个，先看清哪块区别";
                default -> "从" + subject + "里定一个，先看清哪块区别";
            };
            case "creation_idleness" -> "想省点事，" + subject + "里哪个更省心";
            case "subsistence_protection" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> switch (d5) {
                    case "acute_emergency" -> "从" + subject + "里定一个，先卡哪一点更稳";
                    case "anticipatory_constraint" -> "准备从" + subject + "里定一个，先看住哪一点更稳妥";
                    default -> switch (interactionPosture) {
                        case "deliberate_structuring" -> "从" + subject + "里定一个，先核哪一点更稳";
                        default -> "从" + subject + "里定一个，先看住哪一点更稳妥";
                    };
                };
                case "constraint_driven_query" -> "reflective".equals(d5)
                        ? "准备从" + subject + "里定一个，先核哪一点更稳"
                        : "想从" + subject + "里定一个，先看住哪一点更稳妥";
                default -> "从" + subject + "里定一个，先看住哪一点更稳妥";
            };
            default -> subject + "值得直接拿吗";
        };
    }

    private String fallbackActionableCoreStem(String subject,
                                              ProbeExposurePolicy exposurePolicy,
                                              String conversationMode,
                                              String d3,
                                              String d5) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (conversationMode) {
                case "back_and_forth_consultation" -> switch (d3) {
                    case "understanding_competence" -> "如果现在想判断" + subject + "值不值得选，该从哪几块差别看起会更有用";
                    case "identity_freedom" -> "acute_emergency".equals(d5)
                            ? "现在想快点定个不太大众的，" + subject + "先从哪几处看起"
                            : "如果现在想判断" + subject + "值不值得留意，先从哪几处看起才不太大众";
                    case "creation_idleness" -> "想判断" + subject + "适不适合直接拿，该从哪几步看起会更省事";
                    default -> "anticipatory_constraint".equals(d5)
                            ? "如果现在想判断" + subject + "适不适合，先按哪几步筛再定"
                            : "如果现在想判断" + subject + "适不适合，先从哪几步筛起";
                };
                case "constraint_driven_query" -> switch (d3) {
                    case "understanding_competence" -> "想判断" + subject + "适不适合，该从哪几块差别看起会更关键";
                    case "identity_freedom" -> "acute_emergency".equals(d5)
                            ? "现在想快点定个不太大众的，" + subject + "先从哪几处看起"
                            : "想判断" + subject + "会不会太大众，先从哪几处看起";
                    case "creation_idleness" -> "想判断" + subject + "适不适合直接拿，先从哪几步看起会更省事";
                    default -> "anticipatory_constraint".equals(d5)
                            ? "想定" + subject + "的话，先按哪几步筛再决定更稳"
                            : "想快点判断" + subject + "适不适合，先从哪几步看起";
                };
                default -> switch (d3) {
                    case "understanding_competence" -> subject + "该从哪几块差别看起会更有用";
                    case "identity_freedom" -> "acute_emergency".equals(d5)
                            ? "现在想快点定个不太普通的，" + subject + "先从哪几处看起"
                            : subject + "先从哪几处看起才不太普通";
                    case "creation_idleness" -> subject + "先按哪几步看会更省事";
                    default -> "anticipatory_constraint".equals(d5)
                            ? subject + "先按哪几步筛再定会更稳"
                            : subject + "先从哪几步看起不容易买错";
                };
            };
        }
        return switch (conversationMode) {
            case "back_and_forth_consultation" -> switch (d3) {
                case "understanding_competence" -> "如果现在挑" + subject + "，该从哪几块差别看起才好判断";
                case "identity_freedom" -> "acute_emergency".equals(d5)
                        ? "现在想快点定个不太普通的，" + subject + "先从哪几处看起"
                        : "如果现在挑" + subject + "，先从哪几处看起才没那么普通";
                case "creation_idleness" -> "如果现在挑" + subject + "，先按哪几步筛会更省心";
                default -> "anticipatory_constraint".equals(d5)
                        ? "如果现在挑" + subject + "，先按哪几步筛再定"
                        : "如果现在挑" + subject + "，先从哪几步筛起";
            };
            case "constraint_driven_query" -> switch (d3) {
                case "understanding_competence" -> "想筛" + subject + "，该从哪几块差别看起会更好判断";
                case "identity_freedom" -> "acute_emergency".equals(d5)
                        ? "现在想快点定个不太普通的，" + subject + "先从哪几处看起"
                        : "想筛" + subject + "，先从哪几处看起才不太普通";
                case "creation_idleness" -> "想筛" + subject + "，先按哪几步走会更省心";
                default -> "anticipatory_constraint".equals(d5)
                        ? "想从" + subject + "里定一个，先按哪几步筛再定更稳"
                        : "想从" + subject + "里定一个，先从哪几步看起";
            };
            default -> switch (d3) {
                case "understanding_competence" -> subject + "该从哪几块差别看起会更容易看懂";
                case "identity_freedom" -> "acute_emergency".equals(d5)
                        ? "现在想快点定个不太普通的，" + subject + "先从哪几处看起"
                        : subject + "先从哪几处看起才不太普通";
                case "creation_idleness" -> subject + "先按哪几步走会更省心";
                default -> "anticipatory_constraint".equals(d5)
                        ? subject + "先按哪几步筛再定更稳"
                        : subject + "先从哪几步看起不容易买错";
            };
        };
    }

    private String fallbackConstraintAngleStem(String subject, String d3, String d5) {
        if ("acute_emergency".equals(d5)) {
            return switch (d3) {
                case "understanding_competence" -> subject + "先卡哪几块差别会更快判断";
                case "identity_freedom" -> subject + "先卡哪几处会不那么普通";
                case "creation_idleness" -> subject + "先按哪几步走会更省心";
                default -> subject + "先卡哪几点会更稳妥";
            };
        }
        if ("anticipatory_constraint".equals(d5)) {
            return switch (d3) {
                case "understanding_competence" -> subject + "先把哪几块差别看明白再定会更好判断";
                case "identity_freedom" -> subject + "先从哪几处看起再定不容易太普通";
                case "creation_idleness" -> subject + "先按哪几步走再定会更省心";
                default -> subject + "先从哪几点看起再定会更稳妥";
            };
        }
        return switch (d3) {
            case "understanding_competence" -> subject + "主要该把哪几块差别看清会更好判断";
            case "identity_freedom" -> subject + "主要该从哪几处看起才不太普通";
            case "creation_idleness" -> subject + "主要该按哪几步走会更省心";
            default -> subject + "主要该卡住哪几点会更稳妥";
        };
    }

    private String fallbackActionStepShortStem(String subject,
                                               ProbeExposurePolicy exposurePolicy,
                                               String conversationMode,
                                               String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (conversationMode) {
                case "back_and_forth_consultation" -> switch (d3) {
                    case "creation_idleness" -> "想判断" + subject + "适不适合直接拿，该从哪几步看起会更省事";
                    case "understanding_competence" -> "如果现在挑" + subject + "，该从哪几块差别入手会更好判断";
                    case "subsistence_protection" -> "如果现在挑" + subject + "，该按什么思路看才不容易买错";
                    default -> "如果现在挑" + subject + "，该从哪几处入手";
                };
                case "constraint_driven_query" -> switch (d3) {
                    case "creation_idleness" -> "要判断" + subject + "适不适合直接拿，先从哪几步看起会更省事";
                    case "understanding_competence" -> "要挑" + subject + "，该从哪几块差别入手会更快";
                    case "subsistence_protection" -> "要挑" + subject + "，该按什么思路看才不容易买错";
                    default -> "要判断" + subject + "适不适合，先从哪几步入手";
                };
                default -> "subsistence_protection".equals(d3)
                        ? subject + "该按什么思路挑才不容易买错"
                        : subject + "该按什么思路挑";
            };
        }
        return switch (d3) {
            case "understanding_competence" -> subject + "先从哪几块差别入手更容易看懂";
            case "identity_freedom" -> subject + "先从哪点看起更不容易撞款";
            case "subsistence_protection" -> "single_turn_search_like".equals(conversationMode)
                    ? subject + "该按什么思路挑才更不容易买错"
                    : "想从" + subject + "里挑一个，先按什么顺序看更不容易买错";
            default -> "single_turn_search_like".equals(conversationMode)
                    ? subject + "该按什么思路挑"
                    : "想从" + subject + "里挑一个，先从哪几步入手";
        };
    }

    private String fallbackSeekClearAnswerShortStem(String subject,
                                                    ProbeExposurePolicy exposurePolicy,
                                                    String conversationMode,
                                                    String d3) {
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE) {
            return switch (d3) {
                case "identity_freedom" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想挑个不那么大众的，" + subject + "值得先看吗";
                    case "constraint_driven_query" -> subject + "先看值不值得选，会不会太大众";
                    default -> subject + "值得先看吗，想挑个不那么大众的";
                };
                case "subsistence_protection" -> switch (conversationMode) {
                    case "back_and_forth_consultation" -> "想先挑个稳一点的，" + subject + "值不值得先看";
                    case "constraint_driven_query" -> "只想先挑个稳一点的，" + subject + "值不值得先看";
                    default -> subject + "值不值得先看，会不会更不容易出问题";
                };
                case "understanding_competence" -> subject + "值得先看吗";
                default -> "back_and_forth_consultation".equals(conversationMode)
                        ? subject + "值得先看吗"
                        : subject + "值得先看吗";
            };
        }
        return switch (d3) {
            case "identity_freedom" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想挑个不那么大众的，" + subject + "里哪个更值得先看";
                case "constraint_driven_query" -> subject + "里哪个更值得先看，也没那么大众";
                default -> subject + "里想挑个不那么大众的，哪个更值得先看";
            };
            case "affection_belonging_participation" -> subject + "里哪个更值得先看，也更像大家常选的";
            case "subsistence_protection" -> switch (conversationMode) {
                case "back_and_forth_consultation" -> "想先挑个稳一点的，先看" + subject + "里哪个更不容易出问题";
                case "constraint_driven_query" -> subject + "里哪个更不容易出问题";
                default -> subject + "里哪个更不容易出问题";
            };
            case "creation_idleness" -> subject + "里哪个更省心";
            default -> subject + "里哪个会更稳";
        };
    }

    private String visibleConfirmLead(String d1, String subject) {
        return switch (d1) {
            case "initiation" -> "刚开始考虑要不要买" + subject;
            case "formulation" -> "临到要买" + subject + "时";
            case "verification" -> "最后准备买" + subject + "时";
            default -> "真要买" + subject;
        };
    }

    private String hiddenConfirmLead(String d1, String subject) {
        return switch (d1) {
            case "initiation" -> "刚开始想从" + subject + "里定一个";
            case "formulation" -> "临到要从" + subject + "里定一个时";
            case "verification" -> "最后准备从" + subject + "里定一个时";
            default -> "真要从" + subject + "里定一个";
        };
    }

    private String semanticCode(RealizationPlan realizationPlan, String dimensionId) {
        if (realizationPlan == null || !StringUtils.hasText(dimensionId)) {
            return "";
        }
        return realizationPlan.realizationConstraints().mustExpressSemantics().stream()
                .filter(StringUtils::hasText)
                .map(semantic -> semantic.split(":", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> dimensionId.equalsIgnoreCase(parts[0]))
                .map(parts -> parts[1])
                .findFirst()
                .orElse("");
    }

    private String stateCode(RealizationPlan realizationPlan, String dimensionId) {
        String mustCode = semanticCode(realizationPlan, dimensionId);
        if (StringUtils.hasText(mustCode)) {
            return mustCode;
        }
        if (realizationPlan == null
                || realizationPlan.realizationConstraints() == null
                || !StringUtils.hasText(dimensionId)) {
            return "";
        }
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .filter(StringUtils::hasText)
                .map(hint -> hint.split(":", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> dimensionId.equalsIgnoreCase(parts[0]))
                .map(parts -> parts[1].split("=", 2)[0])
                .findFirst()
                .orElse("");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+", "")
                .toLowerCase();
    }
}

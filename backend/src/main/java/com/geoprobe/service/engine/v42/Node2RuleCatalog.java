package com.geoprobe.service.engine.v42;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class Node2RuleCatalog {

    static final String VERSION = "node2-rule-catalog-v1";

    static final String ORIGIN_BOUNDARY = "boundary";

    static final String BOUNDARY_TYPE_STATE_TRANSLATION = "state_translation";
    static final String BOUNDARY_TYPE_TEMPLATE_BUCKET = "template_bucket";
    static final String BOUNDARY_TYPE_QUALITY = "quality";
    static final String BOUNDARY_TYPE_FALLBACK_ROUTING = "fallback_routing";

    static final String RULE_M1 = "M1";
    static final String RULE_M2 = "M2";
    static final String RULE_M3 = "M3";
    static final String RULE_M4 = "M4";
    static final String RULE_S1 = "S1";
    static final String RULE_S2 = "S2";
    static final String RULE_S3 = "S3";

    static final String RULE_Q_EMPTY = "Q_EMPTY";
    static final String RULE_Q_LENGTH = "Q_LENGTH";
    static final String RULE_Q_BANNED_OR_PLACEHOLDER = "Q_BANNED_OR_PLACEHOLDER";
    static final String RULE_Q_LITERALIZATION = "Q_LITERALIZATION";
    static final String RULE_Q_ANCHOR = "Q_ANCHOR";
    static final String RULE_Q_BRAND_HIDDEN_EXPOSED = "Q_BRAND_HIDDEN_EXPOSED";
    static final String RULE_Q_BRAND_VISIBLE_MISSING = "Q_BRAND_VISIBLE_MISSING";
    static final String RULE_Q_OFFICIAL_CUE = "Q_OFFICIAL_CUE";
    static final String RULE_Q_SEMANTIC = "Q_SEMANTIC";
    static final String RULE_Q_DUPLICATE = "Q_DUPLICATE";
    static final String RULE_Q_STRUCTURAL_DUPLICATE = "Q_STRUCTURAL_DUPLICATE";
    static final String RULE_Q_MECHANICAL = "Q_MECHANICAL";
    static final String RULE_Q_AWKWARD = "Q_AWKWARD";
    static final String RULE_Q_UNKNOWN = "Q_UNKNOWN";

    static final String RULE_B_SURFACE_BROWSE = "B_SURFACE_BROWSE";
    static final String RULE_B_SURFACE_CONFIRM_BUY = "B_SURFACE_CONFIRM_BUY";
    static final String RULE_B_SURFACE_ACTION_STEPS = "B_SURFACE_ACTION_STEPS";
    static final String RULE_B_SURFACE_CLEAR_ANSWER = "B_SURFACE_CLEAR_ANSWER";
    static final String RULE_B_RETRY_SEMANTIC = "B_RETRY_SEMANTIC";
    static final String RULE_B_RETRY_ANCHOR = "B_RETRY_ANCHOR";
    static final String RULE_B_RETRY_BRAND_VISIBLE = "B_RETRY_BRAND_VISIBLE";
    static final String RULE_B_RETRY_MECHANICAL = "B_RETRY_MECHANICAL";
    static final String RULE_B_RETRY_AWKWARD = "B_RETRY_AWKWARD";

    static final String RULE_FB_RENDER_EXCEPTION = "FB_RENDER_EXCEPTION";
    static final String RULE_FB_CONCURRENT_FAILURE = "FB_CONCURRENT_FAILURE";
    static final String RULE_FB_TOKEN_BUDGET = "FB_TOKEN_BUDGET";
    static final String RULE_FB_TEMPLATE_VALIDATION = "FB_TEMPLATE_VALIDATION";
    static final String RULE_FAMILY_ORTHOGONAL_CORE = "FAMILY_ORTHOGONAL_CORE";
    static final String RULE_FAMILY_ORTHOGONAL_ANGLE = "FAMILY_ORTHOGONAL_ANGLE";
    static final String RULE_FAMILY_ORTHOGONAL_BROWSE = "FAMILY_ORTHOGONAL_BROWSE";
    static final String RULE_FAMILY_ORTHOGONAL_SHORT = "FAMILY_ORTHOGONAL_SHORT";
    static final String RULE_FAMILY_PARETO_TEMPLATE = "FAMILY_PARETO_TEMPLATE";
    static final String RULE_FAMILY_ADVERSARIAL_TEMPLATE = "FAMILY_ADVERSARIAL_TEMPLATE";

    private static final Map<String, RuleEntry> ENTRIES = buildEntries();

    private Node2RuleCatalog() {
    }

    static Map<String, Object> boundaryDetail(String ruleId,
                                              String trigger,
                                              List<String> effect,
                                              String boundaryType) {
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("ruleId", ruleId);
        detail.put("trigger", trigger);
        detail.put("effect", effect == null ? List.of() : List.copyOf(effect));
        detail.put("ruleOrigin", ORIGIN_BOUNDARY);
        detail.put("boundaryType", boundaryType);
        return Map.copyOf(detail);
    }

    static String validationRuleId(String failedCheck) {
        if (failedCheck == null) {
            return RULE_Q_UNKNOWN;
        }
        return switch (failedCheck) {
            case "empty" -> RULE_Q_EMPTY;
            case "length" -> RULE_Q_LENGTH;
            case "banned_or_placeholder" -> RULE_Q_BANNED_OR_PLACEHOLDER;
            case "literalization" -> RULE_Q_LITERALIZATION;
            case "anchor" -> RULE_Q_ANCHOR;
            case "brand_hidden_exposed" -> RULE_Q_BRAND_HIDDEN_EXPOSED;
            case "brand_visible_missing" -> RULE_Q_BRAND_VISIBLE_MISSING;
            case "official_cue" -> RULE_Q_OFFICIAL_CUE;
            case "semantic" -> RULE_Q_SEMANTIC;
            case "duplicate" -> RULE_Q_DUPLICATE;
            case "structural_duplicate" -> RULE_Q_STRUCTURAL_DUPLICATE;
            case "mechanical" -> RULE_Q_MECHANICAL;
            case "awkward" -> RULE_Q_AWKWARD;
            default -> RULE_Q_UNKNOWN;
        };
    }

    static RuleEntry entry(String ruleId) {
        return ruleId == null ? null : ENTRIES.get(ruleId);
    }

    static Map<String, Object> snapshot(Iterable<String> ruleIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (ruleIds != null) {
            for (String ruleId : ruleIds) {
                if (ruleId != null && ENTRIES.containsKey(ruleId)) {
                    uniqueIds.add(ruleId);
                }
            }
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String ruleId : uniqueIds) {
            RuleEntry entry = ENTRIES.get(ruleId);
            if (entry != null) {
                entries.add(entry.toMap());
            }
        }
        return Map.of(
                "version", VERSION,
                "entries", List.copyOf(entries)
        );
    }

    static List<String> behaviorRuleIds(String surfaceIntent, List<String> failedChecks) {
        LinkedHashSet<String> ruleIds = new LinkedHashSet<>();
        if (surfaceIntent != null) {
            switch (surfaceIntent.trim()) {
                case "browse_for_options" -> ruleIds.add(RULE_B_SURFACE_BROWSE);
                case "confirm_and_buy" -> ruleIds.add(RULE_B_SURFACE_CONFIRM_BUY);
                case "ask_for_actionable_steps" -> ruleIds.add(RULE_B_SURFACE_ACTION_STEPS);
                case "seek_clear_answer" -> ruleIds.add(RULE_B_SURFACE_CLEAR_ANSWER);
                default -> {
                }
            }
        }
        if (failedChecks != null) {
            for (String failedCheck : failedChecks) {
                switch (failedCheck) {
                    case "semantic" -> ruleIds.add(RULE_B_RETRY_SEMANTIC);
                    case "anchor" -> ruleIds.add(RULE_B_RETRY_ANCHOR);
                    case "brand_visible_missing" -> ruleIds.add(RULE_B_RETRY_BRAND_VISIBLE);
                    case "mechanical" -> ruleIds.add(RULE_B_RETRY_MECHANICAL);
                    case "awkward" -> ruleIds.add(RULE_B_RETRY_AWKWARD);
                    default -> {
                    }
                }
            }
        }
        return List.copyOf(ruleIds);
    }

    static String fallbackFamilyRuleId(String family) {
        if (family == null) {
            return null;
        }
        return switch (family.trim()) {
            case "orthogonal_fallback_core" -> RULE_FAMILY_ORTHOGONAL_CORE;
            case "orthogonal_fallback_angle" -> RULE_FAMILY_ORTHOGONAL_ANGLE;
            case "orthogonal_fallback_browse" -> RULE_FAMILY_ORTHOGONAL_BROWSE;
            case "orthogonal_fallback_short" -> RULE_FAMILY_ORTHOGONAL_SHORT;
            case "pareto_template_fallback" -> RULE_FAMILY_PARETO_TEMPLATE;
            case "adversarial_template_fallback" -> RULE_FAMILY_ADVERSARIAL_TEMPLATE;
            default -> null;
        };
    }

    private static Map<String, RuleEntry> buildEntries() {
        LinkedHashMap<String, RuleEntry> entries = new LinkedHashMap<>();

        register(entries, RULE_M1, "重负荷口语降解规则", "ThreeLayerMappingEvaluator", "evaluateMustRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "将严重认知负荷映射为碎片化、启发式问法。",
                "当 CSSM-HI 状态翻译器被更高保真映射器替换时退役。", null);
        register(entries, RULE_M2, "急迫启动指令规则", "ThreeLayerMappingEvaluator", "evaluateMustRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "将急迫启动状态映射为更直接的提问张力。",
                "当状态空间不再通过 Java 规则翻译时退役。", null);
        register(entries, RULE_M3, "确认收口规则", "ThreeLayerMappingEvaluator", "evaluateMustRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "把确认阶段映射成更偏判断和决策收口的问法。",
                "当确认阶段由统一 prompt 资产显式表达时退役。", null);
        register(entries, RULE_M4, "对话缓冲规则", "ThreeLayerMappingEvaluator", "evaluateMustRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "将中负荷对话态翻译成更带条件感的表达。",
                "当 D4/D6 组合进入新的状态翻译器后退役。", null);
        register(entries, RULE_S1, "探索缓冲可选规则", "ThreeLayerMappingEvaluator", "evaluateShouldRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "探索态在一定概率下加入更温和的表达层。",
                "当 should 规则被统一策略器接管时退役。", null);
        register(entries, RULE_S2, "表述展开可选规则", "ThreeLayerMappingEvaluator", "evaluateShouldRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "最佳表述态可选择性增加信息展开。",
                "当 should 规则被统一策略器接管时退役。", null);
        register(entries, RULE_S3, "反思起手可选规则", "ThreeLayerMappingEvaluator", "evaluateShouldRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_STATE_TRANSLATION,
                "反思起手态可选择性加入能力条件式表达。",
                "当 should 规则被统一策略器接管时退役。", null);

        register(entries, RULE_Q_EMPTY, "空文本拦截", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "拒绝空输出，阻止无文本进入终稿。",
                "当上游模型合同保证非空且有独立结构化校验时退役。", null);
        register(entries, RULE_Q_LENGTH, "长度上限校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "限制 Probe 过长，避免像提示词或说明文。",
                "当长度约束完全前移到 prompt 资产 golden set 后可退役。", null);
        register(entries, RULE_Q_BANNED_OR_PLACEHOLDER, "禁词与占位符校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "拒绝内部术语、占位符等伪人话泄露。",
                "当模型输出合同与 golden set 足以稳定消灭此类泄露时退役。", null);
        register(entries, RULE_Q_LITERALIZATION, "状态直译校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "拦截把内部状态硬翻成表层句子的输出。",
                "当状态翻译质量稳定且历史回归为零时退役。", null);
        register(entries, RULE_Q_ANCHOR, "品类锚点校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "确保 Probe 里出现真实品类锚点，而不是空泛问法。",
                "当 Node2 的输入合同天然保证锚点显式出现时退役。", "anchor_repair");
        register(entries, RULE_Q_BRAND_HIDDEN_EXPOSED, "隐藏品牌泄露校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "在品牌隐藏模式下阻止品牌名外露。",
                "当品牌曝光策略被统一解耦到上游渲染器时退役。", null);
        register(entries, RULE_Q_BRAND_VISIBLE_MISSING, "可见品牌缺失校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "在允许提品牌时要求品牌自然出现一次。",
                "当品牌可见策略由 prompt 资产稳定兑现时退役。", "brand_repair");
        register(entries, RULE_Q_OFFICIAL_CUE, "官网来源对象词校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "当语义要求官网/官方/可信来源时，强制 Probe 显式保留这些对象词。",
                "当输入合同能稳定保证官网来源对象自然显式出现时退役。", null);
        register(entries, RULE_Q_SEMANTIC, "必达语义校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "确保问题表达出 realization plan 要求的语义。",
                "当 realization 约束被模型可靠内生遵守且 golden set 覆盖稳定时退役。", null);
        register(entries, RULE_Q_DUPLICATE, "文本去重校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "阻止同批任务出现同文案重复。",
                "当调度层天然保障 Probe 空间去重时退役。", null);
        register(entries, RULE_Q_STRUCTURAL_DUPLICATE, "结构近重复校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "阻止语义近似但句式几乎相同的重复 Probe。",
                "当更强的生成约束覆盖近重复风险时退役。", null);
        register(entries, RULE_Q_MECHANICAL, "机械腔校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "拦截明显像模板改写题的伪人话。",
                "当 prompt 资产与 golden set 已将机械腔压到可接受阈值后退役。", "mechanical_repair");
        register(entries, RULE_Q_AWKWARD, "拗口结构校验", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "拦截倒装、生硬并列、明显不顺的人类不可读结构。",
                "当更上游的自然度评测替代本地启发式时退役。", "mechanical_repair");
        register(entries, RULE_Q_UNKNOWN, "未知质量门原因", "ProbeRenderingService", "evaluateOrthogonalValidation",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "兜底承接未映射的质量门失败原因。",
                "当规则目录覆盖所有失败类型且无未知值后退役。", null);

        register(entries, RULE_B_SURFACE_BROWSE, "浏览型行为约束", "Node2BehaviorRuleComposer", "intentSpecificBehaviorRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "要求生成结果保持先摸一圈候选的浏览开口。",
                "当 surface intent 由 prompt 资产稳定显式控制时退役。", null);
        register(entries, RULE_B_SURFACE_CONFIRM_BUY, "临门确认行为约束", "Node2BehaviorRuleComposer", "intentSpecificBehaviorRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "要求生成结果体现买前确认与尽快收口。",
                "当 confirm-and-buy 意图由 prompt 资产稳定显式控制时退役。", null);
        register(entries, RULE_B_SURFACE_ACTION_STEPS, "动作导向行为约束", "Node2BehaviorRuleComposer", "intentSpecificBehaviorRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "要求生成结果问清先看哪几步或按什么思路挑。",
                "当 actionable intent 由 prompt 资产稳定显式控制时退役。", null);
        register(entries, RULE_B_SURFACE_CLEAR_ANSWER, "判断题行为约束", "Node2BehaviorRuleComposer", "intentSpecificBehaviorRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "要求生成结果像在要直接判断，而不是完整教程。",
                "当 directed-answer 意图由 prompt 资产稳定显式控制时退役。", null);
        register(entries, RULE_B_RETRY_SEMANTIC, "语义补偿重试约束", "Node2BehaviorRuleComposer", "retryRepairRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "上一版语义不足时，强制把 must semantics 补出来。",
                "当 semantic gate 前移且无需重试补偿时退役。", null);
        register(entries, RULE_B_RETRY_ANCHOR, "锚点补偿重试约束", "Node2BehaviorRuleComposer", "retryRepairRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "上一版缺锚点时，强制把真实品类词补进句子。",
                "当 anchor gate 前移且无需重试补偿时退役。", "anchor_repair");
        register(entries, RULE_B_RETRY_BRAND_VISIBLE, "品牌补偿重试约束", "Node2BehaviorRuleComposer", "retryRepairRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "上一版漏品牌时，强制把品牌自然补进主问题。",
                "当 brand-visible gate 前移且无需重试补偿时退役。", "brand_repair");
        register(entries, RULE_B_RETRY_MECHANICAL, "机械腔补偿重试约束", "Node2BehaviorRuleComposer", "retryRepairRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "上一版太像模板句时，强制回到更像真人会发的句子。",
                "当 mechanical gate 前移且无需重试补偿时退役。", "mechanical_repair");
        register(entries, RULE_B_RETRY_AWKWARD, "拗口结构补偿重试约束", "Node2BehaviorRuleComposer", "retryRepairRules",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_QUALITY,
                "上一版结构拗口时，强制避开倒装和生硬并列。",
                "当 awkward gate 前移且无需重试补偿时退役。", "mechanical_repair");

        register(entries, RULE_FB_RENDER_EXCEPTION, "渲染异常回退", "ProbeRenderingService", "renderOrthogonal",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "LLM 渲染抛异常后进入 deterministic fallback。",
                "当主链稳定到异常只作为基础设施告警，不再需要文案回退时退役。", null);
        register(entries, RULE_FB_CONCURRENT_FAILURE, "并发失败回退", "ProbeRenderingService", "buildConcurrentFailureFallback",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "并发执行超时或线程池退化时切入备用回退路径。",
                "当并发执行器稳定且不再需要 caller-thread/紧急兜底时退役。", null);
        register(entries, RULE_FB_TOKEN_BUDGET, "预算耗尽回退", "ProbeRenderingService", "renderOrthogonal",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "令牌预算触顶后直接转 deterministic fallback。",
                "当预算调度器能在任务前预防超额时退役。", null);
        register(entries, RULE_FB_TEMPLATE_VALIDATION, "模板校验失败回退", "ProbeRenderingService", "renderTemplateBased",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "模板输出未过校验时切入模板 fallback。",
                "当模板族全部纳入版本化资产并有 golden set 保障时退役。", null);
        register(entries, RULE_FAMILY_ORTHOGONAL_CORE, "核心 fallback 家族", "Node2FallbackPromptComposer", "fallbackCoreQuestion",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "使用更通用的核心问法承接 deterministic fallback。",
                "当 orthogonal fallback 被统一新资产替代时退役。", null);
        register(entries, RULE_FAMILY_ORTHOGONAL_ANGLE, "角度型 fallback 家族", "Node2FallbackPromptComposer", "fallbackAngleQuestion",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "使用更偏比较角度的问法承接 deterministic fallback。",
                "当 orthogonal fallback 被统一新资产替代时退役。", null);
        register(entries, RULE_FAMILY_ORTHOGONAL_BROWSE, "浏览型 fallback 家族", "Node2FallbackPromptComposer", "fallbackBrowseQuestion",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "使用更偏先看什么的问法承接 deterministic fallback。",
                "当 orthogonal fallback 被统一新资产替代时退役。", null);
        register(entries, RULE_FAMILY_ORTHOGONAL_SHORT, "短句 fallback 家族", "Node2FallbackPromptComposer", "fallbackShortQuestion",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "使用更短、更直给的问法承接 deterministic fallback。",
                "当 orthogonal fallback 被统一新资产替代时退役。", null);
        register(entries, RULE_FAMILY_PARETO_TEMPLATE, "Pareto 模板 fallback 家族", "ProbeRenderingService", "renderTemplateBased",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "Pareto 模板未过校验时使用的模板 fallback 家族。",
                "当 Pareto 模板全部资产化并稳定通过校验时退役。", null);
        register(entries, RULE_FAMILY_ADVERSARIAL_TEMPLATE, "Adversarial 模板 fallback 家族", "ProbeRenderingService", "renderTemplateBased",
                ORIGIN_BOUNDARY, BOUNDARY_TYPE_FALLBACK_ROUTING,
                "Adversarial 模板未过校验时使用的模板 fallback 家族。",
                "当 Adversarial 模板全部资产化并稳定通过校验时退役。", null);

        return Map.copyOf(entries);
    }

    private static void register(Map<String, RuleEntry> entries,
                                 String ruleId,
                                 String name,
                                 String ownerClass,
                                 String ownerMethod,
                                 String origin,
                                 String boundaryType,
                                 String summary,
                                 String retirementCondition,
                                 String cleanupPath) {
        entries.put(ruleId, new RuleEntry(
                ruleId,
                name,
                ownerClass,
                ownerMethod,
                origin,
                boundaryType,
                summary,
                retirementCondition,
                cleanupPath
        ));
    }

    record RuleEntry(
            String ruleId,
            String name,
            String ownerClass,
            String ownerMethod,
            String origin,
            String boundaryType,
            String summary,
            String retirementCondition,
            String cleanupPath
    ) {
        Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("ruleId", ruleId);
            map.put("name", name);
            map.put("ownerClass", ownerClass);
            map.put("ownerMethod", ownerMethod);
            map.put("origin", origin);
            map.put("boundaryType", boundaryType);
            map.put("summary", summary);
            map.put("retirementCondition", retirementCondition);
            if (cleanupPath != null) {
                map.put("cleanupPath", cleanupPath);
            }
            return Map.copyOf(map);
        }
    }
}

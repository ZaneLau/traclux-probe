package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.MinusOneContextExpansionAiService;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MinusOneContextAssemblyService {

    private static final String ASSEMBLY_MODE_DERIVED = "deterministic_derived";
    private static final String ASSEMBLY_MODE_EXTERNAL_EVIDENCE_AUGMENTED = "external_evidence_augmented";
    private static final int MAX_EVIDENCE_SNIPPETS = 24;
    private static final int MAX_BENCHMARK_QUERIES = 16;
    private static final Pattern JSON_CODE_FENCE_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*})\\s*```", Pattern.DOTALL);
    private static final Set<String> BANNED_PLACEHOLDER_VALUES = Set.of(
            "竞品a", "竞品 b", "竞品a/b", "某品牌", "某产品", "某品类", "xx", "xxx"
    );
    private static final List<String> DEFAULT_TARGET_MODEL_SIGNALS = List.of(
            "优先使用用户不一定知道品牌时的泛化问法",
            "允许一句话搜索式提问",
            "允许轻咨询式表达",
            "不要把内部标签直接落到表层问句"
    );
    private static final Set<String> CONTROL_PLANE_EVIDENCE_KEYS = Set.of(
            "samplingrealtimesearch.enabled",
            "samplingrealtimesearch.provider",
            "samplingrealtimesearch.minevidencesuccessrate",
            "samplingrealtimesearch.maxevidenceretries",
            "samplingrealtimesearch.maxbackfillrounds",
            "contextassemblystage",
            "contextassemblymode",
            "categoryanchorsource",
            "inputmode",
            "assemblysource"
    );

    private static final List<LexicalRule> SCENARIO_RULES = List.of(
            new LexicalRule(List.of("厨房", "水槽", "台面"), "厨房台面收纳", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("厨房", "水槽", "沥水"), "水槽旁沥水整理", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("家庭", "家用", "日常"), "家庭日常使用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("办公", "办公室"), "办公场景使用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("户外", "露营"), "户外使用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("旅行", "出差", "便携", "随身"), "出行携带", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("宿舍", "学生", "上课"), "学生日常使用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("儿童", "宝宝", "婴儿"), "给孩子用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("运动"), "运动场景使用", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("泡茶", "茶"), "泡茶场景", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("订水", "桶装"), "家庭常备", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("订水", "办公室"), "办公室订水", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("送礼", "礼盒"), "送人礼赠", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("早餐"), "早餐搭配", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("开发", "编程"), "开发工作", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("设计", "剪辑"), "设计剪辑", MinusOneContextSlots.USAGE_SCENARIO),
            new LexicalRule(List.of("游戏"), "娱乐游戏", MinusOneContextSlots.USAGE_SCENARIO)
    );

    private static final List<LexicalRule> DECISION_RULES = List.of(
            new LexicalRule(List.of("沥水", "排水"), "排水不积水", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("收纳", "置物", "架", "篮"), "容量布局", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("安装", "稳固"), "安装稳固", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("不锈钢"), "材质耐用", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("可折叠", "折叠"), "收纳省空间", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("低噪音"), "噪音低不抗拒", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("可水洗", "水洗"), "清洁方便", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("续航", "电量"), "续航稳定", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("轻薄", "便携"), "便携性", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("游戏"), "性能配置", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("商务"), "办公适配", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("NFC", "纯果汁"), "成分更纯", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("低糖", "无糖"), "控糖负担", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("天然水", "矿泉水", "弱碱"), "水质口感", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("茶", "龙井", "碧螺春", "毛峰", "毛尖"), "口感香型", MinusOneContextSlots.DECISION_FACTOR),
            new LexicalRule(List.of("理发器", "电推子"), "不夹头发", MinusOneContextSlots.DECISION_FACTOR)
    );

    private final MinusOneContextExpansionAiService minusOneContextExpansionAiService;
    private final ObjectMapper objectMapper;
    private boolean evidenceExpansionEnabled = true;

    public MinusOneContextAssemblyService(MinusOneContextExpansionAiService minusOneContextExpansionAiService,
                                          ObjectMapper objectMapper) {
        this.minusOneContextExpansionAiService = minusOneContextExpansionAiService;
        this.objectMapper = objectMapper;
    }

    @Value("${app.engine.v42.minus-one.evidence-expansion.enabled:true}")
    void setEvidenceExpansionEnabled(boolean evidenceExpansionEnabled) {
        this.evidenceExpansionEnabled = evidenceExpansionEnabled;
    }

    public EngineTaskContextPackage assemble(FrontDeskTaskOrder taskOrder,
                                             List<String> categoryAnchors,
                                             boolean fallbackAnchorMode,
                                             String categoryAnchorSource,
                                             Map<String, Object> extensibleSignals) {
        Objects.requireNonNull(taskOrder, "taskOrder 不能为空");
        List<String> anchors = distinct(nonBlankWith(taskOrder.category(), categoryAnchors));
        List<CategoryAnchorSignal> anchorSignals = buildCategoryAnchorSignals(anchors, categoryAnchorSource);
        String userPrompt = readHintText(taskOrder.businessHints(), "userPrompt");
        List<String> negativeTerms = readHintStringList(taskOrder.businessHints(), "negativeTerms");
        List<String> evidenceSnippets = collectEvidenceSnippets(taskOrder, extensibleSignals, userPrompt, anchors);
        boolean hasExternalEvidence = !evidenceSnippets.isEmpty();
        List<String> benchmarkQueries = buildBenchmarkQueries(taskOrder, anchors, userPrompt);
        ContextExpansion expansion = expandFromEvidence(taskOrder, anchors, userPrompt, evidenceSnippets, benchmarkQueries);

        List<String> productForms = mergeDistinct(
                deriveProductForms(taskOrder.category(), anchors),
                expansion.productForms(),
                4
        );
        List<String> scenarios = mergeDistinct(
                deriveValues(taskOrder.category(), anchors, userPrompt, SCENARIO_RULES, 4),
                expansion.usageScenarios(),
                5
        );
        List<String> decisionFactors = mergeDistinct(
                deriveValues(taskOrder.category(), anchors, userPrompt, DECISION_RULES, 4),
                expansion.decisionFactors(),
                5
        );
        List<String> descriptiveCues = mergeDistinct(
                deriveDescriptiveCues(taskOrder.brandName(), taskOrder.category(), anchors),
                expansion.descriptiveCues(),
                4
        );
        List<String> memoryCues = sanitizeDistinctValues(expansion.memoryCues(), 4);
        List<String> channelCues = sanitizeDistinctValues(expansion.channelCues(), 4);
        List<String> geoOrOriginCues = sanitizeDistinctValues(expansion.geoOrOriginCues(), 3);
        List<String> scenarioGraph = sanitizeDistinctValues(expansion.scenarioGraph(), 6);
        List<String> intentLibrary = sanitizeDistinctValues(expansion.intentLibrary(), 6);
        List<String> objectionLibrary = sanitizeDistinctValues(expansion.objectionLibrary(), 6);
        List<String> competitorConfusions = sanitizeDistinctValues(expansion.competitorConfusions(), 5);
        List<String> priceSensitivitySignals = sanitizeDistinctValues(expansion.priceSensitivitySignals(), 4);
        List<String> personaExpressionTendencies = derivePersonaExpressionTendencies(userPrompt);
        List<String> targetModelSignals = mergeDistinct(
                DEFAULT_TARGET_MODEL_SIGNALS,
                expansion.targetModelSignals(),
                8
        );
        List<String> riskNarratives = mergeDistinct(negativeTerms, expansion.riskNarratives(), 10);
        String brandBrief = deriveBrandBrief(expansion.brandBrief());
        List<GroundedContextSignal> groundedSignals = buildGroundedSignals(
                taskOrder,
                anchors,
                anchorSignals,
                productForms,
                scenarios,
                decisionFactors,
                descriptiveCues,
                memoryCues,
                channelCues,
                geoOrOriginCues,
                scenarioGraph,
                intentLibrary,
                objectionLibrary,
                competitorConfusions,
                priceSensitivitySignals,
                riskNarratives,
                expansion.brandEvidence(),
                targetModelSignals,
                hasExternalEvidence,
                buildLocalContextBasis(taskOrder.category(), userPrompt, anchors)
        );
        String assemblyMode = hasExternalEvidence && expansion.expanded()
                ? ASSEMBLY_MODE_EXTERNAL_EVIDENCE_AUGMENTED
                : ASSEMBLY_MODE_DERIVED;
        Map<String, Object> projectionSignals =
                LightConeV43TopicStateProjectionSupport.extractExtensibleSignalPayload(taskOrder.businessHints());

        return new EngineTaskContextPackage(
                taskOrder.brandName(),
                taskOrder.category(),
                taskOrder.competitors(),
                anchors,
                anchorSignals,
                fallbackAnchorMode,
                brandBrief,
                scenarios,
                descriptiveCues,
                taskOrder.competitors(),
                personaExpressionTendencies,
                targetModelSignals,
                groundedSignals,
                missingContextItems(
                        brandBrief,
                        scenarios,
                        descriptiveCues,
                        personaExpressionTendencies,
                        targetModelSignals,
                        groundedSignals
                ),
                mergeMaps(
                        mergeMaps(
                                Map.of(
                                        "contextAssemblyStage", "minus_one_context_assembler_v2",
                                        "contextAssemblyMode", assemblyMode,
                                        "categoryAnchorSource", defaultString(categoryAnchorSource, "front_desk_task_order"),
                                        "brandWebsite", taskOrder.brandWebsite() == null ? "" : taskOrder.brandWebsite(),
                                        "evidenceSnippetCount", evidenceSnippets.size(),
                                        "benchmarkQueryCount", benchmarkQueries.size(),
                                        "evidenceExpansionApplied", hasExternalEvidence && expansion.expanded(),
                                        "derivedContextExpansionApplied", expansion.expanded()
                                ),
                                extensibleSignals
                        ),
                        projectionSignals
                )
        );
    }

    public EngineTaskContextPackage augment(FrontDeskTaskOrder taskOrder,
                                            EngineTaskContextPackage snapshot,
                                            List<String> categoryAnchors,
                                            boolean fallbackAnchorMode,
                                            String categoryAnchorSource,
                                            Map<String, Object> extensibleSignals) {
        EngineTaskContextPackage assembled = assemble(
                taskOrder,
                categoryAnchors,
                fallbackAnchorMode,
                categoryAnchorSource,
                extensibleSignals
        );
        if (snapshot == null) {
            return assembled;
        }

        String brandBrief = preferText(snapshot.brandBrief(), assembled.brandBrief());
        List<String> categorySceneMap = preferList(snapshot.categorySceneMap(), assembled.categorySceneMap());
        List<String> descriptiveEntrypoints = preferList(snapshot.descriptiveEntrypoints(), assembled.descriptiveEntrypoints());
        List<String> competitorPlaceholders = preferList(snapshot.competitorPlaceholders(), assembled.competitorPlaceholders());
        List<String> personaExpressionTendencies = preferList(
                snapshot.personaExpressionTendencies(),
                assembled.personaExpressionTendencies()
        );
        List<String> targetModelSignals = preferList(
                snapshot.targetModelContextSignals(),
                assembled.targetModelContextSignals()
        );
        List<GroundedContextSignal> groundedSignals = snapshot.groundedSignals().isEmpty()
                ? assembled.groundedSignals()
                : snapshot.groundedSignals();

        return new EngineTaskContextPackage(
                taskOrder.brandName(),
                taskOrder.category(),
                taskOrder.competitors(),
                assembled.categoryAnchors(),
                assembled.categoryAnchorSignals(),
                fallbackAnchorMode,
                brandBrief,
                categorySceneMap,
                descriptiveEntrypoints,
                competitorPlaceholders,
                personaExpressionTendencies,
                targetModelSignals,
                groundedSignals,
                missingContextItems(
                        brandBrief,
                        categorySceneMap,
                        descriptiveEntrypoints,
                        personaExpressionTendencies,
                        targetModelSignals,
                        groundedSignals
                ),
                mergeMaps(snapshot.extensibleSignals(), assembled.extensibleSignals())
        );
    }

    private List<GroundedContextSignal> buildGroundedSignals(FrontDeskTaskOrder taskOrder,
                                                             List<String> anchors,
                                                             List<CategoryAnchorSignal> anchorSignals,
                                                             List<String> productForms,
                                                             List<String> scenarios,
                                                             List<String> decisionFactors,
                                                             List<String> descriptiveCues,
                                                             List<String> memoryCues,
                                                             List<String> channelCues,
                                                             List<String> geoOrOriginCues,
                                                             List<String> scenarioGraph,
                                                             List<String> intentLibrary,
                                                             List<String> objectionLibrary,
                                                             List<String> competitorConfusions,
                                                             List<String> priceSensitivitySignals,
                                                             List<String> riskNarratives,
                                                             List<String> brandEvidence,
                                                             List<String> targetModelSignals,
                                                             boolean externalEvidenceAvailable,
                                                             String localContextBasis) {
        LinkedHashMap<String, GroundedContextSignal> signals = new LinkedHashMap<>();
        String localDerivedSource = "local_context_derivation";
        String localExpansionSource = externalEvidenceAvailable ? "external_evidence_assisted_llm" : "local_context_expansion_llm";
        String expansionEvidence = externalEvidenceAvailable ? "external_evidence_snippets" : localContextBasis;
        for (CategoryAnchorSignal anchorSignal : anchorSignals) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                    anchorSignal.anchor(),
                    defaultString(anchorSignal.source(), "front_desk_task_order"),
                    "category_anchor",
                    anchorSignal.confidence(),
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String productForm : productForms) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.PRODUCT_FORM_FACTOR,
                    productForm,
                    localDerivedSource,
                    localContextBasis,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String scenario : scenarios) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.USAGE_SCENARIO,
                    scenario,
                    localDerivedSource,
                    localContextBasis,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String decisionFactor : decisionFactors) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.DECISION_FACTOR,
                    decisionFactor,
                    localDerivedSource,
                    localContextBasis,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String cue : descriptiveCues) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.DESCRIPTIVE_CUE,
                    cue,
                    localDerivedSource,
                    localContextBasis,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String cue : memoryCues) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.MEMORY_CUE,
                    cue,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String cue : channelCues) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.CHANNEL_CUE,
                    cue,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String cue : geoOrOriginCues) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.GEO_OR_ORIGIN_CUE,
                    cue,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String item : scenarioGraph) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.SCENARIO_GRAPH,
                    item,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String item : intentLibrary) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.INTENT_LIBRARY,
                    item,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String item : objectionLibrary) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.OBJECTION_LIBRARY,
                    item,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String item : competitorConfusions) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.COMPETITOR_CONFUSION,
                    item,
                    localExpansionSource,
                    externalEvidenceAvailable ? "external_evidence_snippets" : "competitor_inputs_and_local_context",
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String item : priceSensitivitySignals) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.PRICE_SENSITIVITY,
                    item,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String competitor : taskOrder.competitors()) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.COMPARISON_TARGET,
                    competitor,
                    "frontdesk_competitor_input",
                    "competitors",
                    1.0d,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.NODE2_REALIZATION)
            ));
        }
        for (String targetModelSignal : targetModelSignals) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.TARGET_MODEL_CONTEXT_SIGNAL,
                    targetModelSignal,
                    "system_policy",
                    "minus_one_default_policy",
                    1.0d,
                    List.of(MinusOneContextUses.NODE2_REALIZATION, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String evidence : brandEvidence) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.BRAND_EVIDENCE,
                    evidence,
                    localExpansionSource,
                    expansionEvidence,
                    null,
                    List.of(MinusOneContextUses.NODE0_STATE_INFERENCE, MinusOneContextUses.REPORT_ONLY)
            ));
        }
        for (String negativeTerm : riskNarratives) {
            addSignal(signals, new GroundedContextSignal(
                    MinusOneContextSlots.RISK_NARRATIVE,
                    negativeTerm,
                    "risk_profile",
                    externalEvidenceAvailable ? "negative_terms_and_external_evidence" : "negative_terms_and_local_context",
                    null,
                    List.of(MinusOneContextUses.REPORT_ONLY)
            ));
        }
        return List.copyOf(signals.values());
    }

    private List<CategoryAnchorSignal> buildCategoryAnchorSignals(List<String> anchors, String categoryAnchorSource) {
        String source = defaultString(categoryAnchorSource, "front_desk_task_order");
        return anchors.stream()
                .map(anchor -> new CategoryAnchorSignal(anchor, source, 1.0d))
                .toList();
    }

    private List<String> deriveProductForms(String category, List<String> anchors) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String anchor : anchors) {
            String normalizedAnchor = sanitizeAnchorCue(anchor);
            if (StringUtils.hasText(normalizedAnchor) && !normalizedAnchor.equals(category)) {
                values.add(normalizedAnchor);
            }
        }
        return values.stream().limit(3).toList();
    }

    private List<String> deriveDescriptiveCues(String brandName, String category, List<String> anchors) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String anchor : anchors) {
            String normalizedAnchor = sanitizeAnchorCue(anchor);
            if (!StringUtils.hasText(normalizedAnchor)) {
                continue;
            }
            if (StringUtils.hasText(brandName) && normalizedAnchor.contains(brandName)) {
                continue;
            }
            if (isTaxonomyCue(normalizedAnchor, category)) {
                continue;
            }
            values.add(normalizedAnchor);
        }
        return values.stream().limit(3).toList();
    }

    private String sanitizeAnchorCue(String anchor) {
        if (!StringUtils.hasText(anchor)) {
            return null;
        }
        String normalized = anchor.trim();
        if (normalized.endsWith("品牌")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private boolean isTaxonomyCue(String cue, String category) {
        if (!StringUtils.hasText(cue)) {
            return true;
        }
        if (StringUtils.hasText(category) && cue.equals(category)) {
            return true;
        }
        return cue.endsWith("品牌")
                || cue.endsWith("品类")
                || cue.endsWith("类别")
                || cue.endsWith("类型")
                || cue.endsWith("系列")
                || cue.endsWith("产品")
                || cue.endsWith("商品")
                || cue.endsWith("型号")
                || cue.endsWith("款式")
                || cue.endsWith("选购");
    }

    private List<String> derivePersonaExpressionTendencies(String userPrompt) {
        if (!StringUtils.hasText(userPrompt)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (containsAny(userPrompt, "推荐", "哪个好", "更值得", "靠谱吗")) {
            values.add("偏好直接问结论");
        }
        if (containsAny(userPrompt, "怎么选", "步骤", "先看", "从哪几步")) {
            values.add("偏好先问判断步骤");
        }
        if (containsAny(userPrompt, "区别", "差别", "对比", "比较")) {
            values.add("会先问差别和对比");
        }
        if (containsAny(userPrompt, "稳", "不踩坑", "省心", "不容易出问题")) {
            values.add("更在意稳妥不踩坑");
        }
        return List.copyOf(values);
    }

    private List<String> deriveValues(String category,
                                      List<String> anchors,
                                      String userPrompt,
                                      List<LexicalRule> rules,
                                      int limit) {
        String corpus = String.join(" ", nonBlankWith(category, anchors, userPrompt));
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (LexicalRule rule : rules) {
            if (rule.matches(corpus)) {
                values.add(rule.value());
            }
        }
        return values.stream().limit(limit).toList();
    }

    private ContextExpansion expandFromEvidence(FrontDeskTaskOrder taskOrder,
                                                List<String> anchors,
                                                String userPrompt,
                                                List<String> evidenceSnippets,
                                                List<String> benchmarkQueries) {
        if (minusOneContextExpansionAiService == null) {
            return ContextExpansion.empty();
        }
        if (!evidenceExpansionEnabled) {
            return ContextExpansion.empty();
        }
        try {
            String rawJson = minusOneContextExpansionAiService.expand(
                    taskOrder.brandName(),
                    taskOrder.category(),
                    toJson(taskOrder.competitors()),
                    toJson(anchors),
                    defaultString(userPrompt, ""),
                    defaultString(taskOrder.brandWebsite(), ""),
                    toJson(evidenceSnippets),
                    toJson(benchmarkQueries)
            );
            if (!StringUtils.hasText(rawJson)) {
                return ContextExpansion.empty();
            }
            ExpansionPayload payload = objectMapper.readValue(normalizeJsonPayload(rawJson), ExpansionPayload.class);
            return ContextExpansion.from(payload, taskOrder.category());
        } catch (Exception ex) {
            log.warn("Minus-one evidence expansion fallback engaged. brand={}, category={}, reason={}",
                    taskOrder.brandName(),
                    taskOrder.category(),
                    ex.getMessage());
            return ContextExpansion.empty();
        }
    }

    private List<String> collectEvidenceSnippets(FrontDeskTaskOrder taskOrder,
                                                 Map<String, Object> extensibleSignals,
                                                 String userPrompt,
                                                 List<String> anchors) {
        LinkedHashSet<String> snippets = new LinkedHashSet<>();
        appendEvidenceFromMap(snippets, taskOrder.businessHints());
        appendEvidenceFromMap(snippets, taskOrder.taskMetadata());
        appendEvidenceFromMap(snippets, extensibleSignals);
        return snippets.stream().limit(MAX_EVIDENCE_SNIPPETS).toList();
    }

    private void appendEvidenceFromMap(LinkedHashSet<String> collector, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (!mayContainEvidence(key)) {
                continue;
            }
            appendEvidenceValue(collector, key, entry.getValue());
        }
    }

    private boolean mayContainEvidence(String key) {
        String normalized = normalizeEvidenceKey(key);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (CONTROL_PLANE_EVIDENCE_KEYS.contains(normalized) || normalized.startsWith("samplingrealtimesearch")) {
            return false;
        }
        return normalized.contains("evidence")
                || normalized.contains("benchmark")
                || normalized.contains("snippet")
                || normalized.contains("memo")
                || normalized.contains("reference")
                || normalized.equals("searchresults")
                || normalized.equals("searchsources")
                || normalized.equals("sourcememo");
    }

    @SuppressWarnings("unchecked")
    private void appendEvidenceValue(LinkedHashSet<String> collector, String key, Object raw) {
        if (raw instanceof String text && StringUtils.hasText(text)) {
            collector.add(key + ": " + text.trim());
            return;
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof String text && StringUtils.hasText(text)) {
                    collector.add(key + ": " + text.trim());
                }
            }
            return;
        }
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String nestedKey && entry.getValue() instanceof String nestedValue
                        && StringUtils.hasText(nestedValue)) {
                    collector.add(key + "." + nestedKey.trim() + ": " + nestedValue.trim());
                }
            }
        }
    }

    private List<String> buildBenchmarkQueries(FrontDeskTaskOrder taskOrder,
                                               List<String> anchors,
                                               String userPrompt) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String brandName = taskOrder.brandName();
        String category = taskOrder.category();
        queries.add(brandName + " " + category);
        queries.add(category + " 推荐");
        queries.add(category + " 怎么选");
        queries.add(category + " 对比");
        queries.add(category + " 替代");
        queries.add(category + " 评测");
        if (StringUtils.hasText(userPrompt)) {
            queries.add(userPrompt.trim());
        }
        for (String anchor : anchors) {
            queries.add(anchor + " 推荐");
            queries.add(anchor + " 对比");
        }
        List<String> competitors = taskOrder.competitors();
        if (competitors.size() >= 1) {
            queries.add(brandName + " 和 " + competitors.getFirst() + " 哪个更值得买");
        }
        if (competitors.size() >= 2) {
            queries.add(competitors.get(0) + " vs " + competitors.get(1));
        }
        return queries.stream().limit(MAX_BENCHMARK_QUERIES).toList();
    }

    private List<String> mergeDistinct(List<String> primary, List<String> secondary, int limit) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(primary)) {
            merged.addAll(sanitizeDistinctValues(primary, limit));
        }
        if (!CollectionUtils.isEmpty(secondary)) {
            merged.addAll(sanitizeDistinctValues(secondary, limit));
        }
        return merged.stream().limit(Math.max(0, limit)).toList();
    }

    private List<String> sanitizeDistinctValues(List<String> values, int limit) {
        if (CollectionUtils.isEmpty(values) || limit <= 0) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = sanitizeExpandedValue(value);
            if (cleaned == null) {
                continue;
            }
            normalized.add(cleaned);
            if (normalized.size() >= limit) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private String sanitizeExpandedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 32) {
            return null;
        }
        String lower = normalized.toLowerCase();
        if (BANNED_PLACEHOLDER_VALUES.contains(lower)) {
            return null;
        }
        if (lower.contains("竞品a") || lower.contains("某品牌") || lower.contains("某产品") || lower.contains("xx")) {
            return null;
        }
        return normalized;
    }

    private String deriveBrandBrief(String fromExpansion) {
        String candidate = sanitizeExpandedValue(fromExpansion);
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private String normalizeJsonPayload(String rawJson) {
        String trimmed = normalizeOptional(rawJson);
        if (trimmed == null) {
            throw new IllegalStateException("Minus-one evidence expansion 输出为空");
        }
        var fenced = JSON_CODE_FENCE_PATTERN.matcher(trimmed);
        if (fenced.find()) {
            trimmed = fenced.group(1).trim();
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("minus-one evidence payload JSON 序列化失败", ex);
        }
    }

    private List<String> missingContextItems(String brandBrief,
                                             List<String> categorySceneMap,
                                             List<String> descriptiveEntrypoints,
                                             List<String> personaExpressionTendencies,
                                             List<String> targetModelSignals,
                                             List<GroundedContextSignal> groundedSignals) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(brandBrief)) {
            missing.add("brandBrief");
        }
        if (categorySceneMap == null || categorySceneMap.isEmpty()) {
            missing.add("categorySceneMap");
        }
        if (descriptiveEntrypoints == null || descriptiveEntrypoints.isEmpty()) {
            missing.add("descriptiveEntrypoints");
        }
        if (personaExpressionTendencies == null || personaExpressionTendencies.isEmpty()) {
            missing.add("personaExpressionTendencies");
        }
        if (targetModelSignals == null || targetModelSignals.isEmpty()) {
            missing.add("targetModelContextSignals");
        }
        if (groundedSignals == null || groundedSignals.isEmpty()) {
            missing.add("groundedSignals");
        }
        return List.copyOf(missing);
    }

    private void addSignal(Map<String, GroundedContextSignal> collector, GroundedContextSignal signal) {
        collector.putIfAbsent(signal.dedupeKey(), signal);
    }

    private String joinEvidence(String category, List<String> anchors) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.hasText(category)) {
            values.add(category.trim());
        }
        if (anchors != null) {
            values.addAll(anchors);
        }
        return String.join(" / ", values);
    }

    private String buildLocalContextBasis(String category, String userPrompt, List<String> anchors) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (StringUtils.hasText(category)) {
            values.add("品类=" + category.trim());
        }
        if (anchors != null && !anchors.isEmpty()) {
            values.add("锚词=" + joinEvidence(null, anchors));
        }
        if (StringUtils.hasText(userPrompt)) {
            values.add("用户补充=" + userPrompt.trim());
        }
        return values.isEmpty() ? "local_context_inputs" : String.join("；", values);
    }

    private String normalizeEvidenceKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        return key.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source) || keywords == null || keywords.length == 0) {
            return false;
        }
        return Arrays.stream(keywords).anyMatch(source::contains);
    }

    private List<String> readHintStringList(Map<String, Object> hints, String key) {
        Object raw = hints == null ? null : hints.get(key);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : list) {
            if (item instanceof String value && StringUtils.hasText(value)) {
                values.add(value.trim());
            }
        }
        return List.copyOf(values);
    }

    private String readHintText(Map<String, Object> hints, String key) {
        Object raw = hints == null ? null : hints.get(key);
        if (!(raw instanceof String value) || !StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<String> preferList(List<String> primary, List<String> fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }

    private String preferText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : fallback;
    }

    private List<String> nonBlankWith(String first, List<String> values) {
        List<String> merged = new ArrayList<>();
        if (StringUtils.hasText(first)) {
            merged.add(first.trim());
        }
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    merged.add(value.trim());
                }
            }
        }
        return merged;
    }

    private List<String> nonBlankWith(String category, List<String> anchors, String userPrompt) {
        List<String> merged = new ArrayList<>();
        if (StringUtils.hasText(category)) {
            merged.add(category.trim());
        }
        if (anchors != null) {
            for (String anchor : anchors) {
                if (StringUtils.hasText(anchor)) {
                    merged.add(anchor.trim());
                }
            }
        }
        if (StringUtils.hasText(userPrompt)) {
            merged.add(userPrompt.trim());
        }
        return merged;
    }

    private List<String> distinct(List<String> values) {
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    deduped.add(value.trim());
                }
            }
        }
        return List.copyOf(deduped);
    }

    private Map<String, Object> mergeMaps(Map<String, Object> primary, Map<String, Object> secondary) {
        if ((primary == null || primary.isEmpty()) && (secondary == null || secondary.isEmpty())) {
            return Map.of();
        }
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (primary != null) {
            primary.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null && !isBlankString(value)) {
                    merged.put(key.trim(), value);
                }
            });
        }
        if (secondary != null) {
            secondary.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null && !isBlankString(value)) {
                    merged.put(key.trim(), value);
                }
            });
        }
        return Map.copyOf(merged);
    }

    private boolean isBlankString(Object value) {
        return value instanceof String text && !StringUtils.hasText(text);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record ContextExpansion(
            boolean expanded,
            String brandBrief,
            List<String> productForms,
            List<String> usageScenarios,
            List<String> decisionFactors,
            List<String> descriptiveCues,
            List<String> memoryCues,
            List<String> channelCues,
            List<String> geoOrOriginCues,
            List<String> scenarioGraph,
            List<String> intentLibrary,
            List<String> objectionLibrary,
            List<String> competitorConfusions,
            List<String> priceSensitivitySignals,
            List<String> riskNarratives,
            List<String> targetModelSignals,
            List<String> brandEvidence
    ) {

        private static ContextExpansion empty() {
            return new ContextExpansion(
                    false,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        private static ContextExpansion from(ExpansionPayload payload, String category) {
            if (payload == null) {
                return empty();
            }
            String normalizedCategory = StringUtils.hasText(category) ? category.trim() : "";
            List<String> descriptiveCues = payload.descriptiveCues() == null
                    ? List.of()
                    : payload.descriptiveCues().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(value -> !value.equals(normalizedCategory))
                    .filter(value -> !value.endsWith("品牌") && !value.endsWith("品类") && !value.endsWith("类别"))
                    .toList();
            boolean hasMeaningful = StringUtils.hasText(payload.brandBrief())
                    || !CollectionUtils.isEmpty(payload.productForms())
                    || !CollectionUtils.isEmpty(payload.usageScenarios())
                    || !CollectionUtils.isEmpty(payload.decisionFactors())
                    || !CollectionUtils.isEmpty(descriptiveCues)
                    || !CollectionUtils.isEmpty(payload.memoryCues())
                    || !CollectionUtils.isEmpty(payload.channelCues())
                    || !CollectionUtils.isEmpty(payload.geoOrOriginCues())
                    || !CollectionUtils.isEmpty(payload.brandEvidence());
            return new ContextExpansion(
                    hasMeaningful,
                    payload.brandBrief(),
                    defaultList(payload.productForms()),
                    defaultList(payload.usageScenarios()),
                    defaultList(payload.decisionFactors()),
                    descriptiveCues,
                    defaultList(payload.memoryCues()),
                    defaultList(payload.channelCues()),
                    defaultList(payload.geoOrOriginCues()),
                    defaultList(payload.scenarioGraph()),
                    defaultList(payload.intentLibrary()),
                    defaultList(payload.objectionLibrary()),
                    defaultList(payload.competitorConfusions()),
                    defaultList(payload.priceSensitivitySignals()),
                    defaultList(payload.riskNarratives()),
                    defaultList(payload.targetModelSignals()),
                    defaultList(payload.brandEvidence())
            );
        }

        private static List<String> defaultList(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    private record ExpansionPayload(
            String brandBrief,
            List<String> productForms,
            List<String> usageScenarios,
            List<String> decisionFactors,
            List<String> descriptiveCues,
            List<String> memoryCues,
            List<String> channelCues,
            List<String> geoOrOriginCues,
            List<String> scenarioGraph,
            List<String> intentLibrary,
            List<String> objectionLibrary,
            List<String> competitorConfusions,
            List<String> priceSensitivitySignals,
            List<String> riskNarratives,
            List<String> targetModelSignals,
            List<String> brandEvidence
    ) {
    }

    private record LexicalRule(List<String> keywords, String value, String slot) {

        private boolean matches(String corpus) {
            if (!StringUtils.hasText(corpus) || keywords == null || keywords.isEmpty()) {
                return false;
            }
            return keywords.stream().anyMatch(corpus::contains);
        }
    }
}

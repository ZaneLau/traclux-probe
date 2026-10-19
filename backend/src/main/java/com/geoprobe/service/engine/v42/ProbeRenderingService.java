package com.geoprobe.service.engine.v42;

import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetAware;
import com.geoprobe.ai.v42.support.V42PromptAssetDescriptor;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator.MappingResult;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service("v42ProbeRenderingService")
public class ProbeRenderingService {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_NODE2_RENDER = "node2_render";
    private static final int MAX_RETRIES = 3;
    private static final int MAX_ORTHOGONAL_PROMPT_LENGTH = 60;
    private static final int MAX_TEMPLATE_PROMPT_LENGTH = 48;
    private static final String CREATIVE_BUDGET_PROVIDER = "deepseek";
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_TEMPLATE = "TEMPLATE";
    public static final String SOURCE_DETERMINISTIC_FALLBACK = "DETERMINISTIC_FALLBACK";
    private static final String REASON_LLM_VALIDATED_ORIGINAL = "llm_validated_original";
    private static final String REASON_LLM_VALIDATED_ANCHOR_REPAIRED = "llm_validated_anchor_repaired";
    private static final String REASON_LLM_VALIDATED_BRAND_REPAIRED = "llm_validated_brand_repaired";
    private static final String REASON_LLM_VALIDATED_OFFICIAL_CUE_REPAIRED = "llm_validated_official_cue_repaired";
    private static final String REASON_LLM_VALIDATED_MECHANICAL_REPAIRED = "llm_validated_mechanical_repaired";
    private static final String REASON_VALIDATION_FAILED_FALLBACK = "validation_failed_fallback";
    private static final List<String> BANNED_WORDS = List.of(
            "类别", "维度", "框架", "信号", "门槛", "误判", "复核", "交叉验证"
    );
    private static final List<String> LITERALIZATION_FRAGMENTS = List.of(
            "急着用",
            "马上要",
            "赶时间",
            "想跟你聊清楚",
            "按我的要求",
            "按自己要求找",
            "问问别人再决定",
            "看懂差别",
            "搞清楚区别",
            "先让我看",
            "少踩坑点",
            "别容易翻车"
    );
    private static final List<String> TEMPLATE_ALIGNMENT_CUES = List.of(
            "官网", "可信来源", "口碑", "资质",
            "真实体验", "优缺点", "风险点",
            "性价比", "价格带", "价格", "预算",
            "适合", "使用场景", "场景", "人群",
            "容量", "空间适配", "小户型", "台面",
            "推荐", "替代", "顾虑", "劝退", "误解", "决策阻力",
            "防水", "静音", "宿舍", "学生党",
            "无线", "双模", "连接",
            "DPI", "做工", "耐用", "办公", "轻度游戏",
            "竞品", "品牌对比", "差异"
    );

    private final ProbeRenderingAiService renderAiService;
    private final ThreeLayerMappingEvaluator threeLayerMappingEvaluator;
    private final Node2BatchRenderExecutor batchRenderExecutor;
    private final Node2BehaviorRuleComposer behaviorRuleComposer;
    private final Node2FallbackPromptComposer fallbackPromptComposer;
    private final Node2TriggeredRulesTraceComposer triggeredRulesTraceComposer;
    private final ProbeQualityGateService surfaceGateService;
    private final LlmTokenBudgetService llmTokenBudgetService;
    private final int configuredConcurrentParallelism;
    private final String creativeModelId;

    @Autowired
    public ProbeRenderingService(ProbeRenderingAiService renderAiService,
                                 ThreeLayerMappingEvaluator threeLayerMappingEvaluator,
                                 Node2TriggeredRulesTraceComposer triggeredRulesTraceComposer,
                                 ObjectProvider<LlmTokenBudgetService> llmTokenBudgetServiceProvider,
                                 @Qualifier("node2RenderExecutor") @Nullable Executor node2RenderExecutor,
                                 @Value("${app.engine.v42.node2.concurrent.enabled:true}") boolean concurrentEnabled,
                                 @Value("${app.engine.v42.node2.concurrent.parallelism:8}") int concurrentParallelism,
                                 @Value("${app.engine.v42.node2.concurrent.task-timeout-millis:45000}") long concurrentTaskTimeoutMillis,
                                 @Value("${llm.deepseek.endpoint-id:}") String deepseekEndpointId,
                                 @Value("${llm.deepseek.model:deepseek-v3.2}") String deepseekModel) {
        this.renderAiService = renderAiService;
        this.threeLayerMappingEvaluator = threeLayerMappingEvaluator;
        this.triggeredRulesTraceComposer = triggeredRulesTraceComposer;
        this.batchRenderExecutor = new Node2BatchRenderExecutor(
                node2RenderExecutor,
                concurrentEnabled,
                concurrentParallelism,
                concurrentTaskTimeoutMillis
        );
        this.behaviorRuleComposer = new Node2BehaviorRuleComposer();
        this.fallbackPromptComposer = new Node2FallbackPromptComposer();
        this.surfaceGateService = new ProbeQualityGateService();
        this.llmTokenBudgetService = llmTokenBudgetServiceProvider.getIfAvailable();
        this.configuredConcurrentParallelism = Math.max(1, concurrentParallelism);
        this.creativeModelId = StringUtils.hasText(deepseekEndpointId)
                ? deepseekEndpointId.trim()
                : deepseekModel.trim();
    }

    ProbeRenderingService(ProbeRenderingAiService probeRenderingAiService,
                          ThreeLayerMappingEvaluator threeLayerMappingEvaluator) {
        this.renderAiService = probeRenderingAiService;
        this.threeLayerMappingEvaluator = threeLayerMappingEvaluator;
        this.triggeredRulesTraceComposer = new Node2TriggeredRulesTraceComposer(threeLayerMappingEvaluator);
        this.batchRenderExecutor = new Node2BatchRenderExecutor(Runnable::run, false, 1, 45000L);
        this.behaviorRuleComposer = new Node2BehaviorRuleComposer();
        this.fallbackPromptComposer = new Node2FallbackPromptComposer();
        this.surfaceGateService = new ProbeQualityGateService();
        this.llmTokenBudgetService = null;
        this.configuredConcurrentParallelism = 1;
        this.creativeModelId = null;
    }

    ProbeRenderingService(ProbeRenderingAiService probeRenderingAiService,
                          ThreeLayerMappingEvaluator threeLayerMappingEvaluator,
                          Executor node2RenderExecutor,
                          boolean concurrentEnabled,
                          int concurrentParallelism,
                          long concurrentTaskTimeoutMillis) {
        this.renderAiService = probeRenderingAiService;
        this.threeLayerMappingEvaluator = threeLayerMappingEvaluator;
        this.triggeredRulesTraceComposer = new Node2TriggeredRulesTraceComposer(threeLayerMappingEvaluator);
        this.batchRenderExecutor = new Node2BatchRenderExecutor(
                node2RenderExecutor,
                concurrentEnabled,
                concurrentParallelism,
                concurrentTaskTimeoutMillis
        );
        this.behaviorRuleComposer = new Node2BehaviorRuleComposer();
        this.fallbackPromptComposer = new Node2FallbackPromptComposer();
        this.surfaceGateService = new ProbeQualityGateService();
        this.llmTokenBudgetService = null;
        this.configuredConcurrentParallelism = Math.max(1, concurrentParallelism);
        this.creativeModelId = null;
    }

    @PostConstruct
    void logNode2Mode() {
        log.info("V4.2 Node2 已冻结为生产单路径：只保留受限渲染 A 主链。");
    }

    public List<RenderedProbe> renderAll(String diagnosisId,
                                         CategoryVariablePool pool,
                                         List<ProbeBlueprint> blueprints) {
        return renderAll(diagnosisId, pool, List.of(), blueprints);
    }

    public List<RenderedProbe> renderAll(String diagnosisId,
                                         CategoryVariablePool pool,
                                         List<String> brandAliases,
                                         List<ProbeBlueprint> blueprints) {
        return renderAllWithMetrics(diagnosisId, pool, brandAliases, blueprints).probes();
    }

    public Node2BatchRenderResult renderAllWithMetrics(String diagnosisId,
                                                       CategoryVariablePool pool,
                                                       List<String> brandAliases,
                                                       List<ProbeBlueprint> blueprints) {
        if (pool == null) {
            throw new IllegalArgumentException("CategoryVariablePool 不能为空");
        }
        Node2BatchRenderResult batchResult = batchRenderExecutor.renderAll(
                diagnosisId,
                pool,
                brandAliases,
                blueprints,
                resolveNode2BatchParallelism(blueprints.size()),
                new Node2BatchRenderExecutor.RenderStrategy() {
                    @Override
                    public RenderedProbe render(String runDiagnosisId,
                                                CategoryVariablePool runPool,
                                                List<String> runBrandAliases,
                                                ProbeBlueprint runBlueprint,
                                                Set<String> usedCanonicalPrompts) {
                        return renderOne(runDiagnosisId, runPool, runBrandAliases, runBlueprint, usedCanonicalPrompts);
                    }

                    @Override
                    public RenderedProbe buildConcurrentFailureFallback(String runDiagnosisId,
                                                                       CategoryVariablePool runPool,
                                                                       List<String> runBrandAliases,
                                                                       ProbeBlueprint runBlueprint,
                                                                       Set<String> usedCanonicalPrompts,
                                                                       RuntimeException failure) {
                        return ProbeRenderingService.this.buildConcurrentFailureFallback(
                                runDiagnosisId,
                                runPool,
                                runBrandAliases,
                                runBlueprint,
                                usedCanonicalPrompts,
                                failure
                        );
                    }
                }
        );
        return new Node2BatchRenderResult(
                stabilizeSurfaceGateFailures(diagnosisId, pool, brandAliases, blueprints, batchResult.probes()),
                batchResult.timing()
        );
    }

    private int resolveNode2BatchParallelism(int batchSize) {
        int desiredParallelism = Math.max(1, Math.min(configuredConcurrentParallelism, batchSize));
        if (llmTokenBudgetService == null || !StringUtils.hasText(creativeModelId)) {
            return desiredParallelism;
        }
        LlmTokenBudgetService.ConcurrencyRecommendation recommendation = llmTokenBudgetService.recommendConcurrency(
                CREATIVE_BUDGET_PROVIDER,
                creativeModelId,
                desiredParallelism
        );
        if (recommendation.limited()) {
            log.info("V4.2 Node2 render concurrency capped by token budget. provider={}, model={}, desired={}, effective={}, remainingTokens={}",
                    CREATIVE_BUDGET_PROVIDER,
                    creativeModelId,
                    recommendation.desiredConcurrency(),
                    recommendation.effectiveConcurrency(),
                    recommendation.remainingTokens());
        }
        return recommendation.effectiveConcurrency();
    }

    private RenderedProbe buildConcurrentFailureFallback(String diagnosisId,
                                                         CategoryVariablePool pool,
                                                         List<String> brandAliases,
                                                         ProbeBlueprint blueprint,
                                                         Set<String> usedCanonicalPrompts,
                                                         RuntimeException failure) {
        String failureTag = extractFailureTag(failure);
        if (blueprint.bucketType() != BucketType.ORTHOGONAL) {
            RenderedProbe templateBased = renderTemplateBased(pool, brandAliases, blueprint);
            return new RenderedProbe(
                    templateBased.probeId(),
                    templateBased.bucketType(),
                    templateBased.abstractTags(),
                    templateBased.translatedTags(),
                    templateBased.realizationPlan(),
                    templateBased.exposurePolicy(),
                    templateBased.renderedPrompt(),
                    templateBased.fallbackUsed(),
                    templateBased.renderSource(),
                    templateBased.renderFamily(),
                    "concurrent_failure_template_recovered[" + failureTag + "]",
                    templateBased.triggeredRulesJson()
            );
        }

        ProbeExposurePolicy exposurePolicy = ProbeExposurePolicy.classifyOrthogonal(blueprint.abstractTags());
        List<String> brandExposureTerms = resolveBrandExposureTerms(pool.brandName(), brandAliases);
        try {
            FallbackCandidate fallback = buildOrthogonalFallback(
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    blueprint.realizationPlan(),
                    blueprint.translatedTags(),
                    diagnosisId,
                    blueprint.probeId(),
                    usedCanonicalPrompts
            );
            return new RenderedProbe(
                    blueprint.probeId(),
                    blueprint.bucketType(),
                    blueprint.abstractTags(),
                    blueprint.translatedTags(),
                    blueprint.realizationPlan(),
                    exposurePolicy,
                    fallback.prompt(),
                    true,
                    SOURCE_DETERMINISTIC_FALLBACK,
                    fallback.family(),
                    "concurrent_failure_fallback[" + failureTag + "]",
                    enrichTriggeredRulesWithBehaviorTrace(
                            enrichTriggeredRulesWithFallbackTrace(
                                    triggeredRulesTraceComposer.composeOrthogonal(
                                            blueprint.abstractTags(),
                                            threeLayerMappingEvaluator.evaluate(blueprint.abstractTags()),
                                            true,
                                            "concurrent_failure_fallback[" + failureTag + "]"
                                    ),
                                    Node2RuleCatalog.RULE_FB_CONCURRENT_FAILURE,
                                    failure
                            ),
                            blueprint.realizationPlan(),
                            List.of(),
                            fallback.family()
                    )
            );
        } catch (RuntimeException fallbackEx) {
            String emergencyPrompt = ensureQuestionTail(buildTemplateGenericFallback(pool, BucketType.ORTHOGONAL));
            return new RenderedProbe(
                    blueprint.probeId(),
                    blueprint.bucketType(),
                    blueprint.abstractTags(),
                    blueprint.translatedTags(),
                    blueprint.realizationPlan(),
                    exposurePolicy,
                    emergencyPrompt,
                    true,
                    SOURCE_DETERMINISTIC_FALLBACK,
                    "orthogonal_concurrent_emergency",
                    "concurrent_failure_emergency[" + failureTag + "]",
                    enrichTriggeredRulesWithBehaviorTrace(
                            enrichTriggeredRulesWithFallbackTrace(
                                    triggeredRulesTraceComposer.composeOrthogonal(
                                            blueprint.abstractTags(),
                                            threeLayerMappingEvaluator.evaluate(blueprint.abstractTags()),
                                            true,
                                            "concurrent_failure_emergency[" + failureTag + "]"
                                    ),
                                    Node2RuleCatalog.RULE_FB_CONCURRENT_FAILURE,
                                    fallbackEx
                            ),
                            blueprint.realizationPlan(),
                            List.of(),
                            "orthogonal_concurrent_emergency"
                    )
            );
        }
    }

    private String extractFailureTag(RuntimeException failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        String message = StringUtils.hasText(cause.getMessage()) ? cause.getMessage().trim() : cause.getClass().getSimpleName();
        if (message.length() > 60) {
            return message.substring(0, 60);
        }
        return message;
    }

    public RenderedProbe renderOne(String diagnosisId, CategoryVariablePool pool, ProbeBlueprint blueprint) {
        return renderOne(diagnosisId, pool, List.of(), blueprint);
    }

    public RenderedProbe renderOne(String diagnosisId,
                                   CategoryVariablePool pool,
                                   List<String> brandAliases,
                                   ProbeBlueprint blueprint) {
        return renderOne(diagnosisId, pool, brandAliases, blueprint, null);
    }

    private RenderedProbe renderOne(String diagnosisId,
                                    CategoryVariablePool pool,
                                    List<String> brandAliases,
                                    ProbeBlueprint blueprint,
                                    Set<String> usedCanonicalPrompts) {
        if (pool == null) {
            throw new IllegalArgumentException("CategoryVariablePool 不能为空");
        }
        if (blueprint == null) {
            throw new IllegalArgumentException("ProbeBlueprint 不能为空");
        }
        return switch (blueprint.bucketType()) {
            case ORTHOGONAL -> renderOrthogonal(diagnosisId, pool, brandAliases, blueprint, usedCanonicalPrompts);
            case PARETO, ADVERSARIAL -> renderTemplateBased(pool, brandAliases, blueprint);
        };
    }

    private List<RenderedProbe> stabilizeSurfaceGateFailures(String diagnosisId,
                                                             CategoryVariablePool pool,
                                                             List<String> brandAliases,
                                                             List<ProbeBlueprint> blueprints,
                                                             List<RenderedProbe> renderedProbes) {
        if (renderedProbes == null || renderedProbes.isEmpty()) {
            return List.of();
        }
        List<RenderedProbe> current = List.copyOf(renderedProbes);
        for (int round = 1; round <= 2; round++) {
            List<ProbeQualityGateService.SurfaceRepairTarget> targets = surfaceGateService.identifyRepairTargets(current);
            if (targets.isEmpty()) {
                return current;
            }
            current = applySurfaceRepairs(diagnosisId, pool, brandAliases, blueprints, current, targets, round);
        }
        return current;
    }

    private List<RenderedProbe> applySurfaceRepairs(String diagnosisId,
                                                    CategoryVariablePool pool,
                                                    List<String> brandAliases,
                                                    List<ProbeBlueprint> blueprints,
                                                    List<RenderedProbe> renderedProbes,
                                                    List<ProbeQualityGateService.SurfaceRepairTarget> targets,
                                                    int round) {
        Map<String, ProbeBlueprint> blueprintById = new LinkedHashMap<>();
        for (ProbeBlueprint blueprint : blueprints) {
            if (blueprint != null && StringUtils.hasText(blueprint.probeId())) {
                blueprintById.putIfAbsent(blueprint.probeId(), blueprint);
            }
        }
        Map<String, ProbeQualityGateService.SurfaceRepairTarget> targetByProbeId = new LinkedHashMap<>();
        for (ProbeQualityGateService.SurfaceRepairTarget target : targets) {
            if (target != null && StringUtils.hasText(target.probeId())) {
                targetByProbeId.put(target.probeId(), target);
            }
        }
        LinkedHashSet<String> usedCanonicalPrompts = new LinkedHashSet<>();
        for (RenderedProbe probe : renderedProbes) {
            if (probe == null || targetByProbeId.containsKey(probe.probeId())) {
                continue;
            }
            usedCanonicalPrompts.add(canonicalPrompt(probe.renderedPrompt()));
        }

        List<RenderedProbe> repaired = new ArrayList<>(renderedProbes.size());
        for (RenderedProbe probe : renderedProbes) {
            if (probe == null) {
                continue;
            }
            ProbeQualityGateService.SurfaceRepairTarget target = targetByProbeId.get(probe.probeId());
            if (target == null) {
                repaired.add(probe);
                continue;
            }
            ProbeBlueprint blueprint = blueprintById.get(probe.probeId());
            if (blueprint == null) {
                repaired.add(probe);
                usedCanonicalPrompts.add(canonicalPrompt(probe.renderedPrompt()));
                continue;
            }
            RenderedProbe replacement = repairSurfaceTarget(
                    diagnosisId,
                    pool,
                    brandAliases,
                    blueprint,
                    probe,
                    target,
                    usedCanonicalPrompts,
                    round
            );
            repaired.add(replacement);
            usedCanonicalPrompts.add(canonicalPrompt(replacement.renderedPrompt()));
        }
        return List.copyOf(repaired);
    }

    private RenderedProbe repairSurfaceTarget(String diagnosisId,
                                              CategoryVariablePool pool,
                                              List<String> brandAliases,
                                              ProbeBlueprint blueprint,
                                              RenderedProbe original,
                                              ProbeQualityGateService.SurfaceRepairTarget target,
                                              Set<String> usedCanonicalPrompts,
                                              int round) {
        String reason = "surface_gate_repair[r" + round + ":" + joinSurfaceRepairReasons(target.reasons()) + "]";
        try {
            return switch (blueprint.bucketType()) {
                case ORTHOGONAL -> buildOrthogonalSurfaceRepairProbe(
                        diagnosisId,
                        pool,
                        brandAliases,
                        blueprint,
                        usedCanonicalPrompts,
                        reason,
                        original
                );
                case PARETO, ADVERSARIAL -> buildTemplateSurfaceRepairProbe(
                        pool,
                        brandAliases,
                        blueprint,
                        usedCanonicalPrompts,
                        reason,
                        original
                );
            };
        } catch (RuntimeException ex) {
            log.warn("V4.2 Node 2 surface repair skipped. probeId={}, reasons={}, message={}",
                    blueprint.probeId(), target.reasons(), ex.getMessage());
            return original;
        }
    }

    private RenderedProbe renderOrthogonal(String diagnosisId,
                                           CategoryVariablePool pool,
                                           List<String> brandAliases,
                                           ProbeBlueprint blueprint,
                                           Set<String> usedCanonicalPrompts) {
        PromptPayload payload = buildOrthogonalPayload(pool, blueprint);
        ProbeExposurePolicy exposurePolicy = ProbeExposurePolicy.classifyOrthogonal(blueprint.abstractTags());
        List<String> brandExposureTerms = resolveBrandExposureTerms(pool.brandName(), brandAliases);
        RuntimeException lastException = null;
        OrthogonalValidationResult lastValidationResult = null;
        String lastRejectedCandidate = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_CREATIVE, PROVIDER_NODE2_RENDER));
                String rendered = extractCandidateLine(renderAiService.renderProbe(
                        pool.brandName(),
                        pool.category(),
                        joinValues(pool.competitors()),
                        joinValues(pool.categoryAnchors()),
                        brandInjectionPolicy(payload.exposurePolicy()),
                        behaviorRuleComposer.simulationProfile(payload.realizationPlan()),
                        behaviorRuleComposer.joinSemanticConstraints(payload.realizationPlan()),
                        joinSoftHints(payload.realizationPlan(), pool),
                        behaviorRuleComposer.joinBehaviorRules(
                                payload.mappingResult(),
                                payload.realizationPlan(),
                                lastValidationResult == null ? List.of() : lastValidationResult.failedChecks()
                        )
                ));
                String finalized = ensureQuestionTail(rendered);
                OrthogonalValidationResult validationResult = evaluateOrthogonalValidation(
                        finalized,
                        pool,
                        brandExposureTerms,
                        exposurePolicy,
                        payload.realizationPlan(),
                        blueprint.translatedTags(),
                        usedCanonicalPrompts,
                        true
                );
                if (validationResult.passed()) {
                    if (tryClaimPrompt(usedCanonicalPrompts, finalized)) {
                        return buildOrthogonalLlmRenderedProbe(
                                blueprint,
                                exposurePolicy,
                                finalized,
                                REASON_LLM_VALIDATED_ORIGINAL,
                                payload.mappingResult(),
                                null,
                                null,
                                null
                        );
                    }
                    lastRejectedCandidate = finalized;
                    lastValidationResult = duplicateValidationResult();
                    lastException = new IllegalStateException("Node 2 并发去重冲突");
                    continue;
                }
                AnchorRepairAttempt anchorRepair = tryRepairMissingCategoryAnchor(
                        finalized,
                        validationResult,
                        pool,
                        brandExposureTerms,
                        exposurePolicy,
                        payload.realizationPlan(),
                        blueprint.translatedTags(),
                        usedCanonicalPrompts
                );
                if (anchorRepair != null) {
                    if (tryClaimPrompt(usedCanonicalPrompts, anchorRepair.repairedPrompt())) {
                        return buildOrthogonalLlmRenderedProbe(
                                blueprint,
                                exposurePolicy,
                                anchorRepair.repairedPrompt(),
                                REASON_LLM_VALIDATED_ANCHOR_REPAIRED,
                                payload.mappingResult(),
                                validationResult,
                                finalized,
                                "anchor"
                        );
                    }
                    lastRejectedCandidate = anchorRepair.repairedPrompt();
                    lastValidationResult = duplicateValidationResult();
                    lastException = new IllegalStateException("Node 2 并发去重冲突");
                    continue;
                }
                BrandRepairAttempt brandRepair = tryRepairMissingBrandExposure(
                        finalized,
                        validationResult,
                        pool,
                        brandExposureTerms,
                        exposurePolicy,
                        payload.realizationPlan(),
                        blueprint.translatedTags(),
                        usedCanonicalPrompts
                );
                if (brandRepair != null) {
                    if (tryClaimPrompt(usedCanonicalPrompts, brandRepair.repairedPrompt())) {
                        return buildOrthogonalLlmRenderedProbe(
                                blueprint,
                                exposurePolicy,
                                brandRepair.repairedPrompt(),
                                REASON_LLM_VALIDATED_BRAND_REPAIRED,
                                payload.mappingResult(),
                                validationResult,
                                finalized,
                                brandRepair.repairApplied()
                        );
                    }
                    lastRejectedCandidate = brandRepair.repairedPrompt();
                    lastValidationResult = duplicateValidationResult();
                    lastException = new IllegalStateException("Node 2 并发去重冲突");
                    continue;
                }
                OfficialCueRepairAttempt officialCueRepair = tryRepairMissingOfficialCue(
                        finalized,
                        validationResult,
                        pool,
                        brandExposureTerms,
                        exposurePolicy,
                        payload.realizationPlan(),
                        blueprint.translatedTags(),
                        usedCanonicalPrompts
                );
                if (officialCueRepair != null) {
                    if (tryClaimPrompt(usedCanonicalPrompts, officialCueRepair.repairedPrompt())) {
                        return buildOrthogonalLlmRenderedProbe(
                                blueprint,
                                exposurePolicy,
                                officialCueRepair.repairedPrompt(),
                                REASON_LLM_VALIDATED_OFFICIAL_CUE_REPAIRED,
                                payload.mappingResult(),
                                validationResult,
                                finalized,
                                officialCueRepair.repairApplied()
                        );
                    }
                    lastRejectedCandidate = officialCueRepair.repairedPrompt();
                    lastValidationResult = duplicateValidationResult();
                    lastException = new IllegalStateException("Node 2 并发去重冲突");
                    continue;
                }
                lastRejectedCandidate = finalized;
                lastValidationResult = validationResult;
                if (log.isDebugEnabled()) {
                    log.debug("V4.2 Node 2 orthogonal validation detail. probeId={}, candidate={}, failedChecks={}",
                            blueprint.probeId(), SensitiveDataSanitizer.preview(finalized, 72), validationResult.failedChecks());
                }
                lastException = new IllegalStateException("Node 2 后验校验失败");
                log.warn("V4.2 Node 2 orthogonal render validation failed. probeId={}, attempt={}",
                        blueprint.probeId(), attempt);
            } catch (RuntimeException ex) {
                lastException = ex;
                log.warn("V4.2 Node 2 orthogonal render failed. probeId={}, attempt={}, reason={}",
                        blueprint.probeId(), attempt, ex.getMessage());
                if (isTokenBudgetFailure(ex)) {
                    break;
                }
            } finally {
                LlmCallContext.clear();
            }
        }

        MechanicalRepairAttempt mechanicalRepair = tryRepairMechanicalPrompt(
                lastRejectedCandidate,
                lastValidationResult,
                pool,
                brandExposureTerms,
                exposurePolicy,
                payload.realizationPlan(),
                blueprint.translatedTags(),
                usedCanonicalPrompts
        );
        if (mechanicalRepair != null) {
            if (tryClaimPrompt(usedCanonicalPrompts, mechanicalRepair.repairedPrompt())) {
                return buildOrthogonalLlmRenderedProbe(
                        blueprint,
                        exposurePolicy,
                        mechanicalRepair.repairedPrompt(),
                        REASON_LLM_VALIDATED_MECHANICAL_REPAIRED,
                        payload.mappingResult(),
                        lastValidationResult,
                        lastRejectedCandidate,
                        mechanicalRepair.repairApplied()
                );
            }
            lastRejectedCandidate = mechanicalRepair.repairedPrompt();
            lastValidationResult = duplicateValidationResult();
            lastException = new IllegalStateException("Node 2 并发去重冲突");
        }

        FallbackCandidate fallback = buildOrthogonalFallback(
                pool,
                brandExposureTerms,
                exposurePolicy,
                payload.realizationPlan(),
                blueprint.translatedTags(),
                diagnosisId,
                blueprint.probeId(),
                usedCanonicalPrompts
        );
        log.warn("V4.2 Node 2 orthogonal fallback engaged. probeId={}, reason={}",
                blueprint.probeId(), lastException == null ? "unknown" : lastException.getMessage());
        return new RenderedProbe(
                blueprint.probeId(),
                blueprint.bucketType(),
                blueprint.abstractTags(),
                blueprint.translatedTags(),
                blueprint.realizationPlan(),
                exposurePolicy,
                fallback.prompt(),
                true,
                SOURCE_DETERMINISTIC_FALLBACK,
                fallback.family(),
                buildFallbackRenderReason(lastValidationResult),
                enrichTriggeredRulesWithPromptAssetTrace(
                        enrichTriggeredRulesWithBehaviorTrace(
                                enrichTriggeredRulesWithFallbackTrace(
                                        enrichTriggeredRulesWithValidationTrace(
                                                triggeredRulesTraceComposer.composeOrthogonal(
                                                        blueprint.abstractTags(),
                                                        payload.mappingResult(),
                                                        true,
                                                        buildFallbackRenderReason(lastValidationResult)
                                                ),
                                                lastValidationResult,
                                                lastRejectedCandidate,
                                                null
                                        ),
                                        resolveFallbackTriggerRule(lastValidationResult, lastException),
                                        lastException
                                ),
                                blueprint.realizationPlan(),
                                lastValidationResult == null ? List.of() : lastValidationResult.failedChecks(),
                                fallback.family()
                        ),
                        V42PromptAssetKey.PROBE_RENDERING
                )
        );
    }

    private RenderedProbe buildOrthogonalLlmRenderedProbe(ProbeBlueprint blueprint,
                                                          ProbeExposurePolicy exposurePolicy,
                                                          String renderedPrompt,
                                                          String renderReason,
                                                          MappingResult mappingResult,
                                                          OrthogonalValidationResult validationResult,
                                                          String rejectedCandidate,
                                                          String repairApplied) {
        return new RenderedProbe(
                blueprint.probeId(),
                blueprint.bucketType(),
                blueprint.abstractTags(),
                blueprint.translatedTags(),
                blueprint.realizationPlan(),
                exposurePolicy,
                renderedPrompt,
                false,
                SOURCE_LLM,
                "orthogonal_constrained_llm",
                renderReason,
                enrichTriggeredRulesWithPromptAssetTrace(
                        enrichTriggeredRulesWithBehaviorTrace(
                                enrichTriggeredRulesWithValidationTrace(
                                        triggeredRulesTraceComposer.composeOrthogonal(
                                                blueprint.abstractTags(),
                                                mappingResult,
                                                false,
                                                renderReason
                                        ),
                                        validationResult,
                                        rejectedCandidate,
                                        repairApplied
                                ),
                                blueprint.realizationPlan(),
                                validationResult == null ? List.of() : validationResult.failedChecks(),
                                null
                        ),
                        V42PromptAssetKey.PROBE_RENDERING
                )
        );
    }

    private String buildFallbackRenderReason(OrthogonalValidationResult validationResult) {
        if (validationResult == null || validationResult.failedChecks() == null || validationResult.failedChecks().isEmpty()) {
            return REASON_VALIDATION_FAILED_FALLBACK;
        }
        return REASON_VALIDATION_FAILED_FALLBACK + "[" + String.join(",", validationResult.failedChecks()) + "]";
    }

    private Map<String, Object> enrichTriggeredRulesWithValidationTrace(Map<String, Object> base,
                                                                        OrthogonalValidationResult validationResult,
                                                                        String rejectedCandidate,
                                                                        String repairApplied) {
        if ((validationResult == null || validationResult.failedChecks() == null || validationResult.failedChecks().isEmpty())
                && !StringUtils.hasText(rejectedCandidate)
                && !StringUtils.hasText(repairApplied)) {
            return base;
        }
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(base == null ? Map.of() : base);
        if (validationResult != null && validationResult.failedChecks() != null && !validationResult.failedChecks().isEmpty()) {
            enriched.put("validationFailedChecks", List.copyOf(validationResult.failedChecks()));
            enriched.put("validationFailedRuleIds", validationResult.failedChecks().stream()
                    .map(Node2RuleCatalog::validationRuleId)
                    .toList());
            enriched.put("validationRuleOrigin", Node2RuleCatalog.ORIGIN_BOUNDARY);
            enriched.put("validationBoundaryType", Node2RuleCatalog.BOUNDARY_TYPE_QUALITY);
        }
        if (validationResult != null && validationResult.gateDecisionInput() != null && !validationResult.gateDecisionInput().isEmpty()) {
            enriched.put("gateDecisionInput", validationResult.gateDecisionInput());
        }
        if (StringUtils.hasText(rejectedCandidate)) {
            enriched.put("validationRejectedCandidate", rejectedCandidate.trim());
        }
        if (StringUtils.hasText(repairApplied)) {
            enriched.put("validationRepairApplied", repairApplied.trim());
        }
        return Map.copyOf(enriched);
    }

    private Map<String, Object> enrichTriggeredRulesWithFallbackTrace(Map<String, Object> base,
                                                                      String fallbackTriggerRule,
                                                                      RuntimeException lastException) {
        if (!StringUtils.hasText(fallbackTriggerRule) && lastException == null) {
            return base;
        }
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(base == null ? Map.of() : base);
        if (StringUtils.hasText(fallbackTriggerRule)) {
            enriched.put("fallbackTriggerRule", fallbackTriggerRule);
            enriched.put("fallbackTriggerOrigin", Node2RuleCatalog.ORIGIN_BOUNDARY);
            enriched.put("fallbackBoundaryType", Node2RuleCatalog.BOUNDARY_TYPE_FALLBACK_ROUTING);
        }
        if (lastException != null && StringUtils.hasText(lastException.getMessage())) {
            enriched.put("fallbackTriggerReason", lastException.getMessage().trim());
        }
        return Map.copyOf(enriched);
    }

    private Map<String, Object> enrichTriggeredRulesWithBehaviorTrace(Map<String, Object> base,
                                                                      RealizationPlan realizationPlan,
                                                                      List<String> failedChecks,
                                                                      String renderFamily) {
        List<String> behaviorRuleIds = Node2RuleCatalog.behaviorRuleIds(
                realizationPlan == null || realizationPlan.simulationIntentProfile() == null
                        ? null
                        : realizationPlan.simulationIntentProfile().surfaceIntent(),
                failedChecks
        );
        String fallbackFamilyRuleId = Node2RuleCatalog.fallbackFamilyRuleId(renderFamily);
        if (behaviorRuleIds.isEmpty() && !StringUtils.hasText(fallbackFamilyRuleId)) {
            return base;
        }
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(base == null ? Map.of() : base);
        if (!behaviorRuleIds.isEmpty()) {
            enriched.put("behaviorRuleIds", behaviorRuleIds);
            enriched.put("behaviorRuleOrigin", Node2RuleCatalog.ORIGIN_BOUNDARY);
        }
        if (StringUtils.hasText(fallbackFamilyRuleId)) {
            enriched.put("fallbackFamilyRuleId", fallbackFamilyRuleId);
            enriched.put("fallbackFamilyRuleOrigin", Node2RuleCatalog.ORIGIN_BOUNDARY);
        }
        return Map.copyOf(enriched);
    }

    private Map<String, Object> enrichTriggeredRulesWithPromptAssetTrace(Map<String, Object> base,
                                                                         V42PromptAssetKey assetKey) {
        V42PromptAssetDescriptor descriptor = resolvePromptAssetDescriptor(assetKey);
        if (descriptor == null) {
            return base == null ? Map.of() : base;
        }
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(base == null ? Map.of() : base);
        enriched.put("promptAssetKey", descriptor.key().name());
        enriched.put("promptAssetVersion", descriptor.version());
        enriched.put("promptAssetSource", descriptor.sourceLocation());
        enriched.put("promptAssetSha256", descriptor.sourceSha256());
        return Map.copyOf(enriched);
    }

    private V42PromptAssetDescriptor resolvePromptAssetDescriptor(V42PromptAssetKey assetKey) {
        if (!(renderAiService instanceof V42PromptAssetAware promptAssetAware)) {
            return null;
        }
        try {
            return promptAssetAware.describePromptAsset(assetKey);
        } catch (RuntimeException ex) {
            log.warn("V4.2 prompt asset trace unavailable. key={}, reason={}", assetKey.name(), ex.getMessage());
            return null;
        }
    }

    private RenderedProbe renderTemplateBased(CategoryVariablePool pool,
                                              List<String> brandAliases,
                                              ProbeBlueprint blueprint) {
        String rendered = ensureQuestionTail(renderTemplatePrompt(pool, blueprint));
        ProbeExposurePolicy exposurePolicy = resolveTemplateExposurePolicy(rendered, pool.brandName(), brandAliases);
        TemplateValidationResult validationResult = evaluateTemplateValidation(rendered, pool, blueprint.realizationPlan());
        if (!validationResult.passed()) {
            FallbackCandidate fallback = buildTemplateFallbackCandidate(
                    pool,
                    brandAliases,
                    blueprint,
                    null,
                    blueprint.probeId()
            );
            rendered = fallback.prompt();
            exposurePolicy = resolveTemplateExposurePolicy(rendered, pool.brandName(), brandAliases);
            return new RenderedProbe(
                    blueprint.probeId(),
                    blueprint.bucketType(),
                    blueprint.abstractTags(),
                    blueprint.translatedTags(),
                    blueprint.realizationPlan(),
                    exposurePolicy,
                    rendered,
                    true,
                    SOURCE_DETERMINISTIC_FALLBACK,
                    fallback.family(),
                    "template_validation_failed[" + validationResult.reason() + "]",
                    enrichTriggeredRulesWithBehaviorTrace(
                            enrichTriggeredRulesWithFallbackTrace(
                                    triggeredRulesTraceComposer.composeTemplate(
                                            blueprint.bucketType(),
                                            true,
                                            "template_validation_failed[" + validationResult.reason() + "]"
                                    ),
                                    Node2RuleCatalog.RULE_FB_TEMPLATE_VALIDATION,
                                    null
                            ),
                            blueprint.realizationPlan(),
                            List.of(),
                            fallback.family()
                    )
            );
        }
        return new RenderedProbe(
            blueprint.probeId(),
            blueprint.bucketType(),
            blueprint.abstractTags(),
            blueprint.translatedTags(),
            blueprint.realizationPlan(),
            exposurePolicy,
            rendered,
            false,
            SOURCE_TEMPLATE,
            blueprint.bucketType() == BucketType.PARETO ? "pareto_template" : "adversarial_template",
            "template_rendered",
            enrichTriggeredRulesWithBehaviorTrace(
                    triggeredRulesTraceComposer.composeTemplate(
                            blueprint.bucketType(),
                            false,
                            "template_rendered"
                    ),
                    blueprint.realizationPlan(),
                    List.of(),
                    null
            )
        );
    }

    private String renderTemplatePrompt(CategoryVariablePool pool, ProbeBlueprint blueprint) {
        String baseRendered = ensureQuestionTail(applyTemplate(resolveTemplateText(pool, blueprint), pool));
        if (shouldPreserveSourceTemplate(blueprint, baseRendered, pool)) {
            return baseRendered;
        }
        return baseRendered;
    }

    private boolean shouldPreserveSourceTemplate(ProbeBlueprint blueprint,
                                                 String rendered,
                                                 CategoryVariablePool pool) {
        return blueprint != null
                && StringUtils.hasText(blueprint.templateText())
                && evaluateTemplateValidation(rendered, pool, blueprint.realizationPlan()).passed();
    }

    private String buildTopicAwareTemplatePrompt(CategoryVariablePool pool,
                                                 ProbeBlueprint blueprint,
                                                 String baseRendered) {
        if (blueprint == null || blueprint.realizationPlan() == null) {
            return baseRendered;
        }
        String topicCue = softHintValue(blueprint.realizationPlan(), "TOPIC");
        if (!StringUtils.hasText(topicCue)) {
            return baseRendered;
        }
        String normalizedTopic = normalize(topicCue);
        if (!StringUtils.hasText(normalizedTopic)) {
            return baseRendered;
        }
        return switch (blueprint.bucketType()) {
            case PARETO -> buildParetoTopicAwarePrompt(pool, blueprint.realizationPlan(), normalizedTopic, blueprint.probeId(), baseRendered);
            case ADVERSARIAL -> buildAdversarialTopicAwarePrompt(pool, blueprint.realizationPlan(), normalizedTopic, blueprint.probeId(), baseRendered);
            case ORTHOGONAL -> baseRendered;
        };
    }

    private String buildParetoTopicAwarePrompt(CategoryVariablePool pool,
                                               RealizationPlan realizationPlan,
                                               String normalizedTopic,
                                               String variantSeed,
                                               String baseRendered) {
        String anchor = primaryCategoryAnchor(pool);
        String brandSubject = templateBrandSubject(pool, realizationPlan, anchor);
        String competitor = primaryCompetitor(pool);
        if (normalizedTopic.contains("选购场景") || normalizedTopic.contains("第一次挑") || normalizedTopic.contains("基础决策")) {
            return chooseVariant(variantSeed,
                    "第一次挑" + anchor + "，先从哪几个决策点看起",
                    "刚开始看" + anchor + "时，最该先确认哪几步",
                    "入门挑" + anchor + "，先看哪几个点更不容易买错"
            );
        }
        if (normalizedTopic.contains("竞品对比") || normalizedTopic.contains("品牌对比") || normalizedTopic.contains("差异化比较")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(pool.brandName())
                            ? "同类" + anchor + "里，" + pool.brandName() + "和" + competitor + "差别先看哪点"
                            : "同类" + anchor + "里，不同品牌差别先看哪点",
                    "想比" + anchor + "和别家差异，最该先确认什么",
                    "挑" + anchor + "时，品牌之间真正该比的是哪几个点"
            );
        }
        if (normalizedTopic.contains("官网") || normalizedTopic.contains("信任") || normalizedTopic.contains("可信来源") || normalizedTopic.contains("第三方口碑") || normalizedTopic.contains("资质")) {
            return chooseVariant(variantSeed,
                    "判断" + brandSubject + "靠不靠谱，官网、可信来源和第三方口碑先看哪边",
                    "挑" + anchor + "时，品牌信任线索到底先看官网、资质还是口碑",
                    "想确认" + brandSubject + "值不值得信，最该先看哪些来源"
            );
        }
        if (normalizedTopic.contains("真实体验") || normalizedTopic.contains("口碑") || normalizedTopic.contains("优缺点") || normalizedTopic.contains("风险点")) {
            return chooseVariant(variantSeed,
                    "想看" + brandSubject + "真实口碑，先看哪些优缺点和风险点",
                    brandSubject + "真实体验里，最常被提到的问题和好评点是什么",
                    "挑" + anchor + "时，用户对" + brandSubject + "最常提到的体验反馈有哪些"
            );
        }
        if (normalizedTopic.contains("性价比") || normalizedTopic.contains("价格带") || normalizedTopic.contains("价格")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(pool.brandName())
                            ? "同价位" + anchor + "里，" + pool.brandName() + "到底值不值"
                            : "同价位" + anchor + "里，哪些更值",
                    "预算差不多时，" + brandSubject + "的性价比主要看哪点",
                    "挑" + anchor + "时，价格和配置之间怎么判断更值"
            );
        }
        if (normalizedTopic.contains("人群") || normalizedTopic.contains("适合什么人") || normalizedTopic.contains("使用场景")) {
            return chooseVariant(variantSeed,
                    "什么人、什么场景更适合选" + brandSubject,
                    "想看" + brandSubject + "适不适合自己，最该先确认什么场景",
                    brandSubject + "到底更适合哪类家庭或使用场景"
            );
        }
        if (normalizedTopic.contains("容量") || normalizedTopic.contains("空间适配") || normalizedTopic.contains("小户型") || normalizedTopic.contains("出租屋") || normalizedTopic.contains("台面空间")) {
            return chooseVariant(variantSeed,
                    "小户型或台面空间有限时，" + anchor + "先看哪些容量和空间适配点",
                    "想看" + brandSubject + "会不会占地方，最该先量哪几个点",
                    brandSubject + "容量和空间适配这块，到底先比什么"
            );
        }
        if (normalizedTopic.contains("推荐与替代") || normalizedTopic.contains("主动推荐") || normalizedTopic.contains("不指定品牌")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(pool.brandName())
                            ? "如果不指定品牌，AI 会不会主动推荐" + pool.brandName() + "这类" + anchor
                            : "如果不指定品牌，AI 会主动推荐哪类" + anchor,
                    "想看" + brandSubject + "会不会被主动推荐，最该先看什么",
                    StringUtils.hasText(pool.brandName())
                            ? "不点名时，" + pool.brandName() + "在" + anchor + "里会不会自然进入推荐名单"
                            : "不点名时，这类" + anchor + "会怎么进入推荐名单"
            );
        }
        if (normalizedTopic.contains("购买决策阻力") || normalizedTopic.contains("顾虑") || normalizedTopic.contains("劝退") || normalizedTopic.contains("决策阻力") || normalizedTopic.contains("误解")) {
            return chooseVariant(variantSeed,
                    "买" + brandSubject + "前，最常见的顾虑和劝退点是什么",
                    "下单" + brandSubject + "前，大家最常卡住的是哪几个问题",
                    "想买" + anchor + "时，什么顾虑最容易让人放弃" + brandSubject
            );
        }
        if (normalizedTopic.contains("值不值得") || normalizedTopic.contains("值得买") || normalizedTopic.contains("百元内")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(pool.brandName())
                            ? "百元内买" + anchor + "，" + pool.brandName() + "值不值得先看"
                            : "百元内买" + anchor + "，值不值得先看",
                    "预算卡在百元内，" + anchor + "到底先看谁",
                    "百元内挑" + anchor + "，最值得先看的会是谁"
            );
        }
        if (normalizedTopic.contains("防水") || normalizedTopic.contains("附加卖点")) {
            return chooseVariant(variantSeed,
                    "挑" + anchor + "时，防水静音这些卖点先看什么才知道靠不靠谱",
                    "防水静音这些卖点放在" + anchor + "里，到底值不值得当重点看",
                    "想挑" + anchor + "，防水静音这些附加卖点该怎么判断靠不靠谱"
            );
        }
        if (normalizedTopic.contains("静音") || normalizedTopic.contains("顺手")) {
            return chooseVariant(variantSeed,
                    "办公用" + anchor + "，静音和顺手度先看什么",
                    "想挑个安静顺手的" + anchor + "，最该先看哪点",
                    anchor + "要是拿来办公，静音和手感到底怎么判断"
            );
        }
        if (normalizedTopic.contains("无线") || normalizedTopic.contains("双模") || normalizedTopic.contains("连接")) {
            return chooseVariant(variantSeed,
                    "无线双模" + anchor + "，连接稳不稳先看哪点",
                    "想买无线双模" + anchor + "，连接稳定性该先看什么",
                    anchor + "要是看无线双模，最该先确认连接哪块"
            );
        }
        if (normalizedTopic.contains("学生党") || normalizedTopic.contains("宿舍")) {
            return chooseVariant(variantSeed,
                    "学生党放宿舍用的" + anchor + "，先看什么更合适",
                    "宿舍里想放一个顺手的" + anchor + "，最该先看哪点",
                    "学生党挑宿舍" + anchor + "，怎么判断更合适"
            );
        }
        if (normalizedTopic.contains("dpi") || normalizedTopic.contains("调节") || normalizedTopic.contains("实用")) {
            return chooseVariant(variantSeed,
                    "买" + anchor + "时，DPI调节到底是不是实用功能",
                    anchor + "那个DPI调节，真有必要专门看吗",
                    "挑" + anchor + "时，DPI调节值不值得当重点"
            );
        }
        if (normalizedTopic.contains("做工") || normalizedTopic.contains("耐用") || normalizedTopic.contains("长期使用")) {
            return chooseVariant(variantSeed,
                    "买" + anchor + "前，做工耐用这块先看什么",
                    "想知道" + anchor + "耐不耐用，最该先看哪点",
                    anchor + "做工这块靠不靠谱，先从什么地方判断"
            );
        }
        if (normalizedTopic.contains("办公用户") || normalizedTopic.contains("轻度游戏用户")) {
            return chooseVariant(variantSeed,
                    "办公和轻度游戏都想兼顾，" + anchor + "该怎么选",
                    "想让" + anchor + "同时兼顾办公和轻度游戏，先看什么",
                    anchor + "要兼顾办公和轻度游戏，怎么挑会更稳"
            );
        }
        if (normalizedTopic.contains("轻度游戏")) {
            return chooseVariant(variantSeed,
                    "轻度游戏用" + anchor + "，先看什么才知道够不够用",
                    "想拿" + anchor + "打轻度游戏，最该先确认什么",
                    anchor + "如果只是轻度游戏，够不够用主要看哪点"
            );
        }
        if (normalizedTopic.contains("同价位") || normalizedTopic.contains("替代") || normalizedTopic.contains("取舍")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(pool.brandName())
                            ? "同价位" + anchor + "里，" + pool.brandName() + "和" + competitor + "该怎么取舍"
                            : "同价位" + anchor + "该怎么取舍",
                    "预算差不多时，" + anchor + "该怎么在不同牌子里取舍",
                    StringUtils.hasText(pool.brandName())
                            ? "同价位想挑" + anchor + "，" + pool.brandName() + "和" + competitor + "差别先看哪"
                            : "同价位挑" + anchor + "时，先看哪点最好取舍"
            );
        }
        if (StringUtils.hasText(brandSubject)) {
            return brandSubject + "到底先看哪点更靠谱";
        }
        return baseRendered;
    }

    private String buildAdversarialTopicAwarePrompt(CategoryVariablePool pool,
                                                    RealizationPlan realizationPlan,
                                                    String normalizedTopic,
                                                    String variantSeed,
                                                    String baseRendered) {
        String anchor = primaryCategoryAnchor(pool);
        String competitor = primaryCompetitor(pool);
        String brandName = StringUtils.hasText(pool.brandName()) ? pool.brandName().trim() : "";
        String brandSubject = templateBrandSubject(pool, realizationPlan, anchor);
        if (normalizedTopic.contains("选购场景") || normalizedTopic.contains("第一次挑") || normalizedTopic.contains("基础决策")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "第一次挑" + anchor + "就直接冲" + brandName + "，会不会太冒进"
                            : "第一次挑" + anchor + "就直接下单，会不会太冒进",
                    "刚开始看" + anchor + "时，最容易忽略的坑会是哪几个",
                    "入门买" + anchor + "，如果只看一个卖点会不会最容易买错"
            );
        }
        if (normalizedTopic.contains("竞品对比") || normalizedTopic.contains("品牌对比") || normalizedTopic.contains("差异化比较")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "同类" + anchor + "里，" + brandName + "和" + competitor + "比，真正容易输在哪"
                            : "同类" + anchor + "里，不同品牌最容易拉开差距的是哪点",
                    StringUtils.hasText(brandName)
                            ? "拿" + brandName + "和" + competitor + "比" + anchor + "，会不会有些短板被忽略了"
                            : "挑" + anchor + "时，只看表面参数会不会把品牌差距看错",
                    StringUtils.hasText(brandName)
                            ? brandName + "放到同类" + anchor + "里，真的比别家更稳吗"
                            : "同类" + anchor + "里，谁更稳会不会被看错"
            );
        }
        if (normalizedTopic.contains("官网") || normalizedTopic.contains("信任") || normalizedTopic.contains("可信来源") || normalizedTopic.contains("第三方口碑") || normalizedTopic.contains("资质")) {
            return chooseVariant(variantSeed,
                    "判断" + brandSubject + "靠不靠谱，只看官网和宣传会不会太早",
                    StringUtils.hasText(brandName)
                            ? brandName + "这类" + anchor + "的信任线索，会不会包装得比实际更好看"
                            : anchor + "的信任线索，会不会包装得比实际更好看",
                    "只看官网、资质和自家说法，就相信" + brandSubject + "会不会不够"
            );
        }
        if (normalizedTopic.contains("真实体验") || normalizedTopic.contains("口碑") || normalizedTopic.contains("优缺点") || normalizedTopic.contains("风险点")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "网上说" + brandName + anchor + "真实体验两极分化，这事靠谱吗"
                            : anchor + "真实体验两极分化，这事靠谱吗",
                    "如果看差评和翻车反馈，" + brandSubject + "最容易被质疑的是哪块",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "那些好评，会不会把真实问题盖住了"
                            : anchor + "那些好评，会不会把真实问题盖住了"
            );
        }
        if (normalizedTopic.contains("性价比") || normalizedTopic.contains("价格带") || normalizedTopic.contains("价格")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandSubject + "是不是定价偏高，换成别家更划算"
                            : anchor + "是不是定价偏高，换成别家更划算",
                    StringUtils.hasText(brandName)
                            ? "预算差不多时，绕开" + brandName + "会不会反而更值"
                            : "预算差不多时，换成别家会不会反而更值",
                    StringUtils.hasText(brandName)
                            ? brandName + "放在同价位" + anchor + "里，会不会其实没那么值"
                            : "同价位" + anchor + "里，会不会有些看着划算其实不值"
            );
        }
        if (normalizedTopic.contains("人群") || normalizedTopic.contains("适合什么人") || normalizedTopic.contains("使用场景")) {
            return chooseVariant(variantSeed,
                    "把" + brandSubject + "推荐给大多数人，会不会其实并不适配",
                    "什么人或什么场景，反而不太适合选" + brandSubject,
                    brandSubject + "是不是只适合很窄的一类使用场景"
            );
        }
        if (normalizedTopic.contains("容量") || normalizedTopic.contains("空间适配") || normalizedTopic.contains("小户型") || normalizedTopic.contains("出租屋") || normalizedTopic.contains("台面空间")) {
            return chooseVariant(variantSeed,
                    "如果厨房空间有限，" + brandSubject + "最容易不合适的是哪点",
                    "小户型或台面小的话，" + brandSubject + "会不会反而更占地方",
                    brandSubject + "容量和空间适配这块，最容易踩坑的会是什么"
            );
        }
        if (normalizedTopic.contains("推荐与替代") || normalizedTopic.contains("主动推荐") || normalizedTopic.contains("不指定品牌")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "如果不点名，AI 凭什么一定会推荐" + brandName + "而不是" + competitor
                            : "如果不点名，AI 凭什么会推荐这类" + anchor,
                    StringUtils.hasText(brandName)
                            ? "不看" + brandName + "，" + anchor + "还有谁更能打"
                            : "不看这类" + anchor + "，还有谁更能打",
                    StringUtils.hasText(brandName)
                            ? brandName + "在不点名推荐里，会不会其实很容易被别家替掉"
                            : "不点名推荐里，会不会很容易被别家替掉"
            );
        }
        if (normalizedTopic.contains("购买决策阻力") || normalizedTopic.contains("顾虑") || normalizedTopic.contains("劝退") || normalizedTopic.contains("决策阻力") || normalizedTopic.contains("误解")) {
            return chooseVariant(variantSeed,
                    "买" + brandSubject + "前，最容易劝退人的会是哪点",
                    "下单前如果还犹豫，" + brandSubject + "最该怀疑的是什么",
                    StringUtils.hasText(brandName)
                            ? brandName + "这类" + anchor + "最常见的误解和顾虑，会不会比想象中更多"
                            : anchor + "最常见的误解和顾虑，会不会比想象中更多"
            );
        }
        if (normalizedTopic.contains("值不值得") || normalizedTopic.contains("值得买") || normalizedTopic.contains("百元内")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "百元内" + anchor + "里不看" + brandName + "，还有谁更值"
                            : "百元内" + anchor + "还有谁更值",
                    StringUtils.hasText(brandName)
                            ? "预算压在百元内，" + anchor + "不选" + brandName + "会不会更划算"
                            : "预算卡在百元内，" + anchor + "还有谁更划算",
                    "百元内" + anchor + "里，现在更值得先避开谁"
            );
        }
        if (normalizedTopic.contains("防水") || normalizedTopic.contains("附加卖点")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "宣传的防水静音，是不是被说过头了"
                            : anchor + "宣传的防水静音，是不是被说过头了",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "那些防水静音卖点，真有必要当卖点看吗"
                            : anchor + "那些防水静音卖点，真有必要当卖点看吗",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "主打的防水静音，会不会更像噱头"
                            : anchor + "主打的防水静音，会不会更像噱头"
            );
        }
        if (normalizedTopic.contains("静音") || normalizedTopic.contains("顺手")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "办公静音" + anchor + "真没必要看" + brandName + "吗"
                            : "办公静音" + anchor + "是不是被吹得太过了",
                    StringUtils.hasText(brandName)
                            ? brandName + "做办公静音" + anchor + "这条线，真有那么稳吗"
                            : "办公静音" + anchor + "这条线，真有那么稳吗",
                    StringUtils.hasText(brandName)
                            ? "想找安静顺手的" + anchor + "，" + brandName + "是不是并不占优"
                            : "想找安静顺手的" + anchor + "，是不是并不占优"
            );
        }
        if (normalizedTopic.contains("无线") || normalizedTopic.contains("双模") || normalizedTopic.contains("连接")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + "这种无线双模" + anchor + "，连接翻车的说法靠谱吗"
                            : "无线双模" + anchor + "翻车的说法靠谱吗",
                    StringUtils.hasText(brandName)
                            ? brandName + "做无线双模" + anchor + "，掉连这类说法到底夸不夸张"
                            : "无线双模" + anchor + "掉连这类说法到底夸不夸张",
                    StringUtils.hasText(brandName)
                            ? "想买" + brandName + anchor + "，无线双模连接会不会更容易出问题"
                            : "想买无线双模" + anchor + "，连接会不会更容易出问题"
            );
        }
        if (normalizedTopic.contains("学生党") || normalizedTopic.contains("宿舍")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "学生党买宿舍" + anchor + "，" + brandName + "是不是并不划算"
                            : "学生党买宿舍" + anchor + "是不是更该避坑",
                    StringUtils.hasText(brandName)
                            ? "宿舍里想放一个" + anchor + "，" + brandName + "会不会并不合适"
                            : "宿舍里想放一个" + anchor + "，会不会更该避坑",
                    StringUtils.hasText(brandName)
                            ? "学生党挑宿舍" + anchor + "，" + brandName + "是不是容易花冤枉钱"
                            : "学生党挑宿舍" + anchor + "，是不是容易花冤枉钱"
            );
        }
        if (normalizedTopic.contains("dpi") || normalizedTopic.contains("调节") || normalizedTopic.contains("实用")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "那个DPI调节，是实用还是噱头"
                            : anchor + "那个DPI调节，是实用还是噱头",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "主打的DPI调节，真有必要当卖点看吗"
                            : anchor + "主打的DPI调节，真有必要当卖点看吗",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "的DPI调节，会不会更像参数包装"
                            : anchor + "的DPI调节，会不会更像参数包装"
            );
        }
        if (normalizedTopic.contains("做工") || normalizedTopic.contains("耐用") || normalizedTopic.contains("长期使用")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "做工耐用这块，真有网上说得那么悬吗"
                            : anchor + "做工耐用这块，真有网上说得那么悬吗",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "长期用下来，做工这块会不会更容易翻车"
                            : anchor + "长期用下来，做工这块会不会更容易翻车",
                    StringUtils.hasText(brandName)
                            ? "如果担心" + brandName + anchor + "不耐用，最该怀疑的会是哪块"
                            : "如果担心" + anchor + "不耐用，最该怀疑的会是哪块"
            );
        }
        if (normalizedTopic.contains("办公用户") || normalizedTopic.contains("轻度游戏用户")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "想兼顾办公和轻度游戏，是不是两头都一般"
                            : anchor + "想兼顾办公和轻度游戏，是不是两头都一般",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "如果既想办公又想轻度游戏，会不会反而哪头都不突出"
                            : anchor + "如果既想办公又想轻度游戏，会不会反而哪头都不突出",
                    StringUtils.hasText(brandName)
                            ? "把" + brandName + anchor + "放到办公和轻度游戏之间看，会不会有点尴尬"
                            : anchor + "放到办公和轻度游戏之间看，会不会有点尴尬"
            );
        }
        if (normalizedTopic.contains("轻度游戏")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "打轻度游戏是不是有点勉强"
                            : anchor + "打轻度游戏是不是有点勉强",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "如果拿来轻度游戏，会不会其实不太够"
                            : anchor + "如果拿来轻度游戏，会不会其实不太够",
                    StringUtils.hasText(brandName)
                            ? brandName + anchor + "只打轻度游戏的话，短板会不会更明显"
                            : anchor + "只打轻度游戏的话，短板会不会更明显"
            );
        }
        if (normalizedTopic.contains("同价位") || normalizedTopic.contains("替代") || normalizedTopic.contains("取舍")) {
            return chooseVariant(variantSeed,
                    StringUtils.hasText(brandName)
                            ? "同价位" + anchor + "里不选" + brandName + "，换成" + competitor + "会不会更稳"
                            : "同价位" + anchor + "里换成" + competitor + "会不会更稳",
                    StringUtils.hasText(brandName)
                            ? "预算差不多时，" + anchor + "直接绕开" + brandName + "会不会更省心"
                            : "预算差不多时，" + anchor + "换成别家会不会更省心",
                    StringUtils.hasText(brandName)
                            ? "同价位挑" + anchor + "，" + brandName + "是不是更容易被替代"
                            : "同价位挑" + anchor + "，是不是更容易被替代"
            );
        }
        return baseRendered;
    }

    private String chooseVariant(String seed, String... variants) {
        if (variants == null || variants.length == 0) {
            return "";
        }
        int index = 0;
        if (StringUtils.hasText(seed)) {
            index = Math.floorMod(seed.trim().hashCode(), variants.length);
        }
        return variants[index];
    }

    private TemplateValidationResult evaluateTemplateValidation(String rendered,
                                                                CategoryVariablePool pool,
                                                                RealizationPlan realizationPlan) {
        if (!StringUtils.hasText(rendered)) {
            return new TemplateValidationResult(false, "empty");
        }
        if (rendered.trim().length() > MAX_TEMPLATE_PROMPT_LENGTH) {
            return new TemplateValidationResult(false, "too_long");
        }
        if (containsBannedWord(rendered)) {
            return new TemplateValidationResult(false, "banned_word");
        }
        if (containsUnresolvedPlaceholder(rendered)) {
            return new TemplateValidationResult(false, "placeholder");
        }
        if (!containsCategoryAnchor(rendered, pool)) {
            return new TemplateValidationResult(false, "missing_anchor");
        }
        if (!matchesTemplateSemanticCues(rendered, realizationPlan)) {
            return new TemplateValidationResult(false, "semantic_misaligned");
        }
        return new TemplateValidationResult(true, "ok");
    }

    private boolean matchesTemplateSemanticCues(String rendered, RealizationPlan realizationPlan) {
        if (realizationPlan == null) {
            return true;
        }
        List<String> cues = extractTemplateSemanticCues(realizationPlan);
        if (cues.isEmpty()) {
            return true;
        }
        String normalizedRendered = canonicalPrompt(rendered);
        return cues.stream()
                .map(OrthogonalPromptFingerprint::canonical)
                .filter(StringUtils::hasText)
                .anyMatch(normalizedRendered::contains);
    }

    private List<String> extractTemplateSemanticCues(RealizationPlan realizationPlan) {
        LinkedHashSet<String> cues = new LinkedHashSet<>();
        addTemplateCueTerms(cues, softHintValue(realizationPlan, "EVIDENCE"));
        addTemplateCueTerms(cues, softHintValue(realizationPlan, "TOPIC"));
        return List.copyOf(cues);
    }

    private void addTemplateCueTerms(Set<String> collector, String rawCue) {
        if (!StringUtils.hasText(rawCue)) {
            return;
        }
        String normalized = rawCue.trim();
        for (String candidate : TEMPLATE_ALIGNMENT_CUES) {
            if (normalized.contains(candidate)) {
                collector.add(candidate);
            }
        }
    }

    private FallbackCandidate buildTemplateFallbackCandidate(CategoryVariablePool pool,
                                                             List<String> brandAliases,
                                                             ProbeBlueprint blueprint,
                                                             @Nullable Set<String> usedCanonicalPrompts,
                                                             String seed) {
        List<FallbackCandidate> candidates = rotateCandidates(
                buildTemplateFallbackCandidates(pool, blueprint),
                seed,
                blueprint.realizationPlan()
        );
        for (FallbackCandidate candidate : candidates) {
            String finalized = ensureQuestionTail(candidate.prompt());
            if (evaluateTemplateValidation(finalized, pool, blueprint.realizationPlan()).passed()
                    && tryClaimPromptAllowStructuralDuplicate(usedCanonicalPrompts, finalized)) {
                return new FallbackCandidate(candidate.family(), finalized);
            }
        }
        for (FallbackCandidate candidate : candidates) {
            String finalized = ensureQuestionTail(candidate.prompt());
            if (tryClaimPromptAllowStructuralDuplicate(usedCanonicalPrompts, finalized)) {
                return new FallbackCandidate(candidate.family(), finalized);
            }
        }
        String emergencyPrompt = ensureQuestionTail(buildTemplateGenericFallback(pool, blueprint.bucketType()));
        ProbeExposurePolicy exposurePolicy = resolveTemplateExposurePolicy(emergencyPrompt, pool.brandName(), brandAliases);
        return new FallbackCandidate(
                blueprint.bucketType() == BucketType.PARETO
                        ? "pareto_template_emergency"
                        : "adversarial_template_emergency",
                ensureTemplateBrandPolicy(emergencyPrompt, exposurePolicy, pool)
        );
    }

    private List<FallbackCandidate> buildTemplateFallbackCandidates(CategoryVariablePool pool,
                                                                    ProbeBlueprint blueprint) {
        if (blueprint == null) {
            return List.of();
        }
        ProbeExposurePolicy exposurePolicy = resolveTemplatePlanExposurePolicy(blueprint.realizationPlan(), pool);
        String subject = fallbackPromptComposer.subjectPhrase(
                primaryCategoryAnchor(pool),
                exposurePolicy,
                pool.brandName(),
                pool.contextPackage()
        );
        LinkedHashMap<String, FallbackCandidate> candidates = new LinkedHashMap<>();
        for (String supportClause : fallbackPromptComposer.fallbackSupportClauses(blueprint.realizationPlan(), exposurePolicy)) {
            if (blueprint.bucketType() == BucketType.PARETO) {
                addCandidate(candidates, "pareto_template_fallback_browse",
                        fallbackPromptComposer.fallbackBrowseQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
                addCandidate(candidates, "pareto_template_fallback_short",
                        fallbackPromptComposer.fallbackShortQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
                addCandidate(candidates, "pareto_template_fallback_core",
                        fallbackPromptComposer.fallbackCoreQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
            } else {
                addCandidate(candidates, "adversarial_template_fallback_core",
                        fallbackPromptComposer.fallbackCoreQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
                addCandidate(candidates, "adversarial_template_fallback_angle",
                        fallbackPromptComposer.fallbackAngleQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
                addCandidate(candidates, "adversarial_template_fallback_short",
                        fallbackPromptComposer.fallbackShortQuestion(subject, exposurePolicy, blueprint.realizationPlan(), supportClause));
            }
        }
        addCandidate(
                candidates,
                blueprint.bucketType() == BucketType.PARETO ? "pareto_template_generic" : "adversarial_template_generic",
                buildTemplateGenericFallback(pool, blueprint.bucketType())
        );
        return List.copyOf(candidates.values());
    }

    private RenderedProbe buildTemplateSurfaceRepairProbe(CategoryVariablePool pool,
                                                          List<String> brandAliases,
                                                          ProbeBlueprint blueprint,
                                                          Set<String> usedCanonicalPrompts,
                                                          String reason,
                                                          RenderedProbe original) {
        FallbackCandidate fallback = buildTemplateFallbackCandidate(
                pool,
                brandAliases,
                blueprint,
                usedCanonicalPrompts,
                blueprint.probeId() + "|" + reason
        );
        String rendered = ensureQuestionTail(fallback.prompt());
        ProbeExposurePolicy exposurePolicy = resolveTemplateExposurePolicy(rendered, pool.brandName(), brandAliases);
        return new RenderedProbe(
                blueprint.probeId(),
                blueprint.bucketType(),
                blueprint.abstractTags(),
                blueprint.translatedTags(),
                blueprint.realizationPlan(),
                exposurePolicy,
                rendered,
                true,
                SOURCE_DETERMINISTIC_FALLBACK,
                fallback.family(),
                reason,
                triggeredRulesTraceComposer.composeTemplate(
                        blueprint.bucketType(),
                        true,
                        reason
                )
        );
    }

    private RenderedProbe buildOrthogonalSurfaceRepairProbe(String diagnosisId,
                                                            CategoryVariablePool pool,
                                                            List<String> brandAliases,
                                                            ProbeBlueprint blueprint,
                                                            Set<String> usedCanonicalPrompts,
                                                            String reason,
                                                            RenderedProbe original) {
        ProbeExposurePolicy exposurePolicy = ProbeExposurePolicy.classifyOrthogonal(blueprint.abstractTags());
        List<String> brandExposureTerms = resolveBrandExposureTerms(pool.brandName(), brandAliases);
        FallbackCandidate fallback = buildOrthogonalFallback(
                pool,
                brandExposureTerms,
                exposurePolicy,
                blueprint.realizationPlan(),
                blueprint.translatedTags(),
                diagnosisId,
                blueprint.probeId(),
                usedCanonicalPrompts
        );
        return new RenderedProbe(
                blueprint.probeId(),
                blueprint.bucketType(),
                blueprint.abstractTags(),
                blueprint.translatedTags(),
                blueprint.realizationPlan(),
                exposurePolicy,
                fallback.prompt(),
                true,
                SOURCE_DETERMINISTIC_FALLBACK,
                fallback.family(),
                reason,
                enrichTriggeredRulesWithValidationTrace(
                        triggeredRulesTraceComposer.composeOrthogonal(
                                blueprint.abstractTags(),
                                threeLayerMappingEvaluator.evaluate(blueprint.abstractTags()),
                                true,
                                reason
                        ),
                        null,
                        original == null ? null : original.renderedPrompt(),
                        null
                )
        );
    }

    private PromptPayload buildOrthogonalPayload(CategoryVariablePool pool, ProbeBlueprint blueprint) {
        validateOrthogonalInputs(pool, blueprint);

        MappingResult mappingResult = threeLayerMappingEvaluator.evaluate(blueprint.abstractTags());
        if (blueprint.realizationPlan() == null) {
            throw new IllegalArgumentException("realizationPlan 不能为空");
        }
        return new PromptPayload(mappingResult, blueprint.realizationPlan());
    }

    private void validateOrthogonalInputs(CategoryVariablePool pool, ProbeBlueprint blueprint) {
        if (blueprint.bucketType() != BucketType.ORTHOGONAL) {
            return;
        }
        if (pool.categoryAnchors() == null || pool.categoryAnchors().isEmpty()) {
            throw new IllegalArgumentException("categoryAnchors 不能为空");
        }
        for (String dimensionId : CssmHiAbstractStates.DIMENSION_IDS) {
            if (!StringUtils.hasText(blueprint.abstractTags().get(dimensionId))) {
                throw new IllegalArgumentException("abstractTags 缺少维度: " + dimensionId);
            }
            if (!StringUtils.hasText(blueprint.translatedTags().get(dimensionId))) {
                throw new IllegalArgumentException("translatedTags 缺少维度: " + dimensionId);
            }
        }
    }

    private boolean passesOrthogonalValidation(String rendered,
                                               CategoryVariablePool pool,
                                               List<String> brandExposureTerms,
                                               ProbeExposurePolicy exposurePolicy,
                                               RealizationPlan realizationPlan,
                                               Map<String, String> translatedTags,
                                               Set<String> usedCanonicalPrompts,
                                               boolean rejectDuplicates) {
        return evaluateOrthogonalValidation(
                rendered,
                pool,
                brandExposureTerms,
                exposurePolicy,
                realizationPlan,
                translatedTags,
                usedCanonicalPrompts,
                rejectDuplicates
        ).passed();
    }

    private OrthogonalValidationResult evaluateOrthogonalValidation(String rendered,
                                                                    CategoryVariablePool pool,
                                                                    List<String> brandExposureTerms,
                                                                    ProbeExposurePolicy exposurePolicy,
                                                                    RealizationPlan realizationPlan,
                                                                    Map<String, String> translatedTags,
                                                                    Set<String> usedCanonicalPrompts,
                                                                    boolean rejectDuplicates) {
        List<String> failedChecks = new ArrayList<>();
        String normalized = normalize(rendered);
        if (!StringUtils.hasText(normalized)) {
            failedChecks.add("empty");
            return new OrthogonalValidationResult(false, List.copyOf(failedChecks), Map.of(
                    "promptLength", 0,
                    "maxPromptLength", MAX_ORTHOGONAL_PROMPT_LENGTH,
                    "normalizedEmpty", true
            ));
        }
        int promptLength = rendered.trim().length();
        boolean overLength = promptLength > MAX_ORTHOGONAL_PROMPT_LENGTH;
        if (overLength) {
            failedChecks.add("length");
        }
        boolean bannedWordHit = containsBannedWord(rendered);
        boolean unresolvedPlaceholderHit = containsUnresolvedPlaceholder(rendered);
        if (bannedWordHit || unresolvedPlaceholderHit) {
            failedChecks.add("banned_or_placeholder");
        }
        boolean literalizationHit = containsLiteralizationExpression(rendered);
        if (literalizationHit) {
            failedChecks.add("literalization");
        }
        boolean anchorHit = containsCategoryAnchor(rendered, pool);
        if (!anchorHit) {
            failedChecks.add("anchor");
        }
        boolean brandExposureHit = containsBrandExposure(rendered, brandExposureTerms);
        if (exposurePolicy == ProbeExposurePolicy.BRAND_HIDDEN && brandExposureHit) {
            failedChecks.add("brand_hidden_exposed");
        }
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE && !brandExposureHit) {
            failedChecks.add("brand_visible_missing");
        }
        boolean officialCueRequired = requiresOfficialCue(realizationPlan);
        boolean officialCueHit = containsOfficialCueObject(rendered);
        if (officialCueRequired && !officialCueHit) {
            failedChecks.add("official_cue");
        }
        boolean semanticHit = matchesRequiredSemantics(rendered, realizationPlan, translatedTags);
        if (!semanticHit) {
            failedChecks.add("semantic");
        }
        boolean canonicalDuplicateHit = rejectDuplicates
                && usedCanonicalPrompts != null
                && usedCanonicalPrompts.contains(canonicalPrompt(rendered));
        if (canonicalDuplicateHit) {
            failedChecks.add("duplicate");
        }
        boolean structuralDuplicateHit = rejectDuplicates
                && usedCanonicalPrompts != null
                && OrthogonalPromptFingerprint.hasStructuralDuplicate(rendered, usedCanonicalPrompts);
        if (structuralDuplicateHit) {
            failedChecks.add("structural_duplicate");
        }
        String mechanicalHeuristicId = matchedMechanicalHeuristicId(rendered);
        boolean mechanicalHit = mechanicalHeuristicId != null;
        if (mechanicalHit) {
            failedChecks.add("mechanical");
        }
        String awkwardHeuristicId = matchedAwkwardHeuristicId(rendered);
        boolean awkwardHit = awkwardHeuristicId != null;
        if (awkwardHit) {
            failedChecks.add("awkward");
        }
        LinkedHashMap<String, Object> gateDecisionInput = new LinkedHashMap<>();
        gateDecisionInput.put("promptLength", promptLength);
        gateDecisionInput.put("maxPromptLength", MAX_ORTHOGONAL_PROMPT_LENGTH);
        gateDecisionInput.put("bannedWordHit", bannedWordHit);
        gateDecisionInput.put("unresolvedPlaceholderHit", unresolvedPlaceholderHit);
        gateDecisionInput.put("literalizationHit", literalizationHit);
        gateDecisionInput.put("anchorHit", anchorHit);
        if (exposurePolicy != null) {
            gateDecisionInput.put("brandExposurePolicy", exposurePolicy.name());
        }
        gateDecisionInput.put("brandExposureHit", brandExposureHit);
        gateDecisionInput.put("officialCueRequired", officialCueRequired);
        gateDecisionInput.put("officialCueHit", officialCueHit);
        gateDecisionInput.put("semanticHit", semanticHit);
        gateDecisionInput.put("canonicalDuplicateHit", canonicalDuplicateHit);
        gateDecisionInput.put("structuralDuplicateHit", structuralDuplicateHit);
        gateDecisionInput.put("mechanicalHit", mechanicalHit);
        if (mechanicalHeuristicId != null) {
            gateDecisionInput.put("mechanicalHeuristicId", mechanicalHeuristicId);
        }
        gateDecisionInput.put("awkwardHit", awkwardHit);
        if (awkwardHeuristicId != null) {
            gateDecisionInput.put("awkwardHeuristicId", awkwardHeuristicId);
        }
        return new OrthogonalValidationResult(failedChecks.isEmpty(), List.copyOf(failedChecks), Map.copyOf(gateDecisionInput));
    }

    private boolean requiresOfficialCue(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.realizationConstraints() == null) {
            return false;
        }
        for (String hint : realizationPlan.realizationConstraints().softSemanticHints()) {
            if (!StringUtils.hasText(hint)) {
                continue;
            }
            String trimmed = hint.trim();
            if (trimmed.contains("官网")
                    || trimmed.contains("官方")
                    || trimmed.contains("可信来源")
                    || trimmed.contains("来源")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOfficialCueObject(String rendered) {
        if (!StringUtils.hasText(rendered)) {
            return false;
        }
        return rendered.contains("官网")
                || rendered.contains("官方")
                || rendered.contains("可信来源")
                || rendered.contains("来源");
    }

    private boolean tryClaimPrompt(Set<String> usedCanonicalPrompts, String rendered) {
        if (usedCanonicalPrompts == null) {
            return true;
        }
        if (usedCanonicalPrompts.contains(canonicalPrompt(rendered))) {
            return false;
        }
        if (OrthogonalPromptFingerprint.hasStructuralDuplicate(rendered, usedCanonicalPrompts)) {
            return false;
        }
        return usedCanonicalPrompts.add(canonicalPrompt(rendered));
    }

    private boolean passesOrthogonalValidationAllowStructuralDuplicate(String rendered,
                                                                       CategoryVariablePool pool,
                                                                       List<String> brandExposureTerms,
                                                                       ProbeExposurePolicy exposurePolicy,
                                                                       RealizationPlan realizationPlan,
                                                                       Map<String, String> translatedTags,
                                                                       Set<String> usedCanonicalPrompts) {
        if (hasCanonicalDuplicate(usedCanonicalPrompts, rendered)) {
            return false;
        }
        return evaluateOrthogonalValidation(
                rendered,
                pool,
                brandExposureTerms,
                exposurePolicy,
                realizationPlan,
                translatedTags,
                null,
                false
        ).passed();
    }

    private boolean tryClaimPromptAllowStructuralDuplicate(Set<String> usedCanonicalPrompts, String rendered) {
        if (usedCanonicalPrompts == null) {
            return true;
        }
        if (hasCanonicalDuplicate(usedCanonicalPrompts, rendered)) {
            return false;
        }
        return usedCanonicalPrompts.add(canonicalPrompt(rendered));
    }

    private boolean hasCanonicalDuplicate(Set<String> usedCanonicalPrompts, String rendered) {
        return usedCanonicalPrompts != null && usedCanonicalPrompts.contains(canonicalPrompt(rendered));
    }

    private boolean isTokenBudgetFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof LlmTokenBudgetExceededException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Keep this overload for reflection-based regression tests that still pass LinkedHashSet.
     */
    @SuppressWarnings("unused")
    private OrthogonalValidationResult evaluateOrthogonalValidation(String rendered,
                                                                    CategoryVariablePool pool,
                                                                    List<String> brandExposureTerms,
                                                                    ProbeExposurePolicy exposurePolicy,
                                                                    RealizationPlan realizationPlan,
                                                                    LinkedHashSet<String> usedCanonicalPrompts,
                                                                    boolean rejectDuplicates) {
        return evaluateOrthogonalValidation(
                rendered,
                pool,
                brandExposureTerms,
                exposurePolicy,
                realizationPlan,
                null,
                (Set<String>) usedCanonicalPrompts,
                rejectDuplicates
        );
    }

    private AnchorRepairAttempt tryRepairMissingCategoryAnchor(String rendered,
                                                               OrthogonalValidationResult validationResult,
                                                               CategoryVariablePool pool,
                                                               List<String> brandExposureTerms,
                                                               ProbeExposurePolicy exposurePolicy,
                                                               RealizationPlan realizationPlan,
                                                               Map<String, String> translatedTags,
                                                               Set<String> usedCanonicalPrompts) {
        if (!shouldAttemptAnchorRepair(validationResult)) {
            return null;
        }
        for (String repairedPrompt : buildCategoryAnchorRepairCandidates(rendered, pool)) {
            OrthogonalValidationResult repairedValidation = evaluateOrthogonalValidation(
                    repairedPrompt,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts,
                    true
            );
            if (repairedValidation.passed()) {
                return new AnchorRepairAttempt(repairedPrompt, repairedValidation);
            }
        }
        return null;
    }

    private boolean shouldAttemptAnchorRepair(OrthogonalValidationResult validationResult) {
        if (validationResult == null || validationResult.failedChecks() == null || validationResult.failedChecks().isEmpty()) {
            return false;
        }
        boolean anchorFailed = false;
        for (String failedCheck : validationResult.failedChecks()) {
            if ("anchor".equals(failedCheck)) {
                anchorFailed = true;
                continue;
            }
            if ("semantic".equals(failedCheck)) {
                continue;
            }
            return false;
        }
        return anchorFailed;
    }

    private BrandRepairAttempt tryRepairMissingBrandExposure(String rendered,
                                                             OrthogonalValidationResult validationResult,
                                                             CategoryVariablePool pool,
                                                             List<String> brandExposureTerms,
                                                             ProbeExposurePolicy exposurePolicy,
                                                             RealizationPlan realizationPlan,
                                                             Map<String, String> translatedTags,
                                                             Set<String> usedCanonicalPrompts) {
        if (!shouldAttemptBrandRepair(validationResult, pool, exposurePolicy)) {
            return null;
        }
        for (String repairedPrompt : buildBrandRepairCandidates(rendered, pool, brandExposureTerms)) {
            OrthogonalValidationResult repairedValidation = evaluateOrthogonalValidation(
                    repairedPrompt,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts,
                    true
            );
            if (repairedValidation.passed()) {
                return new BrandRepairAttempt(repairedPrompt, repairedValidation, "brand_visible");
            }
        }
        return null;
    }

    private MechanicalRepairAttempt tryRepairMechanicalPrompt(String rendered,
                                                              OrthogonalValidationResult validationResult,
                                                              CategoryVariablePool pool,
                                                              List<String> brandExposureTerms,
                                                              ProbeExposurePolicy exposurePolicy,
                                                              RealizationPlan realizationPlan,
                                                              Map<String, String> translatedTags,
                                                              Set<String> usedCanonicalPrompts) {
        if (!shouldAttemptMechanicalRepair(validationResult)) {
            return null;
        }
        for (String repairedPrompt : buildMechanicalRepairCandidates(rendered, pool, exposurePolicy, realizationPlan)) {
            OrthogonalValidationResult repairedValidation = evaluateOrthogonalValidation(
                    repairedPrompt,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts,
                    true
            );
            if (repairedValidation.passed()) {
                return new MechanicalRepairAttempt(repairedPrompt, repairedValidation, "mechanical");
            }
        }
        return null;
    }

    private OfficialCueRepairAttempt tryRepairMissingOfficialCue(String rendered,
                                                                 OrthogonalValidationResult validationResult,
                                                                 CategoryVariablePool pool,
                                                                 List<String> brandExposureTerms,
                                                                 ProbeExposurePolicy exposurePolicy,
                                                                 RealizationPlan realizationPlan,
                                                                 Map<String, String> translatedTags,
                                                                 Set<String> usedCanonicalPrompts) {
        if (!shouldAttemptOfficialCueRepair(validationResult, realizationPlan)) {
            return null;
        }
        for (String repairedPrompt : buildOfficialCueRepairCandidates(
                rendered,
                pool,
                brandExposureTerms,
                exposurePolicy,
                realizationPlan
        )) {
            OrthogonalValidationResult repairedValidation = evaluateOrthogonalValidation(
                    repairedPrompt,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts,
                    true
            );
            if (repairedValidation.passed()) {
                return new OfficialCueRepairAttempt(repairedPrompt, repairedValidation, "official_cue");
            }
        }
        return null;
    }

    private boolean shouldAttemptBrandRepair(OrthogonalValidationResult validationResult,
                                             CategoryVariablePool pool,
                                             ProbeExposurePolicy exposurePolicy) {
        if (exposurePolicy != ProbeExposurePolicy.BRAND_VISIBLE
                || pool == null
                || !StringUtils.hasText(pool.brandName())
                || validationResult == null
                || validationResult.failedChecks() == null
                || validationResult.failedChecks().isEmpty()) {
            return false;
        }
        boolean brandMissing = false;
        for (String failedCheck : validationResult.failedChecks()) {
            switch (failedCheck) {
                case "brand_visible_missing" -> brandMissing = true;
                case "anchor", "semantic", "mechanical", "awkward" -> {
                }
                default -> {
                    return false;
                }
            }
        }
        return brandMissing;
    }

    private boolean shouldAttemptMechanicalRepair(OrthogonalValidationResult validationResult) {
        if (validationResult == null
                || validationResult.failedChecks() == null
                || validationResult.failedChecks().isEmpty()) {
            return false;
        }
        return validationResult.failedChecks().contains("mechanical");
    }

    private boolean shouldAttemptOfficialCueRepair(OrthogonalValidationResult validationResult,
                                                   RealizationPlan realizationPlan) {
        if (!requiresOfficialCue(realizationPlan)
                || validationResult == null
                || validationResult.failedChecks() == null
                || validationResult.failedChecks().isEmpty()) {
            return false;
        }
        boolean officialCueMissing = false;
        for (String failedCheck : validationResult.failedChecks()) {
            switch (failedCheck) {
                case "official_cue" -> officialCueMissing = true;
                case "anchor", "semantic", "brand_visible_missing", "mechanical", "awkward" -> {
                }
                default -> {
                    return false;
                }
            }
        }
        return officialCueMissing;
    }

    private List<String> buildBrandRepairCandidates(String rendered,
                                                    CategoryVariablePool pool,
                                                    List<String> brandExposureTerms) {
        if (!StringUtils.hasText(rendered) || pool == null || !StringUtils.hasText(pool.brandName())) {
            return List.of();
        }
        LinkedHashSet<String> repaired = new LinkedHashSet<>();
        List<String> anchorAlignedCandidates = containsCategoryAnchor(rendered, pool)
                ? List.of(rendered)
                : buildCategoryAnchorRepairCandidates(rendered, pool);
        if (anchorAlignedCandidates.isEmpty()) {
            anchorAlignedCandidates = List.of(rendered);
        }
        for (String candidate : anchorAlignedCandidates) {
            String injected = injectBrandIntoAnchor(candidate, pool);
            if (!StringUtils.hasText(injected) || !containsBrandExposure(injected, brandExposureTerms)) {
                continue;
            }
            repaired.add(ensureQuestionTail(injected));
        }
        repaired.removeIf(candidate -> normalize(candidate).equals(normalize(rendered)));
        return List.copyOf(repaired);
    }

    private List<String> buildMechanicalRepairCandidates(String rendered,
                                                         CategoryVariablePool pool,
                                                         ProbeExposurePolicy exposurePolicy,
                                                         RealizationPlan realizationPlan) {
        if (!StringUtils.hasText(rendered)) {
            return List.of();
        }
        LinkedHashSet<String> repaired = new LinkedHashSet<>();
        String normalized = ensureQuestionTail(rendered);

        addMechanicalRepairCandidate(repaired, rewriteMechanicalStem(normalized));
        addMechanicalRepairCandidate(repaired, rewriteMechanicalAffectionSeek(normalized, pool, exposurePolicy, realizationPlan));
        String fallbackSeed = normalize(rendered) + "|mechanical-repair";
        List<FallbackCandidate> fallbackCandidates = rotateCandidates(
                buildOrthogonalFallbackCandidates(pool, exposurePolicy, realizationPlan, fallbackSeed),
                fallbackSeed,
                realizationPlan
        );
        fallbackCandidates.stream()
                .map(FallbackCandidate::prompt)
                .forEach(candidate -> addMechanicalRepairCandidate(repaired, candidate));

        repaired.removeIf(candidate -> normalize(candidate).equals(normalize(rendered)));
        return List.copyOf(repaired);
    }

    private List<String> buildOfficialCueRepairCandidates(String rendered,
                                                          CategoryVariablePool pool,
                                                          List<String> brandExposureTerms,
                                                          ProbeExposurePolicy exposurePolicy,
                                                          RealizationPlan realizationPlan) {
        if (!StringUtils.hasText(rendered)) {
            return List.of();
        }
        OfficialCuePhrase officialCuePhrase = resolveOfficialCuePhrase(realizationPlan);
        LinkedHashSet<String> baseCandidates = new LinkedHashSet<>();
        List<String> anchorAlignedCandidates = containsCategoryAnchor(rendered, pool)
                ? List.of(ensureQuestionTail(rendered))
                : buildCategoryAnchorRepairCandidates(rendered, pool);
        if (anchorAlignedCandidates.isEmpty()) {
            anchorAlignedCandidates = List.of(ensureQuestionTail(rendered));
        }
        for (String candidate : anchorAlignedCandidates) {
            if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                    && !containsBrandExposure(candidate, brandExposureTerms)) {
                baseCandidates.addAll(buildBrandRepairCandidates(candidate, pool, brandExposureTerms));
            } else {
                baseCandidates.add(ensureQuestionTail(candidate));
            }
        }
        if (baseCandidates.isEmpty()) {
            baseCandidates.add(ensureQuestionTail(rendered));
        }

        LinkedHashSet<String> repaired = new LinkedHashSet<>();
        for (String candidate : baseCandidates) {
            addOfficialCueRepairVariants(repaired, candidate, officialCuePhrase);
        }
        repaired.removeIf(candidate -> normalize(candidate).equals(normalize(rendered)));
        return List.copyOf(repaired);
    }

    private void addOfficialCueRepairVariants(Set<String> collector,
                                              String rendered,
                                              OfficialCuePhrase officialCuePhrase) {
        if (!StringUtils.hasText(rendered) || officialCuePhrase == null) {
            return;
        }
        String normalized = ensureQuestionTail(rendered);
        if (containsOfficialCueObject(normalized)) {
            collector.add(normalized);
            return;
        }
        addOfficialCueVariant(collector, normalized, "有哪些可以先看看",
                "有哪些牌子可以先看看" + officialCuePhrase.browseCue());
        addOfficialCueVariant(collector, normalized, "有哪些可以先看",
                "有哪些牌子可以先看" + officialCuePhrase.browseCue());
        addOfficialCueVariant(collector, normalized, "先看看哪些",
                "先看看哪些牌子的" + officialCuePhrase.compareCue());
        addOfficialCueVariant(collector, normalized, "先看哪几家",
                "先看哪几家的" + officialCuePhrase.compareCue());

        int whichIndex = normalized.indexOf("哪个");
        if (whichIndex > 0) {
            collector.add(ensureQuestionTail(
                    normalized.substring(0, whichIndex) + "的" + officialCuePhrase.compareCue() + normalized.substring(whichIndex)
            ));
        }

        int whichSideIndex = normalized.indexOf("哪边");
        if (whichSideIndex > 0) {
            collector.add(ensureQuestionTail(
                    normalized.substring(0, whichSideIndex)
                            + "哪边的"
                            + officialCuePhrase.compareCue()
                            + normalized.substring(whichSideIndex + 2)
            ));
        }
    }

    private void addOfficialCueVariant(Set<String> collector,
                                       String rendered,
                                       String needle,
                                       String replacement) {
        if (!StringUtils.hasText(rendered) || !StringUtils.hasText(needle) || !rendered.contains(needle)) {
            return;
        }
        collector.add(ensureQuestionTail(rendered.replace(needle, replacement)));
    }

    private OfficialCuePhrase resolveOfficialCuePhrase(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.realizationConstraints() == null) {
            return new OfficialCuePhrase("官网信息", "官网");
        }
        for (String hint : realizationPlan.realizationConstraints().softSemanticHints()) {
            if (!StringUtils.hasText(hint)) {
                continue;
            }
            String trimmed = hint.trim();
            if (trimmed.contains("官网")) {
                return new OfficialCuePhrase("官网信息", "官网");
            }
            if (trimmed.contains("官方")) {
                return new OfficialCuePhrase("官方信息", "官方信息");
            }
            if (trimmed.contains("可信来源")) {
                return new OfficialCuePhrase("可信来源", "可信来源");
            }
            if (trimmed.contains("来源")) {
                return new OfficialCuePhrase("来源信息", "来源");
            }
        }
        return new OfficialCuePhrase("官网信息", "官网");
    }

    private void addMechanicalRepairCandidate(Set<String> collector, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return;
        }
        collector.add(ensureQuestionTail(candidate));
    }

    private String rewriteMechanicalStem(String rendered) {
        if (!StringUtils.hasText(rendered)) {
            return null;
        }
        String rewritten = rendered;
        rewritten = rewritten.replace("大家一般会先看哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家一般会选哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家会先看哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家通常会先拿哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家更常先拿哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家通常会先拿哪种", "大家一般都会先看看哪些");
        rewritten = rewritten.replace("大家更常先拿哪种", "大家一般都会先看看哪些");
        rewritten = rewritten.replace("大家一般会先拿哪种", "大家一般都会先看看哪些");
        rewritten = rewritten.replace("大家常买哪种", "大家一般都会先看看哪些");
        rewritten = rewritten.replace("大家常选哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("大家常买哪个", "哪个更像大家常选的");
        rewritten = rewritten.replace("有哪些值得先看", "有哪些可以先看看");
        rewritten = rewritten.replace("先看哪个更", "哪个更");
        rewritten = rewritten.replace("先看哪个会更", "哪个会更");
        rewritten = rewritten.replace("先看哪几块", "从哪几块入手");
        rewritten = rewritten.replace("先看哪几点", "从哪几点入手");
        rewritten = rewritten.replace("先看哪几项", "从哪几项入手");
        return rewritten;
    }

    private String rewriteMechanicalAffectionSeek(String rendered,
                                                  CategoryVariablePool pool,
                                                  ProbeExposurePolicy exposurePolicy,
                                                  RealizationPlan realizationPlan) {
        if (!StringUtils.hasText(rendered)
                || realizationPlan == null
                || realizationPlan.simulationIntentProfile() == null
                || !"seek_clear_answer".equals(realizationPlan.simulationIntentProfile().surfaceIntent())
                || !hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
            return null;
        }
        String subject = resolveMechanicalRepairSubject(rendered, pool, exposurePolicy);
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        return switch (conversationMode) {
            case "back_and_forth_consultation" -> "按大家常买的来挑，" + subject + "里哪个更值得先看";
            case "constraint_driven_query" -> "如果按大家常买的来挑，" + subject + "里哪个更值得先看";
            default -> subject + "里哪个更值得先看，也更像大家常选的";
        };
    }

    private String resolveMechanicalRepairSubject(String rendered,
                                                  CategoryVariablePool pool,
                                                  ProbeExposurePolicy exposurePolicy) {
        if (pool == null) {
            return null;
        }
        List<String> anchors = orderedFallbackAnchors(pool, rendered);
        for (String anchor : anchors) {
            if (!StringUtils.hasText(anchor)) {
                continue;
            }
            if (rendered.contains(anchor)) {
                return fallbackPromptComposer.subjectPhrase(anchor, exposurePolicy, pool.brandName(), pool.contextPackage());
            }
        }
        if (StringUtils.hasText(pool.category())) {
            return fallbackPromptComposer.subjectPhrase(pool.category(), exposurePolicy, pool.brandName(), pool.contextPackage());
        }
        return null;
    }

    private String injectBrandIntoAnchor(String rendered, CategoryVariablePool pool) {
        if (!StringUtils.hasText(rendered) || pool == null || !StringUtils.hasText(pool.brandName())) {
            return null;
        }
        String brandName = pool.brandName().trim();
        for (String anchor : orderedAnchorRepairAnchors(pool)) {
            if (!StringUtils.hasText(anchor)) {
                continue;
            }
            int matchIndex = rendered.indexOf(anchor);
            if (matchIndex < 0) {
                continue;
            }
            String before = rendered.substring(0, matchIndex);
            String after = rendered.substring(matchIndex + anchor.length());
            return before + brandName + "这个" + anchor + after;
        }
        return null;
    }

    private List<String> buildCategoryAnchorRepairCandidates(String rendered, CategoryVariablePool pool) {
        if (!StringUtils.hasText(rendered) || pool == null) {
            return List.of();
        }
        String normalized = rendered.trim().replaceAll("[？?！!。]+$", "");
        if (!StringUtils.hasText(normalized) || containsCategoryAnchor(normalized, pool)) {
            return List.of();
        }
        LinkedHashSet<String> repaired = new LinkedHashSet<>();
        for (String anchor : orderedAnchorRepairAnchors(pool)) {
            if (!StringUtils.hasText(anchor)) {
                continue;
            }
            repaired.add(ensureQuestionTail(anchor + "里" + normalized));
            if (startsWithRepairableLead(normalized)) {
                repaired.add(ensureQuestionTail(anchor + normalized));
            }
        }
        repaired.removeIf(candidate -> normalize(candidate).equals(normalize(rendered)));
        return List.copyOf(repaired);
    }

    private List<String> orderedAnchorRepairAnchors(CategoryVariablePool pool) {
        LinkedHashSet<String> anchors = new LinkedHashSet<>();
        if (pool != null && StringUtils.hasText(pool.category())) {
            anchors.add(pool.category().trim());
        }
        if (pool != null && pool.categoryAnchors() != null) {
            pool.categoryAnchors().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(anchors::add);
        }
        return List.copyOf(anchors);
    }

    private boolean startsWithRepairableLead(String rendered) {
        if (!StringUtils.hasText(rendered)) {
            return false;
        }
        String trimmed = rendered.trim();
        return trimmed.startsWith("先")
                || trimmed.startsWith("从")
                || trimmed.startsWith("该")
                || trimmed.startsWith("怎么")
                || trimmed.startsWith("哪些")
                || trimmed.startsWith("哪个")
                || trimmed.startsWith("哪种")
                || trimmed.startsWith("哪款")
                || trimmed.startsWith("如果")
                || trimmed.startsWith("想")
                || trimmed.startsWith("现在")
                || trimmed.startsWith("到底");
    }

    private FallbackCandidate buildOrthogonalFallback(CategoryVariablePool pool,
                                                      List<String> brandExposureTerms,
                                                      ProbeExposurePolicy exposurePolicy,
                                                      RealizationPlan realizationPlan,
                                                      Map<String, String> translatedTags,
                                                      String diagnosisId,
                                                      String probeId,
                                                      Set<String> usedCanonicalPrompts) {
        String fallbackSeed = (diagnosisId == null ? "" : diagnosisId) + "|" + (probeId == null ? "" : probeId);
        List<FallbackCandidate> candidates = rotateCandidates(
                buildOrthogonalFallbackCandidates(pool, exposurePolicy, realizationPlan, fallbackSeed),
                fallbackSeed,
                realizationPlan
        );

        for (FallbackCandidate candidate : candidates) {
            String finalized = ensureQuestionTail(candidate.prompt());
            if (passesOrthogonalValidation(finalized,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts,
                    true)
                    && tryClaimPrompt(usedCanonicalPrompts, finalized)) {
                return new FallbackCandidate(candidate.family(), finalized);
            }
        }
        for (FallbackCandidate candidate : candidates) {
            String finalized = ensureQuestionTail(candidate.prompt());
            if (passesOrthogonalValidationAllowStructuralDuplicate(
                    finalized,
                    pool,
                    brandExposureTerms,
                    exposurePolicy,
                    realizationPlan,
                    translatedTags,
                    usedCanonicalPrompts
            ) && tryClaimPromptAllowStructuralDuplicate(usedCanonicalPrompts, finalized)) {
                log.info("V4.2 Node 2 deterministic fallback relaxed structural dedupe. brand={}, category={}, probeId={}, family={}",
                        pool.brandName(), pool.category(), probeId, candidate.family());
                return new FallbackCandidate(candidate.family(), finalized);
            }
        }
        for (FallbackCandidate candidate : candidates) {
            String finalized = ensureQuestionTail(candidate.prompt());
            if (tryClaimPromptAllowStructuralDuplicate(usedCanonicalPrompts, finalized)) {
                log.warn("V4.2 Node 2 deterministic fallback entered last-resort candidate claim. brand={}, category={}, probeId={}, family={}",
                        pool.brandName(), pool.category(), probeId, candidate.family());
                return new FallbackCandidate(candidate.family(), finalized);
            }
        }
        log.warn("V4.2 Node 2 deterministic fallback exhausted. brand={}, category={}, exposurePolicy={}, fallbackFamily={}, candidates={}",
                pool.brandName(),
                pool.category(),
                exposurePolicy,
                realizationPlan == null || realizationPlan.realizationConstraints() == null
                        ? null
                        : realizationPlan.realizationConstraints().fallbackFamily(),
                candidates.stream()
                        .map(candidate -> candidate.family() + ":" + SensitiveDataSanitizer.preview(ensureQuestionTail(candidate.prompt()), 56))
                        .toList());
        throw new IllegalStateException("Node 2 deterministic fallback 生成失败");
    }

    private String buildTemplateGenericFallback(CategoryVariablePool pool, BucketType bucketType) {
        String anchor = pool.categoryAnchors().isEmpty() ? pool.category() : pool.categoryAnchors().getFirst();
        String rendered = switch (bucketType) {
            case PARETO -> anchor + "怎么选才不踩坑";
            case ADVERSARIAL -> StringUtils.hasText(pool.brandName())
                    ? anchor + "里听说" + pool.brandName() + "问题很多，是真的吗"
                    : anchor + "有哪些千万别买的";
            case ORTHOGONAL -> anchor + "怎么选";
        };
        return ensureQuestionTail(rendered);
    }

    private ProbeExposurePolicy resolveTemplatePlanExposurePolicy(RealizationPlan realizationPlan,
                                                                  CategoryVariablePool pool) {
        if (realizationPlan != null
                && realizationPlan.realizationConstraints() != null
                && realizationPlan.realizationConstraints().brandExposurePolicy() != null) {
            return realizationPlan.realizationConstraints().brandExposurePolicy();
        }
        return StringUtils.hasText(pool.brandName())
                ? ProbeExposurePolicy.BRAND_VISIBLE
                : ProbeExposurePolicy.BRAND_HIDDEN;
    }

    private String ensureTemplateBrandPolicy(String rendered,
                                             ProbeExposurePolicy exposurePolicy,
                                             CategoryVariablePool pool) {
        if (exposurePolicy != ProbeExposurePolicy.BRAND_VISIBLE
                || !StringUtils.hasText(rendered)
                || pool == null
                || !StringUtils.hasText(pool.brandName())) {
            return rendered;
        }
        String normalizedBrand = canonicalPrompt(pool.brandName());
        if (canonicalPrompt(rendered).contains(normalizedBrand)) {
            return rendered;
        }
        String anchor = primaryCategoryAnchor(pool);
        if (!StringUtils.hasText(anchor)) {
            return rendered;
        }
        return ensureQuestionTail(pool.brandName().trim() + "这个" + anchor + "，" + rendered);
    }

    private String softHintValue(RealizationPlan realizationPlan, String prefix) {
        if (realizationPlan == null
                || realizationPlan.realizationConstraints() == null
                || realizationPlan.realizationConstraints().softSemanticHints() == null
                || !StringUtils.hasText(prefix)) {
            return null;
        }
        String normalizedPrefix = prefix.trim().toUpperCase(Locale.ROOT);
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .filter(StringUtils::hasText)
                .map(hint -> hint.split(":", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> normalizedPrefix.equals(parts[0].trim().toUpperCase(Locale.ROOT)))
                .map(parts -> parts[1].trim())
                .findFirst()
                .orElse(null);
    }

    private String primaryCategoryAnchor(CategoryVariablePool pool) {
        if (pool == null) {
            return "";
        }
        if (pool.categoryAnchors() != null && !pool.categoryAnchors().isEmpty() && StringUtils.hasText(pool.categoryAnchors().getFirst())) {
            return pool.categoryAnchors().getFirst().trim();
        }
        return StringUtils.hasText(pool.category()) ? pool.category().trim() : "";
    }

    private String primaryCompetitor(CategoryVariablePool pool) {
        if (pool == null || pool.competitors() == null || pool.competitors().isEmpty()) {
            return "别家";
        }
        String candidate = pool.competitors().getFirst();
        return StringUtils.hasText(candidate) ? candidate.trim() : "别家";
    }

    private String templateBrandSubject(CategoryVariablePool pool,
                                        RealizationPlan realizationPlan,
                                        String anchor) {
        if (pool == null || !StringUtils.hasText(anchor)) {
            return anchor;
        }
        ProbeExposurePolicy exposurePolicy = realizationPlan == null
                ? ProbeExposurePolicy.BRAND_HIDDEN
                : realizationPlan.realizationConstraints().brandExposurePolicy();
        if (exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE && StringUtils.hasText(pool.brandName())) {
            return pool.brandName().trim() + "这个" + anchor;
        }
        return anchor;
    }

    private String resolveTemplateText(CategoryVariablePool pool, ProbeBlueprint blueprint) {
        if (StringUtils.hasText(blueprint.templateText())) {
            return blueprint.templateText().trim();
        }
        return switch (blueprint.bucketType()) {
            case PARETO -> "{category}怎么选才不踩坑";
            case ADVERSARIAL -> StringUtils.hasText(pool.brandName())
                    ? "听说{brandName}最近问题很多，是真的吗"
                    : "{category}有哪些千万别买的";
            case ORTHOGONAL -> "{category}怎么选";
        };
    }

    private String applyTemplate(String templateText, CategoryVariablePool pool) {
        List<String> competitors = pool.competitors();
        String competitor1 = competitors.isEmpty() ? "其他品牌" : competitors.getFirst();
        String competitor2 = competitors.size() >= 2 ? competitors.get(1) : competitor1;
        String rendered = templateText
                .replace("{category}", pool.category())
                .replace("{brandName}", pool.brandName())
                .replace("{competitor}", competitor1)
                .replace("{competitor1}", competitor1)
                .replace("{competitor2}", competitor2);
        if (containsCategoryAnchor(rendered, pool)) {
            return rendered;
        }
        String anchor = pool.categoryAnchors().isEmpty() ? pool.category() : pool.categoryAnchors().getFirst();
        return anchor + "里" + rendered;
    }

    private String joinLines(List<String> lines) {
        return lines.stream()
                .filter(StringUtils::hasText)
                .map(line -> "- " + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 无");
    }

    private String joinSoftHints(RealizationPlan realizationPlan, CategoryVariablePool pool) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        hints.addAll(behaviorRuleComposer.softHints(realizationPlan));
        hints.addAll(node2ContextHints(pool));
        return joinLines(List.copyOf(hints));
    }

    private String brandInjectionPolicy(ProbeExposurePolicy exposurePolicy) {
        return exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                ? "必须。品牌名至少要自然出现一次，可以写成“买某品牌前 / 某品牌这个品类靠不靠谱 / 某品牌和谁比更稳”这类真人问法，但不要像广告或口播。"
                : "不允许。不要主动提品牌名，只用真实人会用的品类口语。";
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + " / " + right)
                .orElse("-");
    }

    private String extractCandidateLine(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```(?:text|json)?\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        for (String line : text.split("\\R")) {
            if (StringUtils.hasText(line)) {
                return line.trim();
            }
        }
        return "";
    }

    private boolean containsBannedWord(String rendered) {
        return BANNED_WORDS.stream().anyMatch(rendered::contains);
    }

    private boolean containsUnresolvedPlaceholder(String rendered) {
        return rendered.contains("{") || rendered.contains("}");
    }

    private boolean containsLiteralizationExpression(String rendered) {
        if (!StringUtils.hasText(rendered)) {
            return true;
        }
        return LITERALIZATION_FRAGMENTS.stream().anyMatch(rendered::contains);
    }

    private boolean containsCategoryAnchor(String rendered, CategoryVariablePool pool) {
        String normalized = normalize(rendered);
        if (!StringUtils.hasText(normalized) || pool == null || pool.categoryAnchors() == null) {
            return false;
        }
        LinkedHashSet<String> references = new LinkedHashSet<>();
        addCategoryReferenceVariants(references, pool.category());
        pool.categoryAnchors().forEach(anchor -> addCategoryReferenceVariants(references, anchor));
        return references.stream()
                .filter(StringUtils::hasText)
                .anyMatch(normalized::contains);
    }

    private void addCategoryReferenceVariants(LinkedHashSet<String> collector, String value) {
        V42CategoryAnchorResolver.expandAnchorVariants(value)
                .forEach(variant -> addNormalizedTerm(collector, variant));
    }

    private String ensureQuestionTail(String rendered) {
        String normalized = rendered == null ? "" : rendered.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        normalized = normalized.replaceAll("[？?！!。]+$", "");
        return normalized + "？";
    }

    private boolean matchesRequiredSemantics(String rendered, RealizationPlan realizationPlan) {
        return matchesRequiredSemantics(rendered, realizationPlan, null);
    }

    private boolean matchesRequiredSemantics(String rendered,
                                             RealizationPlan realizationPlan,
                                             Map<String, String> translatedTags) {
        if (realizationPlan == null) {
            return true;
        }
        return realizationPlan.realizationConstraints().mustExpressSemantics().stream()
                .allMatch(mustSemantic -> matchesSemantic(rendered, mustSemantic, translatedTags));
    }

    private boolean matchesSemantic(String rendered,
                                    String mustSemantic,
                                    Map<String, String> translatedTags) {
        String[] parts = mustSemantic.split(":", 2);
        if (parts.length != 2) {
            return true;
        }
        String translatedValue = translatedTags == null
                ? null
                : translatedTags.get(parts[0].trim().toUpperCase(Locale.ROOT));
        return SemanticKeywordMatcher.matches(rendered, parts[0], parts[1], translatedValue);
    }

    private boolean looksMechanical(String rendered) {
        return matchedMechanicalHeuristicId(rendered) != null;
    }

    private String matchedMechanicalHeuristicId(String rendered) {
        String trimmed = rendered == null ? "" : rendered.trim();
        if (trimmed.isEmpty()) {
            return "EMPTY_PROMPT";
        }
        if (trimmed.matches("^如果现在就要买.*")) {
            return "IF_BUY_PREFIX";
        }
        if (trimmed.matches("^.*到底稳不稳[？?]$")) {
            return "STEADY_DIRECT_QUESTION";
        }
        if (trimmed.matches("^.*先看哪几家官网.*[？?]$")
                || trimmed.matches("^.*先看官网哪部分.*[？?]$")
                || trimmed.matches("^.*先看哪几家可信来源.*[？?]$")
                || trimmed.matches("^.*先看可信来源哪部分.*[？?]$")) {
            return null;
        }
        if (trimmed.matches("^.*有哪些值得先看.*[？?]$")) {
            return "XIANKAN_PATTERN";
        }
        if (trimmed.matches("^.*先看哪(个|家|种|款).*(更|会更|更适合|更靠谱|更稳).*[？?]$")) {
            return "XIANKAN_PATTERN";
        }
        if (trimmed.matches("^.*先看哪几(块|点|项).*(会更好挑|更好挑|更省心|更稳妥).*[？?]$")) {
            return "XIANKAN_PATTERN";
        }
        if (trimmed.matches("^.*大家一般会选.*")) {
            return "EVERYONE_CHOOSES_PATTERN";
        }
        if (trimmed.matches("^.*大家通常会先拿哪(个|种).*[？?]$")
                || trimmed.matches("^.*大家更常先拿哪(个|种).*[？?]$")) {
            return "COMMON_PICK_PATTERN";
        }
        if (trimmed.matches("^.*省心.*直接.*")) {
            return "SHENGXIN_DIRECT_PATTERN";
        }
        if (trimmed.matches("^.*(定之前|买.*前).*(哪点更稳|哪一项差别|哪两项差别).*[？?]$")) {
            return "PREBUY_DIFF_PATTERN";
        }
        if (trimmed.matches("^.*先确认哪几个点.*[？?]$")) {
            return "PRECONFIRM_POINTS_PATTERN";
        }
        if (trimmed.matches("^.*大家更常直接拿哪个.*[？?]$")) {
            return "COMMON_PICK_PATTERN";
        }
        if (trimmed.matches(".*大家一般会(选|先看)哪个.*[？?]$")) {
            return "EVERYONE_PICK_TAIL";
        }
        String[] fragments = trimmed.replaceAll("[？?！!。]+$", "").split("[，,、/]");
        if (fragments.length < 3) {
            return null;
        }
        int shortFragments = 0;
        for (String fragment : fragments) {
            String normalized = fragment.trim();
            if (!normalized.isEmpty() && normalized.length() <= 8) {
                shortFragments++;
            }
        }
        return shortFragments == fragments.length ? "SHORT_FRAGMENT_CHAIN" : null;
    }

    private boolean looksHumanlyAwkward(String rendered) {
        return matchedAwkwardHeuristicId(rendered) != null;
    }

    private String matchedAwkwardHeuristicId(String rendered) {
        String trimmed = rendered == null ? "" : rendered.trim();
        if (trimmed.isEmpty()) {
            return "EMPTY_PROMPT";
        }
        if (trimmed.contains("“") || trimmed.contains("”") || trimmed.contains("\"")) {
            return "QUOTED_TEXT";
        }
        if (trimmed.contains("你们这")) {
            return "YOU_MEN_ZHE";
        }
        if (trimmed.matches(".*我选一个(直接买|就买|直接定)(.{0,1}了)?[？?]$")) {
            return "SELF_DECISION_TAIL";
        }
        if (trimmed.matches(".*确认一下这个方向就准备(买|下单|定).*[？?]$")) {
            return "CONFIRM_DIRECTION_BUY";
        }
        if (trimmed.matches(".*(差别先看哪点|值不值，会不会太普通|值不值会不会太普通|省不省心).*[？?]$")) {
            return "LITERALIZED_AWKWARD_PAIR";
        }
        if (trimmed.matches(".*(定之前|买.*前).*(哪点更稳|哪一项差别|哪两项差别).*[？?]$")) {
            return "PREBUY_DIFF_PATTERN";
        }
        if (trimmed.matches(".*先确认哪几个点.*[？?]$")) {
            return "PRECONFIRM_POINTS_PATTERN";
        }
        if (trimmed.matches(".*大家更常直接拿哪个.*[？?]$")) {
            return "COMMON_PICK_TAIL";
        }
        if (trimmed.matches(".*(确认一下|确认好|确认完|比较完|确认清楚|确认明白|问清楚|弄清楚|确认没问题|确认过).*(直接买|直接下单|直接定)(.{0,1}了)?[？?]$")) {
            return "PERFECTIVE_DECISION_CHAIN";
        }
        if (trimmed.matches(".*(准备|想|确认一下|确认清楚|确认明白|问清楚|弄清楚|确认没问题|确认过).*(直接买|直接下单|直接定)(.{0,1}了).*[？?]$")) {
            return "PERFECTIVE_DECISION_INLINE";
        }
        if (trimmed.matches(".*我(直接买|直接下单|直接定)(.{0,1}了).*[？?]$")) {
            return "FIRST_PERSON_DECISION";
        }
        if (trimmed.matches(".*(就|好)(直接买|直接下单|直接定)(.{0,1}了)?[？?]$")) {
            return "ADVERB_DECISION_TAIL";
        }
        return null;
    }

    private String canonicalPrompt(String renderedPrompt) {
        return OrthogonalPromptFingerprint.canonical(renderedPrompt);
    }

    private String joinSurfaceRepairReasons(List<ProbeQualityGateService.SurfaceRepairReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "unknown";
        }
        return reasons.stream()
                .map(Enum::name)
                .reduce((left, right) -> left + "+" + right)
                .orElse("unknown");
    }

    private List<FallbackCandidate> buildOrthogonalFallbackCandidates(CategoryVariablePool pool,
                                                                      ProbeExposurePolicy exposurePolicy,
                                                                      RealizationPlan realizationPlan,
                                                                      String fallbackSeed) {
        List<String> supportClauses = fallbackPromptComposer.fallbackSupportClauses(realizationPlan, exposurePolicy);
        LinkedHashMap<String, FallbackCandidate> uniqueCandidates = new LinkedHashMap<>();

        for (String anchor : orderedFallbackAnchors(pool, fallbackSeed)) {
            String subject = fallbackPromptComposer.subjectPhrase(anchor, exposurePolicy, pool.brandName(), pool.contextPackage());
            for (String supportClause : supportClauses) {
                addCandidate(uniqueCandidates, "orthogonal_fallback_core",
                        fallbackPromptComposer.fallbackCoreQuestion(subject, exposurePolicy, realizationPlan, supportClause));
                addCandidate(uniqueCandidates, "orthogonal_fallback_angle",
                        fallbackPromptComposer.fallbackAngleQuestion(subject, exposurePolicy, realizationPlan, supportClause));
                addCandidate(uniqueCandidates, "orthogonal_fallback_browse",
                        fallbackPromptComposer.fallbackBrowseQuestion(subject, exposurePolicy, realizationPlan, supportClause));
                addCandidate(uniqueCandidates, "orthogonal_fallback_short",
                        fallbackPromptComposer.fallbackShortQuestion(subject, exposurePolicy, realizationPlan, supportClause));
            }
        }
        return List.copyOf(uniqueCandidates.values());
    }

    private void addCandidate(Map<String, FallbackCandidate> collector, String family, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return;
        }
        collector.putIfAbsent(canonicalPrompt(prompt), new FallbackCandidate(family, prompt));
    }

    private List<String> orderedFallbackAnchors(CategoryVariablePool pool, String fallbackSeed) {
        LinkedHashSet<String> uniqueAnchors = new LinkedHashSet<>();
        if (pool != null && pool.categoryAnchors() != null) {
            pool.categoryAnchors().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(uniqueAnchors::add);
        }
        if (pool != null && StringUtils.hasText(pool.category())) {
            uniqueAnchors.add(pool.category().trim());
        }
        if (uniqueAnchors.isEmpty()) {
            return List.of();
        }
        List<String> anchors = List.copyOf(uniqueAnchors);
        int offset = Math.floorMod((fallbackSeed == null ? "" : fallbackSeed).hashCode(), anchors.size());
        List<String> ordered = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            ordered.add(anchors.get((index + offset) % anchors.size()));
        }
        return List.copyOf(ordered);
    }

    private List<FallbackCandidate> rotateCandidates(List<FallbackCandidate> candidates,
                                                     String seed,
                                                     RealizationPlan realizationPlan) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<String> familyOrder = rotatedFallbackFamilyOrder(seed, realizationPlan);
        List<FallbackCandidate> rotated = new ArrayList<>(candidates);
        rotated.sort(Comparator
                .comparingInt((FallbackCandidate candidate) -> fallbackFamilyPriority(candidate.family(), familyOrder))
                .thenComparingInt(candidate -> Math.floorMod((seed + "|" + candidate.prompt()).hashCode(), Integer.MAX_VALUE))
                .thenComparing(FallbackCandidate::family)
                .thenComparing(FallbackCandidate::prompt));
        return List.copyOf(rotated);
    }

    private List<String> rotatedFallbackFamilyOrder(String seed, RealizationPlan realizationPlan) {
        List<String> baseOrder = baseFallbackFamilyOrder(realizationPlan);
        if (baseOrder.size() <= 1) {
            return baseOrder;
        }
        int rotatableSize = Math.min(baseOrder.size(), rotatableFallbackFamilyCount(realizationPlan));
        if (rotatableSize <= 1) {
            return baseOrder;
        }
        int offset = Math.floorMod((seed == null ? "" : seed).hashCode(), rotatableSize);
        List<String> rotated = new ArrayList<>(baseOrder.size());
        for (int index = 0; index < rotatableSize; index++) {
            rotated.add(baseOrder.get((index + offset) % rotatableSize));
        }
        for (int index = rotatableSize; index < baseOrder.size(); index++) {
            rotated.add(baseOrder.get(index));
        }
        return List.copyOf(rotated);
    }

    private int rotatableFallbackFamilyCount(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.simulationIntentProfile() == null) {
            return Integer.MAX_VALUE;
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        return switch (surfaceIntent) {
            case "confirm_and_buy" -> "back_and_forth_consultation".equals(conversationMode) ? 2 : 3;
            case "seek_clear_answer" -> "back_and_forth_consultation".equals(conversationMode) ? 1 : 3;
            default -> Integer.MAX_VALUE;
        };
    }

    private List<String> baseFallbackFamilyOrder(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.simulationIntentProfile() == null) {
            return List.of(
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_browse",
                    "orthogonal_fallback_short"
                );
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        String fallbackFamilyHint = realizationPlan.realizationConstraints() == null
                ? ""
                : realizationPlan.realizationConstraints().fallbackFamily();
        ProbeExposurePolicy exposurePolicy = realizationPlan.realizationConstraints() == null
                ? ProbeExposurePolicy.BRAND_HIDDEN
                : realizationPlan.realizationConstraints().brandExposurePolicy();

        if ("back_and_forth_consultation".equals(conversationMode)
                && fallbackFamilyHint.startsWith("orthogonal_")) {
            if ("confirm_and_buy".equals(surfaceIntent)) {
                if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                    return List.of(
                            "orthogonal_fallback_angle",
                            "orthogonal_fallback_short",
                            "orthogonal_fallback_core",
                            "orthogonal_fallback_browse"
                    );
                }
                if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")
                        || hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
                    return List.of(
                            "orthogonal_fallback_short",
                            "orthogonal_fallback_angle",
                            "orthogonal_fallback_core",
                            "orthogonal_fallback_browse"
                    );
                }
            }
            if ("seek_clear_answer".equals(surfaceIntent)
                    && hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                return exposurePolicy == ProbeExposurePolicy.BRAND_VISIBLE
                        ? List.of(
                        "orthogonal_fallback_short",
                        "orthogonal_fallback_angle",
                        "orthogonal_fallback_core",
                        "orthogonal_fallback_browse"
                )
                        : List.of(
                        "orthogonal_fallback_angle",
                        "orthogonal_fallback_short",
                        "orthogonal_fallback_core",
                        "orthogonal_fallback_browse"
                );
            }
            if ("seek_clear_answer".equals(surfaceIntent)
                    && (hasRequiredSemantic(realizationPlan, "D3:identity_freedom")
                    || hasRequiredSemantic(realizationPlan, "D3:understanding_competence"))) {
                return List.of(
                        "orthogonal_fallback_angle",
                        "orthogonal_fallback_short",
                        "orthogonal_fallback_core",
                        "orthogonal_fallback_browse"
                );
            }
            if ("ask_for_actionable_steps".equals(surfaceIntent)) {
                if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                    return List.of(
                            "orthogonal_fallback_angle",
                            "orthogonal_fallback_short",
                            "orthogonal_fallback_core",
                            "orthogonal_fallback_browse"
                    );
                }
                if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")
                        || hasRequiredSemantic(realizationPlan, "D3:creation_idleness")) {
                    return List.of(
                            "orthogonal_fallback_short",
                            "orthogonal_fallback_angle",
                            "orthogonal_fallback_core",
                            "orthogonal_fallback_browse"
                    );
                }
            }
        }
        return switch (surfaceIntent) {
            case "browse_for_options" -> List.of(
                    "orthogonal_fallback_browse",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_short"
            );
            case "confirm_and_buy" -> List.of(
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_short",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_browse"
            );
            case "ask_for_actionable_steps" -> List.of(
                    "orthogonal_fallback_short",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_browse"
            );
            case "seek_clear_answer" -> List.of(
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_short",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_browse"
            );
            default -> List.of(
                    "orthogonal_fallback_core",
                    "orthogonal_fallback_angle",
                    "orthogonal_fallback_browse",
                    "orthogonal_fallback_short"
            );
        };
    }

    private int fallbackFamilyPriority(String family, List<String> familyOrder) {
        if (familyOrder == null || familyOrder.isEmpty()) {
            return 0;
        }
        int index = familyOrder.indexOf(family);
        return index >= 0 ? index : familyOrder.size();
    }

    private boolean hasRequiredSemantic(RealizationPlan realizationPlan, String semantic) {
        if (realizationPlan == null
                || realizationPlan.realizationConstraints() == null
                || realizationPlan.realizationConstraints().mustExpressSemantics() == null) {
            return false;
        }
        return realizationPlan.realizationConstraints().mustExpressSemantics().stream()
                .anyMatch(semantic::equals);
    }

    private List<String> node2ContextHints(CategoryVariablePool pool) {
        if (pool == null || pool.contextPackage() == null) {
            return List.of();
        }
        return pool.contextPackage().effectiveGroundedSignals().stream()
                .filter(signal -> signal.isUsedFor(MinusOneContextUses.NODE2_REALIZATION))
                .map(this::humanizeContextSignal)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
    }

    private String humanizeContextSignal(GroundedContextSignal signal) {
        if (signal == null) {
            return null;
        }
        return switch (signal.slot()) {
            case MinusOneContextSlots.USAGE_SCENARIO -> "可落在这样的真实场景：" + signal.value();
            case MinusOneContextSlots.DECISION_FACTOR -> "用户可能会在意：" + signal.value();
            case MinusOneContextSlots.DESCRIPTIVE_CUE -> "可以贴近这种真人说法：" + signal.value();
            case MinusOneContextSlots.COMPARISON_TARGET -> "允许自然拿这些对象做比较：" + signal.value();
            case MinusOneContextSlots.PERSONA_EXPRESSION_TENDENCY -> "语气可偏向：" + signal.value();
            case MinusOneContextSlots.TARGET_MODEL_CONTEXT_SIGNAL -> signal.value();
            case MinusOneContextSlots.CHANNEL_CUE -> "渠道或获取方式可自然带上：" + signal.value();
            case MinusOneContextSlots.MEMORY_CUE -> "允许使用模糊记忆线索，但别像广告：" + signal.value();
            default -> null;
        };
    }

    private ProbeExposurePolicy resolveTemplateExposurePolicy(String rendered, String brandName, List<String> brandAliases) {
        return containsBrandExposure(rendered, resolveBrandExposureTerms(brandName, brandAliases))
                ? ProbeExposurePolicy.BRAND_VISIBLE
                : ProbeExposurePolicy.BRAND_HIDDEN;
    }

    private List<String> resolveBrandExposureTerms(String brandName, List<String> brandAliases) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        addNormalizedTerm(terms, brandName);
        if (brandAliases != null) {
            for (String alias : brandAliases) {
                addNormalizedTerm(terms, alias);
            }
        }
        return List.copyOf(terms);
    }

    private void addNormalizedTerm(LinkedHashSet<String> collector, String value) {
        String normalized = normalize(value);
        if (StringUtils.hasText(normalized)) {
            collector.add(normalized);
        }
    }

    private boolean containsBrandExposure(String rendered, List<String> brandExposureTerms) {
        String normalized = normalize(rendered);
        if (!StringUtils.hasText(normalized) || brandExposureTerms == null || brandExposureTerms.isEmpty()) {
            return false;
        }
        return brandExposureTerms.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+", "")
                .toLowerCase();
    }

    private record PromptPayload(
            MappingResult mappingResult,
            RealizationPlan realizationPlan
    ) {
        private ProbeExposurePolicy exposurePolicy() {
            return realizationPlan.realizationConstraints().brandExposurePolicy();
        }
    }

    private record FallbackCandidate(
            String family,
            String prompt
    ) {
    }

    private record AnchorRepairAttempt(
            String repairedPrompt,
            OrthogonalValidationResult validationResult
    ) {
    }

    private record BrandRepairAttempt(
            String repairedPrompt,
            OrthogonalValidationResult validationResult,
            String repairApplied
    ) {
    }

    private record MechanicalRepairAttempt(
            String repairedPrompt,
            OrthogonalValidationResult validationResult,
            String repairApplied
    ) {
    }

    private record TemplateValidationResult(
            boolean passed,
            String reason
    ) {
    }

    private record OfficialCuePhrase(
            String compareCue,
            String browseCue
    ) {
    }

    private record OfficialCueRepairAttempt(
            String repairedPrompt,
            OrthogonalValidationResult validationResult,
            String repairApplied
    ) {
    }

    private record OrthogonalValidationResult(
            boolean passed,
            List<String> failedChecks,
            Map<String, Object> gateDecisionInput
    ) {
        private OrthogonalValidationResult(boolean passed, List<String> failedChecks) {
            this(passed, failedChecks, Map.of());
        }
    }

    private OrthogonalValidationResult duplicateValidationResult() {
        return new OrthogonalValidationResult(false, List.of("duplicate"), Map.of(
                "canonicalDuplicateHit", true,
                "structuralDuplicateHit", false,
                "claimStage", "post_validation"
        ));
    }

    private String resolveFallbackTriggerRule(OrthogonalValidationResult validationResult, RuntimeException lastException) {
        if (validationResult != null && validationResult.failedChecks() != null && !validationResult.failedChecks().isEmpty()) {
            return Node2RuleCatalog.validationRuleId(validationResult.failedChecks().getFirst());
        }
        if (isTokenBudgetFailure(lastException)) {
            return Node2RuleCatalog.RULE_FB_TOKEN_BUDGET;
        }
        if (lastException != null) {
            return Node2RuleCatalog.RULE_FB_RENDER_EXCEPTION;
        }
        return null;
    }

    public record Node2BatchRenderResult(
            List<RenderedProbe> probes,
            Node2TimingMetrics timing
    ) {
        public Node2BatchRenderResult {
            probes = probes == null ? List.of() : List.copyOf(probes);
        }
    }

    public record Node2TimingMetrics(
            long endToEndMillis,
            long node2Millis,
            long perProbeAvgMillis,
            long perProbeP50Millis,
            long perProbeP95Millis,
            long perProbeMaxMillis
    ) {
    }
}

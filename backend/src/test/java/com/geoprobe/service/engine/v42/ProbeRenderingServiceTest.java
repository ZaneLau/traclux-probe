package com.geoprobe.service.engine.v42;

import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetAware;
import com.geoprobe.ai.v42.support.V42PromptAssetDescriptor;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeStatus;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import com.geoprobe.service.shared.LightConeV43TopicStateProjectionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProbeRenderingServiceTest {

    @Mock
    private ProbeRenderingAiService probeRenderingAiService;

    private final ProbeSimulationPlanningService planningService = new ProbeSimulationPlanningService();
    private final OrthogonalMatrixService orthogonalMatrixService = new OrthogonalMatrixService(planningService);

    private ProbeRenderingService service;

    @BeforeEach
    void setUp() {
        service = new ProbeRenderingService(probeRenderingAiService, new ThreeLayerMappingEvaluator());
    }

    @Test
    void shouldRenderOrthogonalProbeWithM4BrandPolicy() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？");

        RenderedProbe rendered = service.renderOne("diag-1", samplePool(), orthogonalM4Probe());

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("orthogonal_constrained_llm", rendered.renderFamily());
        assertEquals("confirm_and_buy", rendered.realizationPlan().simulationIntentProfile().surfaceIntent());
        assertEquals("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？", rendered.renderedPrompt());

        ArgumentCaptor<String> brandPolicy = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> promptInstructions = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService).renderProbe(
                anyString(), anyString(), anyString(), anyString(),
                brandPolicy.capture(),
                anyString(), anyString(), anyString(),
                promptInstructions.capture()
        );
        assertTrue(brandPolicy.getValue().contains("必须"));
        assertTrue(promptInstructions.getValue().contains("确认式问法"));
        assertTrue(promptInstructions.getValue().contains("临近购买前的最后确认"));
        assertTrue(promptInstructions.getValue().contains("购买前信号"));
        assertTrue(promptInstructions.getValue().contains("保留一次上层品类词"));
        assertEquals("node2-rule-catalog-v1", rendered.triggeredRulesJson().get("ruleCatalogVersion"));
        assertEquals(List.of(Node2RuleCatalog.RULE_B_SURFACE_CONFIRM_BUY), rendered.triggeredRulesJson().get("behaviorRuleIds"));
    }

    @Test
    void shouldInjectUpperCategoryAnchorRuleForVisibleConfirmBuyRiskPath() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("厨房收纳里准备定霜山的水槽沥水架了，买前想确认下哪个更稳？");

        service.renderOne("diag-kitchen-upper-anchor-rule", kitchenStoragePool(), orthogonalM4Probe());

        ArgumentCaptor<String> behaviorRulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(1)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRulesCaptor.capture()
        );

        assertTrue(behaviorRulesCaptor.getValue().contains("保留一次上层品类词"));
        assertTrue(behaviorRulesCaptor.getValue().contains("先点出厨房收纳，再落到水槽沥水架"));
    }

    @Test
    void shouldAttachPromptAssetTraceToOrthogonalProbe() {
        ProbeRenderingService traceService = new ProbeRenderingService(
                new PromptAssetAwareStub("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？"),
                new ThreeLayerMappingEvaluator()
        );

        RenderedProbe rendered = traceService.renderOne("diag-trace", samplePool(), orthogonalM4Probe());

        assertEquals("PROBE_RENDERING", rendered.triggeredRulesJson().get("promptAssetKey"));
        assertEquals("node2-v2026-05-10", rendered.triggeredRulesJson().get("promptAssetVersion"));
        assertEquals("classpath:test/node2/probe_rendering/current.json", rendered.triggeredRulesJson().get("promptAssetSource"));
        assertEquals("abc123", rendered.triggeredRulesJson().get("promptAssetSha256"));
    }

    @Test
    void shouldKeepValidatedOriginalPromptOnSinglePath() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？");

        RenderedProbe rendered = service.renderOne("diag-single-path", samplePool(), orthogonalM4Probe());

        assertEquals("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？", rendered.renderedPrompt());
        assertEquals("llm_validated_original", rendered.renderReason());
    }

    @Test
    void shouldFallbackWhenOriginalPromptFailedValidation() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("矿泉水这个类别怎么选？");

        RenderedProbe rendered = service.renderOne("diag-single-path-invalid", samplePool(), orthogonalSeekClearProbe(
                "probe-single-path-invalid",
                Map.of("D3", "affection_belonging_participation", "D6", "specification")
        ));

        assertTrue(rendered.fallbackUsed());
        assertEquals(Node2RuleCatalog.RULE_Q_BANNED_OR_PLACEHOLDER, rendered.triggeredRulesJson().get("fallbackTriggerRule"));
        assertEquals(true, ((Map<?, ?>) rendered.triggeredRulesJson().get("gateDecisionInput")).get("bannedWordHit"));
        assertEquals(Node2RuleCatalog.fallbackFamilyRuleId(rendered.renderFamily()), rendered.triggeredRulesJson().get("fallbackFamilyRuleId"));
        assertTrue(((List<?>) rendered.triggeredRulesJson().get("behaviorRuleIds")).contains(Node2RuleCatalog.RULE_B_SURFACE_CLEAR_ANSWER));
    }

    @Test
    void shouldRepairMechanicalAffectionPromptBeforeFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("矿泉水里大家一般会选哪个？");

        RenderedProbe rendered = service.renderOne("diag-seek-affection-mechanical-repair", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-affection-mechanical-repair",
                Map.of("D3", "affection_belonging_participation", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_mechanical_repaired", rendered.renderReason());
        assertFalse(rendered.renderedPrompt().contains("大家一般会选哪个"));
        assertFalse(rendered.renderedPrompt().contains("先看哪个"));
        assertTrue(rendered.renderedPrompt().contains("矿泉水"));
        assertTrue(rendered.renderedPrompt().contains("大家"));
    }

    @Test
    void shouldRepairMissingCategoryAnchorBeforeFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在买哪个更稳，不想踩坑？");

        RenderedProbe rendered = service.renderOne("diag-anchor-repair", samplePool(), orthogonalSeekClearProbe(
                "probe-anchor-repair",
                Map.of("D3", "subsistence_protection", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_anchor_repaired", rendered.renderReason());
        assertTrue(rendered.renderedPrompt().contains("瓶装饮用水") || rendered.renderedPrompt().contains("矿泉水"));
        assertTrue(rendered.renderedPrompt().contains("不想踩坑"));
        assertEquals(List.of("anchor"), rendered.triggeredRulesJson().get("validationFailedChecks"));
        assertEquals(List.of(Node2RuleCatalog.RULE_Q_ANCHOR), rendered.triggeredRulesJson().get("validationFailedRuleIds"));
        assertEquals("boundary", rendered.triggeredRulesJson().get("validationRuleOrigin"));
        assertEquals(false, ((Map<?, ?>) rendered.triggeredRulesJson().get("gateDecisionInput")).get("anchorHit"));
        assertEquals("现在买哪个更稳，不想踩坑？", rendered.triggeredRulesJson().get("validationRejectedCandidate"));
        assertEquals("anchor", rendered.triggeredRulesJson().get("validationRepairApplied"));
    }

    @Test
    void shouldRepairMissingBrandExposureBeforeFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("瓶装饮用水现在买哪个更稳，不想踩坑？");

        RenderedProbe rendered = service.renderOne("diag-brand-repair", samplePool(), orthogonalM4Probe());

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_brand_repaired", rendered.renderReason());
        assertTrue(rendered.renderedPrompt().contains("农夫山泉"));
        assertEquals(List.of("brand_visible_missing"), rendered.triggeredRulesJson().get("validationFailedChecks"));
        assertEquals("瓶装饮用水现在买哪个更稳，不想踩坑？", rendered.triggeredRulesJson().get("validationRejectedCandidate"));
        assertEquals("brand_visible", rendered.triggeredRulesJson().get("validationRepairApplied"));
    }

    @Test
    void shouldAcceptCompactCategoryTailAliasWithoutAnchorRepair() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在买手机该怎么选，差别主要在哪？");

        RenderedProbe rendered = service.renderOne("diag-smartphone-tail-anchor", smartphonePoolWithFallbackAnchors(), orthogonalSeekClearProbe(
                "probe-smartphone-tail-anchor",
                Map.of("D3", "understanding_competence", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_original", rendered.renderReason());
        assertEquals("现在买手机该怎么选，差别主要在哪？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptNaturalIdentityCueOnBrandHiddenConstraintPath() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("矿泉水想要更有个性一点，要怎么挑会更合适？");

        Map<String, String> tags = tags(Map.of(
                "D1", "exploration",
                "D2", "instructional_active",
                "D3", "identity_freedom",
                "D4", "moderate_strain",
                "D5", "anticipatory_constraint",
                "D6", "specification"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "越看越乱",
                "D2", "想知道具体怎么挑",
                "D3", "想要更有个性",
                "D4", "有点累但还能想",
                "D5", "还有一点时间，先定个稳的",
                "D6", "我会顺手带点条件去问"
        ));
        ProbeBlueprint blueprint = new ProbeBlueprint(
                "probe-translated-semantic",
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );

        RenderedProbe rendered = service.renderOne("diag-translated-semantic", samplePool(), blueprint);

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_original", rendered.renderReason());
        assertEquals("brandHidden", rendered.exposurePolicy().contractValue());
        assertTrue(rendered.renderedPrompt().contains("更有个性"));
    }

    @Test
    void shouldAddAnchorRepairGuidanceOnRetryAfterAnchorFailure() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(
                "到底哪个好？",
                "矿泉水里哪个更值得看，差别主要在哪？"
        );

        RenderedProbe rendered = service.renderOne("diag-anchor-retry-guidance", samplePool(), orthogonalSeekClearProbe(
                "probe-anchor-retry-guidance",
                Map.of("D3", "understanding_competence", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        ArgumentCaptor<String> behaviorRules = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(2)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRules.capture()
        );
        assertTrue(behaviorRules.getAllValues().get(1).contains("缺少明确品类锚点"));
    }

    @Test
    void shouldAddBrandVisibleRepairGuidanceOnRetryAfterBrandMissing() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(
                "瓶装饮用水{品牌}现在买哪个更稳，不想踩坑？",
                "农夫山泉这个瓶装饮用水现在买哪个更稳，不想踩坑？"
        );

        RenderedProbe rendered = service.renderOne("diag-brand-retry-guidance", samplePool(), orthogonalM4Probe());

        assertFalse(rendered.fallbackUsed());
        ArgumentCaptor<String> behaviorRules = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(2)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRules.capture()
        );
        assertTrue(behaviorRules.getAllValues().get(1).contains("品牌名必须自然出现一次"));
        assertEquals("农夫山泉这个瓶装饮用水现在买哪个更稳，不想踩坑？", rendered.renderedPrompt());
    }

    @Test
    void shouldAddSeekIdentityMechanicalRepairGuidanceOnRetry() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(
                "现在买手机，怎么选能又快又准，又不会太普通？",
                "手机里有没有哪款更靠谱，又不会太普通？"
        );

        RenderedProbe rendered = service.renderOne("diag-seek-identity-mechanical-retry", smartphonePoolWithFallbackAnchors(), orthogonalSeekClearProbe(
                "probe-seek-identity-mechanical-retry",
                Map.of("D4", "severely_depleted", "D5", "reflective")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("手机里有没有哪款更靠谱，又不会太普通？", rendered.renderedPrompt());

        ArgumentCaptor<String> behaviorRules = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(2)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRules.capture()
        );
        assertTrue(behaviorRules.getAllValues().get(1).contains("两个短条件硬并列"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("手机里有没有哪款更靠谱，又不会太普通？"));
    }

    @Test
    void shouldAddSeekIdentitySemanticRepairGuidanceOnRetry() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(
                "现在买手机，怎么选才能既快又准，还不会太普通？",
                "想挑个不那么大众的，手机里哪个更值得先看？"
        );

        RenderedProbe rendered = service.renderOne("diag-seek-identity-semantic-retry", smartphonePoolWithFallbackAnchors(), orthogonalSeekClearProbe(
                "probe-seek-identity-semantic-retry",
                Map.of("D4", "severely_depleted", "D5", "reflective")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("想挑个不那么大众的，手机里哪个更值得先看？", rendered.renderedPrompt());

        ArgumentCaptor<String> behaviorRules = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(2)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRules.capture()
        );
        assertTrue(behaviorRules.getAllValues().get(0).contains("直接判断/推荐"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("不要再退成“怎么选 / 先从哪几步看 / 按什么思路挑”这类过程型问题"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("哪个更靠谱 / 哪个更稳 / 我该先看哪个"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("不要写成“先看哪个更...”"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("不要写成“怎么选才能……还不会太普通”"));
    }

    @Test
    void shouldAddConfirmBuyLowRiskRepairGuidanceOnRetry() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(
                "准备定小米手机了，买前就想确认下，跟华为苹果比，它用着稳吗，会不会有啥坑？",
                "准备买小米了，跟华为苹果比哪个更稳妥？"
        );

        RenderedProbe rendered = service.renderOne("diag-confirm-low-risk-retry", smartphonePoolWithFallbackAnchors(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-low-risk-retry",
                Map.of(
                        "D1", "verification",
                        "D4", "optimal",
                        "D5", "acute_emergency",
                        "D6", "specification"
                )
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("智能手机里准备买小米了，跟华为苹果比哪个更稳妥？", rendered.renderedPrompt());

        ArgumentCaptor<String> behaviorRules = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(2)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), behaviorRules.capture()
        );
        assertTrue(behaviorRules.getAllValues().get(1).contains("把“准备买 / 买前确认”和“更稳 / 不容易出问题”收进一个主问题里"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("不要写成“准备定了，买前确认下，跟谁比，它稳吗，会不会有啥坑”"));
        assertTrue(behaviorRules.getAllValues().get(1).contains("准备买小米了，跟华为苹果比哪个更稳妥？"));
    }

    @Test
    void shouldPersistRejectedCandidateAndFailedChecksWhenFallbackTriggeredByValidationFailure() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("到底哪个好？");

        RenderedProbe rendered = service.renderOne("diag-fallback-trace", samplePool(), orthogonalSeekClearProbe(
                "probe-fallback-trace",
                Map.of("D3", "understanding_competence", "D6", "specification")
        ));

        assertTrue(rendered.fallbackUsed());
        assertEquals("到底哪个好？", rendered.triggeredRulesJson().get("validationRejectedCandidate"));
        Object failedChecks = rendered.triggeredRulesJson().get("validationFailedChecks");
        List<?> failedChecksList = assertInstanceOf(List.class, failedChecks);
        assertTrue(failedChecksList.contains("anchor"));
        assertTrue(failedChecksList.contains("semantic"));
    }

    @Test
    void shouldFallbackToCallerThreadWhenNode2ExecutorIsRejected() {
        service = new ProbeRenderingService(
                probeRenderingAiService,
                new ThreeLayerMappingEvaluator(),
                command -> {
                    throw new RejectedExecutionException("executor overloaded");
                },
                true,
                4,
                45_000L
        );

        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉这个矿泉水现在买哪个更稳，不想踩坑？");

        ProbeRenderingService.Node2BatchRenderResult result = assertDoesNotThrow(
                () -> service.renderAllWithMetrics(
                        "diag-node2-overload",
                        samplePool(),
                        List.of(),
                        List.of(orthogonalM4Probe(), orthogonalEmergencyProbe())
                )
        );

        assertEquals(2, result.probes().size());
    }

    @Test
    void shouldAcceptNaturalSeekClearCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在买哪种饮用水好，差别在哪？");

        RenderedProbe rendered = service.renderOne("diag-natural-seek", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-seek",
                Map.of("D3", "understanding_competence", "D6", "recognition")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("现在买哪种饮用水好，差别在哪？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptNaturalIdentityFreedomSeekCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("包装饮用水里，有没有什么牌子是既靠谱又不会太普通的？");

        RenderedProbe rendered = service.renderOne("diag-natural-identity-seek", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-identity-seek",
                Map.of("D3", "identity_freedom", "D6", "recognition")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("包装饮用水里，有没有什么牌子是既靠谱又不会太普通的？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptComparativeAdvantageCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("瓶装饮用水里农夫山泉跟怡宝比，主要好在哪？我想尽快定个方向，又不想选得太普通？");

        RenderedProbe rendered = service.renderOne("diag-natural-comparative-advantage", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-comparative-advantage",
                Map.of("D1", "verification", "D4", "optimal", "D6", "dialogic")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("brandVisible", rendered.exposurePolicy().contractValue());
        assertEquals("瓶装饮用水里农夫山泉跟怡宝比，主要好在哪？我想尽快定个方向，又不想选得太普通？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptCommonChoiceDirectionCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在买瓶装饮用水，大家一般会怎么选？我想尽快定一个方向？");

        RenderedProbe rendered = service.renderOne("diag-natural-common-choice-direction", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-common-choice-direction",
                Map.of(
                        "D1", "formulation",
                        "D3", "affection_belonging_participation",
                        "D4", "optimal",
                        "D5", "acute_emergency",
                        "D6", "specification"
                )
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("brandHidden", rendered.exposurePolicy().contractValue());
        assertEquals("现在买瓶装饮用水，大家一般会怎么选？我想尽快定一个方向？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptDifferenceWhereCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("这几个瓶装饮用水牌子差别主要在哪儿，我大概有个方向了，想确认下？");

        RenderedProbe rendered = service.renderOne("diag-natural-difference-where", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-difference-where",
                Map.of(
                        "D1", "verification",
                        "D3", "understanding_competence",
                        "D4", "moderate_strain",
                        "D5", "reflective",
                        "D6", "specification"
                )
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("brandHidden", rendered.exposurePolicy().contractValue());
        assertEquals("这几个瓶装饮用水牌子差别主要在哪儿，我大概有个方向了，想确认下？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptBrowseDifferentiateCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在瓶装饮用水有哪些牌子可以先看看，顺便怎么区分它们？");

        RenderedProbe rendered = service.renderOne("diag-natural-browse-differentiate", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-browse-differentiate",
                Map.of(
                        "D1", "formulation",
                        "D2", "informational_undirected",
                        "D3", "understanding_competence",
                        "D4", "optimal",
                        "D5", "acute_emergency",
                        "D6", "recognition"
                )
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("brandHidden", rendered.exposurePolicy().contractValue());
        assertEquals("现在瓶装饮用水有哪些牌子可以先看看，顺便怎么区分它们？", rendered.renderedPrompt());
    }

    @Test
    void shouldAcceptNaturalConfirmAndBuyCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("这几个饮用水品牌里，哪个更值得买，不容易踩坑？");

        RenderedProbe rendered = service.renderOne("diag-natural-confirm", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-natural-confirm",
                Map.of("D3", "subsistence_protection", "D6", "dialogic")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("这几个饮用水品牌里，哪个更值得买，不容易踩坑？", rendered.renderedPrompt());
    }

    @Test
    void shouldPreserveVisibleBrandWhenContextualCueBecomesSubject() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new RuntimeException("force deterministic rendering fallback"));

        RenderedProbe rendered = service.renderOne("diag-visible-context-subject", samplePoolWithContext(), orthogonalConfirmAndBuyProbe(
                "probe-visible-context-subject",
                Map.of(
                        "D1", "verification",
                        "D3", "subsistence_protection",
                        "D4", "optimal",
                        "D6", "specification"
                )
        ));

        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("农夫山泉"));
    }

    @Test
    void shouldInjectConfirmSpecificPromptRulesIntoAiRequest() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("包装饮用水想买前先看清哪块区别最关键？");

        service.renderOne("diag-confirm-prompt-rules", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-prompt-rules",
                Map.of("D3", "understanding_competence", "D6", "dialogic")
        ));

        ArgumentCaptor<String> mustSemanticsCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> behaviorRulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(1)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(),
                mustSemanticsCaptor.capture(),
                anyString(),
                behaviorRulesCaptor.capture()
        );

        assertTrue(mustSemanticsCaptor.getValue().contains("已经临近购买/下单"));
        assertTrue(mustSemanticsCaptor.getValue().contains("购买前信号"));
        assertTrue(behaviorRulesCaptor.getValue().contains("临近购买前的最后确认"));
        assertTrue(behaviorRulesCaptor.getValue().contains("如果要问差别"));
    }

    @Test
    void shouldInjectActionableStepPromptRulesIntoAiRequest() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉这个包装饮用水该从哪几步看起，更不容易买错？");

        RenderedProbe rendered = service.renderOne("diag-action-prompt-rules", samplePool(), orthogonalActionStepProbe(
                "probe-action-prompt-rules",
                Map.of("D3", "subsistence_protection", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());

        ArgumentCaptor<String> behaviorRulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(),
                anyString(),
                anyString(),
                behaviorRulesCaptor.capture()
        );

        assertTrue(behaviorRulesCaptor.getValue().contains("下一步具体怎么挑"));
        assertTrue(behaviorRulesCaptor.getValue().contains("动作导向句"));
        assertTrue(behaviorRulesCaptor.getValue().contains("不容易买错"));
        assertTrue(behaviorRulesCaptor.getValue().contains("不要写成“先看哪"));
        assertTrue(behaviorRulesCaptor.getValue().contains("保留一个明确的品类锚点"));
    }

    @Test
    void shouldInjectBrowsePromptRulesIntoAiRequest() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("包装饮用水有哪些可以先看看，差别先从哪个方向看会更容易分清？");

        RenderedProbe rendered = service.renderOne("diag-browse-prompt-rules", samplePool(), orthogonalSeekClearProbe(
                "probe-browse-prompt-rules",
                Map.of("D2", "informational_undirected", "D3", "understanding_competence", "D6", "recognition")
        ));

        assertFalse(rendered.fallbackUsed());

        ArgumentCaptor<String> behaviorRulesCaptor = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(),
                anyString(),
                anyString(),
                behaviorRulesCaptor.capture()
        );

        assertTrue(behaviorRulesCaptor.getValue().contains("先摸一圈候选"));
        assertTrue(behaviorRulesCaptor.getValue().contains("不要直接收成“哪个好 / 主要区别在哪 / 该选哪个”"));
        assertTrue(behaviorRulesCaptor.getValue().contains("先从哪个方向看会更容易分清"));
        assertTrue(behaviorRulesCaptor.getValue().contains("不要写“值得先看 / 值得先留意”"));
    }

    @Test
    void shouldAcceptNaturalLowRiskSeekCandidateWithoutFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("饮用水品牌里，哪个更不容易出问题？");

        RenderedProbe rendered = service.renderOne("diag-natural-lowrisk-seek", samplePool(), orthogonalSeekClearProbe(
                "probe-natural-lowrisk-seek",
                Map.of("D3", "subsistence_protection", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("饮用水品牌里，哪个更不容易出问题？", rendered.renderedPrompt());
    }

    @Test
    void shouldNormalizeBrandSuffixAnchorBeforeDeterministicFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new RuntimeException("force deterministic rendering fallback"));

        CategoryVariablePool pool = new CategoryVariablePool(
                "红豆衬衫",
                "男装衬衫",
                List.of("雅戈尔", "海澜之家"),
                List.of("男装衬衫", "男装衬衫品牌"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );

        RenderedProbe rendered = service.renderOne("diag-brand-anchor-normalization", pool, orthogonalConfirmAndBuyProbe(
                "probe-brand-anchor-normalization",
                Map.of("D3", "subsistence_protection", "D6", "dialogic")
        ));

        assertTrue(rendered.fallbackUsed());
        assertFalse(rendered.renderedPrompt().contains("男装衬衫品牌"));
        assertTrue(rendered.renderedPrompt().contains("男装衬衫"));
    }

    @Test
    void shouldRejectMechanicalEveryoneWouldChooseLineFromLlm() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("现在买的话，怡宝、百岁山和景田这几个饮用水牌子，大家一般会选哪个？");

        RenderedProbe rendered = service.renderOne("diag-mechanical-affection", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-mechanical-affection",
                Map.of("D3", "affection_belonging_participation", "D6", "dialogic")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_mechanical_repaired", rendered.renderReason());
        assertNotEquals("现在买的话，怡宝、百岁山和景田这几个饮用水牌子，大家一般会选哪个？", rendered.renderedPrompt());
        assertFalse(rendered.renderedPrompt().contains("大家一般会选哪个"));
        assertEquals("EVERYONE_CHOOSES_PATTERN", ((Map<?, ?>) rendered.triggeredRulesJson().get("gateDecisionInput")).get("mechanicalHeuristicId"));
    }

    @Test
    void shouldRejectMechanicalActionableStepCandidateWithXianKanNaTemplate() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉和怡宝这些包装饮用水，先看哪几块差别会更好挑？");

        RenderedProbe rendered = service.renderOne("diag-action-mechanical-xiankan", samplePool(), orthogonalActionStepProbe(
                "probe-action-mechanical-xiankan",
                Map.of("D3", "understanding_competence", "D6", "specification")
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_mechanical_repaired", rendered.renderReason());
        assertNotEquals("农夫山泉和怡宝这些包装饮用水，先看哪几块差别会更好挑？", rendered.renderedPrompt());
        assertFalse(rendered.renderedPrompt().contains("先看哪几块"));
    }

    @Test
    void shouldRepairMissingOfficialCueForComparativePrompt() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("厨房收纳里懒角落和纳川哪个更稳，差别主要在哪能帮我快点定？");

        RenderedProbe rendered = service.renderOne(
                "diag-official-cue-compare-repair",
                kitchenStoragePool(),
                orthogonalOfficialSourceCompareProbe("probe-official-cue-compare-repair")
        );

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_official_cue_repaired", rendered.renderReason());
        assertTrue(rendered.renderedPrompt().contains("懒角落和纳川的官网信息"));
        assertEquals("official_cue", rendered.triggeredRulesJson().get("validationRepairApplied"));
    }

    @Test
    void shouldRepairMissingOfficialCueBeforeFallingBack() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("买错厨房收纳太折腾了，直接说有哪些可以先看看，差别先从哪块看最容易分清？");

        RenderedProbe rendered = service.renderOne(
                "diag-official-cue-repair",
                kitchenStoragePool(),
                orthogonalOfficialSourceBrowseProbe("probe-official-cue-repair")
        );

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_official_cue_repaired", rendered.renderReason());
        assertTrue(rendered.renderedPrompt().contains("官网"));
        assertEquals(List.of("official_cue"), rendered.triggeredRulesJson().get("validationFailedChecks"));
        assertEquals("official_cue", rendered.triggeredRulesJson().get("validationRepairApplied"));
    }

    @Test
    void shouldAcceptOfficialSourceBrowseCandidateWithoutMechanicalRepair() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("买错厨房收纳太折腾了，直接说先看哪几家官网，会更容易把差别分清？");

        RenderedProbe rendered = service.renderOne("diag-official-source-browse", kitchenStoragePool(), orthogonalSeekClearProbe(
                "probe-official-source-browse",
                Map.of(
                        "D1", "initiation",
                        "D2", "informational_undirected",
                        "D3", "understanding_competence",
                        "D4", "optimal",
                        "D5", "acute_emergency",
                        "D6", "specification"
                )
        ));

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_LLM, rendered.renderSource());
        assertEquals("llm_validated_original", rendered.renderReason());
        assertEquals("买错厨房收纳太折腾了，直接说先看哪几家官网，会更容易把差别分清？", rendered.renderedPrompt());
    }

    @Test
    void shouldNotForceOfficialCueWhenTopicOnlyMentionsWordOfMouth() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("想先看看美厨物语厨房收纳的口碑怎么样，跟懒角落和纳川比，差别主要在哪？");

        Map<String, String> tags = tags(Map.of(
                "D1", "exploration",
                "D2", "informational_directed",
                "D3", "understanding_competence",
                "D4", "moderate_strain",
                "D5", "anticipatory_constraint",
                "D6", "specification"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "越看越乱",
                "D2", "想知道明确答案",
                "D3", "想搞懂原理",
                "D4", "有点累但还能想",
                "D5", "还有几天缓冲",
                "D6", "我会顺手带点条件去问"
        ));
        ProbeBlueprint blueprint = new ProbeBlueprint(
                "probe-word-of-mouth-no-official-cue",
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ORTHOGONAL,
                        tags,
                        translated,
                        null,
                        null,
                        kitchenStorageContextPackage(),
                        topicProjection("真实体验与口碑", List.of())
                )
        );

        RenderedProbe rendered = service.renderOne("diag-word-of-mouth-no-official-cue", kitchenStoragePool(), blueprint);

        assertFalse(rendered.fallbackUsed());
        assertEquals("llm_validated_original", rendered.renderReason());
        assertEquals(null, rendered.triggeredRulesJson().get("validationRepairApplied"));
    }

    @Test
    void shouldUseMinusOneSignalsInSoftHintsAndFallbackSubject() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne(
                "diag-context-fallback",
                samplePoolWithContext(),
                orthogonalSeekClearProbe(
                        "probe-context-fallback",
                        Map.of("D3", "understanding_competence", "D6", "recognition")
                )
        );

        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("家里常备喝的矿泉水"));

        ArgumentCaptor<String> softHintsCaptor = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(3)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), softHintsCaptor.capture(), anyString()
        );
        String joinedSoftHints = String.join("\n", softHintsCaptor.getAllValues());
        assertTrue(joinedSoftHints.contains("家庭常备"));
        assertTrue(joinedSoftHints.contains("家里常备喝的矿泉水"));
        assertTrue(joinedSoftHints.contains("口感更稳"));
    }

    @Test
    void shouldFallbackWhenOrthogonalRenderingFailsValidationThreeTimes() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne("diag-2", samplePool(), orthogonalEmergencyProbe());

        assertTrue(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.renderFamily().startsWith("orthogonal_fallback_"));
        assertTrue(rendered.renderedPrompt().contains("矿泉水")
                || rendered.renderedPrompt().contains("饮用水")
                || rendered.renderedPrompt().contains("天然水"));
        assertFalse(rendered.renderedPrompt().contains("急着用"));
        assertFalse(rendered.renderedPrompt().contains("马上就得用"));
        assertFalse(rendered.renderedPrompt().contains("按我的要求"));
        assertTrue(rendered.renderedPrompt().length() <= 60);
        verify(probeRenderingAiService, times(3)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldNotLiteralizeSoftStateTagsInAiPrompt() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        service.renderOne("diag-soft-hints", samplePool(), orthogonalEmergencyProbe());

        ArgumentCaptor<String> simulationProfile = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> softHints = ArgumentCaptor.forClass(String.class);
        verify(probeRenderingAiService, times(3)).renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                simulationProfile.capture(),
                anyString(),
                softHints.capture(),
                anyString()
        );

        String joinedSimulationProfile = String.join("\n", simulationProfile.getAllValues());
        String joinedSoftHints = String.join("\n", softHints.getAllValues());

        assertFalse(joinedSimulationProfile.contains("着急、有点烦，希望快点得到答案"));
        assertFalse(joinedSoftHints.contains("马上就得用"));
        assertFalse(joinedSoftHints.contains("想搞懂原理"));
    }

    @Test
    void shouldFallbackWithoutLiteralizedUrgencyOrIntentLabels() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne("diag-natural-fallback", samplePool(), orthogonalEmergencyProbe());

        assertTrue(rendered.fallbackUsed());
        assertFalse(rendered.renderedPrompt().contains("急着用"));
        assertFalse(rendered.renderedPrompt().contains("马上就得用"));
        assertFalse(rendered.renderedPrompt().contains("想跟你聊清楚"));
        assertFalse(rendered.renderedPrompt().contains("如果按我的要求"));
        assertFalse(rendered.renderedPrompt().contains("看懂差别"));
        assertFalse(rendered.renderedPrompt().contains("先让我看"));
        assertFalse(rendered.renderedPrompt().contains("少踩坑点"));
        assertFalse(rendered.renderedPrompt().contains("别容易翻车"));
        assertFalse(rendered.renderedPrompt().contains("值不值得先认"));
    }

    @Test
    void shouldEncodeConversationModeAndExposureInFallbackStem() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenSearch = service.renderOne("diag-hidden-search", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-search",
                Map.of("D3", "identity_freedom", "D6", "recognition")
        ));
        RenderedProbe hiddenConstraint = service.renderOne("diag-hidden-constraint", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-constraint",
                Map.of("D3", "identity_freedom", "D6", "specification")
        ));
        RenderedProbe visibleConstraint = service.renderOne("diag-visible-constraint", samplePool(), orthogonalSeekClearProbe(
                "probe-visible-constraint",
                Map.of("D1", "verification", "D4", "optimal", "D3", "identity_freedom", "D6", "specification")
        ));

        assertTrue(hiddenSearch.fallbackUsed());
        assertTrue(hiddenConstraint.fallbackUsed());
        assertTrue(visibleConstraint.fallbackUsed());

        assertNotEquals(structuralPrompt(hiddenSearch.renderedPrompt()), structuralPrompt(hiddenConstraint.renderedPrompt()));
        assertNotEquals(structuralPrompt(hiddenConstraint.renderedPrompt()), structuralPrompt(visibleConstraint.renderedPrompt()));
        assertTrue(visibleConstraint.renderedPrompt().contains("农夫山泉"));
    }

    @Test
    void shouldEncodeConversationModeDifferenceIntoBaseStemNotOnlySupportClause() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe singleTurn = service.renderOne("diag-hidden-single-turn", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-single-turn",
                Map.of("D3", "understanding_competence", "D6", "recognition")
        ));
        RenderedProbe dialogic = service.renderOne("diag-hidden-dialogic", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-dialogic",
                Map.of("D3", "understanding_competence", "D6", "dialogic")
        ));

        assertTrue(singleTurn.fallbackUsed());
        assertTrue(dialogic.fallbackUsed());
        assertEquals("single_turn_search_like", singleTurn.realizationPlan().simulationIntentProfile().conversationMode());
        assertEquals("back_and_forth_consultation", dialogic.realizationPlan().simulationIntentProfile().conversationMode());
        assertNotEquals(structuralPrompt(singleTurn.renderedPrompt()), structuralPrompt(dialogic.renderedPrompt()));
    }

    @Test
    void shouldEncodeD3DifferenceIntoBaseStemNotOnlySupportClause() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe understanding = service.renderOne("diag-hidden-understanding", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-understanding",
                Map.of("D3", "understanding_competence", "D6", "recognition")
        ));
        RenderedProbe identity = service.renderOne("diag-hidden-identity", samplePool(), orthogonalSeekClearProbe(
                "probe-hidden-identity",
                Map.of("D3", "identity_freedom", "D6", "recognition")
        ));

        assertTrue(understanding.fallbackUsed());
        assertTrue(identity.fallbackUsed());
        assertEquals("single_turn_search_like", understanding.realizationPlan().simulationIntentProfile().conversationMode());
        assertEquals("single_turn_search_like", identity.realizationPlan().simulationIntentProfile().conversationMode());
        assertNotEquals(structuralPrompt(understanding.renderedPrompt()), structuralPrompt(identity.renderedPrompt()));
    }

    @Test
    void shouldEncodeD5DifferenceIntoConstraintAngleStemNotOnlySupportClause() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe urgent = service.renderOne("diag-action-urgent", samplePool(), orthogonalActionStepProbe(
                "probe-action-urgent",
                Map.of("D1", "verification", "D2", "instructional_active", "D3", "subsistence_protection",
                        "D4", "optimal", "D5", "acute_emergency", "D6", "specification")
        ));
        RenderedProbe anticipatory = service.renderOne("diag-action-anticipatory", samplePool(), orthogonalActionStepProbe(
                "probe-action-anticipatory",
                Map.of("D1", "verification", "D2", "instructional_active", "D3", "subsistence_protection",
                        "D4", "optimal", "D5", "anticipatory_constraint", "D6", "specification")
        ));

        assertTrue(urgent.fallbackUsed());
        assertTrue(anticipatory.fallbackUsed());
        assertEquals("constraint_driven_query", urgent.realizationPlan().simulationIntentProfile().conversationMode());
        assertEquals("constraint_driven_query", anticipatory.realizationPlan().simulationIntentProfile().conversationMode());
        assertNotEquals(structuralPrompt(urgent.renderedPrompt()), structuralPrompt(anticipatory.renderedPrompt()));
        assertFalse(urgent.renderedPrompt().contains("先确认哪几个点"));
        assertFalse(anticipatory.renderedPrompt().contains("先确认哪几个点"));
        assertFalse(urgent.renderedPrompt().contains("先看哪几"));
        assertFalse(anticipatory.renderedPrompt().contains("先看哪几"));
    }

    @Test
    void shouldPreferShortFallbackForSingleTurnActionableSubsistenceSlice() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne("diag-action-single-turn-family", samplePool(), orthogonalActionStepProbe(
                "probe-action-single-turn-family",
                Map.of(
                        "D1", "exploration",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "recognition"
                )
        ));

        assertTrue(rendered.fallbackUsed());
        assertEquals("orthogonal_fallback_short", rendered.renderFamily());
        assertFalse(rendered.renderedPrompt().contains("先看哪"));
    }

    @Test
    void shouldEncodePostureAndD5IntoHiddenConfirmAndBuyFallbackStems() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe urgentDialogic = service.renderOne("diag-confirm-buy-urgent", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-urgent",
                Map.of("D1", "exploration", "D3", "subsistence_protection", "D5", "reflective", "D6", "dialogic")
        ));
        RenderedProbe exploratoryDialogic = service.renderOne("diag-confirm-buy-exploratory", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-exploratory",
                Map.of("D1", "exploration", "D3", "subsistence_protection", "D5", "anticipatory_constraint", "D6", "dialogic")
        ));
        RenderedProbe deliberateConstraint = service.renderOne("diag-confirm-buy-deliberate", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-deliberate",
                Map.of("D1", "formulation", "D3", "subsistence_protection", "D5", "anticipatory_constraint", "D6", "specification")
        ));
        RenderedProbe reflectiveConstraint = service.renderOne("diag-confirm-buy-reflective", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-reflective",
                Map.of("D1", "formulation", "D3", "subsistence_protection", "D5", "reflective", "D6", "specification")
        ));

        assertTrue(urgentDialogic.fallbackUsed());
        assertTrue(exploratoryDialogic.fallbackUsed());
        assertTrue(deliberateConstraint.fallbackUsed());
        assertTrue(reflectiveConstraint.fallbackUsed());

        assertNotEquals(structuralPrompt(urgentDialogic.renderedPrompt()), structuralPrompt(exploratoryDialogic.renderedPrompt()));
        assertNotEquals(structuralPrompt(exploratoryDialogic.renderedPrompt()), structuralPrompt(deliberateConstraint.renderedPrompt()));
        assertNotEquals(structuralPrompt(deliberateConstraint.renderedPrompt()), structuralPrompt(reflectiveConstraint.renderedPrompt()));
        assertNotEquals("现在直接买矿泉水，先确认哪一点更稳？", urgentDialogic.renderedPrompt());
        assertNotEquals("现在直接买矿泉水，先确认哪一点更稳？", exploratoryDialogic.renderedPrompt());
        assertNotEquals("矿泉水现在直接上稳不稳？", deliberateConstraint.renderedPrompt());
        assertNotEquals("矿泉水现在直接上稳不稳？", reflectiveConstraint.renderedPrompt());
        assertFalse(urgentDialogic.renderedPrompt().contains("定之前"));
        assertFalse(exploratoryDialogic.renderedPrompt().contains("买前"));
        assertFalse(deliberateConstraint.renderedPrompt().contains("先看哪点"));
        assertFalse(reflectiveConstraint.renderedPrompt().contains("先看哪点"));
    }

    @Test
    void shouldEncodeD5IntoVisibleConfirmAndBuyFallbackStem() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe urgentVisible = service.renderOne("diag-confirm-buy-visible-urgent", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-visible-urgent",
                Map.of("D1", "verification", "D3", "subsistence_protection", "D4", "optimal", "D5", "acute_emergency", "D6", "specification")
        ));
        RenderedProbe anticipatoryVisible = service.renderOne("diag-confirm-buy-visible-anticipatory", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-visible-anticipatory",
                Map.of("D1", "verification", "D3", "subsistence_protection", "D4", "optimal", "D5", "anticipatory_constraint", "D6", "specification")
        ));
        RenderedProbe reflectiveVisible = service.renderOne("diag-confirm-buy-visible-reflective", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-buy-visible-reflective",
                Map.of("D1", "verification", "D3", "subsistence_protection", "D4", "optimal", "D5", "reflective", "D6", "dialogic")
        ));

        assertTrue(urgentVisible.fallbackUsed());
        assertTrue(anticipatoryVisible.fallbackUsed());
        assertTrue(reflectiveVisible.fallbackUsed());

        assertTrue(urgentVisible.renderedPrompt().contains("农夫山泉"));
        assertTrue(anticipatoryVisible.renderedPrompt().contains("农夫山泉"));
        assertTrue(reflectiveVisible.renderedPrompt().contains("农夫山泉"));
        assertNotEquals(structuralPrompt(urgentVisible.renderedPrompt()), structuralPrompt(anticipatoryVisible.renderedPrompt()));
        assertNotEquals(structuralPrompt(anticipatoryVisible.renderedPrompt()), structuralPrompt(reflectiveVisible.renderedPrompt()));
        assertFalse(urgentVisible.renderedPrompt().contains("定之前"));
        assertFalse(anticipatoryVisible.renderedPrompt().contains("买前"));
        assertFalse(reflectiveVisible.renderedPrompt().contains("先看哪点"));
    }

    @Test
    void shouldRejectBrandAliasLeakForNonM4OrthogonalProbe() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("NFSQ矿泉水怎么选，想找一个具体方法，想搞懂原理？");

        RenderedProbe rendered = service.renderOne(
                "diag-2b",
                samplePool(),
                List.of("NFSQ"),
                orthogonalEmergencyProbe()
        );

        assertTrue(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.renderFamily().startsWith("orthogonal_fallback_"));
        assertEquals("brandHidden", rendered.exposurePolicy().contractValue());
        assertFalse(rendered.renderedPrompt().contains("NFSQ"));
    }

    @Test
    void shouldProvideDeterministicFallbackForEveryOrthogonalBlueprintWhenAiRenderingFails() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        List<ProbeBlueprint> orthogonalBlueprints = orthogonalMatrixService.generate(samplePool(), 100).stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .toList();

        for (ProbeBlueprint blueprint : orthogonalBlueprints) {
            try {
                RenderedProbe rendered = service.renderOne("diag-all-fallbacks", samplePool(), blueprint);
                assertTrue(rendered.fallbackUsed(), "orthogonal deterministic fallback should be used for " + blueprint.probeId());
            } catch (Exception ex) {
                fail("""
                        deterministic fallback failed for probeId=%s
                        abstractTags=%s
                        translatedTags=%s
                        realizationPlan=%s
                        error=%s
                        """.formatted(
                        blueprint.probeId(),
                        blueprint.abstractTags(),
                        blueprint.translatedTags(),
                        blueprint.realizationPlan(),
                        ex.getMessage()
                ));
            }
        }
    }

    @Test
    void shouldClaimDeterministicFallbackPromptsAcrossBatch() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        List<ProbeBlueprint> orthogonalBlueprints = orthogonalMatrixService.generate(samplePool(), 100).stream()
                .filter(blueprint -> blueprint.bucketType() == BucketType.ORTHOGONAL)
                .limit(12)
                .toList();

        ProbeRenderingService.Node2BatchRenderResult result = service.renderAllWithMetrics(
                "diag-batch-fallback-claim",
                samplePool(),
                List.of(),
                orthogonalBlueprints
        );

        List<String> prompts = result.probes().stream()
                .map(RenderedProbe::renderedPrompt)
                .toList();

        assertEquals(prompts.size(), prompts.stream().map(this::normalizePrompt).distinct().count());
        assertEquals(prompts.size(), prompts.stream().map(this::structuralPrompt).distinct().count());
        assertTrue(result.probes().stream().allMatch(RenderedProbe::fallbackUsed));
    }

    @Test
    void shouldRejectQuotedOrPerfectiveDecisionTailFromLlM() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("“这几个包装饮用水品牌，主要区别在哪？我比较完就能直接下单了。”？");

        RenderedProbe rendered = service.renderOne("diag-awkward-tail", samplePool(), orthogonalM4Probe());

        assertTrue(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertFalse(rendered.renderedPrompt().contains("“"));
        assertFalse(rendered.renderedPrompt().contains("”"));
        assertFalse(rendered.renderedPrompt().contains("直接下单了"));
        assertEquals("QUOTED_TEXT", ((Map<?, ?>) rendered.triggeredRulesJson().get("gateDecisionInput")).get("awkwardHeuristicId"));
    }

    @Test
    void shouldRejectPerfectiveDecisionTailAfterQuestionTailNormalization() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("农夫山泉的包装饮用水靠谱吗？想确认一下没问题就直接买了");

        RenderedProbe rendered = service.renderOne("diag-normalized-awkward-tail", samplePool(), orthogonalM4Probe());

        assertTrue(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertFalse(rendered.renderedPrompt().contains("确认一下没问题就直接买了"));
    }

    @Test
    void shouldRejectScriptedDecisionTailAndAwkwardLiteralizedPhrases() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("这几种包装饮用水到底有啥区别？我选一个直接买了？");

        RenderedProbe rendered = service.renderOne("diag-scripted-tail", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-scripted-tail",
                Map.of("D3", "understanding_competence", "D5", "anticipatory_constraint", "D6", "dialogic")
        ));

        assertTrue(rendered.fallbackUsed());
        assertFalse(rendered.renderedPrompt().contains("我选一个直接买了"));
        assertFalse(rendered.renderedPrompt().contains("差别先看哪点"));
        assertFalse(rendered.renderedPrompt().contains("值不值，会不会太普通"));
        assertFalse(rendered.renderedPrompt().contains("定之前"));
    }

    @Test
    void shouldUseSeeDifferencesLanguageForUnderstandingConfirmAndBuyFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenConstraint = service.renderOne("diag-understanding-hidden", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-understanding-hidden",
                Map.of("D1", "formulation", "D3", "understanding_competence", "D5", "acute_emergency", "D6", "specification")
        ));
        RenderedProbe visibleConstraint = service.renderOne("diag-understanding-visible", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-understanding-visible",
                Map.of("D1", "verification", "D3", "understanding_competence", "D4", "optimal", "D5", "acute_emergency", "D6", "specification")
        ));

        assertTrue(hiddenConstraint.fallbackUsed());
        assertTrue(visibleConstraint.fallbackUsed());
        assertTrue(hiddenConstraint.renderedPrompt().contains("差别") || hiddenConstraint.renderedPrompt().contains("区别"));
        assertTrue(visibleConstraint.renderedPrompt().contains("差别") || visibleConstraint.renderedPrompt().contains("区别"));
        assertFalse(hiddenConstraint.renderedPrompt().contains("差别先看哪点"));
        assertFalse(visibleConstraint.renderedPrompt().contains("现在哪点确认完就能直接定"));
    }

    @Test
    void shouldUseLessScriptedLanguageForSeekClearAnswerFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenIdentity = service.renderOne("diag-seek-hidden-identity", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-hidden-identity",
                Map.of("D3", "identity_freedom", "D6", "recognition")
        ));
        RenderedProbe visibleIdentity = service.renderOne("diag-seek-visible-identity", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-visible-identity",
                Map.of("D1", "verification", "D4", "optimal", "D3", "identity_freedom", "D6", "dialogic")
        ));

        assertTrue(hiddenIdentity.fallbackUsed());
        assertTrue(visibleIdentity.fallbackUsed());
        assertTrue(hiddenIdentity.renderedPrompt().contains("更值得先看")
                || hiddenIdentity.renderedPrompt().contains("先朝哪个方向看")
                || hiddenIdentity.renderedPrompt().contains("先从哪个方向挑"));
        assertTrue(visibleIdentity.renderedPrompt().contains("值得先看吗")
                || visibleIdentity.renderedPrompt().contains("先看值不值得选"));
        assertFalse(hiddenIdentity.renderedPrompt().contains("到底该选哪个，才没那么普通"));
        assertFalse(visibleIdentity.renderedPrompt().contains("值不值得优先选"));
    }

    @Test
    void shouldUseDirectedLowRiskLanguageForHiddenSeekFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenLowRisk = service.renderOne("diag-seek-hidden-low-risk", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-hidden-low-risk",
                Map.of(
                        "D1", "initiation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        ));

        assertTrue(hiddenLowRisk.fallbackUsed());
        assertTrue(hiddenLowRisk.renderedPrompt().contains("更不容易出问题"));
        assertFalse(hiddenLowRisk.renderedPrompt().contains("哪个会更稳"));
    }

    @Test
    void shouldKeepBuyCueForUrgentIdentityActionFallback() throws Exception {
        Map<String, String> families = validFallbackCandidates(orthogonalIdentityBuyActionProbe(
                "probe-urgent-identity-action-fallback"
        ));

        assertTrue(families.get("orthogonal_fallback_core").contains("定个"));
        assertTrue(families.get("orthogonal_fallback_core").contains("不太普通"));
        assertTrue(families.get("orthogonal_fallback_core").contains("从哪几处看起"));
    }

    @Test
    void shouldAvoidMechanicalSeekDirectionWordingForHiddenDialogicLowRiskFamilies() throws Exception {
        ProbeBlueprint seekBlueprint = orthogonalSeekClearProbe(
                "probe-seek-family-low-risk-wording",
                Map.of(
                        "D1", "initiation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        );

        Map<String, String> seekFamilies = validFallbackCandidates(seekBlueprint);

        assertTrue(seekFamilies.get("orthogonal_fallback_angle").contains("先朝哪个方向比"));
        assertFalse(seekFamilies.get("orthogonal_fallback_angle").contains("该先看哪个"));
        assertFalse(seekFamilies.get("orthogonal_fallback_angle").contains("更不容易买错"));

        assertTrue(seekFamilies.get("orthogonal_fallback_browse").contains("先从哪个方向比"));
        assertFalse(seekFamilies.get("orthogonal_fallback_browse").contains("先看哪个"));
    }

    @Test
    void shouldKeepMultipleValidFamiliesForDialogicConfirmAndSeekFallbacks() throws Exception {
        ProbeBlueprint confirmBlueprint = orthogonalConfirmAndBuyProbe(
                "probe-confirm-family-validation",
                Map.of(
                        "D1", "formulation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        );
        ProbeBlueprint seekBlueprint = orthogonalSeekClearProbe(
                "probe-seek-family-validation",
                Map.of(
                        "D1", "initiation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        );

        Map<String, String> confirmFamilies = validFallbackCandidates(confirmBlueprint);
        Map<String, String> seekFamilies = validFallbackCandidates(seekBlueprint);

        assertTrue(confirmFamilies.containsKey("orthogonal_fallback_core"));
        assertTrue(confirmFamilies.containsKey("orthogonal_fallback_short"));
        assertTrue(confirmFamilies.containsKey("orthogonal_fallback_angle"));
        assertTrue(confirmFamilies.containsKey("orthogonal_fallback_browse"));

        assertTrue(seekFamilies.containsKey("orthogonal_fallback_core"));
        assertTrue(seekFamilies.containsKey("orthogonal_fallback_short"));
        assertTrue(seekFamilies.containsKey("orthogonal_fallback_angle"));
        assertTrue(seekFamilies.containsKey("orthogonal_fallback_browse"));
    }

    @Test
    void shouldRotateDeterministicFallbackFamiliesAcrossSeeds() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        ProbeBlueprint confirmBlueprint = orthogonalConfirmAndBuyProbe(
                "probe-confirm-family-rotation",
                Map.of(
                        "D1", "formulation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        );
        ProbeBlueprint seekBlueprint = orthogonalSeekClearProbe(
                "probe-seek-family-rotation",
                Map.of(
                        "D1", "initiation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        );

        LinkedHashSet<String> confirmFamilies = new LinkedHashSet<>();
        LinkedHashSet<String> seekFamilies = new LinkedHashSet<>();
        for (int index = 0; index < 8; index++) {
            confirmFamilies.add(service.renderOne("diag-confirm-rotation-" + index, samplePool(), confirmBlueprint).renderFamily());
            seekFamilies.add(service.renderOne("diag-seek-rotation-" + index, samplePool(), seekBlueprint).renderFamily());
        }

        assertTrue(confirmFamilies.size() >= 2, "confirm fallback family should rotate across seeds");
        assertEquals(Set.of("orthogonal_fallback_angle"), seekFamilies, "seek fallback family should stay deterministic for the same semantic slice");
    }

    @Test
    void shouldRejectStructuralNearDuplicateBeforeQualityGate() throws Exception {
        Method evaluateValidation = ProbeRenderingService.class.getDeclaredMethod(
                "evaluateOrthogonalValidation",
                String.class,
                CategoryVariablePool.class,
                List.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                LinkedHashSet.class,
                boolean.class
        );
        evaluateValidation.setAccessible(true);

        ProbeBlueprint blueprint = orthogonalSeekClearProbe(
                "probe-structural-duplicate",
                Map.of("D3", "subsistence_protection", "D6", "dialogic")
        );
        LinkedHashSet<String> usedPrompts = new LinkedHashSet<>();
        usedPrompts.add(OrthogonalPromptFingerprint.canonical("矿泉水现在买哪个更稳，不想踩坑？"));

        Object validation = evaluateValidation.invoke(
                service,
                "矿泉水现在买哪个更稳，也想看懂差别？",
                samplePool(),
                List.of(),
                ProbeExposurePolicy.BRAND_HIDDEN,
                blueprint.realizationPlan(),
                usedPrompts,
                true
        );

        Method passedMethod = validation.getClass().getDeclaredMethod("passed");
        Method failedChecksMethod = validation.getClass().getDeclaredMethod("failedChecks");
        passedMethod.setAccessible(true);
        failedChecksMethod.setAccessible(true);

        assertFalse((boolean) passedMethod.invoke(validation));
        @SuppressWarnings("unchecked")
        List<String> failedChecks = (List<String>) failedChecksMethod.invoke(validation);
        assertTrue(failedChecks.contains("structural_duplicate"));
    }

    @Test
    void shouldRelaxStructuralDedupeWhenDeterministicFallbackSpaceWouldOtherwiseExhaust() throws Exception {
        Method buildCandidates = ProbeRenderingService.class.getDeclaredMethod(
                "buildOrthogonalFallbackCandidates",
                CategoryVariablePool.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                String.class
        );
        Method buildFallback = ProbeRenderingService.class.getDeclaredMethod(
                "buildOrthogonalFallback",
                CategoryVariablePool.class,
                List.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                Map.class,
                String.class,
                String.class,
                Set.class
        );
        buildCandidates.setAccessible(true);
        buildFallback.setAccessible(true);

        ProbeBlueprint blueprint = orthogonalSeekClearProbe(
                "probe-structural-relax",
                Map.of(
                        "D1", "initiation",
                        "D3", "creation_idleness",
                        "D5", "anticipatory_constraint",
                        "D6", "recognition"
                )
        );
        ProbeExposurePolicy exposurePolicy = ProbeExposurePolicy.classifyOrthogonal(blueprint.abstractTags());

        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) buildCandidates.invoke(
                service,
                samplePool(),
                exposurePolicy,
                blueprint.realizationPlan(),
                "structural-relax-seed"
        );

        LinkedHashSet<String> structurallyOccupied = new LinkedHashSet<>();
        for (Object candidate : candidates) {
            Method promptMethod = candidate.getClass().getDeclaredMethod("prompt");
            promptMethod.setAccessible(true);
            String prompt = (String) promptMethod.invoke(candidate);
            structurallyOccupied.add(OrthogonalPromptFingerprint.canonical(structuralShadowPrompt(prompt)));
        }
        LinkedHashSet<String> preClaimedPrompts = new LinkedHashSet<>(structurallyOccupied);

        Object relaxedCandidate = assertDoesNotThrow(() -> buildFallback.invoke(
                service,
                samplePool(),
                List.of(),
                exposurePolicy,
                blueprint.realizationPlan(),
                blueprint.translatedTags(),
                "diag-structural-relax",
                blueprint.probeId(),
                structurallyOccupied
        ));
        Method promptMethod = relaxedCandidate.getClass().getDeclaredMethod("prompt");
        promptMethod.setAccessible(true);
        String relaxedPrompt = (String) promptMethod.invoke(relaxedCandidate);

        assertFalse(preClaimedPrompts.contains(OrthogonalPromptFingerprint.canonical(relaxedPrompt)));
        assertTrue(OrthogonalPromptFingerprint.hasStructuralDuplicate(relaxedPrompt, preClaimedPrompts));
    }

    @Test
    void shouldExpandFallbackCandidatesAcrossMultipleAnchors() throws Exception {
        Method buildCandidates = ProbeRenderingService.class.getDeclaredMethod(
                "buildOrthogonalFallbackCandidates",
                CategoryVariablePool.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                String.class
        );
        buildCandidates.setAccessible(true);

        ProbeBlueprint blueprint = orthogonalSeekClearProbe(
                "probe-multi-anchor-fallback",
                Map.of("D3", "understanding_competence", "D6", "recognition")
        );
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) buildCandidates.invoke(
                service,
                samplePool(),
                ProbeExposurePolicy.BRAND_HIDDEN,
                blueprint.realizationPlan(),
                "multi-anchor-seed"
        );

        LinkedHashSet<String> coveredAnchors = new LinkedHashSet<>();
        for (Object candidate : candidates) {
            Method promptMethod = candidate.getClass().getDeclaredMethod("prompt");
            promptMethod.setAccessible(true);
            String prompt = (String) promptMethod.invoke(candidate);
            if (prompt.contains("矿泉水")) {
                coveredAnchors.add("矿泉水");
            }
            if (prompt.contains("饮用水")) {
                coveredAnchors.add("饮用水");
            }
            if (prompt.contains("天然水")) {
                coveredAnchors.add("天然水");
            }
        }

        assertTrue(coveredAnchors.size() >= 2, "fallback candidate space should span multiple anchors");
    }

    @Test
    void shouldSplitDialogicSeekFallbackFamiliesByExposure() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenSeek = service.renderOne("diag-seek-hidden-family", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-hidden-family",
                Map.of(
                        "D1", "initiation",
                        "D3", "subsistence_protection",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        ));
        RenderedProbe visibleSeek = service.renderOne("diag-seek-visible-family", samplePool(), orthogonalSeekClearProbe(
                "probe-seek-visible-family",
                Map.of(
                        "D1", "verification",
                        "D3", "subsistence_protection",
                        "D4", "optimal",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        ));

        assertTrue(hiddenSeek.fallbackUsed());
        assertTrue(visibleSeek.fallbackUsed());
        assertEquals("orthogonal_fallback_angle", hiddenSeek.renderFamily());
        assertEquals("orthogonal_fallback_short", visibleSeek.renderFamily());
    }

    @Test
    void shouldEncodeDecisionPhaseIntoConfirmAndBuyUnderstandingFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe initiationConstraint = service.renderOne("diag-confirm-understanding-init", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-understanding-init",
                Map.of("D1", "initiation", "D3", "understanding_competence", "D5", "acute_emergency", "D6", "specification")
        ));
        RenderedProbe formulationConstraint = service.renderOne("diag-confirm-understanding-form", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-understanding-form",
                Map.of("D1", "formulation", "D3", "understanding_competence", "D5", "acute_emergency", "D6", "specification")
        ));

        assertTrue(initiationConstraint.fallbackUsed());
        assertTrue(formulationConstraint.fallbackUsed());
        assertTrue(initiationConstraint.renderedPrompt().contains("刚开始"));
        assertTrue(formulationConstraint.renderedPrompt().contains("临到"));
        assertNotEquals(structuralPrompt(initiationConstraint.renderedPrompt()), structuralPrompt(formulationConstraint.renderedPrompt()));
    }

    @Test
    void shouldKeepResourceAcquisitionCueInSeverelyDepletedDialogicConfirmFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne("diag-confirm-understanding-depleted", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-understanding-depleted",
                Map.of(
                        "D1", "exploration",
                        "D3", "understanding_competence",
                        "D4", "severely_depleted",
                        "D5", "anticipatory_constraint",
                        "D6", "dialogic"
                )
        ));

        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("定一个"));
        assertTrue(rendered.renderedPrompt().contains("看清差别")
                || rendered.renderedPrompt().contains("哪块差别")
                || rendered.renderedPrompt().contains("哪两块差别"));
        assertFalse(rendered.renderedPrompt().contains("聊清"));
    }

    @Test
    void shouldUseAcquisitionCueForAffectionDrivenConfirmFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe rendered = service.renderOne("diag-confirm-affection-hidden", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-affection-hidden",
                Map.of(
                        "D1", "verification",
                        "D3", "affection_belonging_participation",
                        "D5", "reflective",
                        "D6", "dialogic"
                )
        ));

        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("定一个"));
        assertTrue(rendered.renderedPrompt().contains("先拿哪个"));
        assertFalse(rendered.renderedPrompt().contains("先拿哪种"));
    }

    @Test
    void shouldKeepShortCandidateValidForAffectionDrivenDialogicConfirmFallback() throws Exception {
        ProbeBlueprint blueprint = orthogonalConfirmAndBuyProbe(
                "probe-confirm-affection-short",
                Map.of(
                        "D1", "verification",
                        "D3", "affection_belonging_participation",
                        "D5", "reflective",
                        "D6", "dialogic"
                )
        );

        Map<String, String> families = validFallbackCandidates(blueprint);
        String shortLike = "";
        for (String candidate : new String[]{
                families.get("orthogonal_fallback_short"),
                families.get("orthogonal_fallback_browse"),
                families.get("orthogonal_fallback_angle"),
                families.get("orthogonal_fallback_core")
        }) {
            if (candidate != null && !candidate.isBlank()) {
                shortLike = candidate;
                break;
            }
        }
        assertTrue(shortLike != null && !shortLike.isBlank());
        assertFalse(shortLike.contains("大家一般会先看哪个"));
        assertFalse(shortLike.contains("大家一般会选哪个"));
        assertFalse(shortLike.contains("先看哪几块"));
    }

    @Test
    void shouldAvoidLegacyConfirmationScriptInConfirmAndBuyFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        RenderedProbe hiddenSubsistence = service.renderOne("diag-confirm-hidden-subsistence", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-hidden-subsistence",
                Map.of("D1", "formulation", "D3", "subsistence_protection", "D5", "acute_emergency", "D6", "specification")
        ));
        RenderedProbe visibleSubsistence = service.renderOne("diag-confirm-visible-subsistence", samplePool(), orthogonalConfirmAndBuyProbe(
                "probe-confirm-visible-subsistence",
                Map.of("D1", "verification", "D4", "optimal", "D3", "subsistence_protection", "D5", "acute_emergency", "D6", "specification")
        ));

        assertTrue(hiddenSubsistence.fallbackUsed());
        assertTrue(visibleSubsistence.fallbackUsed());
        assertFalse(hiddenSubsistence.renderedPrompt().contains("现在就定先看哪点更稳"));
        assertFalse(visibleSubsistence.renderedPrompt().contains("现在定前还要不要再确认哪点更稳"));
        assertFalse(visibleSubsistence.renderedPrompt().contains("现在直接上稳不稳"));
        assertFalse(hiddenSubsistence.renderedPrompt().contains("定之前"));
        assertFalse(visibleSubsistence.renderedPrompt().contains("买前"));
    }

    @Test
    void shouldUseBrowseStyleLanguageForVisibleIdentityFreedomFallback() {
        when(probeRenderingAiService.renderProbe(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn("随便说说");

        Map<String, String> tags = tags(Map.of(
                "D1", "verification",
                "D2", "informational_undirected",
                "D3", "identity_freedom",
                "D4", "optimal",
                "D5", "reflective",
                "D6", "specification"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "就想确认一下",
                "D2", "先随便了解下",
                "D3", "想要更有个性",
                "D4", "头脑清醒能分析",
                "D5", "不急慢慢看",
                "D6", "直接搜答案"
        ));

        ProbeBlueprint blueprint = new ProbeBlueprint(
                "probe-browse-visible-identity",
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );

        RenderedProbe rendered = service.renderOne("diag-browse-visible-identity", samplePool(), blueprint);

        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("先看看"));
        assertTrue(rendered.renderedPrompt().contains("没那么大众") || rendered.renderedPrompt().contains("别太大众"));
        assertTrue(rendered.renderedPrompt().contains("农夫山泉"));
    }

    @Test
    void shouldKeepShortCandidateValidForVisibleIdentitySeekFallback() throws Exception {
        ProbeBlueprint blueprint = orthogonalSeekClearProbe(
                "probe-seek-visible-identity-short",
                Map.of("D1", "verification", "D4", "optimal", "D3", "identity_freedom", "D6", "dialogic")
        );

        Map<String, String> families = validFallbackCandidates(blueprint);
        assertTrue(families.containsKey("orthogonal_fallback_short"));
        assertTrue(families.get("orthogonal_fallback_short").contains("不那么大众"));
    }

    @Test
    void shouldRenderTemplateBucketsByReplacement() {
        RenderedProbe pareto = service.renderOne("diag-3", samplePool(), paretoProbe());
        RenderedProbe adversarial = service.renderOne("diag-4", samplePool(), adversarialProbe());

        assertEquals(ProbeRenderingService.SOURCE_TEMPLATE, pareto.renderSource());
        assertEquals("pareto_template", pareto.renderFamily());
        assertEquals("瓶装饮用水怎么选才不踩坑？", pareto.renderedPrompt());
        assertEquals("brandHidden", pareto.exposurePolicy().contractValue());
        assertEquals(ProbeRenderingService.SOURCE_TEMPLATE, adversarial.renderSource());
        assertEquals("adversarial_template", adversarial.renderFamily());
        assertEquals("矿泉水里听说农夫山泉最近质量问题很多，是真的吗？", adversarial.renderedPrompt());
        assertEquals("brandVisible", adversarial.exposurePolicy().contractValue());
        assertTrue(adversarial.renderedPrompt().contains("矿泉水"));
    }

    @Test
    void shouldFallbackToSingleAuthorParetoTemplateWhenTopicProjectionMismatchesTemplate() {
        RenderedProbe rendered = service.renderOne("diag-topic-aware-pareto", mousePool(), paretoTopicAwareProbe());

        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("鼠标"));
        assertTrue(rendered.renderedPrompt().contains("DPI"));
        assertFalse(rendered.renderedPrompt().contains("直接推荐几款靠谱的"));
    }

    @Test
    void shouldFallbackToSingleAuthorAdversarialTemplateWhenTopicProjectionMismatchesTemplate() {
        RenderedProbe rendered = service.renderOne("diag-topic-aware-adversarial", mousePool(), adversarialTopicAwareProbe());

        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("鼠标"));
        assertTrue(rendered.renderedPrompt().contains("防水") || rendered.renderedPrompt().contains("静音"));
        assertFalse(rendered.renderedPrompt().contains("被营销吹得太过了"));
    }

    @Test
    void shouldRenderGenericBrandSpaceTrustTopicThroughSingleFallbackAuthor() {
        RenderedProbe rendered = service.renderOne(
                "diag-topic-aware-pareto-trust",
                kitchenStoragePool(),
                paretoBrandSpaceTopicAwareProbe("官网与信任线索")
        );

        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("官网") || rendered.renderedPrompt().contains("可信来源"));
        assertTrue(rendered.renderedPrompt().contains("口碑") || rendered.renderedPrompt().contains("资质"));
        assertTrue(rendered.renderedPrompt().contains("厨房收纳"));
    }

    @Test
    void shouldRenderGenericBrandSpaceDecisionTopicThroughSingleFallbackAuthor() {
        RenderedProbe rendered = service.renderOne(
                "diag-topic-aware-adversarial-decision",
                kitchenStoragePool(),
                adversarialBrandSpaceTopicAwareProbe("购买决策阻力")
        );

        assertEquals(ProbeRenderingService.SOURCE_DETERMINISTIC_FALLBACK, rendered.renderSource());
        assertTrue(rendered.fallbackUsed());
        assertTrue(rendered.renderedPrompt().contains("霜山") || rendered.renderedPrompt().contains("厨房收纳"));
        assertTrue(rendered.renderedPrompt().contains("劝退") || rendered.renderedPrompt().contains("怀疑") || rendered.renderedPrompt().contains("顾虑"));
    }

    @Test
    void shouldPreserveValidSourceTemplateBeforeApplyingTopicAwareRewrite() {
        CategoryVariablePool pool = new CategoryVariablePool(
                "美厨物语",
                "厨房收纳",
                List.of("懒角落", "纳川"),
                List.of("厨房收纳", "厨房置物架", "台面整理"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );
        Map<String, String> tags = tags(Map.of());
        Map<String, String> translated = translated(Map.of());
        String sourceTemplate = "厨房收纳里不想买美厨物语，有没有更值的替代？";
        ProbeBlueprint blueprint = new ProbeBlueprint(
                "probe-adversarial-source-preserve",
                BucketType.ADVERSARIAL,
                tags,
                translated,
                sourceTemplate,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ADVERSARIAL,
                        tags,
                        translated,
                        sourceTemplate,
                        "adversarial_replacement",
                        null,
                        topicProjection("推荐与替代", List.of())
                )
        );

        RenderedProbe rendered = service.renderOne("diag-source-template-preserve", pool, blueprint);

        assertFalse(rendered.fallbackUsed());
        assertEquals(ProbeRenderingService.SOURCE_TEMPLATE, rendered.renderSource());
        assertEquals(sourceTemplate, rendered.renderedPrompt());
        assertTrue(rendered.renderedPrompt().contains("替代"));
    }

    private CategoryVariablePool samplePool() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );
    }

    private CategoryVariablePool samplePoolWithContext() {
        return new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊")),
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "瓶装饮用水",
                        List.of("怡宝", "百岁山"),
                        List.of("矿泉水", "饮用水", "天然水"),
                        List.of(),
                        false,
                        null,
                        List.of(),
                        List.of(),
                        List.of("怡宝", "百岁山"),
                        List.of(),
                        List.of(),
                        List.of(
                                new GroundedContextSignal(
                                        MinusOneContextSlots.USAGE_SCENARIO,
                                        "家庭常备",
                                        "official_store",
                                        "5L 家庭装",
                                        0.88d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DESCRIPTIVE_CUE,
                                        "家里常备喝的矿泉水",
                                        "retrieval_synthesis",
                                        "descriptive entrypoint",
                                        0.72d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DECISION_FACTOR,
                                        "口感更稳",
                                        "retrieval_synthesis",
                                        "taste consideration",
                                        0.68d,
                                        List.of(MinusOneContextUses.NODE2_REALIZATION)
                                )
                        ),
                        List.of(),
                        Map.of()
                )
        );
    }

    private CategoryVariablePool smartphonePoolWithFallbackAnchors() {
        return new CategoryVariablePool(
                "小米",
                "智能手机",
                List.of("华为", "苹果", "三星", "OPPO"),
                List.of("智能手机", "智能手机品牌"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );
    }

    private CategoryVariablePool mousePool() {
        return new CategoryVariablePool(
                "AOC",
                "鼠标",
                List.of("罗技", "雷柏"),
                List.of("鼠标", "办公鼠标", "轻度游戏鼠标"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );
    }

    private CategoryVariablePool kitchenStoragePool() {
        return new CategoryVariablePool(
                "霜山",
                "厨房收纳",
                List.of("美厨物语", "茶花"),
                List.of("厨房收纳", "厨房置物架", "台面整理"),
                dimension("D1", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY,
                        List.of("完全不懂怎么选", "越看越乱", "方向开始清楚了", "就想确认一下")),
                dimension("D2", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL,
                        List.of("想知道明确答案", "先随便了解下", "想找一个具体方法", "就想赶紧买到")),
                dimension("D3", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE,
                        List.of("为了安全放心", "想跟圈子一致", "想搞懂原理", "想要更有个性", "主要图个开心")),
                dimension("D4", CssmHiAbstractStates.D4_COGNITIVE_LOAD,
                        List.of("脑子已经转不动了", "有点累但还能想", "头脑清醒能分析")),
                dimension("D5", CssmHiAbstractStates.D5_TIME_CONSTRAINT,
                        List.of("马上就得用", "还有几天缓冲", "不急慢慢看")),
                dimension("D6", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "想跟AI聊聊"))
        );
    }

    private DimensionVariables dimension(String dimensionId, List<String> abstractCodes, List<String> variables) {
        return new DimensionVariables(
                dimensionId,
                CssmHiAbstractStates.DIMENSION_NAMES.get(dimensionId),
                abstractCodes,
                variables
        );
    }

    private ProbeBlueprint orthogonalM4Probe() {
        return new ProbeBlueprint(
                "probe-orthogonal-m4",
                BucketType.ORTHOGONAL,
                tags(Map.of("D1", "verification", "D2", "resource_acquisition", "D3", "subsistence_protection", "D4", "optimal")),
                translated(Map.of("D1", "就想确认一下", "D2", "就想赶紧买到", "D3", "为了安全放心", "D4", "头脑清醒能分析")),
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ORTHOGONAL,
                        tags(Map.of("D1", "verification", "D2", "resource_acquisition", "D3", "subsistence_protection", "D4", "optimal")),
                        translated(Map.of("D1", "就想确认一下", "D2", "就想赶紧买到", "D3", "为了安全放心", "D4", "头脑清醒能分析")),
                        null
                )
        );
    }

    private ProbeBlueprint orthogonalEmergencyProbe() {
        return new ProbeBlueprint(
                "probe-orthogonal-emergency",
                BucketType.ORTHOGONAL,
                tags(Map.of("D1", "initiation", "D2", "instructional_active", "D3", "understanding_competence", "D5", "acute_emergency")),
                translated(Map.of("D1", "完全不懂怎么选", "D2", "想找一个具体方法", "D3", "想搞懂原理", "D5", "马上就得用")),
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ORTHOGONAL,
                        tags(Map.of("D1", "initiation", "D2", "instructional_active", "D3", "understanding_competence", "D5", "acute_emergency")),
                        translated(Map.of("D1", "完全不懂怎么选", "D2", "想找一个具体方法", "D3", "想搞懂原理", "D5", "马上就得用")),
                        null
                )
        );
    }

    private ProbeBlueprint paretoProbe() {
        return new ProbeBlueprint(
                "probe-pareto-1",
                BucketType.PARETO,
                tags(Map.of()),
                translated(Map.of()),
                "{category}怎么选才不踩坑",
                ProbeStatus.PENDING,
                planningService.plan(BucketType.PARETO, tags(Map.of()), translated(Map.of()), "{category}怎么选才不踩坑")
        );
    }

    private ProbeBlueprint orthogonalSeekClearProbe(String probeId, Map<String, String> overrides) {
        Map<String, String> tags = tags(Map.of(
                "D1", "exploration",
                "D2", "informational_directed",
                "D3", "identity_freedom",
                "D4", "moderate_strain",
                "D5", "anticipatory_constraint",
                "D6", "recognition"
        ));
        tags = new LinkedHashMap<>(tags);
        tags.putAll(overrides);

        Map<String, String> translated = translated(Map.of(
                "D1", "越看越乱",
                "D2", "想知道明确答案",
                "D3", "不想选得太普通",
                "D4", "有点累但还能想",
                "D5", "还有几天缓冲",
                "D6", "随便刷刷看"
        ));
        translated = new LinkedHashMap<>(translated);
        alignDriveTranslation(translated, tags.get("D3"));
        if ("specification".equals(tags.get("D6"))) {
            translated.put("D6", "我会顺手带点条件去问");
        }
        if ("verification".equals(tags.get("D1"))) {
            translated.put("D1", "就差最后确认一下");
        }
        if ("optimal".equals(tags.get("D4"))) {
            translated.put("D4", "头脑清楚，想确认后就定");
        }

        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );
    }

    private ProbeBlueprint orthogonalOfficialSourceBrowseProbe(String probeId) {
        Map<String, String> tags = tags(Map.of(
                "D1", "initiation",
                "D2", "informational_undirected",
                "D3", "understanding_competence",
                "D4", "optimal",
                "D5", "acute_emergency",
                "D6", "specification"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "完全不懂从哪看起",
                "D2", "先摸一圈方向",
                "D3", "想更快分清差别",
                "D4", "头脑清楚，想确认后就定",
                "D5", "马上就得用",
                "D6", "我会顺手带点条件去问"
        ));
        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ORTHOGONAL,
                        tags,
                        translated,
                        null,
                        null,
                        kitchenStorageContextPackage(),
                        topicProjection("官网与信任线索", List.of())
                )
        );
    }

    private ProbeBlueprint orthogonalOfficialSourceCompareProbe(String probeId) {
        Map<String, String> tags = tags(Map.of(
                "D1", "verification",
                "D2", "informational_directed",
                "D3", "understanding_competence",
                "D4", "optimal",
                "D5", "acute_emergency",
                "D6", "specification"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "就差最后确认一下",
                "D2", "想知道明确答案",
                "D3", "想更快分清差别",
                "D4", "头脑清楚，想确认后就定",
                "D5", "马上就得用",
                "D6", "我会顺手带点条件去问"
        ));
        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ORTHOGONAL,
                        tags,
                        translated,
                        null,
                        null,
                        kitchenStorageContextPackage(),
                        topicProjection("官网与信任线索", List.of())
                )
        );
    }

    private ProbeBlueprint adversarialProbe() {
        return new ProbeBlueprint(
                "probe-adversarial-1",
                BucketType.ADVERSARIAL,
                tags(Map.of()),
                translated(Map.of()),
                "听说{brandName}最近质量问题很多，是真的吗",
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ADVERSARIAL, tags(Map.of()), translated(Map.of()), "听说{brandName}最近质量问题很多，是真的吗")
        );
    }

    private ProbeBlueprint paretoTopicAwareProbe() {
        Map<String, String> tags = tags(Map.of());
        Map<String, String> translated = translated(Map.of());
        String sourcePrompt = "鼠标直接推荐几款靠谱的";
        return new ProbeBlueprint(
                "probe-pareto-topic-aware",
                BucketType.PARETO,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.PARETO,
                        tags,
                        translated,
                        sourcePrompt,
                        "pareto_recommendation_shortlist",
                        mouseContextPackage(),
                        topicProjection(
                                "多DPI调节是不是实用功能",
                                List.of(
                                        new GroundedContextSignal(
                                                MinusOneContextSlots.BRAND_EVIDENCE,
                                                "多DPI调节在办公与轻度游戏里的真实实用性",
                                                "topic_projection_compiler",
                                                "topic_contract",
                                                0.88d,
                                                List.of(MinusOneContextUses.NODE2_REALIZATION)
                                        )
                                )
                        )
                )
        );
    }

    private ProbeBlueprint adversarialTopicAwareProbe() {
        Map<String, String> tags = tags(Map.of());
        Map<String, String> translated = translated(Map.of());
        String sourcePrompt = "鼠标里AOC是不是被营销吹得太过了";
        return new ProbeBlueprint(
                "probe-adversarial-topic-aware",
                BucketType.ADVERSARIAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ADVERSARIAL,
                        tags,
                        translated,
                        sourcePrompt,
                        "adversarial_rumor_challenge",
                        mouseContextPackage(),
                        topicProjection(
                                "防水静音这些附加卖点靠不靠谱",
                                List.of(
                                        new GroundedContextSignal(
                                                MinusOneContextSlots.BRAND_EVIDENCE,
                                                "防水、静音等附加卖点是不是噱头、是否靠谱",
                                                "topic_projection_compiler",
                                                "topic_contract",
                                                0.88d,
                                                List.of(MinusOneContextUses.NODE2_REALIZATION)
                                        )
                                )
                        )
                )
        );
    }

    private ProbeBlueprint paretoBrandSpaceTopicAwareProbe(String topicLabel) {
        Map<String, String> tags = tags(Map.of());
        Map<String, String> translated = translated(Map.of());
        String sourcePrompt = "厨房收纳直接推荐几款靠谱的";
        return new ProbeBlueprint(
                "probe-pareto-brand-space-" + topicLabel.hashCode(),
                BucketType.PARETO,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.PARETO,
                        tags,
                        translated,
                        sourcePrompt,
                        "pareto_brand_space_topic",
                        kitchenStorageContextPackage(),
                        topicProjection(topicLabel, List.of())
                )
        );
    }

    private ProbeBlueprint adversarialBrandSpaceTopicAwareProbe(String topicLabel) {
        Map<String, String> tags = tags(Map.of());
        Map<String, String> translated = translated(Map.of());
        String sourcePrompt = "厨房收纳里霜山是不是被营销吹得太过了";
        return new ProbeBlueprint(
                "probe-adversarial-brand-space-" + topicLabel.hashCode(),
                BucketType.ADVERSARIAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(
                        BucketType.ADVERSARIAL,
                        tags,
                        translated,
                        sourcePrompt,
                        "adversarial_brand_space_topic",
                        kitchenStorageContextPackage(),
                        topicProjection(topicLabel, List.of())
                )
        );
    }

    private EngineTaskContextPackage mouseContextPackage() {
        return new EngineTaskContextPackage(
                "AOC",
                "鼠标",
                List.of("罗技", "雷柏"),
                List.of("鼠标", "办公鼠标", "轻度游戏鼠标"),
                List.of(),
                false,
                null,
                List.of(),
                List.of(),
                List.of("罗技"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private EngineTaskContextPackage kitchenStorageContextPackage() {
        return new EngineTaskContextPackage(
                "霜山",
                "厨房收纳",
                List.of("美厨物语", "茶花"),
                List.of("厨房收纳", "厨房置物架", "台面整理"),
                List.of(),
                false,
                null,
                List.of(),
                List.of(),
                List.of("美厨物语"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private LightConeV43TopicStateProjectionSupport.TopicStateProjection topicProjection(String label,
                                                                                         List<GroundedContextSignal> signals) {
        return new LightConeV43TopicStateProjectionSupport.TopicStateProjection(
                "topic-" + label.hashCode(),
                LightConeV43TopicStateProjectionSupport.PROJECTION_VERSION,
                label,
                "general_visibility",
                Map.of("D1", 0.55d, "D2", 0.60d, "D3", 0.55d, "D4", 0.45d, "D5", 0.50d, "D6", 0.55d),
                List.of(),
                1,
                1,
                0.4d,
                0.3d,
                "system_generated",
                signals,
                Map.of()
        );
    }

    private ProbeBlueprint orthogonalActionStepProbe(String probeId, Map<String, String> overrides) {
        Map<String, String> tags = tags(Map.of(
                "D1", "verification",
                "D2", "instructional_active",
                "D3", "subsistence_protection",
                "D4", "optimal",
                "D5", "acute_emergency",
                "D6", "specification"
        ));
        tags = new LinkedHashMap<>(tags);
        tags.putAll(overrides);

        Map<String, String> translated = translated(Map.of(
                "D1", "就差最后确认一下",
                "D2", "想知道具体怎么挑",
                "D3", "怕踩坑",
                "D4", "头脑清楚，想确认后就定",
                "D5", "马上就得用",
                "D6", "我会顺手带点条件去问"
        ));
        translated = new LinkedHashMap<>(translated);
        alignDriveTranslation(translated, tags.get("D3"));
        if ("anticipatory_constraint".equals(tags.get("D5"))) {
            translated.put("D5", "还有一点时间，先定个稳的");
        }

        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );
    }

    private ProbeBlueprint orthogonalIdentityBuyActionProbe(String probeId) {
        Map<String, String> tags = tags(Map.of(
                "D1", "initiation",
                "D2", "instructional_active",
                "D3", "identity_freedom",
                "D4", "optimal",
                "D5", "acute_emergency",
                "D6", "recognition"
        ));
        Map<String, String> translated = translated(Map.of(
                "D1", "我都快挑烦了",
                "D2", "直接告诉我该怎么挑",
                "D3", "不想选得太普通",
                "D4", "头脑清楚，想确认后就定",
                "D5", "马上就得定下来",
                "D6", "随手刷着看，也想直接问清楚"
        ));
        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );
    }

    private Map<String, String> validFallbackCandidates(ProbeBlueprint blueprint) throws Exception {
        Method buildCandidates = ProbeRenderingService.class.getDeclaredMethod(
                "buildOrthogonalFallbackCandidates",
                CategoryVariablePool.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                String.class
        );
        Method ensureQuestionTail = ProbeRenderingService.class.getDeclaredMethod("ensureQuestionTail", String.class);
        Method evaluateValidation = ProbeRenderingService.class.getDeclaredMethod(
                "evaluateOrthogonalValidation",
                String.class,
                CategoryVariablePool.class,
                List.class,
                ProbeExposurePolicy.class,
                com.geoprobe.dto.engine.v42.RealizationPlan.class,
                LinkedHashSet.class,
                boolean.class
        );
        buildCandidates.setAccessible(true);
        ensureQuestionTail.setAccessible(true);
        evaluateValidation.setAccessible(true);

        ProbeExposurePolicy exposurePolicy = ProbeExposurePolicy.classifyOrthogonal(blueprint.abstractTags());
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) buildCandidates.invoke(
                service,
                samplePool(),
                exposurePolicy,
                blueprint.realizationPlan(),
                "validation-seed"
        );

        Map<String, String> passingFamilies = new LinkedHashMap<>();
        for (Object candidate : candidates) {
            Method familyMethod = candidate.getClass().getDeclaredMethod("family");
            Method promptMethod = candidate.getClass().getDeclaredMethod("prompt");
            familyMethod.setAccessible(true);
            promptMethod.setAccessible(true);

            String family = (String) familyMethod.invoke(candidate);
            String prompt = (String) promptMethod.invoke(candidate);
            String finalized = (String) ensureQuestionTail.invoke(service, prompt);
            Object validation = evaluateValidation.invoke(
                    service,
                    finalized,
                    samplePool(),
                    List.of("农夫山泉"),
                    exposurePolicy,
                    blueprint.realizationPlan(),
                    new LinkedHashSet<String>(),
                    true
            );

            Method passedMethod = validation.getClass().getDeclaredMethod("passed");
            passedMethod.setAccessible(true);
            boolean passed = (boolean) passedMethod.invoke(validation);
            if (passed) {
                passingFamilies.put(family, finalized);
            }
        }
        return passingFamilies;
    }

    private ProbeBlueprint orthogonalConfirmAndBuyProbe(String probeId, Map<String, String> overrides) {
        Map<String, String> tags = tags(Map.of(
                "D1", "exploration",
                "D2", "resource_acquisition",
                "D3", "subsistence_protection",
                "D4", "moderate_strain",
                "D5", "anticipatory_constraint",
                "D6", "dialogic"
        ));
        tags = new LinkedHashMap<>(tags);
        tags.putAll(overrides);

        Map<String, String> translated = translated(Map.of(
                "D1", "方向开始清楚了",
                "D2", "就想赶紧买到",
                "D3", "为了安全放心",
                "D4", "有点累但还能想",
                "D5", "还有一点时间，先定个稳的",
                "D6", "想跟AI来回确认一下"
        ));
        translated = new LinkedHashMap<>(translated);
        alignDriveTranslation(translated, tags.get("D3"));
        if ("reflective".equals(tags.get("D5"))) {
            translated.put("D5", "虽然现在买，但还想先多确认一下");
        }
        if ("specification".equals(tags.get("D6"))) {
            translated.put("D6", "我会顺手带条件去确认");
        }
        if ("formulation".equals(tags.get("D1"))) {
            translated.put("D1", "已经缩到几种候选，想先定下来");
        }

        return new ProbeBlueprint(
                probeId,
                BucketType.ORTHOGONAL,
                tags,
                translated,
                null,
                ProbeStatus.PENDING,
                planningService.plan(BucketType.ORTHOGONAL, tags, translated, null)
        );
    }

    private void alignDriveTranslation(Map<String, String> translated, String d3Code) {
        if (translated == null || d3Code == null) {
            return;
        }
        switch (d3Code) {
            case "subsistence_protection" -> translated.put("D3", "怕踩坑");
            case "affection_belonging_participation" -> translated.put("D3", "想跟圈子一致");
            case "understanding_competence" -> translated.put("D3", "想搞懂原理");
            case "identity_freedom" -> translated.put("D3", "想要更有个性");
            case "creation_idleness" -> translated.put("D3", "主要图个开心");
            default -> {
            }
        }
    }

    private Map<String, String> tags(Map<String, String> overrides) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "exploration");
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", "moderate_strain");
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        tags.putAll(overrides);
        return tags;
    }

    private Map<String, String> translated(Map<String, String> overrides) {
        LinkedHashMap<String, String> translated = new LinkedHashMap<>();
        translated.put("D1", "越看越乱");
        translated.put("D2", "想知道明确答案");
        translated.put("D3", "想搞懂原理");
        translated.put("D4", "有点累但还能想");
        translated.put("D5", "还有几天缓冲");
        translated.put("D6", "随便刷刷看");
        translated.putAll(overrides);
        return translated;
    }

    private String structuralPrompt(String prompt) {
        return OrthogonalPromptFingerprint.structural(prompt);
    }

    private String structuralShadowPrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim().replaceAll("[？?！!。]+$", "");
        if (normalized.contains("不想踩坑")) {
            return normalized + "，也想看懂差别？";
        }
        return normalized + "，不想踩坑？";
    }

    private String normalizePrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        return Pattern.compile("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+")
                .matcher(prompt.toLowerCase())
                .replaceAll("");
    }

    private static final class PromptAssetAwareStub implements ProbeRenderingAiService, V42PromptAssetAware {

        private final String response;

        private PromptAssetAwareStub(String response) {
            this.response = response;
        }

        @Override
        public String renderProbe(String brandName,
                                  String category,
                                  String competitors,
                                  String categoryAnchors,
                                  String brandInjectionPolicy,
                                  String simulationProfile,
                                  String mustSemantics,
                                  String softHints,
                                  String behaviorRules) {
            return response;
        }

        @Override
        public V42PromptAssetDescriptor describePromptAsset(V42PromptAssetKey assetKey) {
            return new V42PromptAssetDescriptor(
                    assetKey,
                    "node2-v2026-05-10",
                    "classpath:test/node2/probe_rendering/current.json",
                    "abc123"
            );
        }
    }
}

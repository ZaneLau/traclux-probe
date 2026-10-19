package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.dto.engine.v42.CategoryAnchorSignal;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceRequest;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceResult;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.GroundedContextSignal;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CssmHiStateSpaceServiceTest {

    @Mock
    private CssmHiStateSpaceAiService cssmHiStateSpaceAiService;

    private CssmHiStateSpaceService cssmHiStateSpaceService;

    @BeforeEach
    void setUp() {
        cssmHiStateSpaceService = new CssmHiStateSpaceService(cssmHiStateSpaceAiService, new ObjectMapper());
    }

    @Test
    void shouldGenerateValidatedPoolFromLlm() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                """);

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-1", request(false));

        assertFalse(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_LLM, result.source());
        assertEquals(List.of("initiation", "exploration", "formulation", "verification"),
                result.pool().d1CognitiveAnomaly().abstractCodes());
        assertEquals("认知负荷容量", result.pool().d4CognitiveLoad().dimensionName());
        assertEquals("现在能冷静判断", result.pool().d4CognitiveLoad().variables().get(2));
        assertEquals(List.of("矿泉水", "饮用水", "天然水"), result.pool().categoryAnchors());
        verify(cssmHiStateSpaceAiService).translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void shouldInjectMinusOneSignalsIntoTranslationCategoryPrompt() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                """);

        CssmHiStateSpaceRequest request = new CssmHiStateSpaceRequest(
                "农夫山泉",
                "包装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                false,
                sampleTaskOrder("农夫山泉", "包装饮用水", List.of("怡宝", "百岁山")),
                new EngineTaskContextPackage(
                        "农夫山泉",
                        "包装饮用水",
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
                                        MinusOneContextSlots.PRODUCT_FORM_FACTOR,
                                        "桶装水",
                                        "jd_rank",
                                        "12.9L/19L 大桶",
                                        0.88d,
                                        List.of(MinusOneContextUses.NODE0_STATE_INFERENCE)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.USAGE_SCENARIO,
                                        "家庭常备",
                                        "official_store",
                                        "5L 家庭装",
                                        0.86d,
                                        List.of(MinusOneContextUses.NODE0_STATE_INFERENCE)
                                ),
                                new GroundedContextSignal(
                                        MinusOneContextSlots.DECISION_FACTOR,
                                        "冲奶粉更稳妥",
                                        "retrieval_synthesis",
                                        "母婴场景",
                                        0.72d,
                                        List.of(MinusOneContextUses.NODE0_STATE_INFERENCE)
                                )
                        ),
                        List.of(),
                        Map.of()
                )
        );

        cssmHiStateSpaceService.generate("diag-context-1", request);

        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        verify(cssmHiStateSpaceAiService).translateStateSpace(
                categoryCaptor.capture(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
        assertTrue(categoryCaptor.getValue().contains("包装饮用水"));
        assertTrue(categoryCaptor.getValue().contains("桶装水"));
        assertTrue(categoryCaptor.getValue().contains("家庭常备"));
        assertTrue(categoryCaptor.getValue().contains("冲奶粉更稳妥"));
    }

    @Test
    void shouldFallbackToDeterministicPoolWhenAiFails() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new IllegalStateException("boom"));

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-2", request(false));

        assertTrue(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_DETERMINISTIC_FALLBACK, result.source());
        assertEquals(V42CreativeDegradationReasons.OUTPUT_INVALID, result.fallbackReasonCode());
        assertEquals("完全迷茫不知从何问起", result.pool().d1CognitiveAnomaly().variables().get(0));
        assertEquals("跟AI聊聊", result.pool().d6InteractionModality().variables().get(2));
    }

    @Test
    void shouldFallbackWhenJsonCannotBeParsed() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("not-json");

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-3", request(false));

        assertTrue(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_DETERMINISTIC_FALLBACK, result.source());
        assertEquals(V42CreativeDegradationReasons.OUTPUT_INVALID, result.fallbackReasonCode());
    }

    @Test
    void shouldExposeTokenBudgetFallbackReasonCode() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new LlmTokenBudgetExceededException("deepseek", "deepseek-v3.2", 1_000L, 1_000L));

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-budget", request(false));

        assertTrue(result.fallbackUsed());
        assertEquals(V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED, result.fallbackReasonCode());
    }

    @Test
    void shouldParseCodeFencedJsonWithoutFallingBack() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                ```json
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                ```
                """);

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-code-fence", request(false));

        assertFalse(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_LLM, result.source());
    }

    @Test
    void shouldParseJsonWrappedByExplanationWithoutFallingBack() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                下面是严格 JSON：
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                请按此结果使用。
                """);

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-json-wrapper", request(false));

        assertFalse(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_LLM, result.source());
    }

    @Test
    void shouldParseObjectShapedDimensionPayloadWithoutFallingBack() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                {
                  "d1CognitiveAnomaly": {
                    "initiation": "完全不懂怎么选",
                    "exploration": "越看越有点乱",
                    "formulation": "方向开始清楚了",
                    "verification": "就想确认一下"
                  },
                  "d2TeleologicalGoal": {
                    "clarification": "想知道明确答案",
                    "learning": "先随便了解下",
                    "decision_support": "想找一个做法",
                    "resource_acquisition": "就想赶紧买到"
                  },
                  "d3AxiologicalDrive": {
                    "risk_avoidance": "为了安全放心",
                    "normative_alignment": "想跟圈子一致",
                    "epistemic_need": "想搞懂背后原理",
                    "identity_expression": "就想更有个性",
                    "emotional_resonance": "主要图个开心"
                  },
                  "d4CognitiveLoad": {
                    "overloaded": "脑子已经很累了",
                    "bandwidth_available": "还有点余力分析",
                    "optimal": "现在能冷静判断"
                  },
                  "d5TimeConstraint": {
                    "urgent": "马上就得用上",
                    "near_term": "还有几天缓冲",
                    "open_ended": "暂时不急慢慢看"
                  },
                  "d6InteractionModality": {
                    "search": "直接搜个答案",
                    "feed": "先随便刷一刷",
                    "conversation": "打算跟AI聊聊"
                  }
                }
                """);

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-object-shape", request(false));

        assertFalse(result.fallbackUsed());
        assertEquals(CssmHiStateSpaceService.SOURCE_LLM, result.source());
        assertEquals("完全不懂怎么选", result.pool().d1CognitiveAnomaly().variables().get(0));
        assertEquals("打算跟AI聊聊", result.pool().d6InteractionModality().variables().get(2));
    }

    @Test
    void shouldRejectMismatchedAbstractCodes() {
        CategoryVariablePool invalidPool = new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", "认知异常与情感状态", List.of("wrong", "exploration", "formulation", "verification"),
                        List.of("a", "b", "c", "d")),
                dimension("D2", "目的论微观目标", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("a", "b", "c", "d")),
                dimension("D3", "价值论驱动力", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("a", "b", "c", "d", "e")),
                dimension("D4", "认知负荷容量", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("a", "b", "c")),
                dimension("D5", "时间约束", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("a", "b", "c")),
                dimension("D6", "交互模态", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("a", "b", "c"))
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cssmHiStateSpaceService.validate(invalidPool, false));

        assertTrue(ex.getMessage().contains("abstractCodes"));
    }

    @Test
    void shouldRejectMismatchedVariableCount() {
        CategoryVariablePool invalidPool = basePool()
                .withD2(dimension("D2", "目的论微观目标", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("a", "b", "c")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cssmHiStateSpaceService.validate(invalidPool, false));

        assertTrue(ex.getMessage().contains("数量不一致"));
    }

    @Test
    void shouldRejectMissingM4Preconditions() {
        CategoryVariablePool invalidPool = basePool()
                .withD1(dimension("D1", "认知异常与情感状态", List.of("initiation", "exploration", "formulation", "final_check"),
                        List.of("a", "b", "c", "d")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cssmHiStateSpaceService.validate(invalidPool, false));

        assertTrue(ex.getMessage().contains("verification"));
    }

    @Test
    void shouldRejectVariableLongerThan15Chars() {
        CategoryVariablePool invalidPool = basePool()
                .withD6(dimension("D6", "交互模态", CssmHiAbstractStates.D6_INTERACTION_MODALITY,
                        List.of("直接搜答案", "随便刷刷看", "这是一个明显超过十五个字的翻译变量")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> cssmHiStateSpaceService.validate(invalidPool, false));

        assertTrue(ex.getMessage().contains("超过 15 字"));
    }

    @Test
    void shouldAllowTwoAnchorsOnlyInFallbackAnchorMode() {
        CssmHiStateSpaceRequest twoAnchors = new CssmHiStateSpaceRequest(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝"),
                List.of("矿泉水", "饮用水"),
                false,
                sampleTaskOrder("农夫山泉", "瓶装饮用水", List.of("怡宝")),
                sampleContextPackage("农夫山泉", "瓶装饮用水", List.of("怡宝"), List.of("矿泉水", "饮用水"), false)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cssmHiStateSpaceService.generate("diag-4", twoAnchors));

        assertTrue(ex.getMessage().contains("categoryAnchors"));
    }

    @Test
    void shouldAcceptTwoAnchorsInFallbackAnchorMode() {
        when(cssmHiStateSpaceAiService.translateStateSpace(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        )).thenReturn("""
                {
                  "d1CognitiveAnomaly": ["完全不懂怎么选", "越看越有点乱", "方向开始清楚了", "就想确认一下"],
                  "d2TeleologicalGoal": ["想知道明确答案", "先随便了解下", "想找一个做法", "就想赶紧买到"],
                  "d3AxiologicalDrive": ["为了安全放心", "想跟圈子一致", "想搞懂背后原理", "就想更有个性", "主要图个开心"],
                  "d4CognitiveLoad": ["脑子已经很累了", "还有点余力分析", "现在能冷静判断"],
                  "d5TimeConstraint": ["马上就得用上", "还有几天缓冲", "暂时不急慢慢看"],
                  "d6InteractionModality": ["直接搜个答案", "先随便刷一刷", "打算跟AI聊聊"]
                }
                """);

        CssmHiStateSpaceResult result = cssmHiStateSpaceService.generate("diag-5", request(true));

        assertFalse(result.fallbackUsed());
        assertEquals(List.of("矿泉水", "饮用水"), result.pool().categoryAnchors());
    }

    private CssmHiStateSpaceRequest request(boolean fallbackAnchorMode) {
        return new CssmHiStateSpaceRequest(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                fallbackAnchorMode ? List.of("矿泉水", "饮用水") : List.of("矿泉水", "饮用水", "天然水"),
                fallbackAnchorMode,
                sampleTaskOrder("农夫山泉", "瓶装饮用水", List.of("怡宝", "百岁山")),
                sampleContextPackage(
                        "农夫山泉",
                        "瓶装饮用水",
                        List.of("怡宝", "百岁山"),
                        fallbackAnchorMode ? List.of("矿泉水", "饮用水") : List.of("矿泉水", "饮用水", "天然水"),
                        fallbackAnchorMode
                )
        );
    }

    private FrontDeskTaskOrder sampleTaskOrder(String brandName, String category, List<String> competitors) {
        return new FrontDeskTaskOrder(
                brandName,
                category,
                competitors,
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of(),
                Map.of()
        );
    }

    private EngineTaskContextPackage sampleContextPackage(String brandName,
                                                          String category,
                                                          List<String> competitors,
                                                          List<String> anchors,
                                                          boolean fallbackAnchorMode) {
        String anchorSource = fallbackAnchorMode ? "fallback_category_resolver" : "front_desk_task_order";
        return new EngineTaskContextPackage(
                brandName,
                category,
                competitors,
                anchors,
                anchors.stream().map(anchor -> new CategoryAnchorSignal(anchor, anchorSource, 1.0d)).toList(),
                fallbackAnchorMode,
                null,
                List.of(),
                List.of(),
                competitors,
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "brandBrief",
                        "categorySceneMap",
                        "descriptiveEntrypoints",
                        "personaExpressionTendencies",
                        "targetModelContextSignals"
                ),
                Map.of("categoryAnchorSource", anchorSource)
        );
    }

    private MutablePool basePool() {
        return new MutablePool(new CategoryVariablePool(
                "农夫山泉",
                "瓶装饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "饮用水", "天然水"),
                dimension("D1", "认知异常与情感状态", CssmHiAbstractStates.D1_COGNITIVE_ANOMALY, List.of("a", "b", "c", "d")),
                dimension("D2", "目的论微观目标", CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL, List.of("a", "b", "c", "d")),
                dimension("D3", "价值论驱动力", CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE, List.of("a", "b", "c", "d", "e")),
                dimension("D4", "认知负荷容量", CssmHiAbstractStates.D4_COGNITIVE_LOAD, List.of("a", "b", "c")),
                dimension("D5", "时间约束", CssmHiAbstractStates.D5_TIME_CONSTRAINT, List.of("a", "b", "c")),
                dimension("D6", "交互模态", CssmHiAbstractStates.D6_INTERACTION_MODALITY, List.of("a", "b", "c"))
        ));
    }

    private DimensionVariables dimension(String id, String name, List<String> abstractCodes, List<String> variables) {
        return new DimensionVariables(id, name, abstractCodes, variables);
    }

    private static final class MutablePool {
        private final CategoryVariablePool pool;

        private MutablePool(CategoryVariablePool pool) {
            this.pool = pool;
        }

        private CategoryVariablePool withD1(DimensionVariables dimension) {
            return new CategoryVariablePool(
                    pool.brandName(), pool.category(), pool.competitors(), pool.categoryAnchors(),
                    dimension, pool.d2TeleologicalGoal(), pool.d3AxiologicalDrive(),
                    pool.d4CognitiveLoad(), pool.d5TimeConstraint(), pool.d6InteractionModality()
            );
        }

        private CategoryVariablePool withD2(DimensionVariables dimension) {
            return new CategoryVariablePool(
                    pool.brandName(), pool.category(), pool.competitors(), pool.categoryAnchors(),
                    pool.d1CognitiveAnomaly(), dimension, pool.d3AxiologicalDrive(),
                    pool.d4CognitiveLoad(), pool.d5TimeConstraint(), pool.d6InteractionModality()
            );
        }

        private CategoryVariablePool withD6(DimensionVariables dimension) {
            return new CategoryVariablePool(
                    pool.brandName(), pool.category(), pool.competitors(), pool.categoryAnchors(),
                    pool.d1CognitiveAnomaly(), pool.d2TeleologicalGoal(), pool.d3AxiologicalDrive(),
                    pool.d4CognitiveLoad(), pool.d5TimeConstraint(), dimension
            );
        }
    }
}

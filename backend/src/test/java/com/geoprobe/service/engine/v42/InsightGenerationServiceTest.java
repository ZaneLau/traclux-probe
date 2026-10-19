package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.CommercialInsightAiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.dto.engine.v42.BucketKpi;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CommercialInsights;
import com.geoprobe.dto.engine.v42.CompetitorKpi;
import com.geoprobe.dto.engine.v42.KpiSummary;
import com.geoprobe.dto.engine.v42.LengthBucketKpi;
import com.geoprobe.dto.engine.v42.LengthStratifiedKpi;
import com.geoprobe.dto.engine.v42.Node5AKpiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightGenerationServiceTest {

    @Mock
    private CommercialInsightAiService commercialInsightAiService;

    private InsightGenerationService insightGenerationService;

    @BeforeEach
    void setUp() {
        insightGenerationService = new InsightGenerationService(commercialInsightAiService, new ObjectMapper());
    }

    @Test
    void shouldGenerateCommercialInsightsFromLlm() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                {
                  "headlineInsight": "您的品牌在认知负荷容量维度出现结构失衡，33.0%的结构对比度已经足以说明高负荷场景正在掉队。",
                  "topThreeInsights": [
                    {
                      "title": "认知负荷容量维度已经形成塌陷",
                      "evidence": "D4 结构对比度只有 33.0%，其中 optimal 切片穿透率只有 5%。",
                      "recommendation": "必须立即重写高负荷场景的首选论据，把品牌证据前置到回答开头。"
                    },
                    {
                      "title": "对抗桶正在拖垮品牌结论",
                      "evidence": "对抗桶 confirmationResponseRate 只有 16.67%，有效样本 18 条。",
                      "recommendation": "必须立即重做对抗问题的回答模板，把品牌结论和反击证据压进前两句。"
                    },
                    {
                      "title": "Insta360 正在抢走比较位",
                      "evidence": "Insta360 responseShare 达 36.67%，positiveRate 达 72.73%。",
                      "recommendation": "必须立即围绕 Insta360 抢走的比较问题补齐对比证据和首选结论。"
                    }
                  ],
                  "insightSource": "LLM"
                }
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertEquals(3, result.topThreeInsights().size());
        assertTrue(result.headlineInsight().contains("认知负荷容量"));
        assertEquals("认知负荷容量维度已经形成塌陷", result.topThreeInsights().getFirst().title());
    }

    @Test
    void shouldBindDiagnosisIdIntoNode5BContext() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenAnswer(invocation -> {
            assertEquals("diag-node5b-001", LlmCallContext.get().getDiagnosisId());
            return """
                    {
                      "headlineInsight": "您的品牌在认知负荷容量维度出现结构失衡。",
                      "topThreeInsights": [
                        {
                          "title": "认知负荷容量维度出现结构偏移",
                          "evidence": "D4 结构对比度只有 33.00%，optimal 切片穿透率只有 5.00%。",
                          "recommendation": "必须立即重写高负荷场景的首选论据。"
                        },
                        {
                          "title": "对抗桶正在拖垮品牌结论",
                          "evidence": "对抗桶 confirmationResponseRate 只有 16.67%，有效样本 18 条。",
                          "recommendation": "必须立即重做对抗问题模板。"
                        },
                        {
                          "title": "Insta360 正在抢走比较位",
                          "evidence": "Insta360 responseShare 达 36.67%，positiveRate 达 72.73%。",
                          "recommendation": "必须立即围绕 Insta360 补齐对比证据。"
                        }
                      ],
                      "insightSource": "LLM"
                    }
                    """;
        });

        CommercialInsights result = insightGenerationService.generate("diag-node5b-001", new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertNull(LlmCallContext.get());
    }

    @Test
    void shouldFallbackDeterministicallyWhenLlmFails() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_TEMPLATE_FALLBACK, result.insightSource());
        assertEquals(V42CreativeDegradationReasons.OUTPUT_INVALID, result.fallbackReasonCode());
        assertEquals(3, result.topThreeInsights().size());
        assertTrue(result.headlineInsight().contains("结构失衡"));
        assertTrue(result.topThreeInsights().get(0).evidence().contains("33.00%"));
        assertTrue(result.topThreeInsights().get(1).evidence().contains("16.67%"));
    }

    @Test
    void shouldNormalizeWeakLanguageIntoActionableRecommendation() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                {
                  "headlineInsight": "您的品牌在认知负荷容量维度失衡。",
                  "topThreeInsights": [
                    {
                      "title": "认知负荷容量维度已经失守",
                      "evidence": "D4 结构对比度只有 33.00%。",
                      "recommendation": "建议继续优化高负荷场景话术。"
                    },
                    {
                      "title": "对抗桶正在拖垮品牌结论",
                      "evidence": "对抗桶 confirmationResponseRate 只有 16.67%。",
                      "recommendation": "必须立即重做对抗问题模板。"
                    },
                    {
                      "title": "Insta360 正在抢走比较位",
                      "evidence": "Insta360 responseShare 达 36.67%。",
                      "recommendation": "必须立即围绕 Insta360 补齐对比证据。"
                    }
                  ],
                  "insightSource": "LLM"
                }
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertEquals(null, result.fallbackReasonCode());
        assertEquals(3, result.topThreeInsights().size());
        assertEquals("立即继续优化高负荷场景话术。", result.topThreeInsights().getFirst().recommendation());
    }

    @Test
    void shouldExposeTokenBudgetFallbackReasonCode() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString()))
                .thenThrow(new LlmTokenBudgetExceededException("deepseek", "deepseek-v3.2", 1_000L, 1_000L));

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_TEMPLATE_FALLBACK, result.insightSource());
        assertEquals(V42CreativeDegradationReasons.TOKEN_BUDGET_EXCEEDED, result.fallbackReasonCode());
        verify(commercialInsightAiService, never()).repairInsights(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldAcceptActionableRecommendationsWithoutLiteralUrgencyTokens() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                {
                  "headlineInsight": "品牌在关键场景下心智份额薄弱，面临竞品压制与结构风险，需立即强化主动提及与场景渗透。",
                  "topThreeInsights": [
                    {
                      "title": "正交场景下品牌主动提及率极低，存在严重心智空白风险",
                      "evidence": "正交桶 spontaneousMentionRate 仅为 18.00%，且 effectiveSampleSize 为 45。",
                      "recommendation": "启动针对正交场景的专项品牌沟通战役，通过场景化内容营销与精准广告投放，将品牌与特定使用场景强绑定。"
                    },
                    {
                      "title": "竞品 Insta360 在市场份额与情感强度上形成双重压制",
                      "evidence": "竞品 Insta360 的 responseShare 高达 36.67%，positiveRate 为 72.73%，averageStrength 达到 74.00。",
                      "recommendation": "发起竞争性营销分析，拆解竞品的高强度情感联结策略，并针对性重构反击路径。"
                    },
                    {
                      "title": "品牌在中等长度回答中完全失声，内容策略存在结构性缺陷",
                      "evidence": "中等长度回答的 spontaneousMentionRate 为 20.00%，confirmationResponseRate 为 45.00%。",
                      "recommendation": "全面重构中等篇幅内容资产，补齐深度种草文案、产品故事与使用指南。"
                    }
                  ],
                  "insightSource": "LLM"
                }
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertEquals(3, result.topThreeInsights().size());
    }

    @Test
    void shouldParseJsonWrappedInCodeFence() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                ```json
                {
                  "headlineInsight": "品牌在关键场景下心智份额薄弱，面临竞品压制与结构风险。",
                  "topThreeInsights": [
                    {
                      "title": "正交场景下品牌主动提及率极低，存在严重心智空白风险",
                      "evidence": "正交桶 spontaneousMentionRate 仅为 18.00%，effectiveSampleSize 为 45。",
                      "recommendation": "立即启动针对正交场景的专项品牌沟通战役。"
                    },
                    {
                      "title": "竞品 Insta360 在市场份额与情感强度上形成双重压制",
                      "evidence": "竞品 Insta360 的 responseShare 高达 36.67%，positiveRate 为 72.73%，averageStrength 达到 74.00。",
                      "recommendation": "发起竞争性营销分析，拆解竞品的高强度情感联结策略，并针对性重构反击路径。"
                    },
                    {
                      "title": "品牌在中等长度回答中完全失声，内容策略存在结构性缺陷",
                      "evidence": "中等长度回答的 spontaneousMentionRate 为 20.00%，confirmationResponseRate 为 45.00%。",
                      "recommendation": "全面重构中等篇幅内容资产，补齐深度种草文案、产品故事与使用指南。"
                    }
                  ],
                  "insightSource": "LLM"
                }
                ```
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertEquals("正交场景下品牌主动提及率极低，存在严重心智空白风险", result.topThreeInsights().getFirst().title());
    }

    @Test
    void shouldCoerceNonActionableRecommendationAndBackfillKnownMetric() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                {
                  "headlineInsight": "品牌在关键场景下出现结构失衡。",
                  "topThreeInsights": [
                    {
                      "title": "正交场景下品牌主动提及率正在走弱",
                      "evidence": "正交桶表现持续走弱，品牌开始滑出优先比较位。",
                      "recommendation": "聚焦正交场景内容建设。"
                    },
                    {
                      "title": "对抗桶正在拖垮品牌结论",
                      "evidence": "对抗桶 confirmationResponseRate 只有 16.67%，有效样本 18 条。",
                      "recommendation": "必须立即重做对抗问题模板。"
                    },
                    {
                      "title": "Insta360 正在抢走比较位",
                      "evidence": "Insta360 responseShare 达 36.67%。",
                      "recommendation": "必须立即围绕 Insta360 补齐对比证据。"
                    }
                  ],
                  "insightSource": "LLM"
                }
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_LLM, result.insightSource());
        assertTrue(result.topThreeInsights().getFirst().recommendation().startsWith("立即"));
        assertTrue(result.topThreeInsights().getFirst().evidence().contains("关键指标："));
    }

    @Test
    void shouldRepairStructuredOutputWhenInitialPayloadIsInvalid() {
        when(commercialInsightAiService.generateInsights(anyString(), anyString())).thenReturn("""
                {
                  "headlineInsight": "品牌在关键场景下心智份额薄弱。"
                }
                """);

        CommercialInsights result = insightGenerationService.generate(new Node5AKpiResult(sampleSummary()));

        assertEquals(InsightGenerationService.SOURCE_TEMPLATE_FALLBACK, result.insightSource());
        assertEquals(3, result.topThreeInsights().size());
        assertEquals(V42CreativeDegradationReasons.OUTPUT_INVALID, result.fallbackReasonCode());
        verify(commercialInsightAiService, never()).repairInsights(anyString(), anyString(), anyString(), anyString());
    }

    private KpiSummary sampleSummary() {
        LinkedHashMap<String, Double> dimensionPenetration = new LinkedHashMap<>();
        dimensionPenetration.put("D1_initiation", 0.1800d);
        dimensionPenetration.put("D1_verification", 0.2200d);
        dimensionPenetration.put("D4_optimal", 0.0500d);
        dimensionPenetration.put("D4_moderate_strain", 0.1400d);
        dimensionPenetration.put("D6_dialogic", 0.2600d);

        LinkedHashMap<String, Double> dimensionRadarScores = new LinkedHashMap<>();
        dimensionRadarScores.put("D1", 0.1800d);
        dimensionRadarScores.put("D2", 0.2800d);
        dimensionRadarScores.put("D3", 0.2100d);
        dimensionRadarScores.put("D4", 0.3300d);
        dimensionRadarScores.put("D5", 0.2200d);
        dimensionRadarScores.put("D6", 0.2600d);

        LinkedHashMap<String, CompetitorKpi> competitorKpis = new LinkedHashMap<>();
        competitorKpis.put("Insta360", new CompetitorKpi("Insta360", 22, 0.3667d, 0.7273d, 0.7400d));
        competitorKpis.put("DJI", new CompetitorKpi("DJI", 16, 0.2667d, 0.5000d, 0.5800d));

        return new KpiSummary(
                0.1800d,
                0.4400d,
                0.3000d,
                new LengthStratifiedKpi(
                        new LengthBucketKpi("Short", 20, 10, 10, 0.3000d, 0.5000d, 1.2000d),
                        new LengthBucketKpi("Medium", 35, 15, 20, 0.2000d, 0.4500d, 1.3500d),
                        new LengthBucketKpi("Long", 36, 12, 24, 0.0600d, 0.2000d, 1.8000d)
                ),
                0.1200d,
                0.2100d,
                58.2000d,
                new BucketKpi(BucketType.ORTHOGONAL, 45, 42, 42, 0, 0.1800d, 0.0000d, 0.1800d, 0.0700d, 0.1100d),
                new BucketKpi(BucketType.PARETO, 28, 28, 0, 28, 0.0000d, 0.3929d, 0.3929d, 0.2143d, 0.2500d),
                new BucketKpi(BucketType.ADVERSARIAL, 18, 18, 0, 18, 0.0000d, 0.1667d, 0.1667d, 0.0000d, 0.0000d),
                Map.copyOf(dimensionPenetration),
                Map.copyOf(dimensionRadarScores),
                Map.copyOf(competitorKpis),
                88
        );
    }
}

package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.DefenseCompletenessAiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefenseCompletenessJudgeTest {

    @Mock
    private DefenseCompletenessAiService defenseCompletenessAiService;

    private DefenseCompletenessJudge judge;

    @BeforeEach
    void setUp() {
        judge = new DefenseCompletenessJudge(defenseCompletenessAiService, new ObjectMapper());
    }

    @Test
    void shouldScoreOnlyAdversarialProbesAndApplyFixedWeightedFormula() {
        when(defenseCompletenessAiService.judge(anyString(), anyString(), anyString())).thenReturn("""
                {"evidence":1,"comparison":0.5,"conclusion":1}
                """);

        DefenseCompletenessSummary result = judge.judge("农夫山泉", List.of(
                probe("orthogonal-1", BucketType.ORTHOGONAL, ProbeDispatchStatus.SUCCESS, "普通问法", "普通回答"),
                probe("adversarial-1", BucketType.ADVERSARIAL, ProbeDispatchStatus.SUCCESS, "别推荐这个品牌", "它有行业认证，也比竞品更稳定，仍然值得优先选。"),
                probe("adversarial-2", BucketType.ADVERSARIAL, ProbeDispatchStatus.FAILED, "攻击问法", "")
        ));

        assertEquals(2, result.evaluatedProbeCount());
        assertEquals(0.4250d, result.averageCompositeScore());

        DefenseCompletenessResult scored = result.probeResults().getFirst();
        assertEquals("adversarial-1", scored.probeId());
        assertEquals(1.0d, scored.evidenceScore());
        assertEquals(0.5d, scored.comparisonScore());
        assertEquals(1.0d, scored.conclusionScore());
        assertEquals(0.8500d, scored.compositeScore());

        DefenseCompletenessResult fallback = result.probeResults().get(1);
        assertEquals("adversarial-2", fallback.probeId());
        assertEquals(0.0d, fallback.compositeScore());

        verify(defenseCompletenessAiService, times(1)).judge(anyString(), anyString(), anyString());
    }

    @Test
    void shouldBindDiagnosisIdIntoNode5CContext() {
        when(defenseCompletenessAiService.judge(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            assertEquals("diag-node5c-001", LlmCallContext.get().getDiagnosisId());
            return """
                    {"evidence":1,"comparison":0.5,"conclusion":1}
                    """;
        });

        DefenseCompletenessSummary result = judge.judge("diag-node5c-001", "农夫山泉", List.of(
                probe("adversarial-1", BucketType.ADVERSARIAL, ProbeDispatchStatus.SUCCESS, "攻击问法", "它有行业认证，也比竞品更稳定，仍然值得优先选。")
        ));

        assertEquals(1, result.evaluatedProbeCount());
        assertEquals(0.8500d, result.probeResults().getFirst().compositeScore());
        assertNull(LlmCallContext.get());
    }

    @Test
    void shouldFallbackToMissingWhenLlmOutputIsInvalid() {
        when(defenseCompletenessAiService.judge(anyString(), anyString(), anyString())).thenReturn("""
                {"evidence":1,"comparison":0.3,"conclusion":1}
                """);

        DefenseCompletenessSummary result = judge.judge("农夫山泉", List.of(
                probe("adversarial-invalid", BucketType.ADVERSARIAL, ProbeDispatchStatus.SUCCESS, "攻击问法", "回答文本")
        ));

        assertEquals(1, result.evaluatedProbeCount());
        assertEquals(0.0d, result.averageCompositeScore());
        assertEquals(0.0d, result.probeResults().getFirst().evidenceScore());
        verify(defenseCompletenessAiService, times(1)).judge(anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotCallLlmWhenProbeHasNoResponseText() {
        DefenseCompletenessSummary result = judge.judge("农夫山泉", List.of(
                probe("adversarial-empty", BucketType.ADVERSARIAL, ProbeDispatchStatus.SUCCESS, "攻击问法", " ")
        ));

        assertEquals(0.0d, result.averageCompositeScore());
        verify(defenseCompletenessAiService, never()).judge(anyString(), anyString(), anyString());
    }

    private Node4ProbeOutput probe(String probeId,
                                   BucketType bucketType,
                                   ProbeDispatchStatus dispatchStatus,
                                   String renderedPrompt,
                                   String responseText) {
        return new Node4ProbeOutput(
                probeId,
                bucketType,
                abstractTags(),
                translatedTags(),
                ProbeExposurePolicy.BRAND_VISIBLE,
                renderedPrompt,
                dispatchStatus,
                new RawResponse(responseText, "", List.of(), 36, 128, 164, 1200L, false),
                dispatchStatus == ProbeDispatchStatus.SUCCESS ? null : "dispatch failed",
                BrandDetectionResult.detected(
                        "L1_EXACT",
                        "comparison",
                        "positive",
                        0.88d,
                        ProbeType.BRAND_ASSERTIVE,
                        128,
                        "Medium",
                        List.of(),
                        List.of(),
                        ""
                )
        );
    }

    private Map<String, String> abstractTags() {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "verification");
        tags.put("D2", "resource_acquisition");
        tags.put("D3", "subsistence_protection");
        tags.put("D4", "optimal");
        tags.put("D5", "acute_emergency");
        tags.put("D6", "specification");
        return Map.copyOf(tags);
    }

    private Map<String, String> translatedTags() {
        return Map.of("D2", "想买前最后确认");
    }
}

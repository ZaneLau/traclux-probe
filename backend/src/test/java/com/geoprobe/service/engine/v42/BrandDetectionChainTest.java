package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node4ParsingRequest;
import com.geoprobe.dto.engine.v42.Node4ParsingResult;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandDetectionChainTest {

    @Test
    void shouldUseL1BeforeL3AndAppendNode4Payload() {
        AtomicInteger l3Calls = new AtomicInteger();
        BrandDetectionChain chain = new BrandDetectionChain(
                (brandName, brandAliases, competitors, responseText) -> {
                    l3Calls.incrementAndGet();
                    return "{\"mentioned\":false}";
                },
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-l1",
                        BucketType.PARETO,
                        "探镜 GEO 是我最推荐的运动相机，和Insta360比我还是更偏向它。",
                        72
                )
        )));

        assertEquals(0, l3Calls.get());
        assertEquals(1, result.probes().size());
        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals("L1_EXACT", result.probes().getFirst().brandDetectionResult().detectionLevel());
        assertEquals("top1_recommendation", result.probes().getFirst().brandDetectionResult().position());
        assertEquals("positive", result.probes().getFirst().brandDetectionResult().sentiment());
        assertEquals("Short", result.probes().getFirst().brandDetectionResult().responseLengthBucket());
        assertEquals("probe-l1", result.probes().getFirst().probeId());
        assertEquals(BucketType.PARETO, result.probes().getFirst().bucketType());
        assertEquals("verification", result.probes().getFirst().abstractTags().get("D1"));
    }

    @Test
    void shouldUseL2AliasWhenExactBrandNameMissing() {
        BrandDetectionChain chain = new BrandDetectionChain(
                emptyL3(),
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-l2",
                        BucketType.ORTHOGONAL,
                        "如果你买运动相机，我更推荐GeoProbe，稳定性和画质都更靠谱。",
                        96
                )
        )));

        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals("L2_ALIAS", result.probes().getFirst().brandDetectionResult().detectionLevel());
        assertEquals("positive", result.probes().getFirst().brandDetectionResult().sentiment());
        assertEquals("top1_recommendation", result.probes().getFirst().brandDetectionResult().position());
        assertEquals("Medium", result.probes().getFirst().brandDetectionResult().responseLengthBucket());
    }

    @Test
    void shouldUseDeterministicAliasFallbackWhenConfiguredAliasesAreEmpty() {
        BrandDetectionChain chain = new BrandDetectionChain(
                emptyL3(),
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(new Node4ParsingRequest(
                "diag-hima-fallback",
                "鸿蒙智行",
                List.of(),
                List.of("理想"),
                Map.of(),
                List.of(probe(
                        "probe-hima-fallback",
                        BucketType.ORTHOGONAL,
                        "如果你看新能源，我会更推荐AITO，智能座舱和补能体验都更完整。",
                        96
                ))
        ));

        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals("L2_ALIAS", result.probes().getFirst().brandDetectionResult().detectionLevel());
    }

    @Test
    void shouldBucketLengthByCompletionTokensInsteadOfTotalTokens() {
        BrandDetectionChain chain = new BrandDetectionChain(
                emptyL3(),
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node3ProbeOutput probe = new Node3ProbeOutput(
                "probe-token-bucket",
                BucketType.ORTHOGONAL,
                tags("exploration", "moderate_strain"),
                translatedTags(),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "运动相机怎么选？",
                ProbeDispatchStatus.SUCCESS,
                new RawResponse(
                        "我更推荐探镜 GEO，优先看防抖和稳定性。",
                        "",
                        List.of(),
                        400,
                        40,
                        440,
                        42L,
                        false
                ),
                null
        );

        Node4ParsingResult result = chain.parse(request(List.of(probe)));

        assertEquals(40, result.probes().getFirst().brandDetectionResult().responseTokenCount());
        assertEquals("Short", result.probes().getFirst().brandDetectionResult().responseLengthBucket());
    }

    @Test
    void shouldParseSemanticDetectionPayloadWrappedInCodeFence() {
        BrandDetectionChain chain = new BrandDetectionChain(
                (brandName, brandAliases, competitors, responseText) -> """
                        ```json
                        {"mentioned":true,"position":"comparison","sentiment":"positive","strength":0.81}
                        ```
                        """,
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-l3-fenced",
                        BucketType.ORTHOGONAL,
                        "乐品乐茶和八马放一起比的话，我会更推荐乐品乐茶，口感更顺。",
                        96
                )
        )));

        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals("L3_SEMANTIC", result.probes().getFirst().brandDetectionResult().detectionLevel());
        assertEquals("comparison", result.probes().getFirst().brandDetectionResult().position());
        assertEquals("positive", result.probes().getFirst().brandDetectionResult().sentiment());
    }

    @Test
    void shouldTriggerL3OnlyAfterL1AndL2Miss() {
        AtomicInteger l3Calls = new AtomicInteger();
        BrandDetectionChain chain = new BrandDetectionChain(
                (brandName, brandAliases, competitors, responseText) -> {
                    l3Calls.incrementAndGet();
                    return """
                            {
                              "mentioned": true,
                              "position": "passing_mention",
                              "sentiment": "neutral",
                              "strength": 0.67
                            }
                            """;
                },
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-l3",
                        BucketType.ORTHOGONAL,
                        "这家做全景运动影像的一体化方案最近讨论度挺高，但回答里没直接写品牌名。",
                        260
                )
        )));

        assertEquals(1, l3Calls.get());
        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals("L3_SEMANTIC", result.probes().getFirst().brandDetectionResult().detectionLevel());
        assertEquals("passing_mention", result.probes().getFirst().brandDetectionResult().position());
        assertEquals("neutral", result.probes().getFirst().brandDetectionResult().sentiment());
        assertEquals("Long", result.probes().getFirst().brandDetectionResult().responseLengthBucket());
    }

    @Test
    void shouldProduceCompetitorMentionsInParallelWithBrandDetection() {
        BrandDetectionChain chain = new BrandDetectionChain(
                emptyL3(),
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-competitor",
                        BucketType.PARETO,
                        "探镜 GEO和Insta360比更稳，但和大疆 Osmo Action 相比价格没优势。",
                        123
                )
        )));

        assertEquals(2, result.probes().getFirst().brandDetectionResult().competitorsMentioned().size());
        assertEquals("Insta360", result.probes().getFirst().brandDetectionResult().competitorsMentioned().get(0).competitorName());
        assertEquals("DJI", result.probes().getFirst().brandDetectionResult().competitorsMentioned().get(1).competitorName());
        assertTrue(result.probes().getFirst().brandDetectionResult().mentioned());
    }

    @Test
    void shouldMarkBrandTemplateBucketAsBrandAssertiveEvenWhenNotM4() {
        BrandDetectionChain chain = new BrandDetectionChain(
                emptyL3(),
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-template-assertive",
                        BucketType.PARETO,
                        "探镜 GEO和Insta360到底哪个好？",
                        91,
                        tags("exploration", "moderate_strain")
                )
        )));

        assertEquals("brandAssertive", result.probes().getFirst().brandDetectionResult().probeType().contractValue());
    }

    @Test
    void shouldReturnNoMentionWhenAllThreeLevelsMissOrL3Fails() {
        BrandDetectionChain chain = new BrandDetectionChain(
                (brandName, brandAliases, competitors, responseText) -> {
                    throw new IllegalStateException("mock fail");
                },
                new AliasMatcher(),
                new ObjectMapper()
        );

        Node4ParsingResult result = chain.parse(request(List.of(
                probe(
                        "probe-miss",
                        BucketType.ORTHOGONAL,
                        "我会优先看防抖、画质和续航，不先锁定任何品牌。",
                        45
                )
        )));

        assertFalse(result.probes().getFirst().brandDetectionResult().mentioned());
        assertEquals(0.0d, result.probes().getFirst().brandDetectionResult().strength());
        assertNotNull(result.probes().getFirst().brandDetectionResult().probeType());
    }

    private BrandDetectionL3AiService emptyL3() {
        return (brandName, brandAliases, competitors, responseText) -> "{\"mentioned\":false}";
    }

    private Node4ParsingRequest request(List<Node3ProbeOutput> probes) {
        return new Node4ParsingRequest(
                "diag-brand-detection",
                "探镜 GEO",
                List.of("GeoProbe"),
                List.of("Insta360", "DJI"),
                Map.of(
                        "Insta360", List.of("影石"),
                        "DJI", List.of("大疆", "Osmo Action")
                ),
                probes
        );
    }

    private Node3ProbeOutput probe(String probeId, BucketType bucketType, String responseText, int tokensConsumed) {
        return probe(probeId, bucketType, responseText, tokensConsumed, tags("verification", "optimal"));
    }

    private Node3ProbeOutput probe(String probeId,
                                   BucketType bucketType,
                                   String responseText,
                                   int tokensConsumed,
                                   Map<String, String> abstractTags) {
        ProbeExposurePolicy exposurePolicy = bucketType == BucketType.ORTHOGONAL
                ? ProbeExposurePolicy.BRAND_HIDDEN
                : ProbeExposurePolicy.BRAND_VISIBLE;
        return new Node3ProbeOutput(
                probeId,
                bucketType,
                abstractTags,
                translatedTags(),
                exposurePolicy,
                renderedPrompt(bucketType),
                ProbeDispatchStatus.SUCCESS,
                new RawResponse(
                        responseText,
                        "先判断是否有品牌提及",
                        List.of("https://source.example/a"),
                        20,
                        tokensConsumed,
                        tokensConsumed + 20,
                        42L,
                        false
                ),
                null
        );
    }

    private Map<String, String> tags(String d1, String d4) {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", d1);
        tags.put("D2", "informational_directed");
        tags.put("D3", "understanding_competence");
        tags.put("D4", d4);
        tags.put("D5", "anticipatory_constraint");
        tags.put("D6", "recognition");
        return Map.copyOf(tags);
    }

    private Map<String, String> translatedTags() {
        LinkedHashMap<String, String> tags = new LinkedHashMap<>();
        tags.put("D1", "就想确认一下");
        tags.put("D2", "想知道明确答案");
        tags.put("D3", "想搞懂原理");
        tags.put("D4", "头脑清醒能分析");
        tags.put("D5", "还有时间比较");
        tags.put("D6", "随便刷刷看");
        return Map.copyOf(tags);
    }

    private String renderedPrompt(BucketType bucketType) {
        return switch (bucketType) {
            case ORTHOGONAL -> "运动相机怎么选？";
            case PARETO -> "探镜 GEO和Insta360到底哪个好？";
            case ADVERSARIAL -> "听说探镜 GEO最近质量问题很多，是真的吗？";
        };
    }
}

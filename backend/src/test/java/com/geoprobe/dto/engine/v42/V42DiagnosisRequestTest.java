package com.geoprobe.dto.engine.v42;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42DiagnosisRequestTest {

    @Test
    void shouldRequireExplicitTaskOrderAndContextPackage() {
        FrontDeskTaskOrder taskOrder = sampleTaskOrder();
        EngineTaskContextPackage contextPackage = sampleContextPackage();
        V42DiagnosisRequest request = new V42DiagnosisRequest(
                "task-legacy-001",
                null,
                "4.2",
                taskOrder,
                contextPackage,
                List.of("农夫"),
                Map.of("怡宝", List.of("C'estbon"))
        );

        assertEquals("农夫山泉", request.taskOrder().brandName());
        assertEquals("饮用水", request.taskOrder().category());
        assertEquals(List.of("怡宝", "百岁山"), request.contextPackage().competitors());
        assertEquals(List.of("矿泉水", "天然水"), request.categoryAnchors());
        assertEquals(2, request.contextPackage().categoryAnchorSignals().size());
        assertEquals("front_desk_task_order", request.contextPackage().categoryAnchorSignals().getFirst().source());
        assertTrue(request.contextPackage().missingContextItems().contains("brandBrief"));
        assertTrue(request.contextPackage().missingContextItems().contains("targetModelContextSignals"));
        assertEquals(List.of("矿泉水", "天然水"),
                request.contextPackage().slotValuesForUse(
                        MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                        MinusOneContextUses.NODE0_STATE_INFERENCE
                ));
    }

    @Test
    void shouldRejectMismatchedTaskOrderAndContextPackage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new V42DiagnosisRequest(
                        "task-layer-001",
                        null,
                        "4.2",
                        new FrontDeskTaskOrder(
                                "农夫山泉",
                                "饮用水",
                                List.of("怡宝"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of(),
                                Map.of()
                        ),
                        new EngineTaskContextPackage(
                                "百岁山",
                                "饮用水",
                                List.of("怡宝"),
                                List.of("矿泉水"),
                                List.of(new CategoryAnchorSignal("矿泉水", "manual", 0.9d)),
                                false,
                                null,
                                List.of(),
                                List.of(),
                                List.of("怡宝"),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of()
                        ),
                        List.of(),
                        Map.of()
                )
        );

        assertTrue(exception.getMessage().contains("brandName"));
    }

    @Test
    void shouldRejectMissingExplicitTaskOrderOrContextPackage() {
        IllegalArgumentException taskOrderException = assertThrows(
                IllegalArgumentException.class,
                () -> new V42DiagnosisRequest(
                        "task-001",
                        null,
                        "4.2",
                        null,
                        new EngineTaskContextPackage(
                                "农夫山泉",
                                "饮用水",
                                List.of(),
                                List.of("矿泉水", "天然水"),
                                List.of(
                                        new CategoryAnchorSignal("矿泉水", "front_desk_task_order", 1.0d),
                                        new CategoryAnchorSignal("天然水", "front_desk_task_order", 1.0d)
                                ),
                                false,
                                null,
                                List.of(),
                                List.of(),
                                List.of(),
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
                                Map.of("categoryAnchorSource", "front_desk_task_order")
                        ),
                        List.of(),
                        Map.of()
                )
        );
        assertTrue(taskOrderException.getMessage().contains("taskOrder"));

        IllegalArgumentException contextPackageException = assertThrows(
                IllegalArgumentException.class,
                () -> new V42DiagnosisRequest(
                        "task-001",
                        null,
                        "4.2",
                        new FrontDeskTaskOrder("农夫山泉", "饮用水", List.of(), null, null, null, null, null, Map.of(), Map.of()),
                        null,
                        List.of(),
                        Map.of()
                )
        );
        assertTrue(contextPackageException.getMessage().contains("contextPackage"));
    }

    @Test
    void shouldRejectMismatchedCompetitorsBetweenTaskOrderAndContextPackage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new V42DiagnosisRequest(
                        "task-competitor-001",
                        null,
                        "4.2",
                        new FrontDeskTaskOrder(
                                "农夫山泉",
                                "饮用水",
                                List.of("怡宝"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of(),
                                Map.of()
                        ),
                        new EngineTaskContextPackage(
                                "农夫山泉",
                                "饮用水",
                                List.of("百岁山"),
                                List.of("矿泉水"),
                                List.of(new CategoryAnchorSignal("矿泉水", "front_desk_task_order", 1.0d)),
                                false,
                                null,
                                List.of(),
                                List.of(),
                                List.of("百岁山"),
                                List.of(),
                                List.of(),
                                List.of(),
                                Map.of()
                        ),
                        List.of(),
                        Map.of()
                )
        );

        assertTrue(exception.getMessage().contains("competitors"));
    }

    private FrontDeskTaskOrder sampleTaskOrder() {
        return new FrontDeskTaskOrder(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                null,
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh",
                "doubao",
                Map.of(),
                Map.of()
        );
    }

    private EngineTaskContextPackage sampleContextPackage() {
        return new EngineTaskContextPackage(
                "农夫山泉",
                "饮用水",
                List.of("怡宝", "百岁山"),
                List.of("矿泉水", "天然水"),
                List.of(
                        new CategoryAnchorSignal("矿泉水", "front_desk_task_order", 1.0d),
                        new CategoryAnchorSignal("天然水", "front_desk_task_order", 1.0d)
                ),
                false,
                null,
                List.of(),
                List.of(),
                List.of("怡宝", "百岁山"),
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
                Map.of("categoryAnchorSource", "front_desk_task_order")
        );
    }
}

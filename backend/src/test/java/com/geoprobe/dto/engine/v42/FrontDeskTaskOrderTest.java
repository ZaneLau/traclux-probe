package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrontDeskTaskOrderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptLegacyDiagnosisGoalInputAndNormalizeToRunGoal() {
        FrontDeskTaskOrder taskOrder = objectMapper.convertValue(Map.of(
                "brandName", "探镜",
                "category", "运动相机",
                "competitors", List.of("影石"),
                "diagnosisGoal", "geo_brand_visibility_diagnosis",
                "locale", "zh-CN",
                "language", "zh-CN",
                "businessHints", Map.of(),
                "taskMetadata", Map.of()
        ), FrontDeskTaskOrder.class);

        assertEquals("geo_brand_visibility_diagnosis", taskOrder.runGoal());
        assertEquals("geo_brand_visibility_diagnosis", taskOrder.diagnosisGoal());
    }

    @Test
    void shouldSerializeCanonicalRunGoalWithoutLegacyDiagnosisGoal() {
        FrontDeskTaskOrder taskOrder = new FrontDeskTaskOrder(
                "探镜",
                "运动相机",
                List.of("影石"),
                "https://geo.example.com",
                "geo_brand_visibility_diagnosis",
                "zh-CN",
                "zh-CN",
                "doubao",
                Map.of(),
                Map.of()
        );

        Map<String, Object> snapshot = objectMapper.convertValue(taskOrder, Map.class);

        assertEquals("geo_brand_visibility_diagnosis", snapshot.get("runGoal"));
        assertFalse(snapshot.containsKey("diagnosisGoal"));
    }
}

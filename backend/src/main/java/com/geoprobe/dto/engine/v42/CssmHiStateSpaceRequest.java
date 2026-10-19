package com.geoprobe.dto.engine.v42;

import java.util.List;

public record CssmHiStateSpaceRequest(
        String brandName,
        String category,
        List<String> competitors,
        List<String> categoryAnchors,
        boolean fallbackAnchorMode,
        FrontDeskTaskOrder taskOrder,
        EngineTaskContextPackage contextPackage
) {

    public CssmHiStateSpaceRequest {
        brandName = normalizeRequired(brandName, "brandName 不能为空");
        category = normalizeRequired(category, "category 不能为空");
        competitors = competitors == null ? List.of() : List.copyOf(competitors);
        categoryAnchors = categoryAnchors == null ? List.of() : List.copyOf(categoryAnchors);
        if (taskOrder == null) {
            throw new IllegalArgumentException("taskOrder 不能为空");
        }
        if (contextPackage == null) {
            throw new IllegalArgumentException("contextPackage 不能为空");
        }
        validateLayerConsistency(brandName, category, taskOrder, contextPackage);
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static void validateLayerConsistency(String brandName,
                                                 String category,
                                                 FrontDeskTaskOrder taskOrder,
                                                 EngineTaskContextPackage contextPackage) {
        if (!brandName.equals(taskOrder.brandName())) {
            throw new IllegalArgumentException("request.brandName 与 taskOrder.brandName 必须一致");
        }
        if (!category.equals(taskOrder.category())) {
            throw new IllegalArgumentException("request.category 与 taskOrder.category 必须一致");
        }
        if (!brandName.equals(contextPackage.brandName())) {
            throw new IllegalArgumentException("request.brandName 与 contextPackage.brandName 必须一致");
        }
        if (!category.equals(contextPackage.category())) {
            throw new IllegalArgumentException("request.category 与 contextPackage.category 必须一致");
        }
    }
}

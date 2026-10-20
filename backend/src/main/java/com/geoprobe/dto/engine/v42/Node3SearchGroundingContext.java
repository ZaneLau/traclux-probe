package com.geoprobe.dto.engine.v42;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record Node3SearchGroundingContext(
        String category,
        List<String> categoryAnchors,
        String visibleBrandName
) {

    private static final Node3SearchGroundingContext EMPTY = new Node3SearchGroundingContext(null, List.of(), null);

    public Node3SearchGroundingContext {
        category = normalizeOptional(category);
        categoryAnchors = immutableDistinctList(categoryAnchors);
        visibleBrandName = normalizeOptional(visibleBrandName);
    }

    public static Node3SearchGroundingContext empty() {
        return EMPTY;
    }

    public boolean hasContext() {
        return category != null || !categoryAnchors.isEmpty() || visibleBrandName != null;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> immutableDistinctList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String item : source) {
            String normalized = normalizeOptional(Objects.requireNonNull(item, "categoryAnchors 不能包含 null"));
            if (normalized != null) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }
}

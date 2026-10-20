package com.geoprobe.dto.engine;

import java.util.List;

public record ParsedRunResult(
        boolean mentioned,
        boolean recommended,
        Integer position,
        List<String> allBrandsMentioned,
        List<String> associatedKeywords,
        String sentiment,
        List<String> matchedFragments,
        List<String> matchedAliases
) {
}

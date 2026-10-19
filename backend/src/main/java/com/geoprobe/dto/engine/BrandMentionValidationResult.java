package com.geoprobe.dto.engine;

import java.util.List;

public record BrandMentionValidationResult(
        boolean mentioned,
        List<String> matchedFragments,
        List<String> matchedAliases
) {
}

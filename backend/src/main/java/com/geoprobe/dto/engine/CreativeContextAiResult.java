package com.geoprobe.dto.engine;

import java.util.List;

public record CreativeContextAiResult(
        String category,
        List<String> competitors,
        List<String> negativeTerms,
        String officialWebsite
) {
}

package com.geoprobe.dto.engine;

import java.util.List;

public record CreativeContextResult(
        String brandName,
        String category,
        List<String> competitors,
        List<String> negativeTerms,
        String officialWebsite,
        String source
) {
}

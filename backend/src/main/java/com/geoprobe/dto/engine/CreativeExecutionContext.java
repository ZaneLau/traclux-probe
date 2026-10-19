package com.geoprobe.dto.engine;

import java.util.List;

public record CreativeExecutionContext(
        String brandName,
        String category,
        List<String> competitors,
        String brandWebsite,
        String userPrompt
) {
}

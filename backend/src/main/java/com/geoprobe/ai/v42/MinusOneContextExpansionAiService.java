package com.geoprobe.ai.v42;

public interface MinusOneContextExpansionAiService {

    String expand(
            String brandName,
            String category,
            String competitors,
            String categoryAnchors,
            String userPrompt,
            String brandWebsite,
            String evidenceSnippets,
            String benchmarkQueries
    );
}

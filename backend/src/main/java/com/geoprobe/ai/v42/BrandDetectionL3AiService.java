package com.geoprobe.ai.v42;

public interface BrandDetectionL3AiService {

    String detect(
            String brandName,
            String brandAliases,
            String competitors,
            String responseText
    );
}

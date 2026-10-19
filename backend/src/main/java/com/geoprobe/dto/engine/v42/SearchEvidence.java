package com.geoprobe.dto.engine.v42;

import org.springframework.util.StringUtils;

public record SearchEvidence(
        String sourceType,
        String url,
        String title,
        String snippet,
        String publishTime,
        String retrievedAt,
        String provider,
        String probeId,
        String query
) {

    public SearchEvidence {
        sourceType = normalizeOptional(sourceType);
        url = normalizeOptional(url);
        title = normalizeOptional(title);
        snippet = normalizeOptional(snippet);
        publishTime = normalizeOptional(publishTime);
        retrievedAt = normalizeOptional(retrievedAt);
        provider = normalizeOptional(provider);
        probeId = normalizeOptional(probeId);
        query = normalizeOptional(query);
    }

    public SearchEvidence(String sourceType,
                          String url,
                          String title,
                          String snippet,
                          String publishTime,
                          String retrievedAt,
                          String provider,
                          String probeId) {
        this(sourceType, url, title, snippet, publishTime, retrievedAt, provider, probeId, null);
    }

    private static String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

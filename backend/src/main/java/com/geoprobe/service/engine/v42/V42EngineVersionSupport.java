package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

public final class V42EngineVersionSupport {

    public static final String LEGACY_ENGINE_VERSION = "4.2";
    public static final String CANONICAL_ENGINE_VERSION = "4.3";
    public static final String DEFAULT_ENGINE_VERSION = CANONICAL_ENGINE_VERSION;

    private V42EngineVersionSupport() {
    }

    public static boolean isSupported(String engineVersion) {
        String normalized = normalize(engineVersion);
        return LEGACY_ENGINE_VERSION.equals(normalized) || CANONICAL_ENGINE_VERSION.equals(normalized);
    }

    public static String normalizeOrDefault(String engineVersion) {
        String normalized = normalize(engineVersion);
        return normalized == null ? DEFAULT_ENGINE_VERSION : normalized;
    }

    public static String supportedVersionsLabel() {
        return LEGACY_ENGINE_VERSION + " / " + CANONICAL_ENGINE_VERSION;
    }

    private static String normalize(String engineVersion) {
        if (!StringUtils.hasText(engineVersion)) {
            return null;
        }
        return engineVersion.trim();
    }
}

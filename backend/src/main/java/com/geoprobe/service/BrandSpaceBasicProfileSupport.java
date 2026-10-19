package com.geoprobe.service;

import com.geoprobe.entity.v1.BrandScope;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class BrandSpaceBasicProfileSupport {

    private static final String PROFILE_KEY = "brandSpaceProfile";
    private static final String COMPETITORS_KEY = "competitors";
    private static final String SUPPLEMENTAL_BRIEF_KEY = "supplementalBrief";

    private BrandSpaceBasicProfileSupport() {
    }

    static boolean hasStoredCompetitors(BrandScope scope) {
        return readProfileMap(scope).containsKey(COMPETITORS_KEY);
    }

    static List<String> readStoredCompetitors(BrandScope scope) {
        return readStringList(readProfileMap(scope).get(COMPETITORS_KEY));
    }

    static String readStoredSupplementalBrief(BrandScope scope) {
        return readOptionalText(readProfileMap(scope).get(SUPPLEMENTAL_BRIEF_KEY));
    }

    static Map<String, Object> writeStoredProfile(Map<String, Object> existingPolicy,
                                                  List<String> competitors,
                                                  String supplementalBrief) {
        LinkedHashMap<String, Object> policy = existingPolicy == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(existingPolicy);
        LinkedHashMap<String, Object> profile = new LinkedHashMap<>(readProfileMap(policy));
        profile.put(COMPETITORS_KEY, normalizeCompetitors(competitors));
        if (StringUtils.hasText(supplementalBrief)) {
            profile.put(SUPPLEMENTAL_BRIEF_KEY, supplementalBrief.trim());
        } else {
            profile.remove(SUPPLEMENTAL_BRIEF_KEY);
        }
        policy.put(PROFILE_KEY, profile);
        return policy;
    }

    static List<String> normalizeCompetitors(List<String> competitors) {
        if (competitors == null) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String item : competitors) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            deduplicated.add(item.trim());
        }
        return List.copyOf(deduplicated);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readProfileMap(BrandScope scope) {
        if (scope == null) {
            return Map.of();
        }
        return readProfileMap(scope.getDefaultModelPolicyJson());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readProfileMap(Map<String, Object> policy) {
        if (policy == null) {
            return Map.of();
        }
        Object raw = policy.get(PROFILE_KEY);
        if (raw instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> profile = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    profile.put(String.valueOf(key), value);
                }
            });
            return profile;
        }
        return Map.of();
    }

    private static List<String> readStringList(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (Object item : rawList) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                deduplicated.add(text);
            }
        }
        return List.copyOf(deduplicated);
    }

    private static String readOptionalText(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }
}

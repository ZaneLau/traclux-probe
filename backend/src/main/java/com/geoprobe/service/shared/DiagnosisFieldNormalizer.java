package com.geoprobe.service.shared;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class DiagnosisFieldNormalizer {

    private DiagnosisFieldNormalizer() {
    }

    public static String normalizeRequired(String value, String message, int maxLen) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
        return normalizeOptional(value, maxLen);
    }

    public static String normalizeOptional(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLen) {
            return normalized.substring(0, maxLen);
        }
        return normalized;
    }

    public static List<String> normalizeCompetitors(List<String> competitors,
                                                     String brandName,
                                                     int maxSize,
                                                     int maxItemLen) {
        if (competitors == null || competitors.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String competitor : competitors) {
            if (!StringUtils.hasText(competitor)) {
                continue;
            }
            String normalizedValue = normalizeOptional(competitor, maxItemLen);
            if (normalizedValue == null) {
                continue;
            }
            if (StringUtils.hasText(brandName) && normalizedValue.equalsIgnoreCase(brandName)) {
                continue;
            }
            normalized.add(normalizedValue);
            if (normalized.size() >= maxSize) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    public static List<String> normalizeCategoryAnchors(List<String> categoryAnchors,
                                                         int maxSize,
                                                         int maxItemLen) {
        return normalizeFreeTextList(categoryAnchors, maxSize, maxItemLen);
    }

    public static List<String> normalizeAliasList(List<String> aliases,
                                                  int maxSize,
                                                  int maxItemLen) {
        return normalizeFreeTextList(aliases, maxSize, maxItemLen);
    }

    public static Map<String, List<String>> normalizeCompetitorAliases(Map<String, List<String>> competitorAliases,
                                                                        int maxAliasSize,
                                                                        int maxItemLen) {
        if (competitorAliases == null || competitorAliases.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
        competitorAliases.forEach((key, value) -> {
            if (!StringUtils.hasText(key)) {
                return;
            }
            normalized.put(key.trim(), normalizeAliasList(value, maxAliasSize, maxItemLen));
        });
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    public static List<String> normalizeFreeTextList(List<?> values, int maxSize, int maxItemLen) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object rawValue : values) {
            if (!(rawValue instanceof String value) || !StringUtils.hasText(value)) {
                continue;
            }
            String normalizedValue = normalizeOptional(value, maxItemLen);
            if (normalizedValue == null) {
                continue;
            }
            normalized.add(normalizedValue);
            if (normalized.size() >= maxSize) {
                break;
            }
        }
        return List.copyOf(normalized);
    }
}

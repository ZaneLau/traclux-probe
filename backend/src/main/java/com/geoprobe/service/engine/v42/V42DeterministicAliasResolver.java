package com.geoprobe.service.engine.v42;

import com.geoprobe.service.shared.DiagnosisAliasConfigData;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class V42DeterministicAliasResolver {

    private static final Map<String, List<String>> SPECIAL_ALIASES = Map.of(
            "鸿蒙智行", List.of("AITO", "问界", "智界", "享界", "尊界"),
            "大疆", List.of("DJI", "大疆创新", "Osmo", "Osmo Action"),
            "DJI", List.of("大疆", "大疆创新", "Osmo", "Osmo Action"),
            "Insta360", List.of("影石")
    );

    private V42DeterministicAliasResolver() {
    }

    static DiagnosisAliasConfigData resolve(String brandName,
                                            List<String> competitors,
                                            DiagnosisAliasConfigData source) {
        DiagnosisAliasConfigData safeSource = source == null
                ? new DiagnosisAliasConfigData(List.of(), Map.of())
                : source;

        List<String> resolvedBrandAliases = resolveAliasesForName(
                brandName,
                safeSource.brandAliases()
        );

        LinkedHashMap<String, List<String>> resolvedCompetitorAliases = new LinkedHashMap<>();
        if (competitors != null) {
            for (String competitor : competitors) {
                if (!StringUtils.hasText(competitor)) {
                    continue;
                }
                Map<String, List<String>> configuredAliases = safeSource.competitorAliases() == null
                        ? Map.of()
                        : safeSource.competitorAliases();
                List<String> configured = configuredAliases.getOrDefault(competitor, List.of());
                resolvedCompetitorAliases.put(competitor, resolveAliasesForName(competitor, configured));
            }
        }

        if (safeSource.competitorAliases() != null) {
            for (Map.Entry<String, List<String>> entry : safeSource.competitorAliases().entrySet()) {
                if (resolvedCompetitorAliases.containsKey(entry.getKey())) {
                    continue;
                }
                resolvedCompetitorAliases.put(entry.getKey(), resolveAliasesForName(entry.getKey(), entry.getValue()));
            }
        }

        return new DiagnosisAliasConfigData(
                resolvedBrandAliases,
                Map.copyOf(resolvedCompetitorAliases)
        );
    }

    static List<String> resolveAliasesForName(String canonicalName, List<String> configuredAliases) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (configuredAliases != null) {
            for (String alias : configuredAliases) {
                addAlias(aliases, canonicalName, alias);
            }
        }
        for (String alias : buildFallbackAliases(canonicalName)) {
            addAlias(aliases, canonicalName, alias);
        }
        if (aliases.isEmpty() && StringUtils.hasText(canonicalName)) {
            aliases.add(canonicalName.trim());
        }
        return List.copyOf(aliases);
    }

    private static List<String> buildFallbackAliases(String canonicalName) {
        if (!StringUtils.hasText(canonicalName)) {
            return List.of();
        }
        String trimmed = canonicalName.trim();
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String alias : SPECIAL_ALIASES.getOrDefault(trimmed, List.of())) {
            addAlias(aliases, canonicalName, alias);
        }
        addAlias(aliases, canonicalName, stripSuffix(trimmed, "医院"));
        addAlias(aliases, canonicalName, stripSuffix(trimmed, "集团"));
        addAlias(aliases, canonicalName, stripSuffix(trimmed, "科技"));
        addAlias(aliases, canonicalName, stripSuffix(trimmed, "汽车"));
        addAlias(aliases, canonicalName, stripPrefix(trimmed, "上海市"));
        addAlias(aliases, canonicalName, stripPrefix(trimmed, "江苏省"));
        addAlias(aliases, canonicalName, trimmed.replace("大学附属", ""));
        addAlias(aliases, canonicalName, compact(trimmed));
        return List.copyOf(aliases);
    }

    private static void addAlias(LinkedHashSet<String> aliases, String canonicalName, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return;
        }
        String display = candidate.trim();
        if (display.isEmpty()) {
            return;
        }
        if (normalize(display).equals(normalize(canonicalName))) {
            return;
        }
        aliases.add(display);
    }

    private static String stripSuffix(String value, String suffix) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(suffix) || !value.endsWith(suffix)) {
            return null;
        }
        String stripped = value.substring(0, value.length() - suffix.length()).trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String stripPrefix(String value, String prefix) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(prefix) || !value.startsWith(prefix)) {
            return null;
        }
        String stripped = value.substring(prefix.length()).trim();
        return stripped.isEmpty() ? null : prefix.substring(0, prefix.length() - 1) + stripped;
    }

    private static String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String compacted = value.replaceAll("[\\s·•、，。！？：；“”‘’（）()【】《》-]+", "").trim();
        return compacted.isEmpty() ? null : compacted;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
    }
}

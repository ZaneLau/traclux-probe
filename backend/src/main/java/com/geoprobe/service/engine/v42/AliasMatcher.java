package com.geoprobe.service.engine.v42;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("v42AliasMatcher")
public class AliasMatcher {

    private static final String FLEX_SEPARATOR = "[\\s\\p{Punct}·•、，。！？：；“”‘’（）()【】《》_-]*";
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}·•、，。！？：；“”‘’（）()【】《》_-]+");
    private static final Pattern ASCII_WORD = Pattern.compile("^[\\p{ASCII}0-9A-Za-z]+$");

    Match findFirstExact(String text, String canonicalName) {
        return findAllMatches(text, List.of(canonicalName)).stream().findFirst().orElse(null);
    }

    Match findFirstAlias(String text, String canonicalName, List<String> aliases) {
        return findAllAliasMatches(text, canonicalName, aliases).stream().findFirst().orElse(null);
    }

    List<Match> findAllAliasMatches(String text, String canonicalName, List<String> aliases) {
        List<String> sanitized = new ArrayList<>();
        String normalizedCanonical = normalize(canonicalName);
        if (aliases != null) {
            for (String alias : aliases) {
                String normalizedAlias = normalize(alias);
                if (!StringUtils.hasText(normalizedAlias) || normalizedAlias.equals(normalizedCanonical)) {
                    continue;
                }
                sanitized.add(alias.trim());
            }
        }
        return findAllMatches(text, sanitized);
    }

    List<Match> findAllMatches(String text, List<String> candidates) {
        if (!StringUtils.hasText(text) || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, CompiledAlias> compiled = new LinkedHashMap<>();
        for (String candidate : candidates) {
            CompiledAlias alias = compile(candidate);
            if (alias != null) {
                compiled.putIfAbsent(alias.dedupKey(), alias);
            }
        }
        if (compiled.isEmpty()) {
            return List.of();
        }

        List<Match> matches = new ArrayList<>();
        for (CompiledAlias alias : compiled.values()) {
            Matcher matcher = alias.pattern().matcher(text);
            while (matcher.find()) {
                matches.add(new Match(alias.display(), matcher.start(), matcher.end(), matcher.group()));
            }
        }
        matches.sort(Comparator
                .comparingInt(Match::start)
                .thenComparing((Match match) -> -(match.end() - match.start()))
                .thenComparing(Match::matchedAlias));
        return dedupe(matches);
    }

    private List<Match> dedupe(List<Match> matches) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Match> deduped = new ArrayList<>();
        for (Match match : matches) {
            String key = match.start() + ":" + match.end() + ":" + normalize(match.matchedAlias());
            if (seen.add(key)) {
                deduped.add(match);
            }
        }
        return List.copyOf(deduped);
    }

    private CompiledAlias compile(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String display = candidate.trim();
        String normalized = normalize(display);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        List<String> tokens = splitToTokens(display);
        String body;
        if (tokens.isEmpty()) {
            body = Pattern.quote(display);
        } else {
            body = tokens.stream().map(Pattern::quote).reduce((left, right) -> left + FLEX_SEPARATOR + right).orElse(Pattern.quote(display));
        }

        String regex = requiresBoundary(tokens) ? "(?iu)(?<![A-Za-z0-9])" + body + "(?![A-Za-z0-9])" : "(?iu)" + body;
        return new CompiledAlias(display, normalized, Pattern.compile(regex));
    }

    private boolean requiresBoundary(List<String> tokens) {
        if (tokens.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            if (!ASCII_WORD.matcher(token).matches()) {
                return false;
            }
        }
        return true;
    }

    private List<String> splitToTokens(String value) {
        List<String> tokens = new ArrayList<>();
        for (String token : TOKEN_SPLITTER.split(value.trim())) {
            if (StringUtils.hasText(token)) {
                tokens.add(token.trim());
            }
        }
        return tokens;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record CompiledAlias(String display, String dedupKey, Pattern pattern) {
    }

    record Match(String matchedAlias, int start, int end, String fragment) {
    }
}

package com.geoprobe.dto.engine.v42;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RawResponse(
        String fullText,
        String thinkContent,
        List<String> citations,
        int promptTokenCount,
        int completionTokenCount,
        int totalTokenCount,
        long latencyMs,
        boolean earlyStoppedFlag,
        boolean searchRequested,
        boolean searchSatisfied,
        boolean searchFallbackFlag,
        List<SearchEvidence> searchEvidence,
        int searchCallCount,
        List<String> searchQueries,
        String modelId,
        String routingIntent,
        List<String> routingCandidates
) {

    public RawResponse {
        fullText = normalize(fullText);
        thinkContent = normalize(thinkContent);
        citations = immutableList(citations);
        if (promptTokenCount < 0) {
            throw new IllegalArgumentException("promptTokenCount 不能为负数");
        }
        if (completionTokenCount < 0) {
            throw new IllegalArgumentException("completionTokenCount 不能为负数");
        }
        if (totalTokenCount < 0) {
            throw new IllegalArgumentException("totalTokenCount 不能为负数");
        }
        if (latencyMs < 0L) {
            throw new IllegalArgumentException("latencyMs 不能为负数");
        }
        searchEvidence = immutableEvidenceList(searchEvidence);
        if (searchCallCount < 0) {
            throw new IllegalArgumentException("searchCallCount 不能为负数");
        }
        searchQueries = immutableList(searchQueries);
        modelId = normalize(modelId);
        routingIntent = normalize(routingIntent);
        routingCandidates = immutableList(routingCandidates);
    }

    public static RawResponse empty(long latencyMs) {
        return new RawResponse("", "", List.of(), 0, 0, 0, Math.max(0L, latencyMs), false);
    }

    public RawResponse(String fullText,
                       String thinkContent,
                       List<String> citations,
                       int promptTokenCount,
                       int completionTokenCount,
                       int totalTokenCount,
                       long latencyMs,
                       boolean earlyStoppedFlag) {
        this(
                fullText,
                thinkContent,
                citations,
                promptTokenCount,
                completionTokenCount,
                totalTokenCount,
                latencyMs,
                earlyStoppedFlag,
                false,
                false,
                false,
                List.of(),
                0,
                List.of()
        );
    }

    public RawResponse(String fullText,
                       String thinkContent,
                       List<String> citations,
                       int promptTokenCount,
                       int completionTokenCount,
                       int totalTokenCount,
                       long latencyMs,
                       boolean earlyStoppedFlag,
                       boolean searchRequested,
                       boolean searchSatisfied,
                       boolean searchFallbackFlag,
                       List<SearchEvidence> searchEvidence) {
        this(
                fullText,
                thinkContent,
                citations,
                promptTokenCount,
                completionTokenCount,
                totalTokenCount,
                latencyMs,
                earlyStoppedFlag,
                searchRequested,
                searchSatisfied,
                searchFallbackFlag,
                searchEvidence,
                0,
                List.of(),
                "",
                "",
                List.of()
        );
    }

    public RawResponse(String fullText,
                       String thinkContent,
                       List<String> citations,
                       int promptTokenCount,
                       int completionTokenCount,
                       int totalTokenCount,
                       long latencyMs,
                       boolean earlyStoppedFlag,
                       boolean searchRequested,
                       boolean searchSatisfied,
                       boolean searchFallbackFlag,
                       List<SearchEvidence> searchEvidence,
                       int searchCallCount,
                       List<String> searchQueries) {
        this(
                fullText,
                thinkContent,
                citations,
                promptTokenCount,
                completionTokenCount,
                totalTokenCount,
                latencyMs,
                earlyStoppedFlag,
                searchRequested,
                searchSatisfied,
                searchFallbackFlag,
                searchEvidence,
                searchCallCount,
                searchQueries,
                "",
                "",
                List.of()
        );
    }

    public int responseTokenCount() {
        if (completionTokenCount > 0) {
            return completionTokenCount;
        }
        return approximateResponseTokenCount(fullText);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> immutableList(List<String> source) {
        Objects.requireNonNull(source, "citations 不能为空");
        List<String> sanitized = new ArrayList<>(source.size());
        for (String item : source) {
            if (item == null) {
                throw new IllegalArgumentException("citations 不能包含 null");
            }
            String normalized = item.trim();
            if (!normalized.isEmpty()) {
                sanitized.add(normalized);
            }
        }
        return List.copyOf(sanitized);
    }

    private static List<SearchEvidence> immutableEvidenceList(List<SearchEvidence> source) {
        Objects.requireNonNull(source, "searchEvidence 不能为空");
        List<SearchEvidence> sanitized = new ArrayList<>(source.size());
        for (SearchEvidence item : source) {
            sanitized.add(Objects.requireNonNull(item, "searchEvidence 不能包含 null"));
        }
        return List.copyOf(sanitized);
    }

    private static int approximateResponseTokenCount(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean inAsciiToken = false;
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                inAsciiToken = false;
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                count++;
                inAsciiToken = false;
                continue;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                if (!inAsciiToken) {
                    count++;
                    inAsciiToken = true;
                }
                continue;
            }
            inAsciiToken = false;
        }
        return count;
    }
}

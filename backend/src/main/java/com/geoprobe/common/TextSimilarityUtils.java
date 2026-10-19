package com.geoprobe.common;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文本相似度工具（面向中文/英文混合场景）。
 */
public final class TextSimilarityUtils {

    private TextSimilarityUtils() {
    }

    public static double jaccardSimilarity(String left, String right) {
        Set<String> leftTokens = tokenizeForJaccard(left);
        Set<String> rightTokens = tokenizeForJaccard(right);
        if (leftTokens.isEmpty() && rightTokens.isEmpty()) {
            return 1.0d;
        }
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0d;
        }

        int intersection = 0;
        for (String token : leftTokens) {
            if (rightTokens.contains(token)) {
                intersection++;
            }
        }
        int union = leftTokens.size() + rightTokens.size() - intersection;
        return union == 0 ? 0.0d : (intersection * 1.0d / union);
    }

    private static Set<String> tokenizeForJaccard(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        StringBuilder latinBuffer = new StringBuilder();
        StringBuilder hanBuffer = new StringBuilder();

        for (int i = 0; i < lower.length(); ) {
            int codePoint = lower.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isHan(codePoint)) {
                flushLatinToken(latinBuffer, tokens);
                hanBuffer.appendCodePoint(codePoint);
                continue;
            }
            flushHanToken(hanBuffer, tokens);
            if (Character.isLetterOrDigit(codePoint)) {
                latinBuffer.appendCodePoint(codePoint);
            } else {
                flushLatinToken(latinBuffer, tokens);
            }
        }

        flushHanToken(hanBuffer, tokens);
        flushLatinToken(latinBuffer, tokens);
        return tokens;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private static void flushHanToken(StringBuilder buffer, Set<String> tokens) {
        if (buffer.isEmpty()) {
            return;
        }
        String text = buffer.toString();
        if (text.length() == 1) {
            tokens.add(text);
            buffer.setLength(0);
            return;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
        buffer.setLength(0);
    }

    private static void flushLatinToken(StringBuilder buffer, Set<String> tokens) {
        if (buffer.isEmpty()) {
            return;
        }
        String token = buffer.toString().trim();
        if (StringUtils.hasText(token)) {
            tokens.add(token);
        }
        buffer.setLength(0);
    }
}

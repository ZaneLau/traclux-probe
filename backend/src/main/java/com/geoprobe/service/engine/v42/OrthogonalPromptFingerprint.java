package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

final class OrthogonalPromptFingerprint {

    private static final List<String> SUPPORT_CLAUSES = List.of(
            "少踩坑点",
            "更稳妥点",
            "别容易翻车",
            "别人一般都怎么挑",
            "大多数人会先看什么",
            "也想看懂差别",
            "也想搞清楚区别",
            "也不想太大众",
            "想有点自己的偏好",
            "尽量省点事",
            "别太费脑子",
            "不想踩坑",
            "大家一般怎么选"
    );

    private OrthogonalPromptFingerprint() {
    }

    static String canonical(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return "";
        }
        return prompt.trim()
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+", "")
                .toLowerCase();
    }

    static String structural(String prompt) {
        return structuralFromCanonical(canonical(prompt));
    }

    static boolean hasStructuralDuplicate(String prompt, Set<String> usedCanonicalPrompts) {
        if (usedCanonicalPrompts == null || usedCanonicalPrompts.isEmpty()) {
            return false;
        }
        String target = structural(prompt);
        if (!StringUtils.hasText(target)) {
            return false;
        }
        return usedCanonicalPrompts.stream()
                .map(OrthogonalPromptFingerprint::structuralFromCanonical)
                .anyMatch(target::equals);
    }

    static String structuralFromCanonical(String canonicalPrompt) {
        if (!StringUtils.hasText(canonicalPrompt)) {
            return canonicalPrompt == null ? "" : canonicalPrompt;
        }
        String collapsed = canonicalPrompt;
        for (String clause : SUPPORT_CLAUSES) {
            collapsed = collapsed.replace(canonical(clause), "");
        }
        return collapsed;
    }
}

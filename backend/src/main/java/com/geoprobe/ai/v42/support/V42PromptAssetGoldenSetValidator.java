package com.geoprobe.ai.v42.support;

import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;

public class V42PromptAssetGoldenSetValidator {

    private final V42PromptAssetLoader promptAssetLoader;
    private final V42PromptExecutionSupport promptExecutionSupport;

    public V42PromptAssetGoldenSetValidator(V42PromptAssetLoader promptAssetLoader,
                                            V42PromptExecutionSupport promptExecutionSupport) {
        this.promptAssetLoader = promptAssetLoader;
        this.promptExecutionSupport = promptExecutionSupport;
    }

    public void validate(List<V42PromptGoldenSetCase> cases) {
        if (cases == null || cases.isEmpty()) {
            throw new IllegalStateException("prompt golden set 不能为空");
        }
        EnumSet<V42PromptAssetKey> coveredKeys = EnumSet.noneOf(V42PromptAssetKey.class);
        for (V42PromptGoldenSetCase goldenCase : cases) {
            validateCase(goldenCase);
            coveredKeys.add(goldenCase.assetKey());
        }
        EnumSet<V42PromptAssetKey> missingKeys = EnumSet.allOf(V42PromptAssetKey.class);
        missingKeys.removeAll(coveredKeys);
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("prompt golden set 缺少资产覆盖: " + missingKeys);
        }
    }

    private void validateCase(V42PromptGoldenSetCase goldenCase) {
        if (goldenCase == null || goldenCase.assetKey() == null) {
            throw new IllegalStateException("prompt golden set case 非法");
        }
        V42PromptAssetDocument document = promptAssetLoader.load(goldenCase.assetKey());
        validateGovernance(goldenCase, document);
        V42PromptRenderPlan plan = promptExecutionSupport.prepare(goldenCase.assetKey(), goldenCase.variables());
        assertContains(plan.systemMessage(), goldenCase.expectedSystemContains(), goldenCase.caseId(), "system");
        assertContains(plan.userMessage(), goldenCase.expectedUserContains(), goldenCase.caseId(), "user");
        if (goldenCase.minFewShotExamples() != null
                && plan.fewShotExamples().size() < goldenCase.minFewShotExamples()) {
            throw new IllegalStateException("prompt golden set few-shot 不足: " + goldenCase.caseId());
        }
    }

    private void validateGovernance(V42PromptGoldenSetCase goldenCase, V42PromptAssetDocument document) {
        if (!StringUtils.hasText(document.version())) {
            throw new IllegalStateException("prompt 资产缺少 version: " + goldenCase.assetKey());
        }
        if (document.constraints().isEmpty()) {
            throw new IllegalStateException("prompt 资产缺少约束清单: " + goldenCase.assetKey());
        }
        if (!StringUtils.hasText(document.outputFormat())) {
            throw new IllegalStateException("prompt 资产缺少输出格式规范: " + goldenCase.assetKey());
        }
        if (document.changelog().isEmpty()) {
            throw new IllegalStateException("prompt 资产缺少 changelog: " + goldenCase.assetKey());
        }
        boolean versionTracked = document.changelog().stream()
                .anyMatch(entry -> entry != null && document.version().equals(entry.version()));
        if (!versionTracked) {
            throw new IllegalStateException("prompt 资产 changelog 未登记当前版本: " + goldenCase.assetKey());
        }
    }

    private void assertContains(String content,
                                List<String> expectedFragments,
                                String caseId,
                                String scope) {
        for (String fragment : expectedFragments) {
            if (StringUtils.hasText(fragment) && (content == null || !content.contains(fragment))) {
                throw new IllegalStateException("prompt golden set 断言失败: case=" + caseId + ", scope=" + scope + ", fragment=" + fragment);
            }
        }
    }
}

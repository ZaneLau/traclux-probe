package com.geoprobe.ai.v42.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class V42PromptExecutionSupport {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9]+)\\s*}}");

    private final V42PromptAssetLoader promptAssetLoader;

    public V42PromptExecutionSupport(V42PromptAssetLoader promptAssetLoader) {
        this.promptAssetLoader = promptAssetLoader;
    }

    public V42PromptAssetDescriptor describe(V42PromptAssetKey assetKey) {
        return promptAssetLoader.describe(assetKey);
    }

    public V42PromptAssetDescriptor describeVersion(V42PromptAssetKey assetKey, String version) {
        return promptAssetLoader.describeVersion(assetKey, version);
    }

    public V42PromptRenderPlan prepare(V42PromptAssetKey assetKey, Map<String, String> variables) {
        V42PromptAssetDocument document = promptAssetLoader.load(assetKey);
        return prepare(promptAssetLoader.describe(assetKey), document, variables);
    }

    public V42PromptRenderPlan prepareVersion(V42PromptAssetKey assetKey,
                                              String version,
                                              Map<String, String> variables) {
        V42PromptAssetDocument document = promptAssetLoader.loadVersion(assetKey, version);
        return prepare(promptAssetLoader.describeVersion(assetKey, version), document, variables);
    }

    private V42PromptRenderPlan prepare(V42PromptAssetDescriptor descriptor,
                                        V42PromptAssetDocument document,
                                        Map<String, String> variables) {
        String renderedSystemMessage = render(composeSystemMessage(document), variables);
        String renderedUserMessage = render(document.userMessage(), variables);
        List<V42PromptAssetExample> renderedExamples = document.fewShotExamples().stream()
                .map(example -> new V42PromptAssetExample(
                        render(example.userMessage(), variables),
                        render(example.assistantMessage(), variables)
                ))
                .toList();
        return new V42PromptRenderPlan(
                descriptor,
                renderedSystemMessage,
                renderedUserMessage,
                renderedExamples,
                document.constraints(),
                document.outputFormat()
        );
    }

    public String execute(ChatLanguageModel model, V42PromptAssetKey assetKey, Map<String, String> variables) {
        V42PromptRenderPlan plan = prepare(assetKey, variables);
        return execute(model, plan);
    }

    public String executeVersion(ChatLanguageModel model,
                                 V42PromptAssetKey assetKey,
                                 String version,
                                 Map<String, String> variables) {
        V42PromptRenderPlan plan = prepareVersion(assetKey, version, variables);
        return execute(model, plan);
    }

    private String execute(ChatLanguageModel model, V42PromptRenderPlan plan) {
        Response<AiMessage> response = model.generate(toMessages(plan));
        if (response == null || response.content() == null || !StringUtils.hasText(response.content().text())) {
            return "";
        }
        return response.content().text().trim();
    }

    private List<ChatMessage> toMessages(V42PromptRenderPlan plan) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(plan.systemMessage()));
        for (V42PromptAssetExample example : plan.fewShotExamples()) {
            messages.add(UserMessage.from(example.userMessage()));
            messages.add(AiMessage.from(example.assistantMessage()));
        }
        messages.add(UserMessage.from(plan.userMessage()));
        return List.copyOf(messages);
    }

    private String composeSystemMessage(V42PromptAssetDocument document) {
        StringBuilder builder = new StringBuilder(document.systemMessage().trim());
        if (!document.constraints().isEmpty()) {
            builder.append("\n\n约束清单：");
            for (String constraint : document.constraints()) {
                if (StringUtils.hasText(constraint)) {
                    builder.append("\n- ").append(constraint.trim());
                }
            }
        }
        if (StringUtils.hasText(document.outputFormat())) {
            builder.append("\n\n输出格式规范：\n").append(document.outputFormat().trim());
        }
        return builder.toString();
    }

    private String render(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!variables.containsKey(key)) {
                throw new IllegalStateException("V4.2 prompt 模板缺少变量: " + key);
            }
            String replacement = variables.get(key);
            if (replacement == null) {
                throw new IllegalStateException("V4.2 prompt 模板变量为空: " + key);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        String rendered = buffer.toString();
        Matcher unresolvedMatcher = PLACEHOLDER_PATTERN.matcher(rendered);
        if (unresolvedMatcher.find()) {
            throw new IllegalStateException("V4.2 prompt 模板仍有未替换占位符: " + unresolvedMatcher.group());
        }
        return rendered;
    }
}

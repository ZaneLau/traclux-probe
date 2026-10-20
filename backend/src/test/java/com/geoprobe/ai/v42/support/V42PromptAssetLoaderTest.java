package com.geoprobe.ai.v42.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static java.util.Map.entry;

class V42PromptAssetLoaderTest {

    @Test
    void shouldLoadAllClasspathPromptAssets() {
        V42PromptAssetLoader loader = new V42PromptAssetLoader(
                new V42SecretAssetSettings("classpath:v42-test-prompt-assets", true)
        );

        assertDoesNotThrow(loader::verifyAll);
        V42PromptAssetDocument document = loader.load(V42PromptAssetKey.PROBE_RENDERING);
        assertEquals("test-2026-04-09", document.version());
        assertTrue(document.systemMessage().contains("测试版探针渲染器"));
        assertEquals(1, document.fewShotExamples().size());
        assertEquals(3, document.constraints().size());
    }

    @Test
    void shouldFailFastWhenPromptVariableMissing() {
        V42PromptAssetLoader loader = new V42PromptAssetLoader(
                new V42SecretAssetSettings("classpath:v42-test-prompt-assets", true)
        );
        V42PromptExecutionSupport support = new V42PromptExecutionSupport(loader);
        ChatLanguageModel model = mock(ChatLanguageModel.class);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> support.execute(model, V42PromptAssetKey.CSSM_HI_STATE_SPACE, java.util.Map.of())
        );

        assertTrue(ex.getMessage().contains("缺少变量"));
    }

    @Test
    void shouldRenderPromptFromClasspathAssetsViaExecutionSupport() {
        V42PromptAssetLoader loader = new V42PromptAssetLoader(
                new V42SecretAssetSettings("classpath:v42-test-prompt-assets", true)
        );
        V42PromptExecutionSupport support = new V42PromptExecutionSupport(loader);
        ChatLanguageModel model = mock(ChatLanguageModel.class);
        doReturn(Response.from(AiMessage.from("ok")))
                .when(model)
                .generate(any(List.class));

        String result = support.execute(
                model,
                V42PromptAssetKey.PROBE_RENDERING,
                java.util.Map.ofEntries(
                        entry("brandName", "洛斐"),
                        entry("category", "机械键盘"),
                        entry("competitors", "Keychron,Akko"),
                        entry("categoryAnchors", "机械键盘,客制化"),
                        entry("brandInjectionPolicy", "BRAND_HIDDEN"),
                        entry("simulationProfile", "理性比较"),
                        entry("mustSemantics", "必须体现选择与比较"),
                        entry("softHints", "避免模板味"),
                        entry("behaviorRules", "只输出一句问题"),
                        entry("stateSpaceJson", "{\"D1\":\"verification\"}"),
                        entry("surfaceIntent", "comparison"),
                        entry("conversationMode", "search_box"),
                        entry("exposurePolicy", "BRAND_HIDDEN"),
                        entry("bucketType", "ORTHOGONAL")
                )
        );

        assertEquals("ok", result);
        ArgumentCaptor<List<ChatMessage>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(model).generate(messageCaptor.capture());
        List<ChatMessage> messages = messageCaptor.getValue();
        assertEquals(4, messages.size());
        assertTrue(messages.getFirst().toString().contains("测试版探针渲染器"));
        assertTrue(messages.getFirst().toString().contains("约束清单"));
        assertTrue(messages.get(1).toString().contains("示例品牌"));
        assertTrue(messages.get(3).toString().contains("品牌：洛斐"));
        assertTrue(messages.get(3).toString().contains("品类：机械键盘"));
    }

    @Test
    void shouldLookupHistoricalVersionFromVersionedDirectory() {
        V42PromptAssetLoader loader = new V42PromptAssetLoader(
                new V42SecretAssetSettings("classpath:v42-test-prompt-assets-versioned", true)
        );
        V42PromptExecutionSupport support = new V42PromptExecutionSupport(loader);

        V42PromptAssetDocument current = loader.load(V42PromptAssetKey.PROBE_RENDERING);
        V42PromptAssetDocument historical = loader.loadVersion(V42PromptAssetKey.PROBE_RENDERING, "node2-v2026-05-01");
        V42PromptAssetDescriptor historicalDescriptor = loader.describeVersion(V42PromptAssetKey.PROBE_RENDERING, "node2-v2026-05-01");
        V42PromptRenderPlan historicalPlan = support.prepareVersion(
                V42PromptAssetKey.PROBE_RENDERING,
                "node2-v2026-05-01",
                java.util.Map.ofEntries(
                        entry("brandName", "洛斐"),
                        entry("category", "机械键盘"),
                        entry("competitors", "Keychron,Akko"),
                        entry("categoryAnchors", "机械键盘,客制化"),
                        entry("brandInjectionPolicy", "BRAND_HIDDEN"),
                        entry("simulationProfile", "理性比较"),
                        entry("mustSemantics", "体现选择与比较"),
                        entry("softHints", "避免模板味"),
                        entry("behaviorRules", "只输出一句问题")
                )
        );

        assertEquals("node2-v2026-05-10", current.version());
        assertEquals("node2-v2026-05-01", historical.version());
        assertEquals("node2-v2026-05-01", historicalDescriptor.version());
        assertEquals("node2-v2026-05-01", historicalPlan.descriptor().version());
        assertTrue(loader.describe(V42PromptAssetKey.PROBE_RENDERING).sourceLocation().contains("current.json"));
    }
}

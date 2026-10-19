package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.ai.v42.support.V42PromptAssetKey;
import com.geoprobe.ai.v42.support.V42PromptAssetLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42Node0AiConfig.class,
        V42PromptAssetSpringSmokeTest.TestConfig.class
})
@TestPropertySource(properties = {
        "app.security.secret-assets.prompt-asset-dir=classpath:v42-test-prompt-assets",
        "app.security.secret-assets.prompt-asset-fail-fast=true",
        "app.security.secret-assets.prompt-golden-set-file=classpath:v42-prompt-golden-set/prompt_asset_cases.json"
})
class V42PromptAssetSpringSmokeTest {

    @Autowired
    private CssmHiStateSpaceAiService cssmHiStateSpaceAiService;

    @Autowired
    @Qualifier("creativeModel")
    private ChatLanguageModel creativeModel;

    @Autowired
    private V42PromptAssetLoader promptAssetLoader;

    @Test
    void shouldLoadPromptAssetBackedNode0ServiceFromSpringContext() {
        doReturn(Response.from(AiMessage.from("{\"ok\":true}")))
                .when(creativeModel)
                .generate(any(List.class));

        String result = cssmHiStateSpaceAiService.translateStateSpace(
                "机械键盘",
                "verification",
                "optimize",
                "performance",
                "focused",
                "urgent",
                "search_box"
        );

        assertEquals("{\"ok\":true}", result);
        assertEquals("test-2026-04-09", promptAssetLoader.load(V42PromptAssetKey.CSSM_HI_STATE_SPACE).version());
        ArgumentCaptor<List<ChatMessage>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(creativeModel).generate(messageCaptor.capture());
        List<ChatMessage> messages = messageCaptor.getValue();
        assertTrue(messages.getFirst().toString() != null && !messages.getFirst().toString().isBlank());
        assertTrue(messages.getLast().toString().contains("品类：机械键盘"));
        assertTrue(messages.getLast().toString().contains("D1：verification"));
    }

    @Configuration
    static class TestConfig {

        @Bean
        @Qualifier("creativeModel")
        ChatLanguageModel creativeModel() {
            return Mockito.mock(ChatLanguageModel.class);
        }
    }
}

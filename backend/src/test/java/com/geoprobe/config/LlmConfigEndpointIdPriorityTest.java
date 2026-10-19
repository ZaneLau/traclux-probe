package com.geoprobe.config;

import com.geoprobe.common.BudgetGuardedChatLanguageModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "llm.deepseek.endpoint-id=ep-test-deepseek",
        "llm.deepseek.model=deepseek-v3.2"
})
@ActiveProfiles("test")
class LlmConfigEndpointIdPriorityTest {

    @Autowired
    @Qualifier("creativeModel")
    private ChatLanguageModel creativeModel;

    @Test
    void shouldPreferDeepseekEndpointIdForCreativeModel() {
        ChatLanguageModel delegate = creativeModel instanceof BudgetGuardedChatLanguageModel guarded
                ? guarded.delegate()
                : creativeModel;
        OpenAiChatModel model = assertInstanceOf(OpenAiChatModel.class, delegate);
        assertEquals("ep-test-deepseek", model.modelName());
    }
}

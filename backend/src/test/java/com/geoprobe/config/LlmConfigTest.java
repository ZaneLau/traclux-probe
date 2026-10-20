package com.geoprobe.config;

import com.geoprobe.common.BudgetGuardedChatLanguageModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class LlmConfigTest {

    @Autowired
    @Qualifier("samplingModel")
    private ChatLanguageModel samplingModel;

    @Autowired
    @Qualifier("creativeModel")
    private ChatLanguageModel creativeModel;

    @Autowired
    @Qualifier("extractionModel")
    private ChatLanguageModel extractionModel;

    @Test
    void shouldCreateAllLlmBeans() {
        assertNotNull(samplingModel);
        assertNotNull(creativeModel);
        assertNotNull(extractionModel);

        ChatLanguageModel creativeDelegate = creativeModel instanceof BudgetGuardedChatLanguageModel guarded
                ? guarded.delegate()
                : creativeModel;
        Object listeners = ReflectionTestUtils.getField(creativeDelegate, "listeners");
        assertTrue(listeners instanceof List<?> listenerList && !listenerList.isEmpty());
    }
}

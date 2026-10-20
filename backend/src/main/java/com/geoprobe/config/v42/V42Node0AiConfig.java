package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.ai.v42.impl.PromptAssetBackedCssmHiStateSpaceAiService;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(V42PromptAssetConfig.class)
public class V42Node0AiConfig {

    @Bean
    public CssmHiStateSpaceAiService v42CssmHiStateSpaceAiService(@Qualifier("creativeModel") ChatLanguageModel model,
                                                                  V42PromptExecutionSupport promptExecutionSupport) {
        return new PromptAssetBackedCssmHiStateSpaceAiService(model, promptExecutionSupport);
    }
}

package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.ai.v42.impl.PromptAssetBackedBrandDetectionL3AiService;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(V42PromptAssetConfig.class)
public class V42Node4AiConfig {

    @Bean
    public BrandDetectionL3AiService v42BrandDetectionL3AiService(@Qualifier("creativeModel") ChatLanguageModel model,
                                                                  V42PromptExecutionSupport promptExecutionSupport) {
        return new PromptAssetBackedBrandDetectionL3AiService(model, promptExecutionSupport);
    }
}

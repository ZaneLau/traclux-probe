package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.DefenseCompletenessAiService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class V42Node5CAiConfig {

    @Bean
    public DefenseCompletenessAiService v42DefenseCompletenessAiService(
            @Qualifier("creativeModel") ChatLanguageModel model
    ) {
        return AiServices.create(DefenseCompletenessAiService.class, model);
    }
}

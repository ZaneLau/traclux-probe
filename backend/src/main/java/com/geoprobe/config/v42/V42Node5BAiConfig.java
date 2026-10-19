package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.CommercialInsightAiService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class V42Node5BAiConfig {

    @Bean
    public CommercialInsightAiService v42CommercialInsightAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(CommercialInsightAiService.class, model);
    }
}

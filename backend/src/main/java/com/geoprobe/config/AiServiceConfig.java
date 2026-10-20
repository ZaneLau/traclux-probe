package com.geoprobe.config;

import com.geoprobe.ai.BrandAliasAiService;
import com.geoprobe.ai.BrandMentionValidationAiService;
import com.geoprobe.ai.BrandVoiceTypeAiService;
import com.geoprobe.ai.CreativeContextAiService;
import com.geoprobe.ai.CssmHiStateSpaceAiService;
import com.geoprobe.ai.IntentClusterAiService;
import com.geoprobe.ai.ParsingAiService;
import com.geoprobe.ai.ProbeRenderingAiService;
import com.geoprobe.ai.PromptMatrixAiService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServiceConfig {

    @Bean
    public CreativeContextAiService creativeContextAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(CreativeContextAiService.class, model);
    }

    @Bean
    public CssmHiStateSpaceAiService cssmHiStateSpaceAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(CssmHiStateSpaceAiService.class, model);
    }

    @Bean
    public IntentClusterAiService intentClusterAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(IntentClusterAiService.class, model);
    }

    @Bean
    public PromptMatrixAiService promptMatrixAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(PromptMatrixAiService.class, model);
    }

    @Bean
    public ProbeRenderingAiService probeRenderingAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(ProbeRenderingAiService.class, model);
    }

    @Bean
    public BrandVoiceTypeAiService brandVoiceTypeAiService(@Qualifier("creativeModel") ChatLanguageModel model) {
        return AiServices.create(BrandVoiceTypeAiService.class, model);
    }

    @Bean
    public ParsingAiService parsingAiService(@Qualifier("extractionModel") ChatLanguageModel model) {
        return AiServices.create(ParsingAiService.class, model);
    }

    @Bean
    public BrandAliasAiService brandAliasAiService(@Qualifier("extractionModel") ChatLanguageModel model) {
        return AiServices.create(BrandAliasAiService.class, model);
    }

    @Bean
    public BrandMentionValidationAiService brandMentionValidationAiService(@Qualifier("extractionModel") ChatLanguageModel model) {
        return AiServices.create(BrandMentionValidationAiService.class, model);
    }
}

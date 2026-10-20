package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.ProbeRenderingAiService;
import com.geoprobe.ai.v42.impl.PromptAssetBackedProbeRenderingAiService;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(V42PromptAssetConfig.class)
public class V42Node2AiConfig {

    @Bean(name = "v42ProbeRenderingRenderAiService")
    public ProbeRenderingAiService v42ProbeRenderingRenderAiService(@Qualifier("probeRenderingModel") ChatLanguageModel model,
                                                                    V42PromptExecutionSupport promptExecutionSupport) {
        return new PromptAssetBackedProbeRenderingAiService(model, promptExecutionSupport);
    }
}

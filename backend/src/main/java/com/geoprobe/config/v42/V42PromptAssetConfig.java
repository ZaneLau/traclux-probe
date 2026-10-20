package com.geoprobe.config.v42;

import com.geoprobe.ai.v42.support.V42PromptAssetLoader;
import com.geoprobe.ai.v42.support.V42PromptAssetGoldenSetLoader;
import com.geoprobe.ai.v42.support.V42PromptAssetGoldenSetValidator;
import com.geoprobe.ai.v42.support.V42PromptExecutionSupport;
import com.geoprobe.ai.v42.support.V42SecretAssetSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class V42PromptAssetConfig {

    @Bean
    public V42SecretAssetSettings v42SecretAssetSettings(
            @Value("${app.security.secret-assets.prompt-asset-dir:file:../.private/prompt-assets}") String promptAssetDir,
            @Value("${app.security.secret-assets.prompt-asset-fail-fast:true}") boolean promptAssetFailFast
    ) {
        return new V42SecretAssetSettings(promptAssetDir, promptAssetFailFast);
    }

    @Bean
    public V42PromptAssetLoader v42PromptAssetLoader(V42SecretAssetSettings settings) {
        return new V42PromptAssetLoader(settings);
    }

    @Bean
    public V42PromptExecutionSupport v42PromptExecutionSupport(V42PromptAssetLoader promptAssetLoader) {
        return new V42PromptExecutionSupport(promptAssetLoader);
    }

    @Bean
    public V42PromptAssetGoldenSetLoader v42PromptAssetGoldenSetLoader() {
        return new V42PromptAssetGoldenSetLoader();
    }

    @Bean
    public V42PromptAssetGoldenSetValidator v42PromptAssetGoldenSetValidator(V42PromptAssetLoader promptAssetLoader,
                                                                             V42PromptExecutionSupport promptExecutionSupport) {
        return new V42PromptAssetGoldenSetValidator(promptAssetLoader, promptExecutionSupport);
    }

    @Bean
    public InitializingBean v42PromptAssetVerifier(V42PromptAssetLoader promptAssetLoader,
                                                   V42SecretAssetSettings settings,
                                                   V42PromptAssetGoldenSetLoader goldenSetLoader,
                                                   V42PromptAssetGoldenSetValidator goldenSetValidator,
                                                   @Value("${app.security.secret-assets.prompt-golden-set-file:}") String promptGoldenSetFile) {
        return () -> {
            if (settings.promptAssetFailFast()) {
                promptAssetLoader.verifyAll();
                if (org.springframework.util.StringUtils.hasText(promptGoldenSetFile)) {
                    goldenSetValidator.validate(goldenSetLoader.load(promptGoldenSetFile));
                }
            }
        };
    }
}

package com.geoprobe.service.shared;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CommunityModelConfigurationTest {

    @Test
    void communityConfigurationUsesInstanceModelAndBlocksInheritedSearchRequests() throws IOException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            var sources = context.getEnvironment().getPropertySources();
            sources.addFirst(new MapPropertySource("instance", Map.of(
                    "MODEL_NAME", "local-generation-fixture",
                    "SAMPLING_MODEL_NAME", "local-sampling-fixture")));
            var loader = new YamlPropertySourceLoader();
            for (String name : new String[]{"application-community.yml", "application.yml"}) {
                for (var source : loader.load(name, new ClassPathResource(name))) {
                    sources.addLast(source);
                }
            }
            context.register(V42Node3TargetEnginePolicyResolver.class, V42RealtimeSearchPolicyResolver.class);
            context.refresh();

            var model = context.getBean(V42Node3TargetEnginePolicyResolver.class)
                    .resolveForFrontDeskPlanning(Map.of("samplingEngine", Map.of("provider", "doubao")));
            assertEquals("local-sampling-fixture", model.modelId());
            assertEquals("local-generation-fixture", context.getEnvironment().getProperty("llm.deepseek.model"));
            assertEquals("local-sampling-fixture", context.getEnvironment().getProperty("llm.volcengine.model"));

            var search = context.getBean(V42RealtimeSearchPolicyResolver.class);
            assertFalse(search.defaultPolicy().enabled());
            assertFalse(search.resolveFromTaskOrderHints(Map.of(
                    "samplingRealtimeSearch", Map.of("enabled", true, "provider", "doubao"))).enabled());
            assertFalse(search.resolveFromTaskOrderHints(Map.of("samplingRealtimeSearch.enabled", true)).enabled());
        }
    }
}

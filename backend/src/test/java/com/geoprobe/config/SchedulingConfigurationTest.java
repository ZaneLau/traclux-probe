package com.geoprobe.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void shouldKeepSchedulingDisabledByDefault() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void shouldEnableSchedulingOnlyWhenExplicitlyActivated() {
        contextRunner
                .withPropertyValues("app.scheduling.enabled=true")
                .run(context -> assertThat(context)
                        .hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
}

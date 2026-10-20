package com.geoprobe.config.v42;

import com.geoprobe.service.engine.v42.V42KpiAggregationService;
import com.geoprobe.service.engine.v42.V42StabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig
@ContextConfiguration(classes = {
        V42KpiAggregationService.class,
        V42StabilityService.class
})
class V42Node5AContextSmokeTest {

    @Autowired
    private V42KpiAggregationService kpiAggregationService;

    @Autowired
    private V42StabilityService stabilityService;

    @Test
    void shouldCreateNode5ABeans() {
        assertNotNull(kpiAggregationService);
        assertNotNull(stabilityService);
    }
}

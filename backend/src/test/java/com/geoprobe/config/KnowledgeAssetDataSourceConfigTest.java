package com.geoprobe.config;

import com.geoprobe.config.properties.KnowledgeAssetProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "app.knowledge-asset.enabled=false",
        "app.knowledge-asset.flyway.enabled=false"
})
@ActiveProfiles("test")
class KnowledgeAssetDataSourceConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private KnowledgeAssetProperties knowledgeAssetProperties;

    @Test
    void shouldKeepKnowledgeAssetDataSourceDisabledByDefault() {
        assertFalse(knowledgeAssetProperties.isEnabled());
        assertFalse(applicationContext.containsBean("knowledgeAssetDataSource"));
        assertFalse(applicationContext.containsBean("knowledgeAssetJdbcTemplate"));
        assertFalse(applicationContext.containsBean("knowledgeAssetFlyway"));
        assertFalse(applicationContext.containsBean("knowledgeAssetCompetitorRepository"));
        assertFalse(applicationContext.containsBean("knowledgeAssetRunRepository"));
        assertFalse(applicationContext.containsBean("knowledgeAssetIngestionService"));
    }
}

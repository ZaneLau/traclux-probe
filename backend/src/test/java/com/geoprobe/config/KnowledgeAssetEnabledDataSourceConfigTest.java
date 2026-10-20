package com.geoprobe.config;

import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "app.knowledge-asset.enabled=true",
        "app.knowledge-asset.flyway.enabled=false",
        "app.knowledge-asset.datasource.url=jdbc:h2:mem:knowledge_asset_config;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "app.knowledge-asset.datasource.driver-class-name=org.h2.Driver",
        "app.knowledge-asset.datasource.username=sa",
        "app.knowledge-asset.datasource.password=",
        "app.knowledge-asset.datasource.schema=",
        "app.security.enforce-jwt-secret=false",
        "app.security.secret-assets.prompt-asset-fail-fast=false"
})
@ActiveProfiles("test")
class KnowledgeAssetEnabledDataSourceConfigTest {

    @Autowired
    private @Qualifier("dataSource")
    DataSource businessDataSource;

    @Autowired
    private @Qualifier("knowledgeAssetDataSource")
    DataSource knowledgeAssetDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private KnowledgeAssetRunRepository knowledgeAssetRunRepository;

    @Autowired
    @Qualifier("knowledgeAssetJdbcTemplate")
    private JdbcTemplate knowledgeAssetJdbcTemplate;

    @Test
    void shouldKeepBusinessDataSourcePrimaryWhenKnowledgeAssetDataSourceIsEnabled() {
        assertNotSame(businessDataSource, knowledgeAssetDataSource);
        assertSame(businessDataSource, sqlSessionFactory.getConfiguration().getEnvironment().getDataSource());
        assertSame(businessDataSource, jdbcTemplate.getDataSource());
    }

    @Test
    void shouldReadKnowledgeRunsFromKnowledgeDatabase() {
        knowledgeAssetJdbcTemplate.execute("""
                CREATE TABLE ka_runs (
                    id VARCHAR(64), source_task_id VARCHAR(64), source_report_id VARCHAR(64),
                    brand_scope_id VARCHAR(64), sample_size INTEGER,
                    report_generated_at TIMESTAMP WITH TIME ZONE,
                    run_completed_at TIMESTAMP WITH TIME ZONE,
                    run_started_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE
                )
                """);
        knowledgeAssetJdbcTemplate.update("""
                INSERT INTO ka_runs (id, source_task_id, brand_scope_id, sample_size, created_at)
                VALUES ('knowledge-run', 'knowledge-task', 'knowledge-scope', 5, CURRENT_TIMESTAMP)
                """);

        var run = knowledgeAssetRunRepository.findLatestRunByScope("knowledge-scope");

        assertNotNull(run);
        assertEquals("knowledge-run", run.runId());
        assertEquals(5, run.sampleSize());
    }
}

package com.geoprobe.config;

import com.geoprobe.config.properties.KnowledgeAssetProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.knowledge-asset", name = "enabled", havingValue = "true")
public class KnowledgeAssetDataSourceConfig {

    private final KnowledgeAssetProperties knowledgeAssetProperties;

    @Bean(name = "knowledgeAssetDataSource")
    public DataSource knowledgeAssetDataSource() {
        KnowledgeAssetProperties.Datasource datasource = knowledgeAssetProperties.getDatasource();
        HikariDataSource hikariDataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(datasource.getDriverClassName())
                .url(datasource.getUrl())
                .username(datasource.getUsername())
                .password(datasource.getPassword())
                .build();
        hikariDataSource.setPoolName(datasource.getPoolName());
        hikariDataSource.setMaximumPoolSize(datasource.getMaximumPoolSize());
        hikariDataSource.setMinimumIdle(datasource.getMinimumIdle());
        hikariDataSource.setConnectionTimeout(datasource.getConnectionTimeoutMs());
        if (datasource.getSchema() != null && !datasource.getSchema().isBlank()) {
            hikariDataSource.setSchema(datasource.getSchema().trim());
        }
        return hikariDataSource;
    }

    @Bean(name = "knowledgeAssetJdbcTemplate")
    public JdbcTemplate knowledgeAssetJdbcTemplate(@Qualifier("knowledgeAssetDataSource") DataSource knowledgeAssetDataSource) {
        return new JdbcTemplate(knowledgeAssetDataSource);
    }

    @Bean(name = "knowledgeAssetNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate knowledgeAssetNamedParameterJdbcTemplate(
            @Qualifier("knowledgeAssetDataSource") DataSource knowledgeAssetDataSource) {
        return new NamedParameterJdbcTemplate(knowledgeAssetDataSource);
    }

    @Bean(name = "knowledgeAssetFlyway", initMethod = "migrate")
    @ConditionalOnProperty(prefix = "app.knowledge-asset.flyway", name = "enabled", havingValue = "true")
    public Flyway knowledgeAssetFlyway(@Qualifier("knowledgeAssetDataSource") DataSource knowledgeAssetDataSource) {
        KnowledgeAssetProperties.Flyway flyway = knowledgeAssetProperties.getFlyway();
        return Flyway.configure()
                .dataSource(knowledgeAssetDataSource)
                .baselineOnMigrate(flyway.isBaselineOnMigrate())
                .baselineVersion(flyway.getBaselineVersion())
                .schemas(knowledgeAssetProperties.getDatasource().getSchema())
                .locations(flyway.getLocations().toArray(String[]::new))
                .load();
    }
}

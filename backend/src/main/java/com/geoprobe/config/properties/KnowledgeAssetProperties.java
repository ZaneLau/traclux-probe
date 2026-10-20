package com.geoprobe.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge-asset")
public class KnowledgeAssetProperties {

    private boolean enabled = false;
    private Datasource datasource = new Datasource();
    private Flyway flyway = new Flyway();
    private Ingestion ingestion = new Ingestion();
    private Projection projection = new Projection();

    @Data
    public static class Datasource {

        private String url = "jdbc:postgresql://localhost:5432/geoprobe_knowledge";
        private String username = "geoprobe";
        private String password = "";
        private String driverClassName = "org.postgresql.Driver";
        private String schema = "public";
        private Integer maximumPoolSize = 5;
        private Integer minimumIdle = 0;
        private String poolName = "knowledge-asset-pool";
        private Integer connectionTimeoutMs = 5000;
    }

    @Data
    public static class Flyway {

        private boolean enabled = true;
        private boolean baselineOnMigrate = true;
        private String baselineVersion = "0";
        private List<String> locations = List.of("classpath:db/knowledge-asset");
    }

    @Data
    public static class Ingestion {

        private boolean enabled = false;
        private boolean onRunCompletionEnabled = false;
        private Integer checkpointBatchSize = 200;
        private HistorySync historySync = new HistorySync();
    }

    @Data
    public static class HistorySync {

        private boolean readRepairEnabled = true;
        private boolean runOnStartup = false;
        private Integer startupMaxScopes = 0;
    }

    @Data
    public static class Projection {

        private boolean materializationEnabled = false;
        private boolean strictWorkbenchReadEnabled = true;
        private boolean brandPerformanceEnabled = false;
        private boolean citationSourcesEnabled = false;
        private boolean problemAnalysisEnabled = false;
        private boolean metricSlicesEnabled = false;
    }
}

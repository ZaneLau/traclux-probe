package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VolcengineRewardQuotaGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldBlockLiveExperimentWhenSnapshotMissing() {
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", "target/does-not-exist-reward-snapshot.json");
        try {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> VolcengineRewardQuotaGate.requireLiveExperimentAllowance("Node2 compare", "deepseek-v3.2")
            );
            assertTrue(ex.getMessage().contains("run_reward_budget_preflight.sh"));
            assertTrue(ex.getMessage().contains("Node2 compare"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
        }
    }

    @Test
    void shouldAllowLiveExperimentWhenTargetModelQuotaExists() throws IOException {
        Path snapshot = Files.createTempFile("volcengine-reward-snapshot-fresh", ".json");
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "doubao-seed-2-0-lite-260215",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 223456,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    )
            ));
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", snapshot.toString());

            assertDoesNotThrow(() -> VolcengineRewardQuotaGate.requireLiveExperimentAllowance("Node2 compare", "doubao-seed-2-0-lite-260215"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            Files.deleteIfExists(snapshot);
        }
    }

    @Test
    void shouldBlockLiveExperimentWhenTargetModelQuotaMissing() throws IOException {
        Path snapshot = Files.createTempFile("volcengine-reward-snapshot-missing-model", ".json");
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "doubao-seed-2-0-lite-260215",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 223456,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    )
            ));
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", snapshot.toString());

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> VolcengineRewardQuotaGate.requireLiveExperimentAllowance("Node2 compare", "deepseek-v3.2")
            );
            assertTrue(ex.getMessage().contains("deepseek-v3-2"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            Files.deleteIfExists(snapshot);
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}

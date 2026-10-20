package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VolcengineRewardSnapshotSupportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldLoadOnlyEngineManagedRewardRows() throws IOException {
        Path snapshot = Files.createTempFile("volcengine-reward-snapshot", ".json");
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "doubao-seed-2-0-lite-260215",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 123456,
                            "fetched_at", now.toString()
                    ),
                    Map.of(
                            "model_id", "doubao-seed-2-0-pro-260215",
                            "engine_managed", false,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 888888,
                            "fetched_at", now.toString()
                    ),
                    Map.of(
                            "model_id", "doubao-seed-1-8-251228",
                            "engine_managed", true,
                            "reward_plan_enabled", false,
                            "page_quota_remaining_tokens", 777777,
                            "fetched_at", now.toString()
                    )
            ));

            VolcengineRewardSnapshotSupport.RewardSnapshot loaded =
                    VolcengineRewardSnapshotSupport.load(snapshot, Duration.ofMinutes(15));

            assertEquals(Map.of("doubao-seed-2-0-lite", 123456L), loaded.remainingTokensByModel());
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    @Test
    void shouldRejectExpiredSnapshot() throws IOException {
        Path snapshot = Files.createTempFile("volcengine-reward-snapshot-expired", ".json");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "doubao-seed-2-0-lite-260215",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 123456,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString()
                    )
            ));

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> VolcengineRewardSnapshotSupport.load(snapshot, Duration.ofMinutes(15))
            );
            assertEquals(true, ex.getMessage().contains("已过期"));
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }
}

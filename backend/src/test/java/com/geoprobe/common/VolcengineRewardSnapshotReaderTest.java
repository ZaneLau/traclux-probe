package com.geoprobe.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcengineRewardSnapshotReaderTest {

    @Test
    void shouldDefaultToBackendTargetSnapshotPath() {
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        System.clearProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            Path resolved = VolcengineRewardSnapshotReader.resolveConfiguredPath();
            assertTrue(resolved.isAbsolute());
            assertTrue(resolved.toString().endsWith("backend/target/volcengine-ark-reward-model-snapshot.json"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
        }
    }

    @Test
    void shouldResolveRelativeConfiguredPathAgainstRepoRoot() {
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", "backend/target/custom-reward-snapshot.json");
        try {
            Path resolved = VolcengineRewardSnapshotReader.resolveConfiguredPath();
            assertTrue(resolved.isAbsolute());
            assertTrue(resolved.toString().endsWith("backend/target/custom-reward-snapshot.json"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
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

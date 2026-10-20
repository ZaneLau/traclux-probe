package com.geoprobe.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42RealtimeSearchPolicyResolver;
import com.geoprobe.service.shared.V42TargetProbeCountPolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcengineRewardRunAdmissionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldRecognizeVolcengineManagedProviders() {
        VolcengineRewardRunAdmissionService service = newService();

        assertTrue(service.requiresRewardGate("doubao", "doubao-seed-2-0-lite-260215"));
        assertTrue(service.requiresRewardGate("deepseek", "deepseek-v3.2"));
        assertFalse(service.requiresRewardGate("chatgpt", "gpt-5-mini"));
        assertFalse(service.requiresRewardGate("minimax", "MiniMax-M1"));
    }

    @Test
    void shouldBlockWhenSnapshotMissing() throws IOException {
        VolcengineRewardRunAdmissionService service = newService();
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        Path missingSnapshotDir = null;
        try {
            missingSnapshotDir = Files.createTempDirectory("volcengine-missing-snapshot");
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", missingSnapshotDir.resolve("missing.json").toString());
            LlmRunAdmissionBlockedException ex = assertThrows(
                    LlmRunAdmissionBlockedException.class,
                    () -> service.requireAllowed("deepseek", "deepseek-v3.2")
            );
            assertTrue(ex.getMessage().contains("先手动同步一次本地快照"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            if (missingSnapshotDir != null) {
                Files.deleteIfExists(missingSnapshotDir.resolve("missing.json"));
                Files.deleteIfExists(missingSnapshotDir);
            }
        }
    }

    @Test
    void shouldAllowWhenFreshSnapshotContainsTargetModel() throws IOException {
        VolcengineRewardRunAdmissionService service = newService();
        Path snapshot = Files.createTempFile("volcengine-admission-snapshot", ".json");
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "deepseek-v3-2",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 120000,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    )
            ));
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", snapshot.toString());
            assertDoesNotThrow(() -> service.requireAllowed("deepseek", "deepseek-v3.2"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            Files.deleteIfExists(snapshot);
        }
    }

    @Test
    void shouldAllowDeepseekEndpointWhenSnapshotContainsConfiguredMonitorModel() throws IOException {
        VolcengineRewardRunAdmissionService service = newService();
        Path snapshot = Files.createTempFile("volcengine-admission-deepseek-endpoint", ".json");
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "deepseek-v3-2",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 120000,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    )
            ));
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", snapshot.toString());
            assertDoesNotThrow(() -> service.requireAllowed("deepseek", "ep-test-deepseek"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            Files.deleteIfExists(snapshot);
        }
    }

    @Test
    void shouldBlockWhenSnapshotExpiredWithoutRuntimeAutoRefresh() throws IOException {
        Path snapshot = Files.createTempFile("volcengine-admission-stale", ".json");
        String previousPath = System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH");
        try {
            MAPPER.writeValue(snapshot.toFile(), List.of(
                    Map.of(
                            "model_id", "deepseek-v3-2",
                            "engine_managed", true,
                            "reward_plan_enabled", true,
                            "page_quota_remaining_tokens", 120000,
                            "fetched_at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(2).toString()
                    )
            ));
            System.setProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", snapshot.toString());
            VolcengineRewardRunAdmissionService service = newService();
            ReflectionTestUtils.setField(service, "snapshotMaxAgeMinutes", 15L);
            LlmRunAdmissionBlockedException ex = assertThrows(
                    LlmRunAdmissionBlockedException.class,
                    () -> service.requireAllowed("deepseek", "deepseek-v3.2")
            );
            assertTrue(ex.getMessage().contains("社区包不包含运营快照采集与自动刷新"));
        } finally {
            restoreProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH", previousPath);
            Files.deleteIfExists(snapshot);
        }
    }

    private static VolcengineRewardRunAdmissionService newService() {
        VolcengineRewardRunAdmissionService service = new VolcengineRewardRunAdmissionService(
                newResolver(),
                new V42TaskOrderExecutionPolicyResolver(new V42TargetProbeCountPolicy(), new V42RealtimeSearchPolicyResolver()),
                new V42Node3TargetEnginePolicyResolver(),
                emptyMapperProvider()
        );
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "snapshotMaxAgeMinutes", 2880L);
        ReflectionTestUtils.setField(service, "gatedProvidersText", "doubao,deepseek,kimi,glm,volcengine");
        ReflectionTestUtils.setField(service, "accountingZoneIdText", "Asia/Shanghai");
        ReflectionTestUtils.setField(service, "databaseV1Enabled", false);
        return service;
    }

    private static ObjectProvider<ModelInvocationRecordMapper> emptyMapperProvider() {
        return new ObjectProvider<>() {
            @Override
            public ModelInvocationRecordMapper getObject(Object... args) {
                return null;
            }

            @Override
            public ModelInvocationRecordMapper getIfAvailable() {
                return null;
            }

            @Override
            public ModelInvocationRecordMapper getIfUnique() {
                return null;
            }

            @Override
            public ModelInvocationRecordMapper getObject() {
                return null;
            }

            @Override
            public Stream<ModelInvocationRecordMapper> stream() {
                return Stream.empty();
            }

            @Override
            public Stream<ModelInvocationRecordMapper> orderedStream() {
                return Stream.empty();
            }

            @Override
            public java.util.Iterator<ModelInvocationRecordMapper> iterator() {
                return Collections.emptyIterator();
            }
        };
    }

    private static VolcengineRewardModelIdentityResolver newResolver() {
        VolcengineRewardModelIdentityResolver resolver = new VolcengineRewardModelIdentityResolver();
        ReflectionTestUtils.setField(resolver, "deepseekModel", "deepseek-v3.2");
        ReflectionTestUtils.setField(resolver, "deepseekEndpointId", "ep-test-deepseek");
        ReflectionTestUtils.setField(resolver, "doubaoModel", "doubao-seed-1.8");
        ReflectionTestUtils.setField(resolver, "doubaoEndpointId", "");
        ReflectionTestUtils.setField(resolver, "doubaoRealtimeEndpointId", "doubao-seed-1-6-lite-251015");
        ReflectionTestUtils.setField(resolver, "kimiModel", "kimi-k2-thinking-251104");
        ReflectionTestUtils.setField(resolver, "glmModel", "glm-4-7-251222");
        return resolver;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}

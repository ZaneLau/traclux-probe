package com.geoprobe.service.shared;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LightConeV43VersionTruthSupport {

    public static final String TASK_ORDER_CONTRACT_VERSION = "light_cone_task_order_v4_3";
    public static final String LEGACY_TASK_ORDER_CONTRACT_VERSION = "traclux_task_order_v4_3";
    public static final String RUN_VERSION_TRUTH_KEY = "lightConeRunVersionTruth";
    public static final String LEGACY_RUN_VERSION_TRUTH_KEY = "tracluxRunVersionTruth";
    public static final String PROBE_VERSION_TRUTH_KEY = "lightConeProbeVersionTruth";
    public static final String LEGACY_PROBE_VERSION_TRUTH_KEY = "tracluxProbeVersionTruth";

    public static final String MIGRATION_MODE_LEGACY_LITERAL = "legacy_literal";
    public static final String MIGRATION_MODE_SHADOW = "shadow";
    public static final String MIGRATION_MODE_CANONICAL = "canonical";

    public static final String ENGINE_RUNTIME_VERSION_V42 = "4.2";
    public static final String ENGINE_RUNTIME_VERSION_CANONICAL = "light_cone_4_3";
    public static final String LEGACY_ENGINE_RUNTIME_VERSION_CANONICAL = "traclux_light_cone_4_3";

    public static final String COMPILER_VERSION_LEGACY_LITERAL = "legacy_literal_compiler_v1";
    public static final String PROJECTION_VERSION_LEGACY_LITERAL = "legacy_literal_projection_v1";
    public static final String GATE_PROFILE_VERSION_LEGACY_LITERAL = "legacy_literal_gate_v1";
    public static final String BLUEPRINT_SCHEMA_VERSION_LEGACY_LITERAL = "legacy_literal_v1";
    public static final String RENDER_CONTRACT_VERSION_LITERAL_BRIDGE_V1 = "literal_bridge_v1";

    public static final String COMPILER_VERSION_CANONICAL = "topic_compiler_v4_3";
    public static final String PROJECTION_VERSION_CANONICAL = "topic_projection_v4_3";
    public static final String GATE_PROFILE_VERSION_CANONICAL = "triple_gate_v4_3";
    public static final String BLUEPRINT_SCHEMA_VERSION_CANONICAL = "light_cone_4_3";
    public static final String LEGACY_BLUEPRINT_SCHEMA_VERSION_CANONICAL = "traclux_4_3";
    public static final String RENDER_CONTRACT_VERSION_CANONICAL_PREFIX = "canonical_render_v1";

    public static final String EXECUTION_LANE_LEGACY = "legacyExecutionLane";
    public static final String EXECUTION_LANE_CANONICAL_SHADOW = "canonicalShadowLane";
    public static final String EXECUTION_LANE_CANONICAL = "canonicalExecutionLane";

    private LightConeV43VersionTruthSupport() {
    }

    public static Map<String, Object> legacyLiteralRunVersionTruth(String engineVersion) {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("engineRuntimeVersion", defaultIfBlank(engineVersion, ENGINE_RUNTIME_VERSION_V42));
        truth.put("compilerVersion", COMPILER_VERSION_LEGACY_LITERAL);
        truth.put("projectionVersion", PROJECTION_VERSION_LEGACY_LITERAL);
        truth.put("gateProfileVersion", GATE_PROFILE_VERSION_LEGACY_LITERAL);
        truth.put("migrationMode", MIGRATION_MODE_LEGACY_LITERAL);
        truth.put("taskOrderContractVersion", TASK_ORDER_CONTRACT_VERSION);
        truth.put("executionLane", EXECUTION_LANE_LEGACY);
        return Map.copyOf(truth);
    }

    public static Map<String, Object> canonicalShadowRunVersionTruth() {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("engineRuntimeVersion", ENGINE_RUNTIME_VERSION_CANONICAL);
        truth.put("compilerVersion", COMPILER_VERSION_CANONICAL);
        truth.put("projectionVersion", PROJECTION_VERSION_CANONICAL);
        truth.put("gateProfileVersion", GATE_PROFILE_VERSION_CANONICAL);
        truth.put("migrationMode", MIGRATION_MODE_SHADOW);
        truth.put("taskOrderContractVersion", TASK_ORDER_CONTRACT_VERSION);
        truth.put("executionLane", EXECUTION_LANE_CANONICAL_SHADOW);
        return Map.copyOf(truth);
    }

    public static Map<String, Object> canonicalRunVersionTruth() {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("engineRuntimeVersion", ENGINE_RUNTIME_VERSION_CANONICAL);
        truth.put("compilerVersion", COMPILER_VERSION_CANONICAL);
        truth.put("projectionVersion", PROJECTION_VERSION_CANONICAL);
        truth.put("gateProfileVersion", GATE_PROFILE_VERSION_CANONICAL);
        truth.put("migrationMode", MIGRATION_MODE_CANONICAL);
        truth.put("taskOrderContractVersion", TASK_ORDER_CONTRACT_VERSION);
        truth.put("executionLane", EXECUTION_LANE_CANONICAL);
        return Map.copyOf(truth);
    }

    public static Map<String, Object> legacyLiteralProbeVersionTruth(String sourcePath,
                                                                     String topicBindingSeed,
                                                                     String overrideReason) {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("blueprintSchemaVersion", BLUEPRINT_SCHEMA_VERSION_LEGACY_LITERAL);
        truth.put("renderContractVersion", RENDER_CONTRACT_VERSION_LITERAL_BRIDGE_V1);
        truth.put("sourcePath", defaultIfBlank(sourcePath, "legacy_literal_prompt"));
        truth.put("topicBindingHash", sha256Hex(defaultIfBlank(topicBindingSeed, "legacy_literal_binding")));
        truth.put("projectionVersion", PROJECTION_VERSION_LEGACY_LITERAL);
        truth.put("executionLane", EXECUTION_LANE_LEGACY);
        if (StringUtils.hasText(overrideReason)) {
            truth.put("overrideReason", overrideReason.trim());
        }
        return Map.copyOf(truth);
    }

    public static Map<String, Object> canonicalShadowProbeVersionTruth(String sourcePath,
                                                                       String topicBindingSeed,
                                                                       String renderContractVersion,
                                                                       String overrideReason) {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("blueprintSchemaVersion", BLUEPRINT_SCHEMA_VERSION_CANONICAL);
        truth.put("renderContractVersion", defaultIfBlank(renderContractVersion, RENDER_CONTRACT_VERSION_CANONICAL_PREFIX));
        truth.put("sourcePath", defaultIfBlank(sourcePath, "topic_compiler"));
        truth.put("topicBindingHash", sha256Hex(defaultIfBlank(topicBindingSeed, "canonical_shadow_binding")));
        truth.put("projectionVersion", PROJECTION_VERSION_CANONICAL);
        truth.put("executionLane", EXECUTION_LANE_CANONICAL_SHADOW);
        if (StringUtils.hasText(overrideReason)) {
            truth.put("overrideReason", overrideReason.trim());
        }
        return Map.copyOf(truth);
    }

    public static Map<String, Object> canonicalProbeVersionTruth(String sourcePath,
                                                                 String topicBindingSeed,
                                                                 String renderContractVersion,
                                                                 String overrideReason) {
        LinkedHashMap<String, Object> truth = new LinkedHashMap<>();
        truth.put("blueprintSchemaVersion", BLUEPRINT_SCHEMA_VERSION_CANONICAL);
        truth.put("renderContractVersion", defaultIfBlank(renderContractVersion, RENDER_CONTRACT_VERSION_CANONICAL_PREFIX));
        truth.put("sourcePath", defaultIfBlank(sourcePath, "topic_compiler"));
        truth.put("topicBindingHash", sha256Hex(defaultIfBlank(topicBindingSeed, "canonical_runtime_binding")));
        truth.put("projectionVersion", PROJECTION_VERSION_CANONICAL);
        truth.put("executionLane", EXECUTION_LANE_CANONICAL);
        if (StringUtils.hasText(overrideReason)) {
            truth.put("overrideReason", overrideReason.trim());
        }
        return Map.copyOf(truth);
    }

    public static Map<String, Object> ensureTaskMetadataContract(Map<String, Object> taskMetadata,
                                                                 String engineVersion) {
        LinkedHashMap<String, Object> normalized = mutableObjectMap(taskMetadata);
        normalized.put("taskOrderContractVersion", TASK_ORDER_CONTRACT_VERSION);
        normalized.put(RUN_VERSION_TRUTH_KEY, resolveRunVersionTruth(
                firstNonEmptyMap(normalized.get(RUN_VERSION_TRUTH_KEY), normalized.get(LEGACY_RUN_VERSION_TRUTH_KEY)),
                engineVersion
        ));
        normalized.remove(LEGACY_RUN_VERSION_TRUTH_KEY);
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    public static Map<String, Object> ensureTaskRuntimeContract(Map<String, Object> taskMetadata,
                                                                String engineVersion) {
        return ensureTaskRuntimeContract(taskMetadata, Map.of(), engineVersion);
    }

    public static Map<String, Object> ensureTaskRuntimeContract(Map<String, Object> taskMetadata,
                                                                Map<String, Object> runVersionTruth,
                                                                String engineVersion) {
        LinkedHashMap<String, Object> normalized = mutableObjectMap(taskMetadata);
        LinkedHashMap<String, Object> runtime = mutableObjectMap(normalized.get("_runtime"));
        Map<String, Object> resolvedRunTruth = normalizeRunVersionTruth(runVersionTruth, engineVersion);
        if (resolvedRunTruth.isEmpty()) {
            resolvedRunTruth = resolveRunVersionTruth(
                    firstNonEmptyMap(runtime.get(RUN_VERSION_TRUTH_KEY), runtime.get(LEGACY_RUN_VERSION_TRUTH_KEY)),
                    engineVersion
            );
        }
        runtime.put(RUN_VERSION_TRUTH_KEY, resolvedRunTruth);
        runtime.remove(LEGACY_RUN_VERSION_TRUTH_KEY);
        normalized.put("_runtime", Map.copyOf(runtime));
        return Map.copyOf(normalized);
    }

    public static Map<String, Object> ensureProbeMetadataContract(Map<String, Object> renderMetadata,
                                                                  Map<String, Object> probeVersionTruth) {
        LinkedHashMap<String, Object> normalized = mutableObjectMap(renderMetadata);
        normalized.put(PROBE_VERSION_TRUTH_KEY, normalizeProbeVersionTruth(
                firstNonEmptyMap(probeVersionTruth, normalized.get(LEGACY_PROBE_VERSION_TRUTH_KEY))
        ));
        normalized.remove(LEGACY_PROBE_VERSION_TRUTH_KEY);
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    public static Map<String, Object> readRunVersionTruth(Map<String, Object> taskOrderSnapshot,
                                                          Map<String, Object> taskMetadataSnapshot) {
        Map<String, Object> runtime = mutableObjectMap(mutableObjectMap(taskMetadataSnapshot).get("_runtime"));
        Map<String, Object> runtimeTruth = normalizeRunVersionTruth(
                firstNonEmptyMap(runtime.get(RUN_VERSION_TRUTH_KEY), runtime.get(LEGACY_RUN_VERSION_TRUTH_KEY)),
                null
        );
        Map<String, Object> taskMetadata = mutableObjectMap(mutableObjectMap(taskOrderSnapshot).get("taskMetadata"));
        Map<String, Object> taskOrderTruth = normalizeRunVersionTruth(
                firstNonEmptyMap(taskMetadata.get(RUN_VERSION_TRUTH_KEY), taskMetadata.get(LEGACY_RUN_VERSION_TRUTH_KEY)),
                null
        );
        if (!runtimeTruth.isEmpty()) {
            return shouldPreferTaskOrderTruth(runtimeTruth, taskOrderTruth) ? taskOrderTruth : runtimeTruth;
        }
        return taskOrderTruth;
    }

    public static void assertCompatible(Map<String, Object> runVersionTruth,
                                        Map<String, Object> probeVersionTruth) {
        Map<String, Object> normalizedRunTruth = normalizeRunVersionTruth(runVersionTruth, null);
        Map<String, Object> normalizedProbeTruth = normalizeProbeVersionTruth(probeVersionTruth);
        if (normalizedRunTruth.isEmpty() || normalizedProbeTruth.isEmpty()) {
            return;
        }
        String migrationMode = defaultIfBlank(text(normalizedRunTruth.get("migrationMode")), MIGRATION_MODE_LEGACY_LITERAL);
        String engineRuntimeVersion = defaultIfBlank(text(normalizedRunTruth.get("engineRuntimeVersion")), ENGINE_RUNTIME_VERSION_V42);
        String runProjectionVersion = text(normalizedRunTruth.get("projectionVersion"));
        String probeProjectionVersion = text(normalizedProbeTruth.get("projectionVersion"));
        if (StringUtils.hasText(runProjectionVersion)
                && StringUtils.hasText(probeProjectionVersion)
                && !runProjectionVersion.equals(probeProjectionVersion)) {
            throw new IllegalArgumentException("projectionVersion 不兼容: run=" + runProjectionVersion
                    + ", probe=" + probeProjectionVersion);
        }
        switch (migrationMode) {
            case MIGRATION_MODE_LEGACY_LITERAL -> assertLegacyLiteralCompatibility(engineRuntimeVersion, normalizedProbeTruth);
            case MIGRATION_MODE_SHADOW -> assertShadowCompatibility(normalizedProbeTruth);
            case MIGRATION_MODE_CANONICAL -> assertCanonicalCompatibility(engineRuntimeVersion, normalizedProbeTruth);
            default -> throw new IllegalArgumentException("未知 migrationMode: " + migrationMode);
        }
    }

    private static Map<String, Object> resolveRunVersionTruth(Object rawRunVersionTruth, String engineVersion) {
        Map<String, Object> normalized = normalizeRunVersionTruth(rawRunVersionTruth, engineVersion);
        if (normalized.isEmpty()) {
            return legacyLiteralRunVersionTruth(engineVersion);
        }
        return normalized;
    }

    private static void assertLegacyLiteralCompatibility(String engineRuntimeVersion,
                                                         Map<String, Object> probeVersionTruth) {
        if (!ENGINE_RUNTIME_VERSION_V42.equals(engineRuntimeVersion)
                && !com.geoprobe.service.engine.v42.V42EngineVersionSupport.CANONICAL_ENGINE_VERSION.equals(engineRuntimeVersion)) {
            throw new IllegalArgumentException("legacy_literal 仅允许 engineRuntimeVersion=4.2 / 4.3");
        }
        assertProbeShape(
                probeVersionTruth,
                BLUEPRINT_SCHEMA_VERSION_LEGACY_LITERAL,
                RENDER_CONTRACT_VERSION_LITERAL_BRIDGE_V1,
                EXECUTION_LANE_LEGACY
        );
    }

    private static void assertShadowCompatibility(Map<String, Object> probeVersionTruth) {
        String lane = text(probeVersionTruth.get("executionLane"));
        if (EXECUTION_LANE_LEGACY.equals(lane)) {
            assertProbeShape(
                    probeVersionTruth,
                    BLUEPRINT_SCHEMA_VERSION_LEGACY_LITERAL,
                    RENDER_CONTRACT_VERSION_LITERAL_BRIDGE_V1,
                    EXECUTION_LANE_LEGACY
            );
            return;
        }
        if (EXECUTION_LANE_CANONICAL_SHADOW.equals(lane)) {
            String blueprintSchemaVersion = text(probeVersionTruth.get("blueprintSchemaVersion"));
            String renderContractVersion = text(probeVersionTruth.get("renderContractVersion"));
            if (!BLUEPRINT_SCHEMA_VERSION_CANONICAL.equals(blueprintSchemaVersion)
                    || !startsWith(renderContractVersion, RENDER_CONTRACT_VERSION_CANONICAL_PREFIX)) {
                throw new IllegalArgumentException("shadow canonical lane 仅允许 light_cone_4_3 + canonical_render_v1+");
            }
            return;
        }
        throw new IllegalArgumentException("shadow lane 非法: " + lane);
    }

    private static void assertCanonicalCompatibility(String engineRuntimeVersion,
                                                     Map<String, Object> probeVersionTruth) {
        if (!ENGINE_RUNTIME_VERSION_CANONICAL.equals(engineRuntimeVersion)) {
            throw new IllegalArgumentException("canonical 仅允许 engineRuntimeVersion=light_cone_4_3");
        }
        String blueprintSchemaVersion = text(probeVersionTruth.get("blueprintSchemaVersion"));
        String renderContractVersion = text(probeVersionTruth.get("renderContractVersion"));
        String lane = text(probeVersionTruth.get("executionLane"));
        if (!BLUEPRINT_SCHEMA_VERSION_CANONICAL.equals(blueprintSchemaVersion)
                || !startsWith(renderContractVersion, RENDER_CONTRACT_VERSION_CANONICAL_PREFIX)
                || !EXECUTION_LANE_CANONICAL.equals(lane)) {
            throw new IllegalArgumentException("canonical lane 仅允许 light_cone_4_3 + canonical_render_v1+");
        }
    }

    private static void assertProbeShape(Map<String, Object> probeVersionTruth,
                                         String blueprintSchemaVersion,
                                         String renderContractVersion,
                                         String executionLane) {
        if (!blueprintSchemaVersion.equals(text(probeVersionTruth.get("blueprintSchemaVersion")))
                || !renderContractVersion.equals(text(probeVersionTruth.get("renderContractVersion")))
                || !executionLane.equals(text(probeVersionTruth.get("executionLane")))) {
            throw new IllegalArgumentException("probe 版本真相不兼容: " + probeVersionTruth);
        }
    }

    private static boolean startsWith(String value, String prefix) {
        return StringUtils.hasText(value) && StringUtils.hasText(prefix) && value.trim().startsWith(prefix.trim());
    }

    private static Map<String, Object> normalizeRunVersionTruth(Object rawRunVersionTruth, String engineVersion) {
        LinkedHashMap<String, Object> existing = mutableObjectMap(rawRunVersionTruth);
        if (existing.isEmpty()) {
            return Map.of();
        }
        existing.put("taskOrderContractVersion", normalizeTaskOrderContractVersion(text(existing.get("taskOrderContractVersion"))));
        existing.put("engineRuntimeVersion", normalizeEngineRuntimeVersion(defaultIfBlank(
                text(existing.get("engineRuntimeVersion")),
                defaultIfBlank(engineVersion, ENGINE_RUNTIME_VERSION_V42)
        )));
        String migrationMode = defaultIfBlank(text(existing.get("migrationMode")), MIGRATION_MODE_LEGACY_LITERAL);
        existing.put("migrationMode", migrationMode);
        existing.putIfAbsent("executionLane", switch (migrationMode) {
            case MIGRATION_MODE_CANONICAL -> EXECUTION_LANE_CANONICAL;
            case MIGRATION_MODE_SHADOW -> EXECUTION_LANE_CANONICAL_SHADOW;
            default -> EXECUTION_LANE_LEGACY;
        });
        return Map.copyOf(existing);
    }

    private static boolean shouldPreferTaskOrderTruth(Map<String, Object> runtimeTruth,
                                                      Map<String, Object> taskOrderTruth) {
        if (runtimeTruth == null || runtimeTruth.isEmpty() || taskOrderTruth == null || taskOrderTruth.isEmpty()) {
            return false;
        }
        String runtimeMigrationMode = defaultIfBlank(text(runtimeTruth.get("migrationMode")), MIGRATION_MODE_LEGACY_LITERAL);
        String runtimeEngineVersion = defaultIfBlank(text(runtimeTruth.get("engineRuntimeVersion")), ENGINE_RUNTIME_VERSION_V42);
        String taskOrderMigrationMode = defaultIfBlank(text(taskOrderTruth.get("migrationMode")), MIGRATION_MODE_LEGACY_LITERAL);
        if (!MIGRATION_MODE_LEGACY_LITERAL.equals(runtimeMigrationMode)
                || !ENGINE_RUNTIME_VERSION_V42.equals(runtimeEngineVersion)) {
            return false;
        }
        return !MIGRATION_MODE_LEGACY_LITERAL.equals(taskOrderMigrationMode);
    }

    private static Map<String, Object> normalizeProbeVersionTruth(Object rawProbeVersionTruth) {
        LinkedHashMap<String, Object> probeTruth = mutableObjectMap(rawProbeVersionTruth);
        if (probeTruth.isEmpty()) {
            return Map.of();
        }
        String blueprintSchemaVersion = normalizeBlueprintSchemaVersion(text(probeTruth.get("blueprintSchemaVersion")));
        if (StringUtils.hasText(blueprintSchemaVersion)) {
            probeTruth.put("blueprintSchemaVersion", blueprintSchemaVersion);
        }
        return Map.copyOf(probeTruth);
    }

    private static String normalizeTaskOrderContractVersion(String value) {
        if (!StringUtils.hasText(value) || LEGACY_TASK_ORDER_CONTRACT_VERSION.equals(value.trim())) {
            return TASK_ORDER_CONTRACT_VERSION;
        }
        return value.trim();
    }

    private static String normalizeEngineRuntimeVersion(String value) {
        if (LEGACY_ENGINE_RUNTIME_VERSION_CANONICAL.equals(value)) {
            return ENGINE_RUNTIME_VERSION_CANONICAL;
        }
        return value;
    }

    private static String normalizeBlueprintSchemaVersion(String value) {
        if (LEGACY_BLUEPRINT_SCHEMA_VERSION_CANONICAL.equals(value)) {
            return BLUEPRINT_SCHEMA_VERSION_CANONICAL;
        }
        return value;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Map<String, Object> firstNonEmptyMap(Object primary, Object secondary) {
        LinkedHashMap<String, Object> primaryMap = mutableObjectMap(primary);
        if (!primaryMap.isEmpty()) {
            return primaryMap;
        }
        return mutableObjectMap(secondary);
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> mutableObjectMap(Object raw) {
        if (raw instanceof Map<?, ?> map && !map.isEmpty()) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                copy.put(String.valueOf(key), value);
            }
            return copy;
        }
        return new LinkedHashMap<>();
    }
}

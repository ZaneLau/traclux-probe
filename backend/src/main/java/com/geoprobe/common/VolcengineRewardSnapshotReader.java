package com.geoprobe.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class VolcengineRewardSnapshotReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SNAPSHOT_FILENAME = "volcengine-ark-reward-model-snapshot.json";
    private static final Pattern DATE_SUFFIX_PATTERN = Pattern.compile("-\\d{6,8}$");

    private VolcengineRewardSnapshotReader() {
    }

    static Path resolveConfiguredPath() {
        String configuredPath = firstNonBlank(
                System.getProperty("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH"),
                System.getenv("V42_VOLCENGINE_REWARD_SNAPSHOT_PATH")
        );
        if (StringUtils.hasText(configuredPath)) {
            return resolvePathAgainstRepoRoot(Path.of(configuredPath.trim()));
        }
        return resolveRepoRoot().resolve("backend/target").resolve(SNAPSHOT_FILENAME).toAbsolutePath().normalize();
    }

    static RewardSnapshot load(Duration maxAge) throws IOException {
        return load(resolveConfiguredPath(), maxAge);
    }

    static RewardSnapshot load(Path path, Duration maxAge) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("snapshot path 不能为空");
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("奖励余量快照不存在: " + path);
        }
        List<Map<String, Object>> rows = MAPPER.readValue(
                path.toFile(),
                new TypeReference<List<Map<String, Object>>>() {
                }
        );
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("奖励余量快照为空: " + path);
        }

        OffsetDateTime newestFetchedAt = null;
        LinkedHashMap<String, Long> remainingBudgets = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (!Boolean.TRUE.equals(asBoolean(row.get("engine_managed")))) {
                continue;
            }
            if (!Boolean.TRUE.equals(asBoolean(row.get("reward_plan_enabled")))) {
                continue;
            }
            String modelId = normalizeModelId(asText(row.get("model_id")));
            Long remaining = asLong(row.get("page_quota_remaining_tokens"));
            if (!StringUtils.hasText(modelId) || remaining == null || remaining <= 0L) {
                continue;
            }
            remainingBudgets.put(modelId.trim(), remaining);
            OffsetDateTime fetchedAt = parseOffsetDateTime(asText(row.get("fetched_at")));
            if (fetchedAt != null && (newestFetchedAt == null || fetchedAt.isAfter(newestFetchedAt))) {
                newestFetchedAt = fetchedAt;
            }
        }

        if (remainingBudgets.isEmpty()) {
            throw new IllegalStateException("奖励余量快照里没有可用的白名单模型额度: " + path);
        }
        if (newestFetchedAt == null) {
            throw new IllegalStateException("奖励余量快照缺少 fetched_at 时间戳: " + path);
        }
        if (maxAge != null && !maxAge.isNegative() && !maxAge.isZero()) {
            Duration age = Duration.between(newestFetchedAt, OffsetDateTime.now(ZoneOffset.UTC));
            if (age.compareTo(maxAge) > 0) {
                throw new IllegalStateException(
                        "奖励余量快照已过期: age=%d min, limit=%d min, path=%s"
                                .formatted(age.toMinutes(), maxAge.toMinutes(), path)
                );
            }
        }
        return new RewardSnapshot(path, newestFetchedAt, Map.copyOf(remainingBudgets));
    }

    static String normalizeModelId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String normalized = raw.trim().toLowerCase().replace(".", "-");
        return DATE_SUFFIX_PATTERN.matcher(normalized).replaceFirst("");
    }

    private static Boolean asBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    private static Long asLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String asText(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static OffsetDateTime parseOffsetDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static Path resolvePathAgainstRepoRoot(Path path) {
        if (path == null) {
            return resolveRepoRoot().resolve("backend/target").resolve(SNAPSHOT_FILENAME).toAbsolutePath().normalize();
        }
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return resolveRepoRoot().resolve(path).toAbsolutePath().normalize();
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if ("backend".equalsIgnoreCase(cwd.getFileName() == null ? "" : cwd.getFileName().toString())
                && cwd.getParent() != null) {
            return cwd.getParent();
        }
        return cwd;
    }

    record RewardSnapshot(Path path,
                          OffsetDateTime fetchedAt,
                          Map<String, Long> remainingTokensByModel) {
    }
}

package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.geoprobe.service.shared.V42SnapshotSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FrontDeskPreviewTokenService {

    private static final String KEY_PREFIX = "diagnosis:frontdesk:preview-token:";
    private static final TypeReference<StoredPreviewSnapshot> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final RedisSupportService redisSupportService;

    private final ConcurrentHashMap<String, LocalSnapshotEntry> localSnapshots = new ConcurrentHashMap<>();

    private Duration ttl = Duration.ofHours(24);

    @Value("${app.diagnosis.frontdesk.preview-token-ttl-hours:24}")
    void setTtlHours(long ttlHours) {
        long normalizedHours = Math.max(1L, ttlHours);
        this.ttl = Duration.ofHours(normalizedHours);
    }

    public String issue(String userId,
                        int targetProbeCount,
                        Map<String, Object> frontDeskTaskOrder,
                        Map<String, Object> contextPackageSnapshot,
                        Map<String, Object> createDiagnosisPayloadSnapshot) {
        String normalizedUserId = normalizeTokenField(userId);
        if (!StringUtils.hasText(normalizedUserId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        cleanupExpiredLocalSnapshots();
        String token = "fdp_" + UUID.randomUUID().toString().replace("-", "");
        StoredPreviewSnapshot snapshot = new StoredPreviewSnapshot(
                normalizedUserId,
                targetProbeCount,
                V42SnapshotSanitizer.sanitizeObjectMap(frontDeskTaskOrder),
                V42SnapshotSanitizer.sanitizeObjectMap(contextPackageSnapshot),
                V42SnapshotSanitizer.sanitizeObjectMap(createDiagnosisPayloadSnapshot),
                LocalDateTime.now().toString()
        );
        long expireAtEpochMillis = System.currentTimeMillis() + ttl.toMillis();
        localSnapshots.put(token, new LocalSnapshotEntry(snapshot, expireAtEpochMillis));
        redisSupportService.setJson(redisKey(token), snapshot, ttl, "frontdesk_preview_token");
        return token;
    }

    public StoredPreviewSnapshot load(String userId, String previewToken) {
        String normalizedUserId = normalizeTokenField(userId);
        String normalizedToken = normalizeTokenField(previewToken);
        if (!StringUtils.hasText(normalizedUserId) || !StringUtils.hasText(normalizedToken)) {
            return null;
        }
        cleanupExpiredLocalSnapshots();
        StoredPreviewSnapshot snapshot = redisSupportService.getJson(
                redisKey(normalizedToken),
                SNAPSHOT_TYPE,
                "frontdesk_preview_token"
        );
        if (snapshot == null) {
            LocalSnapshotEntry localEntry = localSnapshots.get(normalizedToken);
            snapshot = localEntry == null ? null : localEntry.snapshot();
        }
        if (snapshot == null) {
            return null;
        }
        return normalizedUserId.equals(snapshot.userId()) ? snapshot.sanitized() : null;
    }

    private void cleanupExpiredLocalSnapshots() {
        long now = System.currentTimeMillis();
        localSnapshots.entrySet().removeIf(entry -> entry.getValue().expireAtEpochMillis() <= now);
    }

    private String redisKey(String token) {
        return KEY_PREFIX + token;
    }

    private String normalizeTokenField(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record LocalSnapshotEntry(StoredPreviewSnapshot snapshot, long expireAtEpochMillis) {
    }

    public record StoredPreviewSnapshot(String userId,
                                        int targetProbeCount,
                                        Map<String, Object> frontDeskTaskOrder,
                                        Map<String, Object> contextPackageSnapshot,
                                        Map<String, Object> createDiagnosisPayloadSnapshot,
                                        String createdAt) {
        public StoredPreviewSnapshot sanitized() {
            return new StoredPreviewSnapshot(
                    userId,
                    Math.max(1, targetProbeCount),
                    V42SnapshotSanitizer.sanitizeObjectMap(frontDeskTaskOrder),
                    V42SnapshotSanitizer.sanitizeObjectMap(contextPackageSnapshot),
                    V42SnapshotSanitizer.sanitizeObjectMap(createDiagnosisPayloadSnapshot),
                    createdAt
            );
        }
    }
}

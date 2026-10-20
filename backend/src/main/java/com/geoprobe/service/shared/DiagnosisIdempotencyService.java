package com.geoprobe.service.shared;

import com.geoprobe.entity.Diagnosis;
import com.geoprobe.service.DiagnosisAccessService;
import com.geoprobe.service.RedisSupportService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DiagnosisIdempotencyService {

    private static final Duration IDEMPOTENCY_RESULT_TTL = Duration.ofHours(6);
    private static final Duration IDEMPOTENCY_LOCK_TTL = Duration.ofMinutes(2);
    private static final int IDEMPOTENCY_WAIT_RETRIES = 10;
    private static final long IDEMPOTENCY_WAIT_MILLIS = 200L;

    private final DiagnosisAccessService diagnosisAccessService;
    private final RedisSupportService redisSupportService;
    private final Map<String, LocalIdempotencyEntry> localIdempotencyResultStore = new ConcurrentHashMap<>();
    private final Map<String, LocalIdempotencyEntry> localIdempotencyLockStore = new ConcurrentHashMap<>();

    public DiagnosisIdempotencyService(DiagnosisAccessService diagnosisAccessService,
                                       RedisSupportService redisSupportService) {
        this.diagnosisAccessService = diagnosisAccessService;
        this.redisSupportService = redisSupportService;
    }

    public Diagnosis findIdempotentDiagnosis(String userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        cleanupLocalIdempotencyStore();
        String diagnosisId = readIdempotentResult(idempotencyResultKey(userId, requestId));
        if (!StringUtils.hasText(diagnosisId)) {
            return null;
        }
        return diagnosisAccessService.findRuntimeReadByIdAndUserId(diagnosisId, userId);
    }

    public Diagnosis waitForIdempotentDiagnosis(String userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        for (int i = 0; i < IDEMPOTENCY_WAIT_RETRIES; i++) {
            Diagnosis existing = findIdempotentDiagnosis(userId, requestId);
            if (existing != null) {
                return existing;
            }
            try {
                Thread.sleep(IDEMPOTENCY_WAIT_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public IdempotencyLock acquireLock(String userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        String key = idempotencyLockKey(userId, requestId);
        if (redisSupportService.setIfAbsent(key, "1", IDEMPOTENCY_LOCK_TTL, "diagnosis idempotency lock")) {
            return new IdempotencyLock(key, true, false);
        }
        long now = System.currentTimeMillis();
        long expireAt = now + IDEMPOTENCY_LOCK_TTL.toMillis();
        AtomicBoolean acquired = new AtomicBoolean(false);
        localIdempotencyLockStore.compute(key, (lockKey, existing) -> {
            if (existing == null || existing.expireAtMillis() <= now) {
                acquired.set(true);
                return new LocalIdempotencyEntry("1", expireAt);
            }
            return existing;
        });
        return acquired.get() ? new IdempotencyLock(key, false, true) : null;
    }

    public void releaseLock(IdempotencyLock lock) {
        if (lock == null || !StringUtils.hasText(lock.key())) {
            return;
        }
        if (lock.redisLock()) {
            redisSupportService.delete(lock.key(), "diagnosis idempotency lock release");
            return;
        }
        if (lock.localLock()) {
            localIdempotencyLockStore.remove(lock.key());
        }
    }

    public void cacheResult(String userId, String requestId, String diagnosisId) {
        if (!StringUtils.hasText(requestId) || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        String key = idempotencyResultKey(userId, requestId);
        if (redisSupportService.isEnabled()) {
            redisSupportService.setString(key, diagnosisId, IDEMPOTENCY_RESULT_TTL, "diagnosis idempotency result");
            if (StringUtils.hasText(redisSupportService.getString(key, "diagnosis idempotency result verify"))) {
                return;
            }
        }
        localIdempotencyResultStore.put(
                key,
                new LocalIdempotencyEntry(diagnosisId, System.currentTimeMillis() + IDEMPOTENCY_RESULT_TTL.toMillis())
        );
    }

    private String readIdempotentResult(String key) {
        String cachedValue = redisSupportService.getString(key, "diagnosis idempotency result");
        if (StringUtils.hasText(cachedValue)) {
            return cachedValue;
        }
        LocalIdempotencyEntry local = localIdempotencyResultStore.get(key);
        if (local == null || local.expireAtMillis() <= System.currentTimeMillis()) {
            localIdempotencyResultStore.remove(key);
            return null;
        }
        return local.value();
    }

    private void cleanupLocalIdempotencyStore() {
        long now = System.currentTimeMillis();
        localIdempotencyResultStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expireAtMillis() <= now);
        localIdempotencyLockStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expireAtMillis() <= now);
    }

    private String idempotencyResultKey(String userId, String requestId) {
        return "diagnosis:idempotency:result:" + userId + ":" + requestId;
    }

    private String idempotencyLockKey(String userId, String requestId) {
        return "diagnosis:idempotency:lock:" + userId + ":" + requestId;
    }

    private record LocalIdempotencyEntry(String value, long expireAtMillis) {
    }

    public record IdempotencyLock(String key, boolean redisLock, boolean localLock) {
    }
}

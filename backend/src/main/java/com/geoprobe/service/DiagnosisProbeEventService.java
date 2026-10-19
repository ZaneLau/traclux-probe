package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.DiagnosisProbeEvent;
import com.geoprobe.mapper.DiagnosisProbeEventMapper;
import com.geoprobe.service.engine.v42.V42ExecutionObserver;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisProbeEventService {

    private static final Duration EVENT_CACHE_TTL = Duration.ofHours(12);
    private static final long EVENT_CACHE_MAX_LENGTH = 4_096L;
    private static final int PERSIST_BATCH_SIZE = 200;
    private static final int MAX_PENDING_PERSISTENCE = 10_000;
    private static final String EVENT_CACHE_KEY_PREFIX = "diagnosis:probe-events:";

    private final DiagnosisProbeEventMapper diagnosisProbeEventMapper;
    private final RedisSupportService redisSupportService;
    @Autowired(required = false)
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Qualifier("probeEventPersistenceExecutor")
    private final ThreadPoolTaskExecutor probeEventPersistenceExecutor;
    private final ConcurrentLinkedQueue<DiagnosisProbeEvent> pendingPersistence = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DiagnosisProbeEvent> overflowPersistence = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingPersistenceSize = new AtomicInteger();
    private final AtomicInteger overflowPersistenceSize = new AtomicInteger();
    private final AtomicBoolean asyncFlushScheduled = new AtomicBoolean(false);
    private final long nodeEntropy = ThreadLocalRandom.current().nextLong(1_000L);
    private final AtomicLong localSequence = new AtomicLong(initialLocalSequenceFloor());

    public DiagnosisService.ProbeEventItem append(String diagnosisId, V42ExecutionObserver.RealtimeEventTrace trace) {
        if (!StringUtils.hasText(diagnosisId) || trace == null) {
            return null;
        }
        DiagnosisProbeEvent event = DiagnosisProbeEvent.builder()
                .seq(nextSequence())
                .diagnosisId(diagnosisId.trim())
                .eventType(trace.eventType())
                .provisional(trace.provisional())
                .payload(trace.payload())
                .createdAt(LocalDateTime.now())
                .build();
        DiagnosisService.ProbeEventItem item = toItem(event);
        cacheEvent(diagnosisId.trim(), item);
        enqueueForPersistence(event);
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onEvent(diagnosisId.trim(), item);
        }
        return item;
    }

    public DiagnosisService.ProbeEventPage readPage(String diagnosisId, Long afterSeq, Integer limit) {
        if (!StringUtils.hasText(diagnosisId)) {
            return new DiagnosisService.ProbeEventPage(List.of(), 0L);
        }
        long cursor = afterSeq == null ? 0L : Math.max(0L, afterSeq);
        int effectiveLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        CachedProbeEventWindow cachedWindow = readPageFromCache(diagnosisId.trim(), cursor, effectiveLimit);
        if (!cachedWindow.page().events().isEmpty() && cursor >= Math.max(0L, cachedWindow.firstSeq() - 1L)) {
            return cachedWindow.page();
        }
        if (!cachedWindow.page().events().isEmpty()) {
            DiagnosisService.ProbeEventPage mergedPage = readPageWithDatabaseBackfill(diagnosisId.trim(), cursor, effectiveLimit, cachedWindow);
            if (!mergedPage.events().isEmpty()) {
                return mergedPage;
            }
        }
        List<DiagnosisService.ProbeEventItem> events = readPageFromDatabase(diagnosisId.trim(), cursor, effectiveLimit, null);
        if (events.isEmpty()) {
            return new DiagnosisService.ProbeEventPage(List.of(), cursor);
        }
        long nextSeq = events.get(events.size() - 1).seq();
        return new DiagnosisService.ProbeEventPage(events, nextSeq);
    }

    @Scheduled(fixedDelayString = "${app.diagnosis.probe-events.persist-fixed-delay-ms:250}")
    void flushPendingPersistence() {
        drainPendingPersistence(false);
    }

    @PreDestroy
    void flushPendingPersistenceOnShutdown() {
        while (drainAndPersist(PERSIST_BATCH_SIZE, true) > 0) {
            // Drain remaining events before shutdown to reduce event loss window.
        }
    }

    private CachedProbeEventWindow readPageFromCache(String diagnosisId, long cursor, int effectiveLimit) {
        if (!redisSupportService.isEnabled()) {
            return new CachedProbeEventWindow(new DiagnosisService.ProbeEventPage(List.of(), cursor), 0L);
        }
        List<DiagnosisService.ProbeEventItem> cachedItems = redisSupportService.readJsonList(
                cacheKey(diagnosisId),
                new TypeReference<DiagnosisService.ProbeEventItem>() {
                },
                "diagnosis probe events cache"
        );
        if (cachedItems.isEmpty()) {
            return new CachedProbeEventWindow(new DiagnosisService.ProbeEventPage(List.of(), cursor), 0L);
        }
        long firstSeq = cachedItems.getFirst().seq();
        List<DiagnosisService.ProbeEventItem> page = cachedItems.stream()
                .filter(item -> item != null && item.seq() > cursor)
                .limit(effectiveLimit)
                .toList();
        if (page.isEmpty()) {
            return new CachedProbeEventWindow(new DiagnosisService.ProbeEventPage(List.of(), cursor), firstSeq);
        }
        long nextSeq = page.get(page.size() - 1).seq();
        return new CachedProbeEventWindow(new DiagnosisService.ProbeEventPage(page, nextSeq), firstSeq);
    }

    private DiagnosisService.ProbeEventPage readPageWithDatabaseBackfill(String diagnosisId,
                                                                         long cursor,
                                                                         int effectiveLimit,
                                                                         CachedProbeEventWindow cachedWindow) {
        long firstCachedSeq = cachedWindow.firstSeq();
        if (firstCachedSeq <= 0L) {
            return new DiagnosisService.ProbeEventPage(List.of(), cursor);
        }
        List<DiagnosisService.ProbeEventItem> databaseItems = readPageFromDatabase(diagnosisId, cursor, effectiveLimit, firstCachedSeq);
        if (databaseItems.size() >= effectiveLimit) {
            long nextSeq = databaseItems.get(databaseItems.size() - 1).seq();
            return new DiagnosisService.ProbeEventPage(databaseItems, nextSeq);
        }
        List<DiagnosisService.ProbeEventItem> merged = new ArrayList<>(databaseItems);
        long mergedCursor = merged.isEmpty() ? cursor : merged.get(merged.size() - 1).seq();
        for (DiagnosisService.ProbeEventItem cached : cachedWindow.page().events()) {
            if (cached == null || cached.seq() <= mergedCursor) {
                continue;
            }
            merged.add(cached);
            if (merged.size() >= effectiveLimit) {
                break;
            }
        }
        if (merged.isEmpty()) {
            return new DiagnosisService.ProbeEventPage(List.of(), cursor);
        }
        long nextSeq = merged.get(merged.size() - 1).seq();
        return new DiagnosisService.ProbeEventPage(merged, nextSeq);
    }

    private void cacheEvent(String diagnosisId, DiagnosisService.ProbeEventItem item) {
        if (!StringUtils.hasText(diagnosisId) || item == null) {
            return;
        }
        redisSupportService.appendJsonListItem(
                cacheKey(diagnosisId),
                item,
                EVENT_CACHE_TTL,
                EVENT_CACHE_MAX_LENGTH,
                "diagnosis probe events cache"
        );
    }

    private long drainAndPersist(int batchSize, boolean dropOnFailure) {
        if (batchSize <= 0 || (!hasPendingPersistence() && overflowPersistence.isEmpty())) {
            return 0L;
        }
        List<DiagnosisProbeEvent> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            DiagnosisProbeEvent event = pendingPersistence.poll();
            if (event == null) {
                break;
            }
            pendingPersistenceSize.decrementAndGet();
            batch.add(event);
        }
        while (batch.size() < batchSize) {
            DiagnosisProbeEvent overflowEvent = overflowPersistence.poll();
            if (overflowEvent == null) {
                break;
            }
            overflowPersistenceSize.decrementAndGet();
            batch.add(overflowEvent);
        }
        if (batch.isEmpty()) {
            return 0L;
        }
        if (!persistBatch(batch, dropOnFailure)) {
            return 0L;
        }
        return batch.size();
    }

    private boolean persistBatch(List<DiagnosisProbeEvent> batch, boolean dropOnFailure) {
        if (batch == null || batch.isEmpty()) {
            return true;
        }
        try {
            if (batch.size() == 1) {
                diagnosisProbeEventMapper.insert(batch.getFirst());
            } else {
                diagnosisProbeEventMapper.insertBatch(batch);
            }
            return true;
        } catch (Exception ex) {
            if (!dropOnFailure) {
                requeueBatchWithinCapacity(batch);
            }
            log.warn("Diagnosis probe events persist failed, count={}, dropOnFailure={}, reason={}",
                    batch.size(), dropOnFailure, ex.getMessage());
            return false;
        }
    }

    private void enqueueForPersistence(DiagnosisProbeEvent event) {
        if (event == null) {
            return;
        }
        if (tryEnqueue(event)) {
            if (pendingPersistenceSize.get() >= PERSIST_BATCH_SIZE) {
                scheduleAsyncFlush();
            }
            return;
        }
        enqueueOverflow(event);
        scheduleAsyncFlush();
        log.warn("Diagnosis probe event queue saturated, moved to overflow queue. diagnosisId={}, seq={}, pending={}, overflow={}",
                event.getDiagnosisId(), event.getSeq(), pendingPersistenceSize.get(), overflowPersistenceSize.get());
    }

    private boolean tryEnqueue(DiagnosisProbeEvent event) {
        int queued = pendingPersistenceSize.incrementAndGet();
        if (queued > MAX_PENDING_PERSISTENCE) {
            pendingPersistenceSize.decrementAndGet();
            return false;
        }
        pendingPersistence.offer(event);
        return true;
    }

    private void requeueBatchWithinCapacity(List<DiagnosisProbeEvent> batch) {
        int restored = 0;
        int overflowed = 0;
        for (DiagnosisProbeEvent event : batch) {
            if (event == null) {
                continue;
            }
            if (tryEnqueue(event)) {
                restored++;
            } else {
                enqueueOverflow(event);
                overflowed++;
            }
        }
        if (overflowed > 0) {
            log.warn("Diagnosis probe events requeue spilled to overflow queue after persist failure, restored={}, overflowed={}",
                    restored, overflowed);
        }
    }

    private void enqueueOverflow(DiagnosisProbeEvent event) {
        if (event == null) {
            return;
        }
        overflowPersistence.offer(event);
        overflowPersistenceSize.incrementAndGet();
    }

    private boolean hasPendingPersistence() {
        return !pendingPersistence.isEmpty() || !overflowPersistence.isEmpty();
    }

    private void scheduleAsyncFlush() {
        if (!asyncFlushScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            probeEventPersistenceExecutor.execute(() -> {
                try {
                    drainPendingPersistence(false);
                } finally {
                    asyncFlushScheduled.set(false);
                    if (hasPendingPersistence()) {
                        scheduleAsyncFlush();
                    }
                }
            });
        } catch (RuntimeException ex) {
            asyncFlushScheduled.set(false);
            log.warn("Diagnosis probe event async flush rejected, keep buffered for scheduler. reason={}", ex.getMessage());
        }
    }

    private void drainPendingPersistence(boolean dropOnFailure) {
        while (drainAndPersist(PERSIST_BATCH_SIZE, dropOnFailure) > 0) {
            if (pendingPersistenceSize.get() < PERSIST_BATCH_SIZE && overflowPersistenceSize.get() == 0) {
                break;
            }
        }
    }

    private List<DiagnosisService.ProbeEventItem> readPageFromDatabase(String diagnosisId,
                                                                       long cursor,
                                                                       int effectiveLimit,
                                                                       Long upperExclusiveSeq) {
        LambdaQueryWrapper<DiagnosisProbeEvent> wrapper = new LambdaQueryWrapper<DiagnosisProbeEvent>()
                .eq(DiagnosisProbeEvent::getDiagnosisId, diagnosisId)
                .gt(DiagnosisProbeEvent::getSeq, cursor);
        if (upperExclusiveSeq != null && upperExclusiveSeq > 0L) {
            wrapper.lt(DiagnosisProbeEvent::getSeq, upperExclusiveSeq);
        }
        wrapper.orderByAsc(DiagnosisProbeEvent::getSeq)
                .last("LIMIT " + effectiveLimit);
        List<DiagnosisProbeEvent> events = diagnosisProbeEventMapper.selectList(wrapper);
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .map(this::toItem)
                .filter(Objects::nonNull)
                .toList();
    }

    private DiagnosisService.ProbeEventItem toItem(DiagnosisProbeEvent event) {
        if (event == null || event.getSeq() == null) {
            return null;
        }
        return new DiagnosisService.ProbeEventItem(
                event.getSeq(),
                event.getEventType(),
                Boolean.TRUE.equals(event.getProvisional()),
                event.getCreatedAt() == null ? null : event.getCreatedAt().toString(),
                event.getPayload() == null ? Map.of() : event.getPayload()
        );
    }

    private long nextSequence() {
        return localSequence.updateAndGet(previous -> Math.max(previous + 1L, currentSequenceFloor()));
    }

    private long currentSequenceFloor() {
        return System.currentTimeMillis() * 1_000_000L + (nodeEntropy * 1_000L);
    }

    private static long initialLocalSequenceFloor() {
        return System.currentTimeMillis() * 1_000_000L;
    }

    private String cacheKey(String diagnosisId) {
        return EVENT_CACHE_KEY_PREFIX + diagnosisId;
    }

    private record CachedProbeEventWindow(DiagnosisService.ProbeEventPage page, long firstSeq) {
    }
}

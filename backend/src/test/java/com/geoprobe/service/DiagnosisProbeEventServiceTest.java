package com.geoprobe.service;

import com.geoprobe.service.engine.v42.V42ExecutionObserver;
import com.geoprobe.entity.DiagnosisProbeEvent;
import com.geoprobe.mapper.DiagnosisProbeEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisProbeEventServiceTest {

    @Mock
    private DiagnosisProbeEventMapper diagnosisProbeEventMapper;
    @Mock
    private RedisSupportService redisSupportService;
    @Mock
    private ThreadPoolTaskExecutor probeEventPersistenceExecutor;

    private DiagnosisProbeEventService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosisProbeEventService(
                diagnosisProbeEventMapper,
                redisSupportService,
                probeEventPersistenceExecutor
        );
    }

    @Test
    void shouldCacheAndDeferPersistenceWhenRedisEnabled() {
        DiagnosisService.ProbeEventItem item = service.append(
                "diag-1",
                new V42ExecutionObserver.RealtimeEventTrace("probe_result", true, Map.of("probeId", "p-1"))
        );

        assertNotNull(item);
        assertFalse(item.seq() <= 0);
        verify(redisSupportService).appendJsonListItem(
                eq("diagnosis:probe-events:diag-1"),
                eq(item),
                any(),
                eq(4_096L),
                eq("diagnosis probe events cache")
        );
        verify(diagnosisProbeEventMapper, never()).insert(any(DiagnosisProbeEvent.class));

        service.flushPendingPersistence();

        ArgumentCaptor<DiagnosisProbeEvent> eventCaptor = ArgumentCaptor.forClass(DiagnosisProbeEvent.class);
        verify(diagnosisProbeEventMapper).insert(eventCaptor.capture());
        DiagnosisProbeEvent persisted = eventCaptor.getValue();
        assertEquals(item.seq(), persisted.getSeq());
        assertEquals("diag-1", persisted.getDiagnosisId());
        assertEquals("probe_result", persisted.getEventType());
    }

    @Test
    void shouldReadPageFromRedisCacheBeforeDatabase() {
        when(redisSupportService.isEnabled()).thenReturn(true);
        when(redisSupportService.readJsonList(eq("diagnosis:probe-events:diag-2"), any(), eq("diagnosis probe events cache")))
                .thenReturn(List.of(
                        new DiagnosisService.ProbeEventItem(101L, "probe_result", true, LocalDateTime.now().minusSeconds(2).toString(), Map.of("probeId", "p-101")),
                        new DiagnosisService.ProbeEventItem(102L, "final_gate", false, LocalDateTime.now().minusSeconds(1).toString(), Map.of("passed", true))
                ));

        DiagnosisService.ProbeEventPage page = service.readPage("diag-2", 101L, 20);

        assertEquals(1, page.events().size());
        assertEquals(102L, page.nextSeq());
        assertEquals("final_gate", page.events().getFirst().eventType());
        verify(diagnosisProbeEventMapper, never()).selectList(any());
    }

    @Test
    void shouldBackfillDatabaseEventsWhenCursorFallsBeforeCachedWindow() {
        when(redisSupportService.isEnabled()).thenReturn(true);
        when(redisSupportService.readJsonList(eq("diagnosis:probe-events:diag-4"), any(), eq("diagnosis probe events cache")))
                .thenReturn(List.of(
                        new DiagnosisService.ProbeEventItem(101L, "probe_result", true, LocalDateTime.now().minusSeconds(2).toString(), Map.of("probeId", "p-101")),
                        new DiagnosisService.ProbeEventItem(102L, "probe_result", true, LocalDateTime.now().minusSeconds(1).toString(), Map.of("probeId", "p-102"))
                ));
        when(diagnosisProbeEventMapper.selectList(any()))
                .thenReturn(List.of(
                        DiagnosisProbeEvent.builder()
                                .seq(99L)
                                .diagnosisId("diag-4")
                                .eventType("engine_log")
                                .provisional(true)
                                .payload(Map.of("stage", "NODE2"))
                                .createdAt(LocalDateTime.now().minusSeconds(3))
                                .build(),
                        DiagnosisProbeEvent.builder()
                                .seq(100L)
                                .diagnosisId("diag-4")
                                .eventType("engine_log")
                                .provisional(true)
                                .payload(Map.of("stage", "NODE3"))
                                .createdAt(LocalDateTime.now().minusSeconds(2))
                                .build()
                ));

        DiagnosisService.ProbeEventPage page = service.readPage("diag-4", 0L, 3);

        assertEquals(List.of(99L, 100L, 101L), page.events().stream().map(DiagnosisService.ProbeEventItem::seq).toList());
        assertEquals(101L, page.nextSeq());
        verify(diagnosisProbeEventMapper).selectList(any());
    }

    @Test
    void shouldPersistQueuedEventsWhenRedisDisabled() {
        DiagnosisService.ProbeEventItem item = service.append(
                "diag-3",
                new V42ExecutionObserver.RealtimeEventTrace("engine_log", false, Map.of("stage", "NODE3"))
        );

        assertNotNull(item);
        verify(diagnosisProbeEventMapper, never()).insert(any(DiagnosisProbeEvent.class));
        service.flushPendingPersistence();
        verify(diagnosisProbeEventMapper).insert(any(DiagnosisProbeEvent.class));
    }

    @Test
    void shouldPersistQueuedEventsInBatchWhenRedisEnabled() {
        when(diagnosisProbeEventMapper.insertBatch(any())).thenReturn(2);

        service.append("diag-batch", new V42ExecutionObserver.RealtimeEventTrace("probe_result", true, Map.of("probeId", "p-1")));
        service.append("diag-batch", new V42ExecutionObserver.RealtimeEventTrace("probe_result", true, Map.of("probeId", "p-2")));

        service.flushPendingPersistence();

        ArgumentCaptor<List<DiagnosisProbeEvent>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(diagnosisProbeEventMapper).insertBatch(batchCaptor.capture());
        assertEquals(2, batchCaptor.getValue().size());
        verify(diagnosisProbeEventMapper, never()).insert(any(DiagnosisProbeEvent.class));
    }

    @Test
    void shouldSpillToOverflowQueueWhenQueueIsSaturated() {
        AtomicInteger size = (AtomicInteger) ReflectionTestUtils.getField(service, "pendingPersistenceSize");
        assertNotNull(size);
        size.set(10_000);

        DiagnosisService.ProbeEventItem item = service.append(
                "diag-overflow",
                new V42ExecutionObserver.RealtimeEventTrace("probe_result", true, Map.of("probeId", "p-overflow"))
        );

        assertNotNull(item);
        service.flushPendingPersistence();

        verify(diagnosisProbeEventMapper).insert(any(DiagnosisProbeEvent.class));
        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<DiagnosisProbeEvent> pendingQueue =
                (ConcurrentLinkedQueue<DiagnosisProbeEvent>) ReflectionTestUtils.getField(service, "pendingPersistence");
        assertNotNull(pendingQueue);
        assertTrue(pendingQueue.isEmpty());
        assertEquals(10_000, size.get());
        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<DiagnosisProbeEvent> overflowQueue =
                (ConcurrentLinkedQueue<DiagnosisProbeEvent>) ReflectionTestUtils.getField(service, "overflowPersistence");
        assertNotNull(overflowQueue);
        assertTrue(overflowQueue.isEmpty());
    }
}

package com.geoprobe.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisProgressStreamServiceTest {

    @Mock
    private DiagnosisService diagnosisService;

    @Mock
    private ExecutorService observationExecutor;

    private DiagnosisProgressStreamService streamService;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(observationExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        streamService = new DiagnosisProgressStreamService(diagnosisService, observationExecutor);
    }

    @Test
    void shouldReplayCurrentStateForLateSubscriberOnSharedHub() {
        String userId = "u-1";
        String diagnosisId = "d-1";
        DiagnosisService.ProgressStreamSnapshot snapshot = new DiagnosisService.ProgressStreamSnapshot(
                diagnosisId,
                1,
                null,
                "node3",
                "采样中",
                "SAMPLING",
                3,
                8,
                "Node3",
                35.0d,
                7,
                50,
                50,
                7,
                43,
                false,
                120
        );
        DiagnosisService.ProbeEventItem item = new DiagnosisService.ProbeEventItem(
                1L,
                "probe_result",
                true,
                "2026-04-03T14:00:00+08:00",
                Map.of("probeId", "p-1")
        );

        when(diagnosisService.getProgressStreamSnapshot(eq(userId), eq(diagnosisId)))
                .thenReturn(snapshot);
        when(diagnosisService.getProbeEvents(eq(userId), eq(diagnosisId), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    long afterSeq = invocation.getArgument(2, Long.class);
                    if (afterSeq == 0L) {
                        return new DiagnosisService.ProbeEventPage(List.of(item), 1L);
                    }
                    return new DiagnosisService.ProbeEventPage(List.of(), afterSeq);
                });

        streamService.openStream(userId, diagnosisId);
        streamService.openStream(userId, diagnosisId);

        verify(diagnosisService, times(2)).getProgressStreamSnapshot(eq(userId), eq(diagnosisId));
        verify(diagnosisService, atLeast(2)).getProbeEvents(eq(userId), eq(diagnosisId), eq(0L), anyInt());
    }

    @Test
    void shouldNormalizeUnknownProbeEventNameToProbeResult() {
        assertEquals("probe_result", streamService.resolveProbeEventName(null));
        assertEquals("probe_result", streamService.resolveProbeEventName(""));
        assertEquals("engine_log", streamService.resolveProbeEventName("engine_log"));
        assertEquals("round_summary", streamService.resolveProbeEventName("ROUND_SUMMARY"));
        assertEquals("final_gate", streamService.resolveProbeEventName(" final_gate "));
    }
}

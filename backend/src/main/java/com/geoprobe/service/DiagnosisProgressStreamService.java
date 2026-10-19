package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DiagnosisProgressStreamService {

    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_EXPIRED = 6;
    private static final long STREAM_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long HEARTBEAT_INTERVAL_MILLIS = 15_000L;
    private static final long ACTIVE_PROGRESS_INTERVAL_MILLIS = 1_000L;
    private static final long IDLE_PROGRESS_INTERVAL_MILLIS = 3_000L;
    private static final int REPLAY_EVENT_PAGE_SIZE = 200;
    private static final int REPLAY_EVENT_MAX_PAGES = 20;
    private static final Set<String> SUPPORTED_PROBE_EVENT_NAMES = Set.of(
            "probe_result",
            "round_summary",
            "final_gate",
            "engine_log"
    );

    private final DiagnosisService diagnosisService;
    private final ExecutorService observationExecutor;
    private final ConcurrentMap<String, StreamHub> hubs = new ConcurrentHashMap<>();

    public DiagnosisProgressStreamService(DiagnosisService diagnosisService,
                                          @Qualifier("diagnosisObservationExecutor") ExecutorService observationExecutor) {
        this.diagnosisService = diagnosisService;
        this.observationExecutor = observationExecutor;
    }

    public SseEmitter openStream(String userId, String diagnosisId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        String hubKey = buildHubKey(userId, diagnosisId);
        StreamHub hub = hubs.compute(hubKey, (key, existing) -> existing == null ? new StreamHub(key, userId, diagnosisId) : existing);
        hub.attach(emitter);
        return emitter;
    }

    @PreDestroy
    void shutdownHubs() {
        hubs.values().forEach(StreamHub::close);
        hubs.clear();
    }

    private long resolvePollIntervalMillis(boolean progressChanged, boolean hasNewEvents) {
        return progressChanged || hasNewEvents ? ACTIVE_PROGRESS_INTERVAL_MILLIS : IDLE_PROGRESS_INTERVAL_MILLIS;
    }

    private String buildProgressSignature(DiagnosisService.ProgressStreamSnapshot snapshot) {
        if (snapshot == null) {
            return "null";
        }
        return String.join("|",
                String.valueOf(snapshot.stage()),
                String.valueOf(snapshot.status()),
                String.valueOf(snapshot.percent()),
                String.valueOf(snapshot.samplingCompleted()),
                String.valueOf(snapshot.samplingExpectedTotal()),
                String.valueOf(snapshot.samplingGeneratedTotal()),
                String.valueOf(snapshot.samplingMissingTotal()),
                String.valueOf(snapshot.errorMessage())
        );
    }

    private void sendProgress(SseEmitter emitter, DiagnosisService.ProgressStreamSnapshot snapshot) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("currentStep", snapshot.currentStep());
        data.put("currentStepLabel", snapshot.currentStepLabel());
        data.put("stage", snapshot.stage());
        data.put("stageIndex", snapshot.stageIndex());
        data.put("stageTotal", snapshot.stageTotal());
        data.put("stageLabel", snapshot.stageLabel());
        data.put("percent", snapshot.percent());
        data.put("samplingCompleted", snapshot.samplingCompleted());
        data.put("samplingTotal", snapshot.samplingTotal());
        data.put("samplingExpectedTotal", snapshot.samplingExpectedTotal());
        data.put("samplingGeneratedTotal", snapshot.samplingGeneratedTotal());
        data.put("samplingMissingTotal", snapshot.samplingMissingTotal());
        data.put("samplingVariantShortfall", snapshot.samplingVariantShortfall());
        data.put("etaSeconds", snapshot.etaSeconds());
        emitter.send(SseEmitter.event().name("progress").data(data));
    }

    private void sendProbeEvent(SseEmitter emitter,
                                String diagnosisId,
                                DiagnosisService.ProbeEventItem eventItem) throws IOException {
        if (eventItem == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("seq", eventItem.seq());
        data.put("diagnosisId", diagnosisId);
        data.put("eventType", eventItem.eventType());
        data.put("provisional", eventItem.provisional());
        data.put("createdAt", eventItem.createdAt());
        data.put("payload", eventItem.payload());
        String eventName = resolveProbeEventName(eventItem.eventType());
        emitter.send(
                SseEmitter.event()
                        .id(String.valueOf(eventItem.seq()))
                        .name(eventName)
                        .data(data)
        );
    }

    private void sendComplete(SseEmitter emitter, String diagnosisId) throws IOException {
        Map<String, Object> data = Map.of(
                "diagnosisId", diagnosisId,
                "reportReady", true
        );
        emitter.send(SseEmitter.event().name("complete").data(data));
    }

    private void sendError(SseEmitter emitter, int code, String message) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        emitter.send(SseEmitter.event().name("error").data(data));
    }

    private void safeSendError(SseEmitter emitter, int code, String message) {
        try {
            sendError(emitter, code, message);
        } catch (Exception ignored) {
            // Ignore secondary stream errors while shutting down.
        }
    }

    private int resolveErrorCode(Integer status) {
        if (status != null && status == STATUS_FAILED) {
            return ErrorCode.LLM_CALL_FAILED.getCode();
        }
        return ErrorCode.BAD_REQUEST.getCode();
    }

    private String normalizeErrorMessage(String message, Integer status) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        if (status != null && status == STATUS_CANCELLED) {
            return "诊断任务已取消";
        }
        if (status != null && status == STATUS_EXPIRED) {
            return "诊断任务已过期";
        }
        return "诊断任务执行失败";
    }

    private String buildHubKey(String userId, String diagnosisId) {
        return (userId == null ? "" : userId.trim()) + "::" + (diagnosisId == null ? "" : diagnosisId.trim());
    }

    String resolveProbeEventName(String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return "probe_result";
        }
        String normalized = rawEventType.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_PROBE_EVENT_NAMES.contains(normalized) ? normalized : "probe_result";
    }

    private final class StreamHub {

        private final String hubKey;
        private final String userId;
        private final String diagnosisId;
        private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private volatile DiagnosisService.ProgressStreamSnapshot lastSnapshot;
        private volatile String lastProgressSignature;
        private volatile long lastHeartbeatAt;
        private volatile long lastProbeEventSeq;

        private StreamHub(String hubKey, String userId, String diagnosisId) {
            this.hubKey = hubKey;
            this.userId = userId;
            this.diagnosisId = diagnosisId;
        }

        private void attach(SseEmitter emitter) {
            emitter.onCompletion(() -> detach(emitter));
            emitter.onTimeout(() -> detach(emitter));
            emitter.onError(ignored -> detach(emitter));
            try {
                replayCurrentState(emitter);
                emitters.add(emitter);
            } catch (BusinessException ex) {
                safeSendError(emitter, ex.getErrorCode().getCode(), ex.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Ignore emitter close errors while attach replay fails.
                }
                return;
            } catch (IOException ex) {
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignored) {
                    // Ignore emitter close errors while attach replay fails.
                }
                return;
            }
            startIfNeeded();
        }

        private void replayCurrentState(SseEmitter emitter) throws IOException {
            DiagnosisService.ProgressStreamSnapshot snapshot = diagnosisService.getProgressStreamSnapshot(userId, diagnosisId);
            if (snapshot != null) {
                sendProgress(emitter, snapshot);
                lastSnapshot = snapshot;
                lastProgressSignature = buildProgressSignature(snapshot);
            }
            long replayAfterSeq = 0L;
            long replayedMaxSeq = 0L;
            int pageCount = 0;
            while (pageCount < REPLAY_EVENT_MAX_PAGES) {
                DiagnosisService.ProbeEventPage page = diagnosisService.getProbeEvents(
                        userId,
                        diagnosisId,
                        replayAfterSeq,
                        REPLAY_EVENT_PAGE_SIZE
                );
                if (page.events().isEmpty()) {
                    break;
                }
                for (DiagnosisService.ProbeEventItem eventItem : page.events()) {
                    sendProbeEvent(emitter, diagnosisId, eventItem);
                }
                long nextSeq = page.nextSeq();
                replayedMaxSeq = Math.max(replayedMaxSeq, nextSeq);
                if (nextSeq <= replayAfterSeq || page.events().size() < REPLAY_EVENT_PAGE_SIZE) {
                    break;
                }
                replayAfterSeq = nextSeq;
                pageCount++;
            }
            if (replayedMaxSeq > 0L) {
                lastProbeEventSeq = Math.max(lastProbeEventSeq, replayedMaxSeq);
            }
        }

        private void detach(SseEmitter emitter) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                hubs.remove(hubKey, this);
            }
        }

        private void close() {
            closed.set(true);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // Ignore emitter shutdown failures during hub cleanup.
                }
            }
            emitters.clear();
        }

        private void startIfNeeded() {
            if (closed.get()) {
                return;
            }
            if (running.compareAndSet(false, true)) {
                observationExecutor.execute(this::streamLoop);
            }
        }

        private void streamLoop() {
            try {
                while (!closed.get() && !emitters.isEmpty()) {
                    DiagnosisService.ProgressStreamFrame frame =
                            diagnosisService.getProgressStreamFrame(userId, diagnosisId, lastProbeEventSeq, 200);
                    DiagnosisService.ProgressStreamSnapshot snapshot = frame.snapshot();
                    lastSnapshot = snapshot;
                    String progressSignature = buildProgressSignature(snapshot);
                    boolean progressChanged = lastProgressSignature == null || !lastProgressSignature.equals(progressSignature);
                    if (progressChanged) {
                        broadcastProgress(snapshot);
                        lastProgressSignature = progressSignature;
                    }

                    DiagnosisService.ProbeEventPage probeEventPage = frame.probeEventPage();
                    boolean hasNewEvents = !probeEventPage.events().isEmpty();
                    if (hasNewEvents) {
                        for (DiagnosisService.ProbeEventItem eventItem : probeEventPage.events()) {
                            broadcastProbeEvent(eventItem);
                        }
                    }
                    lastProbeEventSeq = probeEventPage.nextSeq();

                    Integer status = snapshot.status();
                    if (status != null && status == STATUS_COMPLETED) {
                        broadcastComplete();
                        close();
                        return;
                    }
                    if (status != null && (status == STATUS_FAILED || status == STATUS_CANCELLED || status == STATUS_EXPIRED)) {
                        broadcastError(resolveErrorCode(status), normalizeErrorMessage(snapshot.errorMessage(), status));
                        close();
                        return;
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MILLIS) {
                        broadcastHeartbeat();
                        lastHeartbeatAt = now;
                    }
                    Thread.sleep(resolvePollIntervalMillis(progressChanged, hasNewEvents));
                }
            } catch (BusinessException ex) {
                broadcastError(ex.getErrorCode().getCode(), ex.getMessage());
                close();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                close();
            } catch (Exception ex) {
                log.warn("Diagnosis SSE hub loop failed, diagnosisId={}, reason={}", diagnosisId, ex.getMessage());
                broadcastError(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
                close();
            } finally {
                running.set(false);
                if (!closed.get() && !emitters.isEmpty()) {
                    startIfNeeded();
                } else {
                    hubs.remove(hubKey, this);
                }
            }
        }

        private void broadcastProgress(DiagnosisService.ProgressStreamSnapshot snapshot) {
            broadcast(emitter -> sendProgress(emitter, snapshot));
        }

        private void broadcastProbeEvent(DiagnosisService.ProbeEventItem eventItem) {
            broadcast(emitter -> sendProbeEvent(emitter, diagnosisId, eventItem));
        }

        private void broadcastComplete() {
            broadcast(emitter -> sendComplete(emitter, diagnosisId));
        }

        private void broadcastError(int code, String message) {
            broadcast(emitter -> sendError(emitter, code, message));
        }

        private void broadcastHeartbeat() {
            broadcast(emitter -> emitter.send(SseEmitter.event().comment("heartbeat")));
        }

        private void broadcast(EmitterCallback callback) {
            for (SseEmitter emitter : emitters) {
                try {
                    callback.accept(emitter);
                } catch (IOException ex) {
                    emitters.remove(emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // Ignore follow-up emitter close failure after IO error.
                    }
                } catch (Exception ex) {
                    emitters.remove(emitter);
                    try {
                        emitter.completeWithError(ex);
                    } catch (Exception ignored) {
                        // Ignore follow-up emitter close failure after callback error.
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface EmitterCallback {
        void accept(SseEmitter emitter) throws Exception;
    }
}

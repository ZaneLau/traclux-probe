package com.geoprobe.service.engine.v42;

import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.SamplingProgress;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Slf4j
final class Node3SamplingBatchRunner {

    private static final int PROGRESS_LOG_INTERVAL = 10;

    private final ExecutorService probeCallExecutor;
    private final int maxConcurrency;
    private final int realtimeAimdMinConcurrency;
    private final int realtimeAimdMaxConcurrency;
    private final int realtimeAimdIncreaseStep;
    private final int realtimeAimdAdjustInterval;
    private final double realtimeAimdCongestionRatio;
    private final long realtimeAimdHighLatencyMillis;
    private final ProbeDispatcher probeDispatcher;
    private final boolean exposeRoutingDebug;

    Node3SamplingBatchRunner(ExecutorService probeCallExecutor,
                             int maxConcurrency,
                             int realtimeAimdMinConcurrency,
                             int realtimeAimdMaxConcurrency,
                             int realtimeAimdIncreaseStep,
                             int realtimeAimdAdjustInterval,
                             double realtimeAimdCongestionRatio,
                             long realtimeAimdHighLatencyMillis,
                             ProbeDispatcher probeDispatcher,
                             boolean exposeRoutingDebug) {
        this.probeCallExecutor = Objects.requireNonNull(probeCallExecutor, "probeCallExecutor 不能为空");
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.realtimeAimdMinConcurrency = Math.max(1, realtimeAimdMinConcurrency);
        this.realtimeAimdMaxConcurrency = Math.max(this.realtimeAimdMinConcurrency, realtimeAimdMaxConcurrency);
        this.realtimeAimdIncreaseStep = Math.max(1, realtimeAimdIncreaseStep);
        this.realtimeAimdAdjustInterval = Math.max(1, realtimeAimdAdjustInterval);
        this.realtimeAimdCongestionRatio = realtimeAimdCongestionRatio;
        this.realtimeAimdHighLatencyMillis = Math.max(2_000L, realtimeAimdHighLatencyMillis);
        this.probeDispatcher = Objects.requireNonNull(probeDispatcher, "probeDispatcher 不能为空");
        this.exposeRoutingDebug = exposeRoutingDebug;
    }

    Node3SamplingBatchRunner(ExecutorService probeCallExecutor,
                             int maxConcurrency,
                             int realtimeAimdMinConcurrency,
                             int realtimeAimdMaxConcurrency,
                             int realtimeAimdIncreaseStep,
                             int realtimeAimdAdjustInterval,
                             double realtimeAimdCongestionRatio,
                             long realtimeAimdHighLatencyMillis,
                             ProbeDispatcher probeDispatcher) {
        this(
                probeCallExecutor,
                maxConcurrency,
                realtimeAimdMinConcurrency,
                realtimeAimdMaxConcurrency,
                realtimeAimdIncreaseStep,
                realtimeAimdAdjustInterval,
                realtimeAimdCongestionRatio,
                realtimeAimdHighLatencyMillis,
                probeDispatcher,
                false
        );
    }

    BatchDispatchResult runBatch(String diagnosisId,
                                 List<IndexedProbeInput> indexedProbes,
                                 RealtimeSearchPolicy policy,
                                 Node3TargetEnginePolicy targetEnginePolicy,
                                 int round,
                                 int concurrencyCap,
                                 @Nullable Consumer<SamplingProgress> progressCallback,
                                 @Nullable V42Node3SamplingGatewayService.DispatchEventListener eventListener) {
        int total = indexedProbes.size();
        if (total == 0) {
            return new BatchDispatchResult(List.of(), 1);
        }
        int modeAwareMaxConcurrency = policy.enabled()
                ? Math.max(1, Math.min(total, realtimeAimdMaxConcurrency))
                : Math.max(1, Math.min(total, maxConcurrency));
        int effectiveMaxConcurrency = Math.max(1, Math.min(modeAwareMaxConcurrency, concurrencyCap));
        int effectiveConcurrency = Math.min(resolveInitialConcurrency(total, policy, effectiveMaxConcurrency), total);
        CompletionService<IndexedProbeOutput> completionService = new ExecutorCompletionService<>(probeCallExecutor);
        List<Future<IndexedProbeOutput>> submittedTasks = new ArrayList<>(total);
        Node3AdaptiveWindowController windowController = new Node3AdaptiveWindowController(
                effectiveConcurrency,
                resolveAdaptiveMin(total, policy, effectiveMaxConcurrency),
                resolveAdaptiveMax(total, policy, effectiveMaxConcurrency),
                realtimeAimdIncreaseStep,
                realtimeAimdAdjustInterval,
                realtimeAimdCongestionRatio,
                realtimeAimdHighLatencyMillis
        );
        List<IndexedProbeOutput> collected = new ArrayList<>(total);
        int successCount = 0;
        int failedCount = 0;
        int completed = 0;
        int nextSubmitIndex = 0;
        int inFlight = 0;
        safeProgressCallback(progressCallback, new SamplingProgress(0, total, 0, 0));

        while (nextSubmitIndex < total && inFlight < windowController.currentWindow()) {
            IndexedProbeInput indexedProbe = indexedProbes.get(nextSubmitIndex);
            submittedTasks.add(submitProbe(
                    completionService,
                    diagnosisId,
                    indexedProbe.probe(),
                    indexedProbe.index(),
                    policy,
                    targetEnginePolicy,
                    round
            ));
            nextSubmitIndex++;
            inFlight++;
        }

        try {
            while (completed < total) {
                IndexedProbeOutput indexedOutput = awaitCompletedOutput(completionService);
                inFlight--;
                completed++;
                Node3ProbeOutput output = indexedOutput.output();
                collected.add(indexedOutput);
                if (output.dispatchStatus() == ProbeDispatchStatus.SUCCESS) {
                    successCount++;
                } else {
                    failedCount++;
                }
                windowController.record(output);
                int activeWindow = windowController.maybeAdjust();
                logSamplingProgress(diagnosisId, output, completed, total, successCount, failedCount);
                safeProgressCallback(progressCallback, new SamplingProgress(completed, total, successCount, failedCount));
                emitProbeResultEvent(
                        diagnosisId,
                        round,
                        indexedOutput.index(),
                        completed,
                        total,
                        activeWindow,
                        output,
                        eventListener
                );

                while (nextSubmitIndex < total && inFlight < activeWindow) {
                    IndexedProbeInput indexedProbe = indexedProbes.get(nextSubmitIndex);
                    submittedTasks.add(submitProbe(
                            completionService,
                            diagnosisId,
                            indexedProbe.probe(),
                            indexedProbe.index(),
                            policy,
                            targetEnginePolicy,
                            round
                    ));
                    nextSubmitIndex++;
                    inFlight++;
                }
            }
        } catch (RuntimeException ex) {
            cancelOutstanding(submittedTasks);
            throw ex;
        }
        return new BatchDispatchResult(List.copyOf(collected), windowController.currentWindow());
    }

    private Future<IndexedProbeOutput> submitProbe(CompletionService<IndexedProbeOutput> completionService,
                                                   String diagnosisId,
                                                   Node3ProbeInput probe,
                                                   int index,
                                                   RealtimeSearchPolicy policy,
                                                   Node3TargetEnginePolicy targetEnginePolicy,
                                                   int round) {
        return completionService.submit(() -> new IndexedProbeOutput(
                index,
                probeDispatcher.dispatch(diagnosisId, probe, policy, targetEnginePolicy, round)
        ));
    }

    private void cancelOutstanding(List<Future<IndexedProbeOutput>> submittedTasks) {
        for (Future<IndexedProbeOutput> submittedTask : submittedTasks) {
            if (submittedTask == null || submittedTask.isDone()) {
                continue;
            }
            submittedTask.cancel(true);
        }
    }

    private IndexedProbeOutput awaitCompletedOutput(CompletionService<IndexedProbeOutput> completionService) {
        try {
            return completionService.take().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Node 3 并发采样线程被中断", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(extractThrowableMessage(cause), cause);
        }
    }

    private String extractThrowableMessage(Throwable throwable) {
        if (throwable == null) {
            return "UNKNOWN_ERROR";
        }
        String message = throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            return throwable.getClass().getSimpleName();
        }
        return SensitiveDataSanitizer.sanitize(message.trim());
    }

    private int resolveInitialConcurrency(int total, RealtimeSearchPolicy policy, int configuredMaxConcurrency) {
        if (total <= 0) {
            return 1;
        }
        if (!policy.enabled()) {
            return Math.max(1, Math.min(configuredMaxConcurrency, total));
        }
        return Math.max(
                1,
                Math.min(
                        resolveAdaptiveMax(total, policy, configuredMaxConcurrency),
                        Math.max(resolveAdaptiveMin(total, policy, configuredMaxConcurrency), Math.min(total, realtimeAimdMinConcurrency))
                )
        );
    }

    private int resolveAdaptiveMin(int total, RealtimeSearchPolicy policy, int configuredMaxConcurrency) {
        if (!policy.enabled()) {
            return Math.max(1, Math.min(total, configuredMaxConcurrency));
        }
        return Math.max(1, Math.min(Math.min(total, configuredMaxConcurrency), realtimeAimdMinConcurrency));
    }

    private int resolveAdaptiveMax(int total, RealtimeSearchPolicy policy, int configuredMaxConcurrency) {
        if (!policy.enabled()) {
            return Math.max(1, Math.min(total, configuredMaxConcurrency));
        }
        return Math.max(
                resolveAdaptiveMin(total, policy, configuredMaxConcurrency),
                Math.min(Math.min(total, configuredMaxConcurrency), realtimeAimdMaxConcurrency)
        );
    }

    private void emitProbeResultEvent(String diagnosisId,
                                      int round,
                                      int probeIndex,
                                      int completed,
                                      int total,
                                      int activeWindow,
                                      Node3ProbeOutput output,
                                      @Nullable V42Node3SamplingGatewayService.DispatchEventListener eventListener) {
        safeDispatchEvent(
                eventListener,
                Node3DispatchEventFactory.probeResultEvent(
                        diagnosisId,
                        round,
                        probeIndex,
                        completed,
                        total,
                        activeWindow,
                        output,
                        exposeRoutingDebug
                )
        );
    }

    private void safeDispatchEvent(@Nullable V42Node3SamplingGatewayService.DispatchEventListener eventListener,
                                   @Nullable V42Node3SamplingGatewayService.DispatchEvent event) {
        if (eventListener == null || event == null) {
            return;
        }
        try {
            eventListener.onEvent(event);
        } catch (RuntimeException ex) {
            log.warn("Node 3 dispatch event callback failed, eventType={}, reason={}", event.eventType(), ex.getMessage());
        }
    }

    private void safeProgressCallback(@Nullable Consumer<SamplingProgress> progressCallback,
                                      @Nullable SamplingProgress progress) {
        if (progressCallback == null || progress == null) {
            return;
        }
        try {
            progressCallback.accept(progress);
        } catch (RuntimeException ex) {
            log.warn("Node 3 sampling progress callback failed, message={}", ex.getMessage());
        }
    }

    private void logSamplingProgress(String diagnosisId,
                                     Node3ProbeOutput output,
                                     int completed,
                                     int total,
                                     int successCount,
                                     int failedCount) {
        if (!StringUtils.hasText(diagnosisId)) {
            return;
        }
        boolean shouldLog = completed == 1
                || completed == total
                || completed % PROGRESS_LOG_INTERVAL == 0
                || output.dispatchStatus() == ProbeDispatchStatus.FAILED;
        if (!shouldLog) {
            return;
        }
        log.info("V4.2 Node 3 sampling progress. diagnosisId={}, completed={}, total={}, success={}, failed={}, lastProbeId={}, lastStatus={}",
                diagnosisId, completed, total, successCount, failedCount, output.probeId(), output.dispatchStatus());
    }

    @FunctionalInterface
    interface ProbeDispatcher {
        Node3ProbeOutput dispatch(String diagnosisId,
                                  Node3ProbeInput probe,
                                  RealtimeSearchPolicy policy,
                                  Node3TargetEnginePolicy targetEnginePolicy,
                                  int round);
    }

    record IndexedProbeInput(int index, Node3ProbeInput probe) {
    }

    record IndexedProbeOutput(int index, Node3ProbeOutput output) {
    }

    record BatchDispatchResult(List<IndexedProbeOutput> outputs, int lastWindowSize) {
    }
}

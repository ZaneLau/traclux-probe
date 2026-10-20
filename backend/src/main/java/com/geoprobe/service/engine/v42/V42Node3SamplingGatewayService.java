package com.geoprobe.service.engine.v42;

import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.LlmTokenBudgetService;
import com.geoprobe.dto.engine.SamplingProgress;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3SamplingRequest;
import com.geoprobe.dto.engine.v42.Node3SamplingResult;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.Node3UsageCostSummary;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import com.geoprobe.dto.engine.v42.RealtimeSearchSummary;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Slf4j
@Service("v42Node3SamplingGatewayService")
public class V42Node3SamplingGatewayService {

    private static final int MODULE_SAMPLING = 3;
    private static final String PROVIDER_DOUBAO = "doubao";
    private static final int DEFAULT_MAX_ATTEMPTS = 1;
    private static final String DEFAULT_RETRY_BACKOFF_MILLIS = "800";
    private static final long DEFAULT_TASK_TIMEOUT_SECONDS = 180L;
    private static final long DEFAULT_REALTIME_TASK_TIMEOUT_SECONDS = 150L;
    private static final String SEARCH_EVIDENCE_FAILURE = "REALTIME_SEARCH_EVIDENCE_UNSATISFIED";
    private static final int DEFAULT_AIMD_MIN_CONCURRENCY = 40;
    private static final int DEFAULT_AIMD_MAX_CONCURRENCY = 120;
    private static final int DEFAULT_AIMD_INCREASE_STEP = 8;
    private static final int DEFAULT_AIMD_ADJUST_INTERVAL = 8;
    private static final double DEFAULT_AIMD_CONGESTION_RATIO = 0.25d;
    private static final long DEFAULT_AIMD_HIGH_LATENCY_MS = 12_000L;

    private final ModelAdapter modelAdapter;
    private final RetryBackoffSleeper retryBackoffSleeper;
    private final ExecutorService probeCallExecutor;
    private final ExecutorService probeModelCallExecutor;
    private final boolean ownsProbeCallExecutor;
    private final boolean ownsProbeModelCallExecutor;
    private final int maxConcurrency;
    private final int maxAttempts;
    private final long[] retryBackoffMillis;
    private final long taskTimeoutSeconds;
    private final long realtimeTaskTimeoutSeconds;
    private final int realtimeAimdMinConcurrency;
    private final int realtimeAimdMaxConcurrency;
    private final int realtimeAimdIncreaseStep;
    private final int realtimeAimdAdjustInterval;
    private final double realtimeAimdCongestionRatio;
    private final long realtimeAimdHighLatencyMillis;
    private final boolean exposeRoutingDebug;
    private final LlmTokenBudgetService llmTokenBudgetService;
    private final Node3SamplingBatchRunner samplingBatchRunner;
    private final Node3UsageCostEstimator usageCostEstimator = new Node3UsageCostEstimator();
    private final Node3RealtimeSearchAnalyzer realtimeSearchAnalyzer = new Node3RealtimeSearchAnalyzer();
    private final Node3FinalResponseValidator finalResponseValidator = new Node3FinalResponseValidator();

    @Autowired
    public V42Node3SamplingGatewayService(@Qualifier("v42DoubaoAdapter") ModelAdapter modelAdapter,
                                          @Qualifier("node3SamplingExecutor") @Nullable ExecutorService probeCallExecutor,
                                          @Qualifier("node3SamplingCallExecutor") @Nullable ExecutorService probeModelCallExecutor,
                                          @Value("${app.engine.sampling.max-concurrency:8}") int maxConcurrency,
                                          @Value("${app.engine.sampling.max-attempts:1}") int maxAttempts,
                                          @Value("${app.engine.sampling.retry-backoff-millis:800}") String retryBackoffMillisSpec,
                                          @Value("${app.engine.sampling.task-timeout-seconds:180}") long taskTimeoutSeconds,
                                          @Value("${app.engine.sampling.realtime-search.aimd-min-concurrency:40}") int realtimeAimdMinConcurrency,
                                          @Value("${app.engine.sampling.realtime-search.aimd-max-concurrency:120}") int realtimeAimdMaxConcurrency,
                                          @Value("${app.engine.sampling.realtime-search.aimd-increase-step:8}") int realtimeAimdIncreaseStep,
                                          @Value("${app.engine.sampling.realtime-search.aimd-adjust-interval:8}") int realtimeAimdAdjustInterval,
                                          @Value("${app.engine.sampling.realtime-search.aimd-congestion-ratio:0.25}") double realtimeAimdCongestionRatio,
                                          @Value("${app.engine.sampling.realtime-search.aimd-high-latency-ms:12000}") long realtimeAimdHighLatencyMillis,
                                          @Value("${app.security.secret-assets.expose-routing-debug:false}") boolean exposeRoutingDebug,
                                          @Value("${app.engine.sampling.realtime-search.task-timeout-seconds:150}") long realtimeTaskTimeoutSeconds,
                                          ObjectProvider<LlmTokenBudgetService> llmTokenBudgetServiceProvider) {
        this(
                modelAdapter,
                Thread::sleep,
                Objects.requireNonNull(probeCallExecutor, "node3SamplingExecutor 不能为空"),
                Objects.requireNonNull(probeModelCallExecutor, "node3SamplingCallExecutor 不能为空"),
                false,
                false,
                Node3DispatchSupport.resolveMaxConcurrency(maxConcurrency),
                Node3DispatchSupport.resolveMaxAttempts(maxAttempts),
                Node3DispatchSupport.resolveRetryBackoffMillis(retryBackoffMillisSpec, DEFAULT_RETRY_BACKOFF_MILLIS),
                Node3DispatchSupport.resolveConfiguredTaskTimeoutSeconds(taskTimeoutSeconds),
                realtimeAimdMinConcurrency,
                realtimeAimdMaxConcurrency,
                realtimeAimdIncreaseStep,
                realtimeAimdAdjustInterval,
                realtimeAimdCongestionRatio,
                realtimeAimdHighLatencyMillis,
                exposeRoutingDebug,
                realtimeTaskTimeoutSeconds,
                llmTokenBudgetServiceProvider.getIfAvailable()
        );
    }

    V42Node3SamplingGatewayService(ModelAdapter modelAdapter, RetryBackoffSleeper retryBackoffSleeper) {
        this(
                modelAdapter,
                retryBackoffSleeper,
                newOwnedProbeCallExecutor(),
                newOwnedProbeCallExecutor(),
                true,
                true,
                1,
                DEFAULT_MAX_ATTEMPTS,
                Node3DispatchSupport.parseRetryBackoff(DEFAULT_RETRY_BACKOFF_MILLIS),
                DEFAULT_TASK_TIMEOUT_SECONDS,
                DEFAULT_AIMD_MIN_CONCURRENCY,
                DEFAULT_AIMD_MAX_CONCURRENCY,
                DEFAULT_AIMD_INCREASE_STEP,
                DEFAULT_AIMD_ADJUST_INTERVAL,
                DEFAULT_AIMD_CONGESTION_RATIO,
                DEFAULT_AIMD_HIGH_LATENCY_MS,
                false,
                DEFAULT_REALTIME_TASK_TIMEOUT_SECONDS,
                null
        );
    }

    V42Node3SamplingGatewayService(ModelAdapter modelAdapter,
                                   RetryBackoffSleeper retryBackoffSleeper,
                                   int maxAttempts,
                                   long[] retryBackoffMillis,
                                   long taskTimeoutSeconds) {
        this(
                modelAdapter,
                retryBackoffSleeper,
                1,
                maxAttempts,
                retryBackoffMillis,
                taskTimeoutSeconds
        );
    }

    V42Node3SamplingGatewayService(ModelAdapter modelAdapter,
                                   RetryBackoffSleeper retryBackoffSleeper,
                                   int maxConcurrency,
                                   int maxAttempts,
                                   long[] retryBackoffMillis,
                                   long taskTimeoutSeconds) {
        this(
                modelAdapter,
                retryBackoffSleeper,
                newOwnedProbeCallExecutor(),
                newOwnedProbeCallExecutor(),
                true,
                true,
                Node3DispatchSupport.resolveMaxConcurrency(maxConcurrency),
                Node3DispatchSupport.resolveMaxAttempts(maxAttempts),
                retryBackoffMillis,
                taskTimeoutSeconds,
                DEFAULT_AIMD_MIN_CONCURRENCY,
                DEFAULT_AIMD_MAX_CONCURRENCY,
                DEFAULT_AIMD_INCREASE_STEP,
                DEFAULT_AIMD_ADJUST_INTERVAL,
                DEFAULT_AIMD_CONGESTION_RATIO,
                DEFAULT_AIMD_HIGH_LATENCY_MS,
                false,
                DEFAULT_REALTIME_TASK_TIMEOUT_SECONDS,
                null
        );
    }

    private V42Node3SamplingGatewayService(ModelAdapter modelAdapter,
                                           RetryBackoffSleeper retryBackoffSleeper,
                                           ExecutorService probeCallExecutor,
                                           ExecutorService probeModelCallExecutor,
                                           boolean ownsProbeCallExecutor,
                                           boolean ownsProbeModelCallExecutor,
                                           int maxConcurrency,
                                           int maxAttempts,
                                           long[] retryBackoffMillis,
                                           long taskTimeoutSeconds,
                                           int realtimeAimdMinConcurrency,
                                           int realtimeAimdMaxConcurrency,
                                           int realtimeAimdIncreaseStep,
                                           int realtimeAimdAdjustInterval,
                                           double realtimeAimdCongestionRatio,
                                           long realtimeAimdHighLatencyMillis,
                                           boolean exposeRoutingDebug,
                                           long realtimeTaskTimeoutSeconds,
                                           LlmTokenBudgetService llmTokenBudgetService) {
        this.modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter 不能为空");
        this.retryBackoffSleeper = Objects.requireNonNull(retryBackoffSleeper, "retryBackoffSleeper 不能为空");
        this.probeCallExecutor = Objects.requireNonNull(probeCallExecutor, "probeCallExecutor 不能为空");
        this.probeModelCallExecutor = Objects.requireNonNull(probeModelCallExecutor, "probeModelCallExecutor 不能为空");
        this.ownsProbeCallExecutor = ownsProbeCallExecutor;
        this.ownsProbeModelCallExecutor = ownsProbeModelCallExecutor;
        this.maxConcurrency = Node3DispatchSupport.resolveMaxConcurrency(maxConcurrency);
        this.maxAttempts = Node3DispatchSupport.resolveMaxAttempts(maxAttempts);
        this.retryBackoffMillis = Arrays.copyOf(
                Objects.requireNonNull(retryBackoffMillis, "retryBackoffMillis 不能为空"),
                retryBackoffMillis.length
        );
        this.taskTimeoutSeconds = taskTimeoutSeconds;
        this.realtimeTaskTimeoutSeconds = Node3DispatchSupport.resolveConfiguredTaskTimeoutSeconds(realtimeTaskTimeoutSeconds);
        this.realtimeAimdMinConcurrency = Math.max(1, realtimeAimdMinConcurrency);
        this.realtimeAimdMaxConcurrency = Math.max(this.realtimeAimdMinConcurrency, realtimeAimdMaxConcurrency);
        this.realtimeAimdIncreaseStep = Math.max(1, realtimeAimdIncreaseStep);
        this.realtimeAimdAdjustInterval = Math.max(1, realtimeAimdAdjustInterval);
        this.realtimeAimdCongestionRatio = Node3DispatchSupport.normalizeCongestionRatio(
                realtimeAimdCongestionRatio,
                DEFAULT_AIMD_CONGESTION_RATIO
        );
        this.realtimeAimdHighLatencyMillis = Math.max(2_000L, realtimeAimdHighLatencyMillis);
        this.exposeRoutingDebug = exposeRoutingDebug;
        this.llmTokenBudgetService = llmTokenBudgetService;
        this.samplingBatchRunner = new Node3SamplingBatchRunner(
                this.probeCallExecutor,
                this.maxConcurrency,
                this.realtimeAimdMinConcurrency,
                this.realtimeAimdMaxConcurrency,
                this.realtimeAimdIncreaseStep,
                this.realtimeAimdAdjustInterval,
                this.realtimeAimdCongestionRatio,
                this.realtimeAimdHighLatencyMillis,
                this::dispatchOne,
                this.exposeRoutingDebug
        );
    }

    private static ExecutorService newOwnedProbeCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdownProbeCallExecutor() {
        if (ownsProbeCallExecutor) {
            probeCallExecutor.shutdownNow();
        }
        if (ownsProbeModelCallExecutor) {
            probeModelCallExecutor.shutdownNow();
        }
    }

    @Value("${app.engine.sampling.realtime-search.cost.input-yuan-per-1k-token:0.0008}")
    void setInputCostPer1k(String value) {
        this.usageCostEstimator.setInputCostPer1k(value);
    }

    @Value("${app.engine.sampling.realtime-search.cost.output-yuan-per-1k-token:0.008}")
    void setOutputCostPer1k(String value) {
        this.usageCostEstimator.setOutputCostPer1k(value);
    }

    @Value("${app.engine.sampling.realtime-search.cost.output-short-yuan-per-1k-token:0.002}")
    void setShortOutputCostPer1k(String value) {
        this.usageCostEstimator.setShortOutputCostPer1k(value);
    }

    @Value("${app.engine.sampling.realtime-search.cost.output-short-threshold:200}")
    void setShortOutputThreshold(int value) {
        this.usageCostEstimator.setShortOutputThreshold(value);
    }

    @Value("${app.engine.sampling.realtime-search.cost.web-search-yuan-per-call:0.004}")
    void setWebSearchCostPerCall(String value) {
        this.usageCostEstimator.setWebSearchCostPerCall(value);
    }

    public Node3SamplingResult dispatch(Node3SamplingRequest request) {
        return dispatch(request, null);
    }

    public Node3SamplingResult dispatch(Node3SamplingRequest request,
                                        Consumer<SamplingProgress> progressCallback) {
        return dispatch(request, progressCallback, DispatchEventListener.NO_OP);
    }

    public Node3SamplingResult dispatch(Node3SamplingRequest request,
                                        Consumer<SamplingProgress> progressCallback,
                                        DispatchEventListener dispatchEventListener) {
        Objects.requireNonNull(request, "Node3SamplingRequest 不能为空");
        DispatchEventListener eventListener = dispatchEventListener == null ? DispatchEventListener.NO_OP : dispatchEventListener;
        RealtimeSearchPolicy policy = Objects.requireNonNull(
                request.realtimeSearchPolicy(),
                "Node3SamplingRequest.realtimeSearchPolicy 不能为空"
        );
        int total = request.probes().size();
        if (total == 0) {
            safeProgressCallback(progressCallback, new SamplingProgress(0, 0, 0, 0));
            return new Node3SamplingResult(
                    List.of(),
                    RealtimeSearchSummary.disabled(policy.provider()),
                    Node3UsageCostSummary.empty(),
                    request.targetEnginePolicy().provider()
            );
        }

        List<Node3ProbeOutput> outputs = dispatchInitialBatch(
                request,
                progressCallback,
                policy,
                request.targetEnginePolicy(),
                eventListener
        );
        Node3SearchSnapshot initialSnapshot = realtimeSearchAnalyzer.computeSnapshot(outputs, policy);
        emitRoundSummaryEvent(
                request,
                policy,
                0,
                false,
                total,
                countSearchSatisfied(outputs),
                initialSnapshot,
                outputs,
                eventListener,
                0
        );
        int backfillRoundsUsed = 0;
        int previousUnsatisfiedCount = initialSnapshot.unsatisfiedProbeIds().size();
        if (policy.enabled() && previousUnsatisfiedCount > 0) {
            for (int round = 1; round <= policy.maxBackfillRounds(); round++) {
                List<Integer> unsatisfiedIndexes = realtimeSearchAnalyzer.unsatisfiedIndexes(outputs);
                if (unsatisfiedIndexes.isEmpty()) {
                    break;
                }
                Node3SamplingBatchRunner.BatchDispatchResult rerunBatch =
                        rerunByIndexes(request, outputs, unsatisfiedIndexes, policy, round, eventListener);
                backfillRoundsUsed = round;
                Node3SearchSnapshot updated = realtimeSearchAnalyzer.computeSnapshot(outputs, policy);
                emitRoundSummaryEvent(
                        request,
                        policy,
                        round,
                        true,
                        unsatisfiedIndexes.size(),
                        countSearchSatisfied(rerunBatch.outputs().stream().map(Node3SamplingBatchRunner.IndexedProbeOutput::output).toList()),
                        updated,
                        outputs,
                        eventListener,
                        rerunBatch.lastWindowSize()
                );
                int updatedUnsatisfiedCount = updated.unsatisfiedProbeIds().size();
                if (updatedUnsatisfiedCount == 0) {
                    break;
                }
                // 首轮快扫命中整体成功率阈值后，仍需对未满足 probe 做尾补；
                // 但若一整轮补救没有带来任何恢复，则及时止损，避免空转烧调用。
                if (updatedUnsatisfiedCount >= previousUnsatisfiedCount) {
                    break;
                }
                previousUnsatisfiedCount = updatedUnsatisfiedCount;
            }
        }

        Node3SearchSnapshot finalSnapshot = realtimeSearchAnalyzer.computeSnapshot(outputs, policy);
        int recoveredCount = Math.max(0, initialSnapshot.unsatisfiedProbeIds().size() - finalSnapshot.unsatisfiedProbeIds().size());
        RealtimeSearchSummary summary = new RealtimeSearchSummary(
                policy.enabled(),
                policy.provider(),
                finalSnapshot.requestedProbeCount(),
                finalSnapshot.satisfiedProbeCount(),
                finalSnapshot.evidenceSuccessRate(),
                finalSnapshot.fallbackProbeCount(),
                backfillRoundsUsed,
                recoveredCount,
                finalSnapshot.qualityGatePassed(),
                finalSnapshot.unsatisfiedProbeIds()
        );
        Node3UsageCostSummary usageCostSummary = computeUsageCostSummary(outputs, request.targetEnginePolicy().provider());
        return new Node3SamplingResult(List.copyOf(outputs), summary, usageCostSummary, request.targetEnginePolicy().provider());
    }

    private List<Node3ProbeOutput> dispatchInitialBatch(Node3SamplingRequest request,
                                                        Consumer<SamplingProgress> progressCallback,
                                                        RealtimeSearchPolicy policy,
                                                        Node3TargetEnginePolicy targetEnginePolicy,
                                                        DispatchEventListener eventListener) {
        int total = request.probes().size();
        List<Node3SamplingBatchRunner.IndexedProbeInput> indexedProbes = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            indexedProbes.add(new Node3SamplingBatchRunner.IndexedProbeInput(index, request.probes().get(index)));
        }
        Node3SamplingBatchRunner.BatchDispatchResult batch = samplingBatchRunner.runBatch(
                request.diagnosisId(),
                indexedProbes,
                policy,
                targetEnginePolicy,
                0,
                resolveBudgetAwareConcurrency(targetEnginePolicy, policy, total),
                progressCallback,
                eventListener
        );
        List<Node3ProbeOutput> outputs = new ArrayList<>(Collections.nCopies(total, null));
        for (Node3SamplingBatchRunner.IndexedProbeOutput indexedOutput : batch.outputs()) {
            outputs.set(indexedOutput.index(), indexedOutput.output());
        }
        return outputs;
    }

    private Node3SamplingBatchRunner.BatchDispatchResult rerunByIndexes(Node3SamplingRequest request,
                                                                        List<Node3ProbeOutput> outputs,
                                                                        List<Integer> indexes,
                                                                        RealtimeSearchPolicy policy,
                                                                        int round,
                                                                        DispatchEventListener eventListener) {
        List<Node3SamplingBatchRunner.IndexedProbeInput> indexedProbes = new ArrayList<>(indexes.size());
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= request.probes().size()) {
                continue;
            }
            indexedProbes.add(new Node3SamplingBatchRunner.IndexedProbeInput(index, request.probes().get(index)));
        }
        Node3SamplingBatchRunner.BatchDispatchResult batch = samplingBatchRunner.runBatch(
                request.diagnosisId(),
                indexedProbes,
                policy,
                request.targetEnginePolicy(),
                round,
                resolveBudgetAwareConcurrency(request.targetEnginePolicy(), policy, indexedProbes.size()),
                null,
                eventListener
        );
        for (Node3SamplingBatchRunner.IndexedProbeOutput indexedOutput : batch.outputs()) {
            outputs.set(indexedOutput.index(), indexedOutput.output());
        }
        return batch;
    }

    private Node3ProbeOutput dispatchOne(String diagnosisId,
                                         Node3ProbeInput probe,
                                         RealtimeSearchPolicy policy,
                                         Node3TargetEnginePolicy targetEnginePolicy,
                                         int round) {
        SingleDispatchResult dispatchResult = invokeProbeWithRetries(
                diagnosisId,
                probe,
                policy,
                targetEnginePolicy
        );
        RawResponse latestResponse = dispatchResult.response();
        String latestFailureReason = dispatchResult.failureReason();
        long totalLatencyMs = latestResponse == null ? 0L : latestResponse.latencyMs();

        if (!policy.enabled()) {
            if (latestFailureReason == null) {
                return Node3ProbeOutput.success(probe, latestResponse, round, dispatchResult.attemptNo());
            }
            String failure = latestFailureReason == null ? "UNKNOWN_ERROR" : latestFailureReason;
            RawResponse safe = latestResponse == null ? RawResponse.empty(totalLatencyMs) : latestResponse;
            return Node3ProbeOutput.failed(probe, withTotalLatency(safe, totalLatencyMs), round, dispatchResult.attemptNo(), failure);
        }

        if (latestFailureReason == null && latestResponse.searchSatisfied()) {
            return Node3ProbeOutput.success(probe, latestResponse, round, dispatchResult.attemptNo());
        }
        RawResponse fallback = withSearchFallback(
                latestResponse,
                totalLatencyMs,
                probe,
                dispatchResult.searchRequested()
        );
        String failure = StringUtils.hasText(latestFailureReason) ? latestFailureReason : SEARCH_EVIDENCE_FAILURE;
        return Node3ProbeOutput.failed(probe, fallback, round, dispatchResult.attemptNo(), failure);
    }

    private RawResponse withSearchFallback(RawResponse response, long totalLatencyMs, Node3ProbeInput probe) {
        return withSearchFallback(response, totalLatencyMs, probe, false);
    }

    private RawResponse withSearchFallback(RawResponse response,
                                           long totalLatencyMs,
                                           Node3ProbeInput probe,
                                           boolean searchRequested) {
        RawResponse safe = response == null ? RawResponse.empty(totalLatencyMs) : withTotalLatency(response, totalLatencyMs);
        return new RawResponse(
                safe.fullText(),
                safe.thinkContent(),
                safe.citations(),
                safe.promptTokenCount(),
                safe.completionTokenCount(),
                safe.totalTokenCount(),
                safe.latencyMs(),
                safe.earlyStoppedFlag(),
                safe.searchRequested() || searchRequested,
                false,
                true,
                addProbeIdIfMissing(safe.searchEvidence(), probe.probeId()),
                safe.searchCallCount(),
                safe.searchQueries(),
                safe.modelId(),
                safe.routingIntent(),
                safe.routingCandidates()
        );
    }

    private RawResponse withTotalLatency(RawResponse response, long totalLatencyMs) {
        return new RawResponse(
                response.fullText(),
                response.thinkContent(),
                response.citations(),
                response.promptTokenCount(),
                response.completionTokenCount(),
                response.totalTokenCount(),
                Math.max(response.latencyMs(), totalLatencyMs),
                response.earlyStoppedFlag(),
                response.searchRequested(),
                response.searchSatisfied(),
                response.searchFallbackFlag(),
                response.searchEvidence(),
                response.searchCallCount(),
                response.searchQueries(),
                response.modelId(),
                response.routingIntent(),
                response.routingCandidates()
        );
    }

    private List<com.geoprobe.dto.engine.v42.SearchEvidence> addProbeIdIfMissing(List<com.geoprobe.dto.engine.v42.SearchEvidence> evidence,
                                                                                   String probeId) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<com.geoprobe.dto.engine.v42.SearchEvidence> withProbeId = new ArrayList<>(evidence.size());
        for (com.geoprobe.dto.engine.v42.SearchEvidence item : evidence) {
            if (item == null) {
                continue;
            }
            withProbeId.add(new com.geoprobe.dto.engine.v42.SearchEvidence(
                    item.sourceType(),
                    item.url(),
                    item.title(),
                    item.snippet(),
                    item.publishTime(),
                    item.retrievedAt(),
                    item.provider(),
                    StringUtils.hasText(item.probeId()) ? item.probeId() : probeId,
                    item.query()
            ));
        }
        return List.copyOf(withProbeId);
    }

    private SingleDispatchResult invokeProbeWithRetries(String diagnosisId,
                                                        Node3ProbeInput probe,
                                                        RealtimeSearchPolicy policy,
                                                        Node3TargetEnginePolicy targetEnginePolicy) {
        RuntimeException lastException = null;
        RawResponse lastFailureResponse = null;
        long failedLatencyMs = 0L;
        boolean searchRequested = false;
        int effectiveMaxAttempts = policy.enabled()
                ? Math.max(1, policy.maxEvidenceRetries() + 1)
                : maxAttempts;
        ModelAdapter.DispatchControl dispatchControl = ModelAdapter.DispatchControl.withTargetEngine(
                policy.enabled(),
                policy.provider(),
                targetEnginePolicy.provider(),
                targetEnginePolicy.modelId(),
                targetEnginePolicy.disableReasoning()
        );

        for (int attempt = 1; attempt <= effectiveMaxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            searchRequested = searchRequested || dispatchControl.realtimeSearchEnabled();
            try {
                RawResponse rawResponse = invokeProbe(diagnosisId, probe, dispatchControl);
                RawResponse safeResponse = rawResponse == null
                        ? RawResponse.empty(Node3DispatchSupport.elapsedMillis(startedAt))
                        : rawResponse;
                safeResponse = finalResponseValidator.validate(
                        Node3DispatchSupport.resolveProviderLabel(dispatchControl, PROVIDER_DOUBAO),
                        safeResponse
                );
                return new SingleDispatchResult(safeResponse, null, searchRequested, attempt);
            } catch (RuntimeException ex) {
                failedLatencyMs += Node3DispatchSupport.elapsedMillis(startedAt);
                lastException = ex;
                if (ex instanceof ProviderDispatchException providerDispatchException
                        && providerDispatchException.rawResponse() != null) {
                    lastFailureResponse = providerDispatchException.rawResponse();
                }
                if (Node3DispatchSupport.isSystemicProviderFailure(ex)) {
                    log.error("V4.2 Node 3 systemic provider failure detected, aborting batch. probeId={}, reasonCode={}, detail={}",
                            probe.probeId(),
                            Node3DispatchSupport.resolveFailureReasonCode(ex),
                            Node3DispatchSupport.describeFailure(ex));
                    throw ex;
                }
                if (Node3DispatchSupport.isTokenBudgetFailure(ex)) {
                    break;
                }
                if (attempt == effectiveMaxAttempts) {
                    break;
                }
                long backoffMillis = resolveRetryBackoffMillis(attempt);
                log.warn("V4.2 Node 3 dispatch failed, retrying. probeId={}, attempt={}, backoffMs={}, reasonCode={}, detail={}",
                        probe.probeId(),
                        attempt,
                        backoffMillis,
                        Node3DispatchSupport.resolveFailureReasonCode(ex),
                        Node3DispatchSupport.describeFailure(ex));
                if (!sleepBeforeRetry(backoffMillis, probe.probeId())) {
                    lastException = new IllegalStateException("Node 3 retry backoff interrupted", ex);
                    break;
                }
            }
        }

        String failureReason = Node3DispatchSupport.resolveFailureReasonCode(lastException);
        log.warn("V4.2 Node 3 probe marked failed. probeId={}, reasonCode={}, detail={}",
                probe.probeId(),
                failureReason,
                Node3DispatchSupport.describeFailure(lastException));
        RawResponse failedResponse = lastFailureResponse == null
                ? RawResponse.empty(failedLatencyMs)
                : withTotalLatency(lastFailureResponse, failedLatencyMs);
        return new SingleDispatchResult(failedResponse, failureReason, searchRequested, effectiveMaxAttempts);
    }

    private int resolveBudgetAwareConcurrency(Node3TargetEnginePolicy targetEnginePolicy,
                                              RealtimeSearchPolicy policy,
                                              int batchSize) {
        int desiredConcurrency = Math.max(
                1,
                Math.min(
                        batchSize,
                        policy != null && policy.enabled() ? realtimeAimdMaxConcurrency : maxConcurrency
                )
        );
        if (llmTokenBudgetService == null || targetEnginePolicy == null) {
            return desiredConcurrency;
        }
        String provider = StringUtils.hasText(targetEnginePolicy.provider())
                ? targetEnginePolicy.provider().trim()
                : PROVIDER_DOUBAO;
        String modelId = StringUtils.hasText(targetEnginePolicy.modelId())
                ? targetEnginePolicy.modelId().trim()
                : null;
        if (!StringUtils.hasText(modelId)) {
            return desiredConcurrency;
        }
        LlmTokenBudgetService.ConcurrencyRecommendation recommendation =
                llmTokenBudgetService.recommendConcurrency(provider, modelId, desiredConcurrency);
        if (recommendation.limited()) {
            log.info("V4.2 Node3 sampling concurrency capped by token budget. provider={}, model={}, desired={}, effective={}, remainingTokens={}",
                    provider,
                    modelId,
                    recommendation.desiredConcurrency(),
                    recommendation.effectiveConcurrency(),
                    recommendation.remainingTokens());
        }
        return recommendation.effectiveConcurrency();
    }

    private RawResponse invokeProbe(String diagnosisId, Node3ProbeInput probe, ModelAdapter.DispatchControl dispatchControl) {
        long timeoutSeconds = resolveTaskTimeoutSeconds(dispatchControl);
        if (timeoutSeconds <= 0L) {
            return sendProbeWithContext(diagnosisId, probe, dispatchControl);
        }
        FutureTask<RawResponse> task = new FutureTask<>(
                () -> sendProbeWithContext(diagnosisId, probe, dispatchControl)
        );
        try {
            probeModelCallExecutor.execute(task);
        } catch (RejectedExecutionException ex) {
            throw new IllegalStateException("Node 3 模型调用执行器已过载", ex);
        }
        try {
            return task.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            task.cancel(true);
            throw new IllegalStateException("采样任务超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new IllegalStateException("采样线程被中断", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(Node3DispatchSupport.extractThrowableMessage(cause), cause);
        } catch (CancellationException ex) {
            throw new IllegalStateException("采样任务被取消", ex);
        }
    }

    private RawResponse sendProbeWithContext(String diagnosisId,
                                             Node3ProbeInput probe,
                                             ModelAdapter.DispatchControl dispatchControl) {
        try {
            if (StringUtils.hasText(diagnosisId)) {
                LlmCallContext.set(new LlmCallContext(
                        diagnosisId,
                        MODULE_SAMPLING,
                        Node3DispatchSupport.resolveProviderLabel(dispatchControl, PROVIDER_DOUBAO)
                ));
            }
            return modelAdapter.sendProbe(probe, dispatchControl);
        } finally {
            LlmCallContext.clear();
        }
    }

    private boolean sleepBeforeRetry(long backoffMillis, String probeId) {
        if (backoffMillis <= 0L) {
            return true;
        }
        try {
            retryBackoffSleeper.sleep(backoffMillis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("V4.2 Node 3 retry backoff interrupted. probeId={}", probeId);
            return false;
        }
    }

    private long resolveRetryBackoffMillis(int attempt) {
        if (retryBackoffMillis.length == 0) {
            return 0L;
        }
        int index = Math.max(0, Math.min(attempt - 1, retryBackoffMillis.length - 1));
        return Math.max(0L, retryBackoffMillis[index]);
    }

    private void emitRoundSummaryEvent(Node3SamplingRequest request,
                                       RealtimeSearchPolicy policy,
                                       int round,
                                       boolean backfill,
                                       int roundRequested,
                                       int roundSucceeded,
                                       Node3SearchSnapshot snapshot,
                                       List<Node3ProbeOutput> outputs,
                                       DispatchEventListener eventListener,
                                       int activeWindow) {
        safeDispatchEvent(
                eventListener,
                Node3DispatchEventFactory.roundSummaryEvent(
                        request.diagnosisId(),
                        round,
                        backfill,
                        roundRequested,
                        roundSucceeded,
                        snapshot,
                        outputs,
                        activeWindow,
                        policy
                )
        );
    }

    private void safeDispatchEvent(DispatchEventListener dispatchEventListener, DispatchEvent event) {
        if (dispatchEventListener == null || event == null) {
            return;
        }
        try {
            dispatchEventListener.onEvent(event);
        } catch (RuntimeException ex) {
            log.warn("Node 3 dispatch event callback failed, eventType={}, reason={}", event.eventType(), ex.getMessage());
        }
    }

    private void safeProgressCallback(Consumer<SamplingProgress> progressCallback, SamplingProgress progress) {
        if (progressCallback == null || progress == null) {
            return;
        }
        try {
            progressCallback.accept(progress);
        } catch (RuntimeException ex) {
            log.warn("Node 3 sampling progress callback failed, message={}", ex.getMessage());
        }
    }

    private long resolveTaskTimeoutSeconds(ModelAdapter.DispatchControl dispatchControl) {
        return Node3DispatchSupport.resolveEffectiveTaskTimeoutSeconds(
                dispatchControl,
                taskTimeoutSeconds,
                realtimeTaskTimeoutSeconds
        );
    }

    private Node3UsageCostSummary computeUsageCostSummary(List<Node3ProbeOutput> outputs, String providerCode) {
        return usageCostEstimator.computeSummary(outputs, providerCode);
    }

    private int countSearchSatisfied(List<Node3ProbeOutput> outputs) {
        return Math.toIntExact(outputs.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.rawResponse().searchRequested() && item.rawResponse().searchSatisfied())
                .count());
    }

    @FunctionalInterface
    interface RetryBackoffSleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    public interface DispatchEventListener {
        DispatchEventListener NO_OP = event -> {
        };

        void onEvent(DispatchEvent event);
    }

    private record SingleDispatchResult(RawResponse response,
                                        String failureReason,
                                        boolean searchRequested,
                                        int attemptNo) {
    }

    public record DispatchEvent(
            String eventType,
            boolean provisional,
            Map<String, Object> payload
    ) {
        public DispatchEvent {
            eventType = StringUtils.hasText(eventType) ? eventType.trim() : "unknown";
            payload = payload == null ? Map.of() : Collections.unmodifiableMap(payload);
        }
    }

}

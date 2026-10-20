package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.ProbeBlueprint;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
final class Node2BatchRenderExecutor {

    interface RenderStrategy {

        RenderedProbe render(String diagnosisId,
                             CategoryVariablePool pool,
                             List<String> brandAliases,
                             ProbeBlueprint blueprint,
                             Set<String> usedCanonicalPrompts);

        RenderedProbe buildConcurrentFailureFallback(String diagnosisId,
                                                     CategoryVariablePool pool,
                                                     List<String> brandAliases,
                                                     ProbeBlueprint blueprint,
                                                     Set<String> usedCanonicalPrompts,
                                                     RuntimeException failure);
    }

    private final Executor renderExecutor;
    private final boolean concurrentEnabled;
    private final int concurrentParallelism;
    private final long concurrentTaskTimeoutMillis;

    Node2BatchRenderExecutor(Executor renderExecutor,
                             boolean concurrentEnabled,
                             int concurrentParallelism,
                             long concurrentTaskTimeoutMillis) {
        this.renderExecutor = renderExecutor == null ? Runnable::run : renderExecutor;
        this.concurrentEnabled = concurrentEnabled;
        this.concurrentParallelism = Math.max(1, concurrentParallelism);
        this.concurrentTaskTimeoutMillis = Math.max(1000L, concurrentTaskTimeoutMillis);
    }

    ProbeRenderingService.Node2BatchRenderResult renderAll(String diagnosisId,
                                                           CategoryVariablePool pool,
                                                           List<String> brandAliases,
                                                           List<ProbeBlueprint> blueprints,
                                                           int concurrencyCap,
                                                           RenderStrategy strategy) {
        if (blueprints == null || blueprints.isEmpty()) {
            return new ProbeRenderingService.Node2BatchRenderResult(
                    List.of(),
                    new ProbeRenderingService.Node2TimingMetrics(0L, 0L, 0L, 0L, 0L, 0L)
            );
        }
        long batchStartNanos = System.nanoTime();
        int effectiveParallelism = resolveEffectiveParallelism(blueprints, concurrencyCap);
        List<TimedRenderedProbe> timedRenderedProbes = shouldRenderConcurrently(blueprints, effectiveParallelism)
                ? renderAllConcurrently(diagnosisId, pool, brandAliases, blueprints, effectiveParallelism, strategy)
                : renderAllSequentially(diagnosisId, pool, brandAliases, blueprints, strategy);
        List<RenderedProbe> renderedProbes = timedRenderedProbes.stream()
                .sorted(Comparator.comparingInt(TimedRenderedProbe::index))
                .map(TimedRenderedProbe::probe)
                .toList();
        List<Long> perProbeLatencyMillis = timedRenderedProbes.stream()
                .map(TimedRenderedProbe::latencyMillis)
                .sorted()
                .toList();
        long totalMillis = elapsedMillis(batchStartNanos);
        return new ProbeRenderingService.Node2BatchRenderResult(
                List.copyOf(renderedProbes),
                buildTimingMetrics(totalMillis, perProbeLatencyMillis)
        );
    }

    private int resolveEffectiveParallelism(List<ProbeBlueprint> blueprints, int concurrencyCap) {
        int batchSize = blueprints == null ? 0 : blueprints.size();
        int effectiveCap = Math.max(1, Math.min(concurrentParallelism, concurrencyCap));
        return Math.max(1, Math.min(batchSize, effectiveCap));
    }

    private boolean shouldRenderConcurrently(List<ProbeBlueprint> blueprints, int effectiveParallelism) {
        return concurrentEnabled
                && effectiveParallelism > 1
                && blueprints != null
                && blueprints.size() > 1;
    }

    private List<TimedRenderedProbe> renderAllSequentially(String diagnosisId,
                                                           CategoryVariablePool pool,
                                                           List<String> brandAliases,
                                                           List<ProbeBlueprint> blueprints,
                                                           RenderStrategy strategy) {
        List<TimedRenderedProbe> renderedProbes = new ArrayList<>(blueprints.size());
        LinkedHashMap<BucketType, Set<String>> usedCanonicalPrompts = new LinkedHashMap<>();
        for (int index = 0; index < blueprints.size(); index++) {
            ProbeBlueprint blueprint = blueprints.get(index);
            Set<String> bucketPrompts = usedCanonicalPrompts.computeIfAbsent(
                    blueprint.bucketType(),
                    ignored -> new LinkedHashSet<>()
            );
            renderedProbes.add(renderOneWithTiming(index, diagnosisId, pool, brandAliases, blueprint, bucketPrompts, strategy));
        }
        return List.copyOf(renderedProbes);
    }

    private List<TimedRenderedProbe> renderAllConcurrently(String diagnosisId,
                                                           CategoryVariablePool pool,
                                                           List<String> brandAliases,
                                                           List<ProbeBlueprint> blueprints,
                                                           int parallelism,
                                                           RenderStrategy strategy) {
        Map<BucketType, Set<String>> usedCanonicalPrompts = new ConcurrentHashMap<>();
        List<ConcurrentRenderTask> tasks = buildConcurrentTasks(blueprints, usedCanonicalPrompts);
        Map<CompletableFuture<TimedRenderedProbe>, ConcurrentRenderTask> inFlight = new LinkedHashMap<>();
        List<TimedRenderedProbe> rendered = new ArrayList<>(blueprints.size());
        int nextTaskIndex = 0;

        while (nextTaskIndex < tasks.size() && inFlight.size() < parallelism) {
            ConcurrentRenderTask task = tasks.get(nextTaskIndex++);
            inFlight.put(submitConcurrentRender(diagnosisId, pool, brandAliases, task, strategy), task);
        }

        while (!inFlight.isEmpty()) {
            CompletableFuture<?> firstDone = CompletableFuture.anyOf(inFlight.keySet().toArray(CompletableFuture[]::new));
            try {
                firstDone.join();
            } catch (CompletionException ignored) {
                // Individual task failures are converted when draining completed futures below.
            }
            int completedCount = 0;
            Iterator<Map.Entry<CompletableFuture<TimedRenderedProbe>, ConcurrentRenderTask>> iterator = inFlight.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<CompletableFuture<TimedRenderedProbe>, ConcurrentRenderTask> entry = iterator.next();
                CompletableFuture<TimedRenderedProbe> future = entry.getKey();
                if (!future.isDone()) {
                    continue;
                }
                rendered.add(resolveConcurrentRenderResult(diagnosisId, pool, brandAliases, entry.getValue(), future, strategy));
                iterator.remove();
                completedCount++;
            }
            while (completedCount > 0 && nextTaskIndex < tasks.size()) {
                ConcurrentRenderTask nextTask = tasks.get(nextTaskIndex++);
                inFlight.put(submitConcurrentRender(diagnosisId, pool, brandAliases, nextTask, strategy), nextTask);
                completedCount--;
            }
        }
        return List.copyOf(rendered);
    }

    private List<ConcurrentRenderTask> buildConcurrentTasks(List<ProbeBlueprint> blueprints,
                                                            Map<BucketType, Set<String>> usedCanonicalPrompts) {
        List<ConcurrentRenderTask> tasks = new ArrayList<>(blueprints.size());
        for (int index = 0; index < blueprints.size(); index++) {
            ProbeBlueprint blueprint = blueprints.get(index);
            Set<String> bucketPrompts = usedCanonicalPrompts.computeIfAbsent(
                    blueprint.bucketType(),
                    ignored -> ConcurrentHashMap.newKeySet()
            );
            tasks.add(new ConcurrentRenderTask(index, blueprint, bucketPrompts));
        }
        return List.copyOf(tasks);
    }

    private CompletableFuture<TimedRenderedProbe> submitConcurrentRender(String diagnosisId,
                                                                         CategoryVariablePool pool,
                                                                         List<String> brandAliases,
                                                                         ConcurrentRenderTask task,
                                                                         RenderStrategy strategy) {
        try {
            return CompletableFuture
                    .supplyAsync(
                            () -> renderOneWithTiming(
                                    task.index(),
                                    diagnosisId,
                                    pool,
                                    brandAliases,
                                    task.blueprint(),
                                    task.usedCanonicalPrompts(),
                                    strategy
                            ),
                            renderExecutor
                    )
                    .orTimeout(concurrentTaskTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ex) {
            if (isExecutorRejected(ex)) {
                // 背压策略：执行器满载时降级为调用方线程继续处理，避免整批任务直接失败。
                log.warn("V4.2 Node2 render executor overloaded, fallback to caller thread. diagnosisId={}, probeId={}",
                        diagnosisId, task.blueprint().probeId());
                try {
                    TimedRenderedProbe probe = renderOneWithTiming(
                            task.index(),
                            diagnosisId,
                            pool,
                            brandAliases,
                            task.blueprint(),
                            task.usedCanonicalPrompts(),
                            strategy
                    );
                    return CompletableFuture.completedFuture(probe);
                } catch (RuntimeException syncEx) {
                    return CompletableFuture.failedFuture(syncEx);
                }
            }
            throw ex;
        }
    }

    private TimedRenderedProbe resolveConcurrentRenderResult(String diagnosisId,
                                                             CategoryVariablePool pool,
                                                             List<String> brandAliases,
                                                             ConcurrentRenderTask task,
                                                             CompletableFuture<TimedRenderedProbe> future,
                                                             RenderStrategy strategy) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            RuntimeException failure = unwrapRenderFailure(ex);
            long startNanos = System.nanoTime();
            RenderedProbe fallbackProbe = strategy.buildConcurrentFailureFallback(
                    diagnosisId,
                    pool,
                    brandAliases,
                    task.blueprint(),
                    task.usedCanonicalPrompts(),
                    failure
            );
            return new TimedRenderedProbe(task.index(), fallbackProbe, elapsedMillis(startNanos));
        }
    }

    private TimedRenderedProbe renderOneWithTiming(int index,
                                                   String diagnosisId,
                                                   CategoryVariablePool pool,
                                                   List<String> brandAliases,
                                                   ProbeBlueprint blueprint,
                                                   Set<String> usedCanonicalPrompts,
                                                   RenderStrategy strategy) {
        long startNanos = System.nanoTime();
        RenderedProbe renderedProbe = strategy.render(
                diagnosisId,
                pool,
                brandAliases,
                blueprint,
                usedCanonicalPrompts
        );
        return new TimedRenderedProbe(index, renderedProbe, elapsedMillis(startNanos));
    }

    private ProbeRenderingService.Node2TimingMetrics buildTimingMetrics(long totalMillis, List<Long> sortedProbeLatencyMillis) {
        if (sortedProbeLatencyMillis == null || sortedProbeLatencyMillis.isEmpty()) {
            return new ProbeRenderingService.Node2TimingMetrics(totalMillis, totalMillis, 0L, 0L, 0L, 0L);
        }
        long sum = 0L;
        for (Long latency : sortedProbeLatencyMillis) {
            sum += latency == null ? 0L : latency;
        }
        long p50 = percentile(sortedProbeLatencyMillis, 0.50d);
        long p95 = percentile(sortedProbeLatencyMillis, 0.95d);
        long max = sortedProbeLatencyMillis.get(sortedProbeLatencyMillis.size() - 1);
        long avg = Math.round((double) sum / sortedProbeLatencyMillis.size());
        return new ProbeRenderingService.Node2TimingMetrics(totalMillis, totalMillis, avg, p50, p95, max);
    }

    private long percentile(List<Long> sortedProbeLatencyMillis, double ratio) {
        if (sortedProbeLatencyMillis == null || sortedProbeLatencyMillis.isEmpty()) {
            return 0L;
        }
        int size = sortedProbeLatencyMillis.size();
        int index = (int) Math.ceil(size * ratio) - 1;
        index = Math.max(0, Math.min(index, size - 1));
        return sortedProbeLatencyMillis.get(index);
    }

    private RuntimeException unwrapRenderFailure(CompletionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Node2 并发渲染失败", cause == null ? ex : cause);
    }

    private boolean isExecutorRejected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RejectedExecutionException
                    || current.getClass().getName().endsWith("TaskRejectedException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    private record TimedRenderedProbe(
            int index,
            RenderedProbe probe,
            long latencyMillis
    ) {
    }

    private record ConcurrentRenderTask(
            int index,
            ProbeBlueprint blueprint,
            Set<String> usedCanonicalPrompts
    ) {
    }
}

package com.geoprobe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${app.async.core-pool-size:5}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = "diagnosisExecutionExecutor")
    public ThreadPoolTaskExecutor diagnosisExecutionExecutor(
            @Value("${app.async.execution.core-pool-size:${app.async.core-pool-size:5}}") int executionCorePoolSize,
            @Value("${app.async.execution.max-pool-size:${app.async.max-pool-size:10}}") int executionMaxPoolSize,
            @Value("${app.async.execution.queue-capacity:${app.async.queue-capacity:50}}") int executionQueueCapacity) {
        ThreadPoolTaskExecutor executor = buildThreadPool(
                executionCorePoolSize,
                executionMaxPoolSize,
                executionQueueCapacity,
                "geo-diagnosis-"
        );
        log.info("Async executor configured: name=diagnosisExecutionExecutor, corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getThreadPoolExecutor() == null ? executionQueueCapacity : executor.getThreadPoolExecutor().getQueue().remainingCapacity() + executor.getThreadPoolExecutor().getQueue().size());
        return executor;
    }

    @Bean(name = "diagnosisWarmupExecutor")
    public ThreadPoolTaskExecutor diagnosisWarmupExecutor(
            @Value("${app.async.warmup.core-pool-size:1}") int warmupCorePoolSize,
            @Value("${app.async.warmup.max-pool-size:2}") int warmupMaxPoolSize,
            @Value("${app.async.warmup.queue-capacity:32}") int warmupQueueCapacity) {
        return buildThreadPool(warmupCorePoolSize, warmupMaxPoolSize, warmupQueueCapacity, "geo-warmup-");
    }

    @Bean(name = "node2RenderExecutor")
    public ThreadPoolTaskExecutor node2RenderExecutor(
            @Value("${app.async.node2-render.core-pool-size:${app.async.core-pool-size:5}}") int renderCorePoolSize,
            @Value("${app.async.node2-render.max-pool-size:${app.async.max-pool-size:10}}") int renderMaxPoolSize,
            @Value("${app.async.node2-render.queue-capacity:${app.async.queue-capacity:50}}") int renderQueueCapacity) {
        return buildThreadPool(renderCorePoolSize, renderMaxPoolSize, renderQueueCapacity, "geo-node2-");
    }

    @Bean(name = "probeEventPersistenceExecutor")
    public ThreadPoolTaskExecutor probeEventPersistenceExecutor(
            @Value("${app.async.probe-event-persistence.core-pool-size:1}") int persistenceCorePoolSize,
            @Value("${app.async.probe-event-persistence.max-pool-size:1}") int persistenceMaxPoolSize,
            @Value("${app.async.probe-event-persistence.queue-capacity:256}") int persistenceQueueCapacity) {
        return buildThreadPool(persistenceCorePoolSize, persistenceMaxPoolSize, persistenceQueueCapacity, "geo-probe-persist-");
    }

    @Bean(name = "diagnosisObservationExecutor", destroyMethod = "close")
    public ExecutorService diagnosisObservationExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("geo-observe-", 0).factory()
        );
    }

    @Bean(name = "node3SamplingExecutor", destroyMethod = "shutdown")
    public ExecutorService node3SamplingExecutor(
            @Value("${app.async.node3-sampling.core-pool-size:24}") int node3CorePoolSize,
            @Value("${app.async.node3-sampling.max-pool-size:120}") int node3MaxPoolSize,
            @Value("${app.async.node3-sampling.queue-capacity:512}") int node3QueueCapacity) {
        return buildExecutorService(node3CorePoolSize, node3MaxPoolSize, node3QueueCapacity, "geo-node3-");
    }

    @Bean(name = "node3SamplingCallExecutor", destroyMethod = "shutdown")
    public ExecutorService node3SamplingCallExecutor(
            @Value("${app.async.node3-sampling-call.core-pool-size:${app.async.node3-sampling.core-pool-size:24}}") int callCorePoolSize,
            @Value("${app.async.node3-sampling-call.max-pool-size:${app.async.node3-sampling.max-pool-size:120}}") int callMaxPoolSize,
            @Value("${app.async.node3-sampling-call.queue-capacity:${app.async.node3-sampling.queue-capacity:512}}") int callQueueCapacity) {
        return buildExecutorService(callCorePoolSize, callMaxPoolSize, callQueueCapacity, "geo-node3-call-");
    }

    private ThreadPoolTaskExecutor buildThreadPool(int configuredCorePoolSize,
                                                   int configuredMaxPoolSize,
                                                   int configuredQueueCapacity,
                                                   String threadNamePrefix) {
        int normalizedCorePoolSize = Math.max(1, configuredCorePoolSize);
        int normalizedMaxPoolSize = Math.max(normalizedCorePoolSize, configuredMaxPoolSize);
        int normalizedQueueCapacity = Math.max(1, configuredQueueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(normalizedCorePoolSize);
        executor.setMaxPoolSize(normalizedMaxPoolSize);
        executor.setQueueCapacity(normalizedQueueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    private ExecutorService buildExecutorService(int configuredCorePoolSize,
                                                 int configuredMaxPoolSize,
                                                 int configuredQueueCapacity,
                                                 String threadNamePrefix) {
        int normalizedCorePoolSize = Math.max(1, configuredCorePoolSize);
        int normalizedMaxPoolSize = Math.max(normalizedCorePoolSize, configuredMaxPoolSize);
        int normalizedQueueCapacity = Math.max(1, configuredQueueCapacity);
        ThreadFactory threadFactory = Thread.ofPlatform().name(threadNamePrefix, 0).factory();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                normalizedCorePoolSize,
                normalizedMaxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(normalizedQueueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

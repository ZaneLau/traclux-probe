package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Node3SamplingBatchRunnerTest {

    @Test
    void shouldPreserveNonRuntimeCauseMessageFromWorkerFailure() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Node3SamplingBatchRunner runner = new Node3SamplingBatchRunner(
                    executor,
                    4,
                    40,
                    120,
                    8,
                    8,
                    0.25d,
                    12_000L,
                    (diagnosisId, probe, policy, targetEnginePolicy, round) -> {
                        throw new AssertionError("fatal worker failure");
                    }
            );

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    runner.runBatch(
                            "diag-runner",
                            List.of(new Node3SamplingBatchRunner.IndexedProbeInput(0, probe("probe-1"))),
                            RealtimeSearchPolicy.disabled(),
                            Node3TargetEnginePolicy.defaultPolicy(),
                            0,
                            4,
                            null,
                            V42Node3SamplingGatewayService.DispatchEventListener.NO_OP
                    )
            );
            assertTrue(exception.getMessage().contains("fatal worker failure"));
            assertTrue(exception.getCause() instanceof AssertionError);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCancelOutstandingTasksWhenSystemicProviderFailureOccurs() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch blockingStarted = new CountDownLatch(1);
        CountDownLatch interruptedLatch = new CountDownLatch(1);
        try {
            Node3SamplingBatchRunner runner = new Node3SamplingBatchRunner(
                    executor,
                    2,
                    40,
                    120,
                    8,
                    8,
                    0.25d,
                    12_000L,
                    (diagnosisId, probe, policy, targetEnginePolicy, round) -> {
                        if ("probe-overdue".equals(probe.probeId())) {
                            throw new ProviderDispatchException(
                                    "UPSTREAM_PROVIDER_ACCOUNT_OVERDUE",
                                    "doubao 返回非 2xx: 403, body={\"error\":{\"code\":\"AccountOverdueError\"}}"
                            );
                        }
                        blockingStarted.countDown();
                        try {
                            Thread.sleep(10_000L);
                            return com.geoprobe.dto.engine.v42.Node3ProbeOutput.success(
                                    probe,
                                    new com.geoprobe.dto.engine.v42.RawResponse("ok", "", List.of(), 1, 1, 2, 10L, false),
                                    round,
                                    1
                            );
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            interruptedLatch.countDown();
                            throw new IllegalStateException("blocking probe interrupted", ex);
                        }
                    }
            );

            ProviderDispatchException exception = assertThrows(
                    ProviderDispatchException.class,
                    () -> runner.runBatch(
                            "diag-overdue",
                            List.of(
                                    new Node3SamplingBatchRunner.IndexedProbeInput(0, probe("probe-blocking")),
                                    new Node3SamplingBatchRunner.IndexedProbeInput(1, probe("probe-overdue"))
                            ),
                            RealtimeSearchPolicy.disabled(),
                            Node3TargetEnginePolicy.defaultPolicy(),
                            0,
                            2,
                            null,
                            V42Node3SamplingGatewayService.DispatchEventListener.NO_OP
                    )
            );

            assertEquals("UPSTREAM_PROVIDER_ACCOUNT_OVERDUE", exception.errorCode());
            assertTrue(blockingStarted.await(1, TimeUnit.SECONDS));
            assertTrue(interruptedLatch.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Node3ProbeInput probe(String probeId) {
        return new Node3ProbeInput(
                probeId,
                BucketType.ORTHOGONAL,
                Map.of("D1", "verification"),
                Map.of("D1", "确认一下"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "测试问题-" + probeId
        );
    }
}

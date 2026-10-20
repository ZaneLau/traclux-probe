package com.geoprobe.service.engine.v42;

import com.geoprobe.common.LlmTokenBudgetExceededException;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node3SamplingRequest;
import com.geoprobe.dto.engine.v42.Node3SamplingResult;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42Node3SamplingGatewayServiceTest {

    @Test
    void shouldAppendRawResponseToEverySuccessfulProbe() {
        List<String> visitedProbeIds = new ArrayList<>();
        ModelAdapter adapter = (probe, control) -> {
            visitedProbeIds.add(probe.probeId());
            assertEquals(false, control.earlyStopEnabled());
            assertEquals(0, control.earlyStopBrandHitThreshold());
            return new RawResponse("answer-" + probe.probeId(), "", List.of(), 8, 18, 26, 25L, false);
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(adapter, millis -> {
        }, 1, new long[]{0L}, 0L);

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(
                probe("probe-1", "verification"),
                probe("probe-2", "exploration")
        )));

        assertEquals(2L, result.successCount());
        assertEquals(0L, result.failedCount());
        assertIterableEquals(List.of("probe-1", "probe-2"), visitedProbeIds);
        assertEquals(ProbeDispatchStatus.SUCCESS, result.probes().get(0).dispatchStatus());
        assertEquals("answer-probe-1", result.probes().get(0).rawResponse().fullText());
        assertEquals("verification", result.probes().get(0).abstractTags().get("D1"));
        assertEquals("确认一下", result.probes().get(0).translatedTags().get("D1"));
        assertEquals("运动相机问题-probe-1", result.probes().get(0).renderedPrompt());
    }

    @Test
    void shouldFailProbeWhenAdapterReturnsEmptyFinalResponse() {
        ModelAdapter adapter = (probe, control) -> new RawResponse("", "只有思考没有最终答案", List.of(), 12, 34, 46, 20L, false);
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(adapter, millis -> {
        }, 1, new long[]{0L}, 0L);

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-empty-final",
                List.of(probe("probe-empty", "verification")),
                RealtimeSearchPolicy.disabled(),
                new Node3TargetEnginePolicy("glm", "glm-4-7-251222", true)
        ));

        assertEquals(0L, result.successCount());
        assertEquals(1L, result.failedCount());
        Node3ProbeOutput failed = result.probes().getFirst();
        assertEquals(ProbeDispatchStatus.FAILED, failed.dispatchStatus());
        assertEquals("EMPTY_FINAL_RESPONSE", failed.failureReason());
        assertEquals(12, failed.rawResponse().promptTokenCount());
        assertEquals(34, failed.rawResponse().completionTokenCount());
        assertEquals(46, failed.rawResponse().totalTokenCount());
        assertEquals("只有思考没有最终答案", failed.rawResponse().thinkContent());
    }

    @Test
    void shouldFailProbeWhenAdapterReturnsTruncatedFinalResponse() {
        ModelAdapter adapter = (probe, control) -> new RawResponse("回答被截断", "", List.of(), 22, 88, 110, 35L, true);
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(adapter, millis -> {
        }, 1, new long[]{0L}, 0L);

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-truncated-final",
                List.of(probe("probe-truncated", "verification")),
                RealtimeSearchPolicy.disabled(),
                new Node3TargetEnginePolicy("doubao", "doubao-seed-1-6-lite-251015", true)
        ));

        assertEquals(0L, result.successCount());
        assertEquals(1L, result.failedCount());
        Node3ProbeOutput failed = result.probes().getFirst();
        assertEquals(ProbeDispatchStatus.FAILED, failed.dispatchStatus());
        assertEquals("TRUNCATED_FINAL_RESPONSE", failed.failureReason());
        assertTrue(failed.rawResponse().earlyStoppedFlag());
        assertEquals(110, failed.rawResponse().totalTokenCount());
    }

    @Test
    void shouldRetryThreeTimesWithExponentialBackoffBeforeSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleepCalls = new ArrayList<>();
        ModelAdapter adapter = (probe, control) -> {
            if (attempts.getAndIncrement() < 3) {
                throw new IllegalStateException("rate limit");
            }
            return new RawResponse("ok", "", List.of(), 9, 20, 29, 30L, false);
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                sleepCalls::add,
                4,
                new long[]{1000L, 2000L, 4000L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(probe("probe-retry", "verification"))));

        assertEquals(4, attempts.get());
        assertIterableEquals(List.of(1000L, 2000L, 4000L), sleepCalls);
        assertEquals(1L, result.successCount());
        assertEquals(ProbeDispatchStatus.SUCCESS, result.probes().getFirst().dispatchStatus());
        assertEquals("ok", result.probes().getFirst().rawResponse().fullText());
    }

    @Test
    void shouldMarkFailedProbeAndContinuePipelineWhenRetriesExhausted() {
        List<Long> sleepCalls = new ArrayList<>();
        ModelAdapter adapter = new ModelAdapter() {
            @Override
            public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
                return switch (probeInput.probeId()) {
                    case "probe-ok-1" -> new RawResponse("first", "", List.of(), 4, 10, 14, 15L, false);
                    case "probe-fail" -> throw new IllegalStateException("upstream unavailable");
                    case "probe-ok-2" -> new RawResponse("third", "", List.of(), 5, 12, 17, 18L, false);
                    default -> throw new IllegalArgumentException("unexpected probe");
                };
            }
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                sleepCalls::add,
                4,
                new long[]{1000L, 2000L, 4000L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(
                probe("probe-ok-1", "verification"),
                probe("probe-fail", "exploration"),
                probe("probe-ok-2", "formulation")
        )));

        assertEquals(2L, result.successCount());
        assertEquals(1L, result.failedCount());
        assertIterableEquals(List.of(1000L, 2000L, 4000L), sleepCalls);

        Node3ProbeOutput failedProbe = result.probes().stream()
                .filter(probe -> probe.probeId().equals("probe-fail"))
                .findFirst()
                .orElseThrow();
        assertEquals(ProbeDispatchStatus.FAILED, failedProbe.dispatchStatus());
        assertEquals("UPSTREAM_PROVIDER_ERROR", failedProbe.failureReason());
        assertTrue(failedProbe.rawResponse().latencyMs() >= 0L);

        Node3ProbeOutput lastProbe = result.probes().get(2);
        assertEquals(ProbeDispatchStatus.SUCCESS, lastProbe.dispatchStatus());
        assertEquals("third", lastProbe.rawResponse().fullText());
    }

    @Test
    void shouldStopRetryingWhenTokenBudgetIsExceeded() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleepCalls = new ArrayList<>();
        ModelAdapter adapter = (probe, control) -> {
            attempts.incrementAndGet();
            throw new LlmTokenBudgetExceededException("deepseek", "deepseek-v3.2", 100, 100);
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                sleepCalls::add,
                4,
                new long[]{1000L, 2000L, 4000L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-budget-stop",
                List.of(probe("probe-budget", "verification")),
                RealtimeSearchPolicy.disabled(),
                new Node3TargetEnginePolicy("deepseek", "deepseek-v3.2", true)
        ));

        assertEquals(1, attempts.get());
        assertTrue(sleepCalls.isEmpty());
        assertEquals(0L, result.successCount());
        assertEquals(1L, result.failedCount());
        assertEquals("TOKEN_BUDGET_EXCEEDED", result.probes().getFirst().failureReason());
    }

    @Test
    void shouldAbortBatchImmediatelyWhenProviderAccountIsOverdue() {
        List<Long> sleepCalls = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            attempts.incrementAndGet();
            throw new ProviderDispatchException(
                    "UPSTREAM_PROVIDER_ACCOUNT_OVERDUE",
                    "doubao 返回非 2xx: 403, body={\"error\":{\"code\":\"AccountOverdueError\"}}"
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                sleepCalls::add,
                2,
                4,
                new long[]{1000L, 2000L, 4000L},
                5L
        );

        ProviderDispatchException exception = assertThrows(
                ProviderDispatchException.class,
                () -> service.dispatch(new Node3SamplingRequest(List.of(
                        probe("probe-overdue-1", "verification"),
                        probe("probe-overdue-2", "exploration")
                )))
        );

        assertEquals("UPSTREAM_PROVIDER_ACCOUNT_OVERDUE", exception.errorCode());
        assertTrue(sleepCalls.isEmpty());
        assertTrue(attempts.get() <= 2);
    }

    @Test
    void shouldTimeoutSingleProbeAndContinuePipeline() {
        List<Long> sleepCalls = new ArrayList<>();
        ModelAdapter timeoutAwareAdapter = new ModelAdapter() {
            @Override
            public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
                return switch (probeInput.probeId()) {
                    case "probe-ok-1" -> new RawResponse("first", "", List.of(), 4, 10, 14, 15L, false);
                    case "probe-timeout" -> {
                        try {
                            Thread.sleep(2_000L);
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("interrupted timeout stub", ex);
                        }
                        yield new RawResponse("late", "", List.of(), 5, 12, 17, 18L, false);
                    }
                    case "probe-ok-2" -> new RawResponse("third", "", List.of(), 5, 12, 17, 18L, false);
                    default -> throw new IllegalArgumentException("unexpected probe");
                };
            }
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                timeoutAwareAdapter,
                sleepCalls::add,
                1,
                new long[]{0L},
                1L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest("diag-timeout", List.of(
                probe("probe-ok-1", "verification"),
                probe("probe-timeout", "exploration"),
                probe("probe-ok-2", "formulation")
        )));

        assertEquals(2L, result.successCount());
        assertEquals(1L, result.failedCount());
        assertTrue(sleepCalls.isEmpty());

        Node3ProbeOutput failedProbe = result.probes().stream()
                .filter(probe -> probe.probeId().equals("probe-timeout"))
                .findFirst()
                .orElseThrow();
        assertEquals(ProbeDispatchStatus.FAILED, failedProbe.dispatchStatus());
        assertEquals("PROBE_TASK_TIMEOUT", failedProbe.failureReason());
        assertTrue(failedProbe.rawResponse().latencyMs() >= 1000L);

        Node3ProbeOutput lastProbe = result.probes().get(2);
        assertEquals(ProbeDispatchStatus.SUCCESS, lastProbe.dispatchStatus());
        assertEquals("third", lastProbe.rawResponse().fullText());
    }

    @Test
    void shouldPreserveDistinctFailureReasonsAcrossBatchFailures() {
        ModelAdapter adapter = new ModelAdapter() {
            @Override
            public RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl) {
                switch (probeInput.probeId()) {
                    case "probe-timeout" -> throw new IllegalStateException("采样任务超时");
                    case "probe-upstream" -> throw new IllegalStateException("doubao gateway timeout");
                    case "probe-ok" -> {
                        return new RawResponse("ok", "", List.of(), 4, 10, 14, 15L, false);
                    }
                    default -> throw new IllegalArgumentException("unexpected probe");
                }
            }
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                3,
                1,
                new long[]{0L},
                5L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(
                probe("probe-timeout", "verification"),
                probe("probe-upstream", "exploration"),
                probe("probe-ok", "formulation")
        )));

        assertEquals(1L, result.successCount());
        assertEquals(2L, result.failedCount());

        Map<String, Node3ProbeOutput> outputsByProbeId = result.probes().stream()
                .collect(java.util.stream.Collectors.toMap(Node3ProbeOutput::probeId, output -> output));

        assertEquals("PROBE_TASK_TIMEOUT", outputsByProbeId.get("probe-timeout").failureReason());
        assertEquals("UPSTREAM_PROVIDER_TIMEOUT", outputsByProbeId.get("probe-upstream").failureReason());
        assertEquals(ProbeDispatchStatus.SUCCESS, outputsByProbeId.get("probe-ok").dispatchStatus());
        assertEquals(
                2L,
                outputsByProbeId.values().stream()
                        .filter(output -> output.dispatchStatus() == ProbeDispatchStatus.FAILED)
                        .map(Node3ProbeOutput::failureReason)
                        .distinct()
                        .count()
        );
    }

    @Test
    void shouldDispatchMultipleProbesConcurrentlyWhenConcurrencyIsGreaterThanOne() throws Exception {
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch enteredLatch = new CountDownLatch(2);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        ModelAdapter adapter = (probe, control) -> {
            int inFlight = activeCalls.incrementAndGet();
            maxInFlight.accumulateAndGet(inFlight, Math::max);
            enteredLatch.countDown();
            try {
                if (!releaseLatch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test release latch timed out");
                }
                return new RawResponse("ok-" + probe.probeId(), "", List.of(), 5, 12, 17, 20L, false);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted in test adapter", ex);
            } finally {
                activeCalls.decrementAndGet();
            }
        };

        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                2,
                1,
                new long[]{0L},
                0L
        );

        Thread releaseThread = new Thread(() -> {
            try {
                assertTrue(enteredLatch.await(2, TimeUnit.SECONDS));
                releaseLatch.countDown();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        });
        releaseThread.start();

        try {
            Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(
                    probe("probe-1", "verification"),
                    probe("probe-2", "exploration")
            )));

            assertEquals(2L, result.successCount());
            assertEquals(0L, result.failedCount());
            assertEquals(2, maxInFlight.get());
            assertEquals(List.of("probe-1", "probe-2"), result.probes().stream().map(Node3ProbeOutput::probeId).toList());
        } finally {
            releaseLatch.countDown();
            releaseThread.join(2_000L);
        }
    }

    @Test
    void shouldKeepFixedConcurrencyForNonRealtimeSamplingEvenWhenLatencyIsHigh() {
        ModelAdapter adapter = (probe, control) ->
                new RawResponse("ok-" + probe.probeId(), "", List.of(), 5, 12, 17, 35_000L, false);

        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                1,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(
                        "diag-high-latency-non-realtime",
                        probes(16),
                        RealtimeSearchPolicy.disabled()
                ),
                null,
                events::add
        );

        assertEquals(16L, result.successCount());
        List<Integer> activeWindows = events.stream()
                .filter(event -> "probe_result".equals(event.eventType()))
                .map(event -> (Number) event.payload().get("activeWindow"))
                .filter(value -> value != null)
                .map(Number::intValue)
                .toList();
        assertFalse(activeWindows.isEmpty());
        assertTrue(activeWindows.stream().allMatch(window -> window == 4), "非联网采样不应仅因高延时降并发");
    }

    @Test
    void shouldEmitSamplingProgressSnapshots() {
        ModelAdapter adapter = (probe, control) ->
                new RawResponse("ok-" + probe.probeId(), "", List.of(), 5, 12, 17, 20L, false);

        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                2,
                1,
                new long[]{0L},
                0L
        );

        List<com.geoprobe.dto.engine.SamplingProgress> progressEvents = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(List.of(
                probe("probe-1", "verification"),
                probe("probe-2", "exploration"),
                probe("probe-3", "comparison")
        )), progressEvents::add);

        assertEquals(3L, result.successCount());
        assertFalse(progressEvents.isEmpty());
        assertEquals(0, progressEvents.getFirst().completed());
        assertEquals(3, progressEvents.getFirst().total());
        assertEquals(3, progressEvents.getLast().completed());
        assertEquals(3, progressEvents.getLast().succeeded());
    }

    @Test
    void shouldPassTargetEnginePolicyToAdapterAndExposeProviderCode() {
        ModelAdapter adapter = (probe, control) -> {
            assertEquals("chatgpt", control.targetProvider());
            assertEquals("gpt-5-mini", control.targetModelId());
            return new RawResponse(
                    "chatgpt-answer",
                    "",
                    List.of(),
                    9,
                    20,
                    29,
                    30L,
                    false,
                    false,
                    false,
                    false,
                    List.of(),
                    0,
                    List.of(),
                    "gpt-5-mini",
                    "",
                    List.of()
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                1,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-target-chatgpt",
                List.of(probe("probe-chatgpt", "verification")),
                RealtimeSearchPolicy.disabled(),
                new Node3TargetEnginePolicy("chatgpt", "gpt-5-mini")
        ));

        assertEquals(1L, result.successCount());
        assertEquals("chatgpt", result.providerCode());
        assertEquals("gpt-5-mini", result.probes().getFirst().rawResponse().modelId());
    }

    @Test
    void shouldInferDeepseekProviderFromModelIdWhenProviderMissing() {
        ModelAdapter adapter = (probe, control) -> {
            assertEquals("deepseek", control.targetProvider());
            assertEquals("deepseek-v3.2", control.targetModelId());
            return new RawResponse(
                    "deepseek-answer",
                    "",
                    List.of(),
                    12,
                    24,
                    36,
                    31L,
                    false,
                    false,
                    false,
                    false,
                    List.of(),
                    0,
                    List.of(),
                    "deepseek-v3.2",
                    "",
                    List.of()
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                1,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-target-deepseek",
                List.of(probe("probe-deepseek", "verification")),
                RealtimeSearchPolicy.disabled(),
                new Node3TargetEnginePolicy(null, "deepseek-v3.2")
        ));

        assertEquals(1L, result.successCount());
        assertEquals("deepseek", result.providerCode());
        assertEquals("deepseek-v3.2", result.probes().getFirst().rawResponse().modelId());
    }

    @Test
    void shouldNotAssumeArkEndpointIdAlwaysMeansDoubao() {
        assertNull(Node3TargetEnginePolicy.inferProviderFromModelId("ep-test-opaque"));
    }

    @Test
    void shouldPersistStructuredFailureCodeWhenRealtimeSearchRequestedForUnsupportedProvider() {
        ModelAdapter adapter = (probe, control) -> {
            throw new ProviderDispatchException(
                    "REALTIME_SEARCH_UNSUPPORTED",
                    "chatgpt 当前未接入实时联网搜索"
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                1,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-chatgpt-search",
                List.of(probe("probe-chatgpt-search", "verification")),
                new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0),
                new Node3TargetEnginePolicy("chatgpt", "gpt-5-mini")
        ));

        Node3ProbeOutput failed = result.probes().getFirst();
        assertEquals(ProbeDispatchStatus.FAILED, failed.dispatchStatus());
        assertEquals("REALTIME_SEARCH_UNSUPPORTED", failed.failureReason());
        assertTrue(failed.rawResponse().searchRequested());
        assertTrue(failed.rawResponse().searchFallbackFlag());
    }

    @Test
    void shouldPreserveUpstreamFailureReasonWhenRealtimeSearchCallFails() {
        ModelAdapter adapter = (probe, control) -> {
            throw new IllegalStateException("doubao gateway timeout");
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                1,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-search-upstream-fail",
                List.of(probe("probe-search-fail", "verification")),
                new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0)
        ));

        Node3ProbeOutput failed = result.probes().getFirst();
        assertEquals(ProbeDispatchStatus.FAILED, failed.dispatchStatus());
        assertEquals("UPSTREAM_PROVIDER_TIMEOUT", failed.failureReason());
        assertTrue(failed.rawResponse().searchRequested());
        assertFalse(failed.rawResponse().searchSatisfied());
        assertTrue(failed.rawResponse().searchFallbackFlag());
    }

    @Test
    void shouldUseSearchEvidenceFailureOnlyWhenCallSucceededButEvidenceMissing() {
        ModelAdapter adapter = (probe, control) -> new RawResponse(
                "只有回答没有来源",
                "",
                List.of(),
                5,
                12,
                17,
                20L,
                false,
                true,
                false,
                false,
                List.of()
        );
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                1,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-search-unsatisfied",
                List.of(probe("probe-search-unsatisfied", "verification")),
                new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0)
        ));

        Node3ProbeOutput failed = result.probes().getFirst();
        assertEquals(ProbeDispatchStatus.FAILED, failed.dispatchStatus());
        assertEquals("REALTIME_SEARCH_EVIDENCE_UNSATISFIED", failed.failureReason());
        assertTrue(failed.rawResponse().searchRequested());
        assertFalse(failed.rawResponse().searchSatisfied());
        assertTrue(failed.rawResponse().searchFallbackFlag());
    }

    @Test
    void shouldUseFastSweepThenBackfillWithoutInlineRetriesWhenRealtimeSearchEnabled() {
        AtomicInteger calls = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            int attempt = calls.incrementAndGet();
            if (attempt == 1) {
                return new RawResponse(
                        "第一轮没有证据",
                        "",
                        List.of(),
                        5,
                        12,
                        17,
                        20L,
                        false,
                        true,
                        false,
                        false,
                        List.of(),
                        0,
                        List.of()
                );
            }
            return new RawResponse(
                    "补齐轮拿到证据",
                    "",
                    List.of("https://example.com/a"),
                    5,
                    12,
                    17,
                    20L,
                    false,
                    true,
                    true,
                    false,
                    List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                            "web",
                            "https://example.com/a",
                            "标题",
                            "摘要",
                            "",
                            "2026-04-01T00:00:00",
                            "doubao",
                            probe.probeId(),
                            "佳帮手 厨房置物架"
                    )),
                    1,
                    List.of("佳帮手 厨房置物架")
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        Node3SamplingResult result = service.dispatch(new Node3SamplingRequest(
                "diag-fast-sweep",
                List.of(probe("probe-fast", "verification")),
                new RealtimeSearchPolicy(true, "doubao", 0.9d, 2, 1)
        ));

        assertEquals(2, calls.get());
        assertEquals(1L, result.successCount());
        assertTrue(result.realtimeSearchSummary().qualityGatePassed());
    }

    @Test
    void shouldBackfillUnsatisfiedProbesEvenWhenBatchGateAlreadyPasses() {
        AtomicInteger probeAAttempts = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            if (probe.probeId().equals("probe-a") && probeAAttempts.getAndIncrement() == 0) {
                return unsatisfiedSearchResponse("首轮缺证据", "智能手机 性能体验 续航稳定性");
            }
            return satisfiedSearchResponse(probe.probeId(), "智能手机 性能体验 续航稳定性");
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(
                        "diag-batch-pass-backfill",
                        List.of(probe("probe-a", "verification"), probe("probe-b", "exploration")),
                        new RealtimeSearchPolicy(true, "doubao", 0.5d, 0, 1)
                ),
                null,
                events::add
        );

        assertEquals(2, probeAAttempts.get());
        assertEquals(2L, result.successCount());
        assertEquals(0L, result.failedCount());
        assertTrue(result.realtimeSearchSummary().qualityGatePassed());
        assertEquals(1, result.realtimeSearchSummary().backfillRoundsUsed());
        assertEquals(1, result.realtimeSearchSummary().recoveredProbeCount());
        assertTrue(result.realtimeSearchSummary().unsatisfiedProbeIds().isEmpty());

        V42Node3SamplingGatewayService.DispatchEvent backfillSummary = events.stream()
                .filter(event -> "round_summary".equals(event.eventType()))
                .filter(event -> Boolean.TRUE.equals(event.payload().get("backfill")))
                .findFirst()
                .orElseThrow();
        Number roundRequested = (Number) backfillSummary.payload().get("roundRequested");
        Number roundSucceeded = (Number) backfillSummary.payload().get("roundSucceeded");
        assertEquals(1, roundRequested.intValue());
        assertEquals(1, roundSucceeded.intValue());
    }

    @Test
    void shouldStopBackfillWhenUnsatisfiedProbesDoNotRecover() {
        AtomicInteger probeAAttempts = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            if (probe.probeId().equals("probe-a")) {
                probeAAttempts.incrementAndGet();
                return unsatisfiedSearchResponse("持续缺证据", "智能手机 性能体验");
            }
            return satisfiedSearchResponse(probe.probeId(), "智能手机 影像能力");
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(
                        "diag-stop-empty-backfill",
                        List.of(probe("probe-a", "verification"), probe("probe-b", "exploration")),
                        new RealtimeSearchPolicy(true, "doubao", 0.5d, 0, 2)
                ),
                null,
                events::add
        );

        assertEquals(2, probeAAttempts.get());
        assertEquals(1L, result.successCount());
        assertEquals(1L, result.failedCount());
        assertTrue(result.realtimeSearchSummary().qualityGatePassed());
        assertEquals(1, result.realtimeSearchSummary().backfillRoundsUsed());
        assertEquals(0, result.realtimeSearchSummary().recoveredProbeCount());
        assertIterableEquals(List.of("probe-a"), result.realtimeSearchSummary().unsatisfiedProbeIds());

        long backfillSummaryCount = events.stream()
                .filter(event -> "round_summary".equals(event.eventType()))
                .filter(event -> Boolean.TRUE.equals(event.payload().get("backfill")))
                .count();
        assertEquals(1L, backfillSummaryCount);
    }

    @Test
    void shouldEmitProbeAndRoundEventsInRealtimeMode() {
        ModelAdapter adapter = (probe, control) -> new RawResponse(
                "返回内容",
                "",
                List.of("https://example.com/r"),
                5,
                12,
                17,
                20L,
                false,
                true,
                true,
                false,
                List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                        "web",
                        "https://example.com/r",
                        "标题",
                        "摘要",
                        "",
                        "2026-04-01T00:00:00",
                        "doubao",
                        probe.probeId(),
                        "美菱 制冰机"
                )),
                1,
                List.of("美菱 制冰机")
        );
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(
                        "diag-events",
                        List.of(probe("probe-event", "verification")),
                        new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0)
                ),
                null,
                events::add
        );

        assertEquals(1L, result.successCount());
        assertTrue(events.stream().anyMatch(event -> "probe_result".equals(event.eventType())));
        assertTrue(events.stream().anyMatch(event -> "round_summary".equals(event.eventType())));
        V42Node3SamplingGatewayService.DispatchEvent probeEvent = events.stream()
                .filter(event -> "probe_result".equals(event.eventType()))
                .findFirst()
                .orElseThrow();
        assertNotNull(probeEvent.payload().get("probeId"));
    }

    @Test
    void shouldEmitBackfillRoundSummaryWithRoundScopedSuccessCount() {
        AtomicInteger probeAAttempts = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            boolean firstAttemptForProbeA = probe.probeId().equals("probe-a") && probeAAttempts.getAndIncrement() == 0;
            if (firstAttemptForProbeA) {
                return new RawResponse(
                        "首轮无证据",
                        "",
                        List.of(),
                        5,
                        10,
                        15,
                        20L,
                        false,
                        true,
                        false,
                        true,
                        List.of(),
                        0,
                        List.of("佳帮手 厨房置物架")
                );
            }
            return new RawResponse(
                    "成功拿到证据",
                    "",
                    List.of("https://example.com/s"),
                    5,
                    12,
                    17,
                    20L,
                    false,
                    true,
                    true,
                    false,
                    List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                            "web",
                            "https://example.com/s",
                            "标题",
                            "摘要",
                            "",
                            "2026-04-01T00:00:00",
                            "doubao",
                            probe.probeId(),
                            "佳帮手 厨房置物架"
                    )),
                    1,
                    List.of("佳帮手 厨房置物架")
            );
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        service.dispatch(
                new Node3SamplingRequest(
                        "diag-round-summary",
                        List.of(probe("probe-a", "verification"), probe("probe-b", "exploration")),
                        new RealtimeSearchPolicy(true, "doubao", 0.95d, 0, 1)
                ),
                null,
                events::add
        );

        V42Node3SamplingGatewayService.DispatchEvent backfillSummary = events.stream()
                .filter(event -> "round_summary".equals(event.eventType()))
                .filter(event -> Boolean.TRUE.equals(event.payload().get("backfill")))
                .findFirst()
                .orElseThrow();

        Number roundRequested = (Number) backfillSummary.payload().get("roundRequested");
        Number roundSucceeded = (Number) backfillSummary.payload().get("roundSucceeded");
        assertEquals(1, roundRequested.intValue());
        assertEquals(1, roundSucceeded.intValue());
        assertTrue(roundSucceeded.intValue() <= roundRequested.intValue());
    }

    @Test
    void shouldHideRoutingDebugFieldsByDefaultInProbeEvent() {
        ModelAdapter adapter = (probe, control) -> new RawResponse(
                "成功拿到证据",
                "",
                List.of("https://example.com/s"),
                5,
                12,
                17,
                20L,
                false,
                true,
                true,
                false,
                List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                        "web",
                        "https://example.com/s",
                        "标题",
                        "摘要",
                        "",
                        "2026-04-01T00:00:00",
                        "doubao",
                        probe.probeId(),
                        "佳帮手 厨房置物架"
                )),
                1,
                List.of("佳帮手 厨房置物架"),
                "doubao-seed-2-0-pro-260215",
                "HIGH_VALUE_HARD",
                List.of("doubao-seed-2-0-pro-260215", "doubao-seed-2-0-mini-260215")
        );
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        service.dispatch(
                new Node3SamplingRequest(
                        "diag-routing-hidden",
                        List.of(probe("probe-hidden", "verification")),
                        new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0)
                ),
                null,
                events::add
        );

        V42Node3SamplingGatewayService.DispatchEvent probeEvent = events.stream()
                .filter(event -> "probe_result".equals(event.eventType()))
                .findFirst()
                .orElseThrow();

        assertEquals("HIGH_VALUE_HARD", probeEvent.payload().get("routingIntent"));
        org.junit.jupiter.api.Assertions.assertFalse(probeEvent.payload().containsKey("routingCandidates"));
        org.junit.jupiter.api.Assertions.assertFalse(probeEvent.payload().containsKey("routingLog"));
    }

    @Test
    void shouldReduceActiveWindowWhenRealtimeSearchIsCongested() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        ModelAdapter adapter = (probe, control) -> {
            int active = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(active, Math::max);
            int probeIndex = Integer.parseInt(probe.probeId().replace("probe-", ""));
            try {
                // 先返回一段成功样本，推动 AIMD 窗口上升；随后返回拥塞失败，验证窗口回退。
                Thread.sleep(probeIndex < 16 ? 10L : 80L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test interrupted", ex);
            } finally {
                inFlight.decrementAndGet();
            }
            if (probeIndex < 16) {
                return new RawResponse(
                        "ok-" + probe.probeId(),
                        "",
                        List.of("https://example.com/" + probe.probeId()),
                        5,
                        12,
                        17,
                        20L,
                        false,
                        true,
                        true,
                        false,
                        List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                                "web",
                                "https://example.com/" + probe.probeId(),
                                "标题",
                                "摘要",
                                "",
                                "2026-04-01T00:00:00",
                                "doubao",
                                probe.probeId(),
                                "拥塞测试"
                        )),
                        1,
                        List.of("拥塞测试")
                );
            }
            throw new IllegalStateException("429 rate limit");
        };
        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                8,
                new long[]{0L},
                0L
        );

        List<V42Node3SamplingGatewayService.DispatchEvent> events = new ArrayList<>();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(
                        "diag-congestion",
                        probes(64),
                        new RealtimeSearchPolicy(true, "doubao", 0.9d, 0, 0)
                ),
                null,
                events::add
        );

        assertEquals(16L, result.successCount());
        assertEquals(48L, result.failedCount());
        List<Integer> activeWindows = events.stream()
                .filter(event -> "probe_result".equals(event.eventType()))
                .map(event -> (Number) event.payload().get("activeWindow"))
                .filter(value -> value != null)
                .map(Number::intValue)
                .toList();
        assertFalse(activeWindows.isEmpty());
        int peakWindow = activeWindows.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(maxInFlight.get() <= peakWindow, "在途请求数必须受窗口上限约束");
        assertTrue(peakWindow > 40, "成功段应推动窗口上升");
        boolean reducedAfterRise = false;
        int peakSoFar = 0;
        for (Integer window : activeWindows) {
            peakSoFar = Math.max(peakSoFar, window);
            if (peakSoFar > 40 && window < peakSoFar) {
                reducedAfterRise = true;
                break;
            }
        }
        assertTrue(reducedAfterRise, "拥塞段应触发窗口回退");
    }

    @Test
    void shouldNotBreakDispatchWhenObserverCallbacksThrow() {
        ModelAdapter adapter = (probe, control) ->
                new RawResponse("ok-" + probe.probeId(), "", List.of(), 5, 12, 17, 20L, false);

        V42Node3SamplingGatewayService service = new V42Node3SamplingGatewayService(
                adapter,
                millis -> {
                },
                4,
                1,
                new long[]{0L},
                0L
        );

        AtomicInteger progressCalls = new AtomicInteger();
        AtomicInteger eventCalls = new AtomicInteger();
        Node3SamplingResult result = service.dispatch(
                new Node3SamplingRequest(probes(12)),
                progress -> {
                    progressCalls.incrementAndGet();
                    throw new IllegalStateException("progress observer failure");
                },
                event -> {
                    eventCalls.incrementAndGet();
                    throw new IllegalStateException("event observer failure");
                }
        );

        assertEquals(12L, result.successCount());
        assertEquals(0L, result.failedCount());
        assertTrue(progressCalls.get() > 0);
        assertTrue(eventCalls.get() > 0);
    }

    private Node3ProbeInput probe(String probeId, String d1Tag) {
        return new Node3ProbeInput(
                probeId,
                BucketType.ORTHOGONAL,
                Map.of("D1", d1Tag),
                Map.of("D1", "确认一下"),
                ProbeExposurePolicy.BRAND_HIDDEN,
                "运动相机问题-" + probeId
        );
    }

    private List<Node3ProbeInput> probes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> probe("probe-" + index, index % 2 == 0 ? "verification" : "exploration"))
                .toList();
    }

    private RawResponse unsatisfiedSearchResponse(String text, String query) {
        return new RawResponse(
                text,
                "",
                List.of(),
                5,
                12,
                17,
                20L,
                false,
                true,
                false,
                true,
                List.of(),
                1,
                List.of(query)
        );
    }

    private RawResponse satisfiedSearchResponse(String probeId, String query) {
        return new RawResponse(
                "成功拿到证据",
                "",
                List.of("https://example.com/" + probeId),
                5,
                12,
                17,
                20L,
                false,
                true,
                true,
                false,
                List.of(new com.geoprobe.dto.engine.v42.SearchEvidence(
                        "web",
                        "https://example.com/" + probeId,
                        "标题",
                        "摘要",
                        "",
                        "2026-04-01T00:00:00",
                        "doubao",
                        probeId,
                        query
                )),
                1,
                List.of(query)
        );
    }
}

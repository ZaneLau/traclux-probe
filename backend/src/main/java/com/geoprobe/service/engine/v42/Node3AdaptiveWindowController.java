package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Node3AdaptiveWindowController {

    private final int increaseStep;
    private final int adjustInterval;
    private final double congestionRatioThreshold;
    private final long highLatencyMillis;
    private int currentWindow;
    private final int minWindow;
    private final int maxWindow;
    private final Deque<Boolean> congestionWindow = new ArrayDeque<>();
    private final Deque<Long> latencyWindow = new ArrayDeque<>();
    private final Deque<Boolean> failureWindow = new ArrayDeque<>();
    private int completionCount = 0;

    Node3AdaptiveWindowController(int initialWindow,
                                  int minWindow,
                                  int maxWindow,
                                  int increaseStep,
                                  int adjustInterval,
                                  double congestionRatioThreshold,
                                  long highLatencyMillis) {
        this.minWindow = Math.max(1, minWindow);
        this.maxWindow = Math.max(this.minWindow, maxWindow);
        this.currentWindow = Math.max(this.minWindow, Math.min(initialWindow, this.maxWindow));
        this.increaseStep = Math.max(1, increaseStep);
        this.adjustInterval = Math.max(1, adjustInterval);
        this.congestionRatioThreshold = Math.max(0.05d, Math.min(0.95d, congestionRatioThreshold));
        this.highLatencyMillis = Math.max(2_000L, highLatencyMillis);
    }

    int currentWindow() {
        return currentWindow;
    }

    void record(Node3ProbeOutput output) {
        completionCount++;
        boolean failed = output.dispatchStatus() == ProbeDispatchStatus.FAILED;
        boolean congested = failed && isCongestionFailure(output.failureReason());
        long latencyMs = Math.max(0L, output.rawResponse().latencyMs());
        congestionWindow.addLast(congested);
        failureWindow.addLast(failed);
        latencyWindow.addLast(latencyMs);
        trimWindows();
    }

    int maybeAdjust() {
        if (completionCount % adjustInterval != 0) {
            return currentWindow;
        }
        double congestionRatio = ratio(congestionWindow);
        double failureRatio = ratio(failureWindow);
        long p95Latency = percentile95(latencyWindow);
        if (congestionRatio >= congestionRatioThreshold
                || failureRatio >= Math.max(0.35d, congestionRatioThreshold)
                || p95Latency >= highLatencyMillis) {
            currentWindow = Math.max(minWindow, currentWindow / 2);
            return currentWindow;
        }
        currentWindow = Math.min(maxWindow, currentWindow + increaseStep);
        return currentWindow;
    }

    private void trimWindows() {
        int maxSize = Math.max(8, adjustInterval * 2);
        while (congestionWindow.size() > maxSize) {
            congestionWindow.pollFirst();
        }
        while (failureWindow.size() > maxSize) {
            failureWindow.pollFirst();
        }
        while (latencyWindow.size() > maxSize) {
            latencyWindow.pollFirst();
        }
    }

    private double ratio(Deque<Boolean> values) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        long positive = values.stream().filter(Boolean::booleanValue).count();
        return (double) positive / values.size();
    }

    private long percentile95(Deque<Long> values) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(sorted.size() * 0.95d) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private boolean isCongestionFailure(String failureReason) {
        if (!StringUtils.hasText(failureReason)) {
            return false;
        }
        String normalized = failureReason.toLowerCase();
        return normalized.contains("429")
                || normalized.contains("rate limit")
                || normalized.contains("限流")
                || normalized.contains("timeout")
                || normalized.contains("超时");
    }
}

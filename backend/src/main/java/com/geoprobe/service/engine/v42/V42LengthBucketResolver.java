package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class V42LengthBucketResolver {

    private static final int STATIC_SHORT_THRESHOLD = 80;
    private static final int STATIC_MEDIUM_THRESHOLD = 200;

    private V42LengthBucketResolver() {
    }

    static LengthBucketThresholds fromProbes(List<Node4ProbeOutput> probes) {
        List<Integer> completionTokens = new ArrayList<>();
        for (Node4ProbeOutput probe : probes) {
            if (probe == null || probe.dispatchStatus() != ProbeDispatchStatus.SUCCESS) {
                continue;
            }
            completionTokens.add(Math.max(0, probe.brandDetectionResult().responseTokenCount()));
        }
        return fromCompletionTokens(completionTokens);
    }

    static LengthBucketThresholds fromCompletionTokens(List<Integer> completionTokens) {
        if (completionTokens == null || completionTokens.isEmpty()) {
            return new LengthBucketThresholds(STATIC_SHORT_THRESHOLD, STATIC_MEDIUM_THRESHOLD);
        }
        List<Integer> sorted = new ArrayList<>(completionTokens.size());
        for (Integer value : completionTokens) {
            if (value != null) {
                sorted.add(Math.max(0, value));
            }
        }
        if (sorted.isEmpty()) {
            return new LengthBucketThresholds(STATIC_SHORT_THRESHOLD, STATIC_MEDIUM_THRESHOLD);
        }
        Collections.sort(sorted);
        int p33 = percentile(sorted, 0.33d);
        int p66 = percentile(sorted, 0.66d);
        if (p66 < p33) {
            p66 = p33;
        }
        return new LengthBucketThresholds(p33, p66);
    }

    private static int percentile(List<Integer> sortedValues, double quantile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(quantile * sortedValues.size());
        rank = Math.max(1, Math.min(sortedValues.size(), rank));
        return sortedValues.get(rank - 1);
    }

    record LengthBucketThresholds(int p33, int p66) {

        String bucketFor(int responseTokenCount) {
            int tokens = Math.max(0, responseTokenCount);
            if (tokens <= p33) {
                return "Short";
            }
            if (tokens <= p66) {
                return "Medium";
            }
            return "Long";
        }
    }
}

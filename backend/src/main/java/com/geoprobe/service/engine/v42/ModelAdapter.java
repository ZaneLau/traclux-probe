package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.RawResponse;
import org.springframework.util.StringUtils;

public interface ModelAdapter {

    RawResponse sendProbe(Node3ProbeInput probeInput, DispatchControl dispatchControl);

    record DispatchControl(
            boolean earlyStopEnabled,
            int earlyStopBrandHitThreshold,
            boolean realtimeSearchEnabled,
            String realtimeSearchProvider,
            String targetProvider,
            String targetModelId,
            boolean disableReasoning
    ) {
        public static DispatchControl earlyStopDisabled() {
            return new DispatchControl(false, 0, false, "doubao", "doubao", null, true);
        }

        public static DispatchControl earlyStopEnabled(int earlyStopBrandHitThreshold) {
            return new DispatchControl(true, earlyStopBrandHitThreshold, false, "doubao", "doubao", null, true);
        }

        public static DispatchControl withRealtimeSearch(boolean realtimeSearchEnabled, String provider) {
            return new DispatchControl(false, 0, realtimeSearchEnabled, provider, "doubao", null, true);
        }

        public static DispatchControl withTargetEngine(boolean realtimeSearchEnabled,
                                                       String realtimeSearchProvider,
                                                       String targetProvider,
                                                       String targetModelId,
                                                       boolean disableReasoning) {
            return new DispatchControl(
                    false,
                    0,
                    realtimeSearchEnabled,
                    realtimeSearchProvider,
                    targetProvider,
                    targetModelId,
                    disableReasoning
            );
        }

        public DispatchControl {
            if (earlyStopBrandHitThreshold < 0) {
                throw new IllegalArgumentException("earlyStopBrandHitThreshold 不能为负数");
            }
            if (earlyStopEnabled && earlyStopBrandHitThreshold <= 0) {
                throw new IllegalArgumentException("启用 early stop 时 earlyStopBrandHitThreshold 必须大于 0");
            }
            realtimeSearchProvider = normalizeProvider(realtimeSearchProvider);
            targetProvider = normalizeTargetProvider(targetProvider, targetModelId);
            targetModelId = normalizeOptional(targetModelId);
        }

        private static String normalizeProvider(String provider) {
            if (!StringUtils.hasText(provider)) {
                return "doubao";
            }
            String normalized = provider.trim().toLowerCase();
            if (!"doubao".equals(normalized)) {
                throw new IllegalArgumentException("realtimeSearch.provider 当前仅支持 doubao");
            }
            return normalized;
        }

        private static String normalizeTargetProvider(String provider, String modelId) {
            String normalized = normalizeOptional(provider);
            if (StringUtils.hasText(normalized)) {
                return normalized.toLowerCase();
            }
            String inferred = com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy.inferProviderFromModelId(modelId);
            return StringUtils.hasText(inferred) ? inferred : null;
        }

        private static String normalizeOptional(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim();
        }
    }
}

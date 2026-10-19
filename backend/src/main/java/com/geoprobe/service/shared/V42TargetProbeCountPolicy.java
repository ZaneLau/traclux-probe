package com.geoprobe.service.shared;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class V42TargetProbeCountPolicy {

    private int defaultTargetProbeCount = 100;
    private int maxTargetProbeCount = 1000;

    @Value("${app.engine.v42.default-target-probe-count:100}")
    void setDefaultTargetProbeCount(int value) {
        int normalized = Math.max(1, value);
        this.defaultTargetProbeCount = Math.min(normalized, maxTargetProbeCount);
    }

    @Value("${app.engine.v42.max-target-probe-count:1000}")
    void setMaxTargetProbeCount(int value) {
        this.maxTargetProbeCount = Math.max(1, value);
        if (defaultTargetProbeCount > maxTargetProbeCount) {
            this.defaultTargetProbeCount = maxTargetProbeCount;
        }
    }

    public int defaultTargetProbeCount() {
        return defaultTargetProbeCount;
    }

    public int maxTargetProbeCount() {
        return maxTargetProbeCount;
    }

    public int resolveFromTaskOrderHints(Map<String, Object> taskOrderHints) {
        Integer requested = readRequestedValue(taskOrderHints);
        return requested == null ? 0 : normalizeRequestedValue(requested);
    }

    public int resolveOrDefaultForFrontDeskPlanning(Map<String, Object> taskOrderHints) {
        int explicit = resolveFromTaskOrderHints(taskOrderHints);
        return explicit > 0 ? explicit : defaultTargetProbeCount;
    }

    public int resolveRequiredFromTaskOrderHints(Map<String, Object> taskOrderHints, String scene) {
        Integer requested = readRequestedValue(taskOrderHints);
        if (requested != null) {
            return normalizeRequestedValue(requested);
        }
        String suffix = StringUtils.hasText(scene) ? "，scene=" + scene.trim() : "";
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数配置" + suffix);
    }

    @SuppressWarnings("unchecked")
    public int resolveFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return 0;
        }
        Object businessHints = taskOrderSnapshot.get("businessHints");
        if (businessHints instanceof Map<?, ?> hints) {
            Integer requested = readRequestedValue((Map<String, Object>) hints);
            if (requested != null) {
                return normalizeRequestedValue(requested);
            }
        }
        return 0;
    }

    public int resolveRequiredFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot, String scene) {
        int strict = resolveFromTaskOrderSnapshot(taskOrderSnapshot);
        if (strict > 0) {
            return strict;
        }
        String suffix = StringUtils.hasText(scene) ? "，scene=" + scene.trim() : "";
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照" + suffix);
    }

    @SuppressWarnings("unchecked")
    public int resolveFromDiagnosisMeta(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) {
            return 0;
        }
        Object taskOrderSnapshot = meta.get("taskOrderSnapshot");
        if (taskOrderSnapshot instanceof Map<?, ?> snapshotMap) {
            return resolveFromTaskOrderSnapshot((Map<String, Object>) snapshotMap);
        }
        return 0;
    }

    public int resolveRequiredFromDiagnosisMeta(Map<String, Object> meta, String scene) {
        int strict = resolveFromDiagnosisMeta(meta);
        if (strict > 0) {
            return strict;
        }
        String suffix = StringUtils.hasText(scene) ? "，scene=" + scene.trim() : "";
        throw new IllegalStateException("V4.2 缺少合法的前台任务单探针数快照" + suffix);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeTaskOrderHints(Map<String, Object> taskOrderHints, int targetProbeCount) {
        int normalizedTarget = normalizeRequestedValue(targetProbeCount);
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (taskOrderHints != null && !taskOrderHints.isEmpty()) {
            normalized.putAll(taskOrderHints);
        }
        LinkedHashMap<String, Object> sampling = new LinkedHashMap<>();
        Object existingSampling = normalized.get("sampling");
        if (existingSampling instanceof Map<?, ?> existingSamplingMap) {
            existingSamplingMap.forEach((key, value) -> {
                if (key instanceof String text && StringUtils.hasText(text) && value != null) {
                    sampling.put(text.trim(), value);
                }
            });
        }
        sampling.put("targetProbeCount", normalizedTarget);
        normalized.put("sampling", Map.copyOf(sampling));
        normalized.remove("sampling.targetProbeCount");
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    public int normalizeRequestedValue(Integer requested) {
        if (requested == null) {
            return defaultTargetProbeCount;
        }
        int value = requested;
        if (value <= 0) {
            throw new IllegalArgumentException("sampling.targetProbeCount 必须是正整数");
        }
        if (value > maxTargetProbeCount) {
            throw new IllegalArgumentException("sampling.targetProbeCount 超出允许上限，当前上限为 " + maxTargetProbeCount);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Integer readRequestedValue(Map<String, Object> taskOrderHints) {
        if (taskOrderHints == null || taskOrderHints.isEmpty()) {
            return null;
        }
        Object sampling = taskOrderHints.get("sampling");
        if (sampling instanceof Map<?, ?> samplingMap) {
            Integer nested = parsePositiveInt(((Map<String, Object>) samplingMap).get("targetProbeCount"));
            if (nested != null) {
                return nested;
            }
        }
        return parsePositiveInt(taskOrderHints.get("sampling.targetProbeCount"));
    }

    private Integer parsePositiveInt(Object value) {
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

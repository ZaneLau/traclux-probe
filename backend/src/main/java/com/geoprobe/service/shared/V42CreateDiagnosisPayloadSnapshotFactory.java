package com.geoprobe.service.shared;

import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.LinkedHashMap;

public final class V42CreateDiagnosisPayloadSnapshotFactory {

    private V42CreateDiagnosisPayloadSnapshotFactory() {
    }

    public static Map<String, Object> build(String requestId,
                                            String edition,
                                            String engineVersion,
                                            Map<String, Object> frontDeskTaskOrder) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (StringUtils.hasText(engineVersion)) {
            payload.put("engineVersion", engineVersion.trim());
        }
        if (StringUtils.hasText(requestId)) {
            payload.put("requestId", requestId.trim());
        }
        if (StringUtils.hasText(edition)) {
            payload.put("edition", edition.trim());
        }
        if (frontDeskTaskOrder != null && !frontDeskTaskOrder.isEmpty()) {
            Map<String, Object> sanitizedTaskOrder = V42SnapshotSanitizer.sanitizeObjectMap(frontDeskTaskOrder);
            if (!sanitizedTaskOrder.isEmpty()) {
                payload.put("frontDeskTaskOrder", sanitizedTaskOrder);
            }
        }
        return payload.isEmpty() ? Map.of() : Map.copyOf(payload);
    }
}

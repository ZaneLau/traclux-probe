package com.geoprobe.dto.engine;

import java.util.Map;

public record ReportAssembleResult(
        Map<String, Object> reportData,
        Map<String, Object> summaryKpis,
        Map<String, Object> seoResult
) {
}

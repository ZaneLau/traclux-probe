package com.geoprobe.dto.workbench;

import java.util.List;

public record WorkbenchAnalysisContextRequest(
        String timePreset,
        String dateFrom,
        String dateTo,
        List<String> platforms,
        String comparison,
        String frequency
) {

    public static WorkbenchAnalysisContextRequest defaults() {
        return new WorkbenchAnalysisContextRequest(null, null, null, List.of(), null, null);
    }
}

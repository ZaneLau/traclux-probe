package com.geoprobe.service.engine.v42;

import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

public final class V42ProgressStateSupport {

    public static final int TOTAL_STEPS = 11;

    private static final int NODE3_SAMPLING_STEP = 4;
    private static final double NODE3_SAMPLING_START_PERCENT = 40.0d;
    private static final double NODE3_SAMPLING_END_PERCENT = 68.0d;

    private static final Map<String, Integer> STEP_INDEX_BY_CODE = Map.ofEntries(
            Map.entry("NODE0_STATE_SPACE", 0),
            Map.entry("NODE1_BLUEPRINTS", 1),
            Map.entry("NODE2_RENDERING", 2),
            Map.entry("QUALITY_GATE", 3),
            Map.entry("NODE3_SAMPLING", NODE3_SAMPLING_STEP),
            Map.entry("NODE4_PARSING", 5),
            Map.entry("NODE5A_KPI", 6),
            Map.entry("NODE5B_INSIGHTS", 7),
            Map.entry("NODE5C_DASHBOARD", 8),
            Map.entry("REPORT_ASSEMBLY", 9),
            Map.entry("REPORT_PROJECTION", 10),
            Map.entry("COMPLETED", TOTAL_STEPS)
    );

    private static final Map<String, Double> CANONICAL_PERCENT_BY_CODE = Map.ofEntries(
            Map.entry("NODE0_STATE_SPACE", 5.0d),
            Map.entry("NODE1_BLUEPRINTS", 12.0d),
            Map.entry("NODE2_RENDERING", 22.0d),
            Map.entry("QUALITY_GATE", 32.0d),
            Map.entry("NODE4_PARSING", 68.0d),
            Map.entry("NODE5A_KPI", 76.0d),
            Map.entry("NODE5B_INSIGHTS", 84.0d),
            Map.entry("NODE5C_DASHBOARD", 90.0d),
            Map.entry("REPORT_ASSEMBLY", 96.0d),
            Map.entry("REPORT_PROJECTION", 98.0d),
            Map.entry("COMPLETED", 100.0d)
    );

    private V42ProgressStateSupport() {
    }

    public static double samplingPercent(int completed, int total) {
        if (total <= 0) {
            return NODE3_SAMPLING_START_PERCENT;
        }
        double ratio = Math.min(1.0d, Math.max(0.0d, (double) Math.max(0, completed) / total));
        return NODE3_SAMPLING_START_PERCENT + ratio * (NODE3_SAMPLING_END_PERCENT - NODE3_SAMPLING_START_PERCENT);
    }

    public static FailureProgressState recoverFailureProgress(DiagnosisProgressSnapshot snapshot,
                                                              int successfulProbes,
                                                              int generatedProbes,
                                                              int expectedProbes) {
        int safeSuccessful = Math.max(0, successfulProbes);
        int safeGenerated = Math.max(0, generatedProbes);
        int effectiveExpected = Math.max(0, expectedProbes);
        if (effectiveExpected == 0 && snapshot != null) {
            effectiveExpected = positiveOrZero(snapshot.getSamplingExpectedTotal());
        }
        int samplingTotal = Math.max(effectiveExpected, safeGenerated);
        int samplingMissing = Math.max(0, effectiveExpected - safeGenerated);
        boolean samplingVariantShortfall = samplingMissing > 0
                || (snapshot != null && Boolean.TRUE.equals(snapshot.getSamplingVariantShortfall()));

        String snapshotStepCode = snapshot == null ? null : normalizeStepCode(snapshot.getCurrentStepCode());
        int stepsCompleted = snapshot == null ? 0 : positiveOrZero(snapshot.getStepsCompleted());
        if (stepsCompleted == 0) {
            stepsCompleted = inferStepsCompleted(snapshotStepCode, safeGenerated);
        }

        int stepsTotal = Math.max(TOTAL_STEPS, snapshot == null ? 0 : positiveOrZero(snapshot.getStepsTotal()));
        double snapshotPercent = snapshot == null || snapshot.getPercent() == null
                ? -1.0d
                : snapshot.getPercent().doubleValue();
        double inferredPercent = inferFailurePercent(snapshotStepCode, safeGenerated, samplingTotal);
        double percent = snapshotPercent < 0.0d
                ? inferredPercent
                : Math.max(snapshotPercent, inferredPercent);

        return new FailureProgressState(
                stepsCompleted,
                stepsTotal,
                clampPercent(percent),
                safeSuccessful,
                effectiveExpected,
                safeGenerated,
                samplingMissing,
                samplingVariantShortfall,
                snapshot == null ? null : snapshot.getLastEventSeq()
        );
    }

    private static int inferStepsCompleted(String currentStepCode, int generatedProbes) {
        if (currentStepCode != null) {
            Integer canonical = STEP_INDEX_BY_CODE.get(currentStepCode);
            if (canonical != null) {
                return canonical;
            }
        }
        return generatedProbes > 0 ? NODE3_SAMPLING_STEP : 0;
    }

    private static double inferFailurePercent(String currentStepCode, int generatedProbes, int samplingTotal) {
        if ("NODE3_SAMPLING".equals(currentStepCode)) {
            return samplingPercent(generatedProbes, samplingTotal);
        }
        if (currentStepCode != null) {
            Double canonical = CANONICAL_PERCENT_BY_CODE.get(currentStepCode);
            if (canonical != null) {
                return canonical;
            }
        }
        if (generatedProbes > 0) {
            return samplingPercent(generatedProbes, samplingTotal);
        }
        return 0.0d;
    }

    private static String normalizeStepCode(String currentStepCode) {
        if (!StringUtils.hasText(currentStepCode)) {
            return null;
        }
        return currentStepCode.trim();
    }

    private static int positiveOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static double clampPercent(double value) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(100.0d, value));
    }

    public record FailureProgressState(
            int stepsCompleted,
            int stepsTotal,
            double percent,
            int samplingCompleted,
            int samplingExpectedTotal,
            int samplingGeneratedTotal,
            int samplingMissingTotal,
            boolean samplingVariantShortfall,
            Long lastEventSeq
    ) {
        public BigDecimal percentAsBigDecimal() {
            return BigDecimal.valueOf(percent).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}

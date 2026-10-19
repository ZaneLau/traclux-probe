package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolcengineRewardPlanBudgetPlannerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUTPUT_FILE = Path.of("target/volcengine-reward-plan-budget-preflight.json");
    private static final Path SHUANGSHAN_OUTPUT_FILE =
            Path.of("target/light-cone-v43-shuangshan-kitchen-storage-budget-preflight.json");

    @Test
    void shouldAllocateFirstRunToSingleSafeRewardModelWhenBudgetIsEnough() {
        VolcengineRewardPlanBudgetPlanner.RewardPlanBudgetPlan plan =
                VolcengineRewardPlanBudgetPlanner.planFirstRun50(
                        Map.of("doubao-seed-2-0-lite-260215", 2_200_000L)
                );

        assertEquals("doubao-seed-2-0-lite", plan.controlPlane().modelId());
        assertEquals(50, plan.targetProbeCount());
        assertEquals(1, plan.renderAllocations().size());
        assertEquals(50, plan.renderAllocations().getFirst().probeCount());
    }

    @Test
    void shouldFailClosedWhenRewardBudgetIsMissing() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> VolcengineRewardPlanBudgetPlanner.planFirstRun50(
                        Map.of("doubao-seed-1-6-lite-251015", 40_000L)
                )
        );
        assertTrue(ex.getMessage().contains("余量不足"));
    }

    @Test
    void shouldWriteManualPreflightSnapshotWhenRewardBudgetEnvIsPresent() throws IOException {
        Map<String, Long> remainingBudgets = loadRemainingBudgetsForManualPreflight();

        VolcengineRewardPlanBudgetPlanner.RewardPlanBudgetPlan plan =
                VolcengineRewardPlanBudgetPlanner.planFirstRun50(remainingBudgets);

        Files.createDirectories(OUTPUT_FILE.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(OUTPUT_FILE.toFile(), plan.toMap());

        assertTrue(Files.exists(OUTPUT_FILE));
        assertTrue(StringUtils.hasText(plan.controlPlane().modelId()));
    }

    @Test
    void shouldWriteShuangshanFirstRun50BudgetSnapshotWhenRewardBudgetEnvIsPresent() throws IOException {
        Map<String, Long> remainingBudgets = loadRemainingBudgetsForManualPreflight();

        VolcengineRewardPlanBudgetPlanner.RewardPlanBudgetPlan plan =
                VolcengineRewardPlanBudgetPlanner.plan(
                        "shuangshan_kitchen_storage_first_run_50",
                        50,
                        new VolcengineRewardPlanBudgetPlanner.ScenarioProfile(
                                12_000L,
                                8_000L,
                                10_000L,
                                2_200L,
                                0.20d,
                                List.of(
                                        "doubao-seed-1-6-lite",
                                        "doubao-seed-1-6-flash",
                                        "doubao-seed-1-6",
                                        "doubao-seed-2-0-mini",
                                        "doubao-seed-2-0-lite",
                                        "doubao-seed-1-8",
                                        "doubao-seed-2-0-pro"
                                )
                        ),
                        remainingBudgets
                );

        Files.createDirectories(SHUANGSHAN_OUTPUT_FILE.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(SHUANGSHAN_OUTPUT_FILE.toFile(), plan.toMap());

        assertTrue(Files.exists(SHUANGSHAN_OUTPUT_FILE));
        assertEquals("shuangshan_kitchen_storage_first_run_50", plan.scenarioId());
        assertEquals(50, plan.targetProbeCount());
    }

    private static Map<String, Long> loadRemainingBudgetsForManualPreflight() throws IOException {
        try {
            return VolcengineRewardSnapshotSupport.loadFromConfiguredPath().remainingTokensByModel();
        } catch (IllegalStateException snapshotMissingOrExpired) {
            String rawBudgetText = firstNonBlank(
                    System.getenv("V42_VOLCENGINE_REWARD_MODEL_REMAINING_TOKENS"),
                    System.getProperty("V42_VOLCENGINE_REWARD_MODEL_REMAINING_TOKENS")
            );
            Assumptions.assumeTrue(
                    StringUtils.hasText(rawBudgetText),
                    "缺少奖励余量快照与 V42_VOLCENGINE_REWARD_MODEL_REMAINING_TOKENS，跳过手工 preflight 快照写出"
            );
            return VolcengineRewardPlanBudgetPlanner.parseRemainingBudgetText(rawBudgetText);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}

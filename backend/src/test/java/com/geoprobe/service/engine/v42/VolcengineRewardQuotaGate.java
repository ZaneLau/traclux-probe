package com.geoprobe.service.engine.v42;

final class VolcengineRewardQuotaGate {

    private VolcengineRewardQuotaGate() {
    }

    static void requireLiveExperimentAllowance(String runLabel, String quotaModelId) {
        String safeRunLabel = runLabel == null || runLabel.isBlank() ? "live 实验" : runLabel.trim();
        String normalizedQuotaModelId = VolcengineRewardSnapshotSupport.normalizeModelId(quotaModelId);
        try {
            VolcengineRewardSnapshotSupport.RewardSnapshot snapshot =
                    VolcengineRewardSnapshotSupport.loadFromConfiguredPath();
            if (!org.springframework.util.StringUtils.hasText(normalizedQuotaModelId)) {
                throw new IllegalStateException("缺少可校验的监控模型 ID");
            }
            if (!snapshot.remainingTokensByModel().containsKey(normalizedQuotaModelId)) {
                throw new IllegalStateException("奖励余量快照里没有目标模型额度: " + normalizedQuotaModelId);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    safeRunLabel
                            + " 已被火山奖励额度门禁阻断。先执行 bash scripts/volcengine/run_reward_budget_preflight.sh，"
                            + "确认生成 15 分钟内的新鲜快照后再重试。根因: "
                            + ex.getMessage(),
                    ex
            );
        }
    }
}

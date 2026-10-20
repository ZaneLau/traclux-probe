package com.geoprobe.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.RealtimeSearchPolicy;
import com.geoprobe.entity.v1.ModelInvocationRecord;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolcengineRewardRunAdmissionService {

    private static final Set<String> DEFAULT_GATED_PROVIDERS = Set.of("doubao", "deepseek", "kimi", "glm", "volcengine");

    private final VolcengineRewardModelIdentityResolver modelIdentityResolver;
    private final V42TaskOrderExecutionPolicyResolver taskOrderExecutionPolicyResolver;
    private final V42Node3TargetEnginePolicyResolver node3TargetEnginePolicyResolver;
    private final ObjectProvider<ModelInvocationRecordMapper> modelInvocationRecordMapperProvider;

    @Value("${app.volcengine.reward-quota-enforcement.enabled:true}")
    private boolean enabled;

    @Value("${app.volcengine.reward-quota-enforcement.max-age-minutes:2880}")
    private long snapshotMaxAgeMinutes;

    @Value("${app.volcengine.reward-quota-enforcement.gated-providers:doubao,deepseek,kimi,glm,volcengine}")
    private String gatedProvidersText;

    @Value("${app.volcengine.reward-quota-enforcement.zone-id:Asia/Shanghai}")
    private String accountingZoneIdText;

    @Value("${app.database.v1.enabled:true}")
    private boolean databaseV1Enabled;


    @Value("${llm.volcengine.realtime-fallback-endpoint-ids:doubao-seed-1-6-251015,doubao-seed-1-6-flash-250828,doubao-seed-1-8-251228,doubao-seed-2-0-lite-260215,doubao-seed-2-0-mini-260215,doubao-seed-2-0-pro-260215}")
    private String doubaoRealtimeFallbackModelIdsText;

    private final Map<String, RewardAdmissionLease> activeLeases = new ConcurrentHashMap<>();

    public void requireAllowed(String providerCode, String modelId) {
        VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity = modelIdentityResolver.resolve(providerCode, modelId);
        if (!enabled || !requiresRewardGate(identity)) {
            return;
        }
        try {
            validateIdentityAllowed(identity, "provider=%s, model=%s"
                    .formatted(identity.providerCode(), firstNonBlank(identity.runtimeModelId(), identity.monitorModelId())));
        } catch (IOException | IllegalStateException ex) {
            throw blocked(identity.providerCode(), firstNonBlank(identity.runtimeModelId(), identity.monitorModelId()),
                    "运行准入门拒绝放行：缺少可用的供应商额度基线。provider=%s, runtimeModel=%s, monitorModel=%s。先手动同步一次本地快照，再重试。根因: %s"
                            .formatted(
                                    identity.providerCode(),
                                    firstNonBlank(identity.runtimeModelId(), "-"),
                                    firstNonBlank(identity.monitorModelId(), "-"),
                                    ex.getMessage()
                            ));
        }
    }

    public boolean isAllowed(String providerCode, String modelId) {
        VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity = modelIdentityResolver.resolve(providerCode, modelId);
        if (!enabled || !requiresRewardGate(identity)) {
            return true;
        }
        try {
            validateIdentityAllowed(identity, "provider=%s, model=%s"
                    .formatted(identity.providerCode(), firstNonBlank(identity.runtimeModelId(), identity.monitorModelId())));
            return true;
        } catch (IOException | IllegalStateException ex) {
            return false;
        }
    }

    public void requireSnapshotReady(String providerCode, String scene) {
        VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity = modelIdentityResolver.resolve(providerCode, "");
        if (!enabled || !requiresRewardGateForProvider(identity.providerCode())) {
            return;
        }
        try {
            loadRuntimeAccounting("provider=%s, scene=%s".formatted(identity.providerCode(), firstNonBlank(scene, "unknown")));
        } catch (IOException | IllegalStateException ex) {
            throw blocked(identity.providerCode(), "",
                    "运行准入门拒绝放行：缺少可用的供应商额度基线。provider=%s, scene=%s。根因: %s"
                            .formatted(identity.providerCode(), firstNonBlank(scene, "unknown"), ex.getMessage()));
        }
    }

    public void requireTaskOrderReady(FrontDeskTaskOrder taskOrder, String scene) {
        if (!enabled || taskOrder == null) {
            return;
        }
        try {
            RewardDependencyPlan plan = buildPlan(taskOrder, scene);
            validatePlan(plan, loadRuntimeAccountingUnchecked(scene), scene);
        } catch (IllegalStateException ex) {
            throw planBlocked(scene, ex);
        }
    }

    public void requireTaskOrderSnapshotReady(Map<String, Object> taskOrderSnapshot, String scene) {
        if (!enabled || taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return;
        }
        try {
            RewardDependencyPlan plan = buildPlan(taskOrderSnapshot, scene);
            validatePlan(plan, loadRuntimeAccountingUnchecked(scene), scene);
        } catch (IllegalStateException ex) {
            throw planBlocked(scene, ex);
        }
    }

    public void openMainChainLease(String runKey, FrontDeskTaskOrder taskOrder, String scene) {
        if (!enabled || !StringUtils.hasText(runKey) || taskOrder == null) {
            return;
        }
        try {
            RewardDependencyPlan plan = buildPlan(taskOrder, scene);
            RewardQuotaAccounting accounting = loadRuntimeAccountingUnchecked(scene);
            RewardAdmissionLease lease = validatePlan(plan, accounting, scene);
            activeLeases.put(runKey.trim(), lease);
        } catch (IllegalStateException ex) {
            throw planBlocked(scene, ex);
        }
    }

    public void closeLease(String runKey) {
        if (!StringUtils.hasText(runKey)) {
            return;
        }
        activeLeases.remove(runKey.trim());
    }

    boolean requiresRewardGate(String providerCode, String modelId) {
        return requiresRewardGate(modelIdentityResolver.resolve(providerCode, modelId));
    }

    boolean requiresRewardGateForProvider(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return false;
        }
        return parseGatedProviders().contains(providerCode.trim().toLowerCase(Locale.ROOT));
    }

    public RewardQuotaAccounting readQuotaAccounting() throws IOException {
        VolcengineRewardSnapshotReader.RewardSnapshot snapshot =
                VolcengineRewardSnapshotReader.load(VolcengineRewardSnapshotReader.resolveConfiguredPath(), null);
        Duration age = Duration.between(snapshot.fetchedAt(), OffsetDateTime.now(ZoneId.of("UTC")));
        Duration freshnessLimit = Duration.ofMinutes(Math.max(1L, snapshotMaxAgeMinutes));
        Map<String, Long> consumedTokensByModel = loadConsumedTokensSince(snapshot.fetchedAt());
        LinkedHashMap<String, RewardQuotaModelBalance> modelBalances = new LinkedHashMap<>();
        long baselineTotal = 0L;
        long consumedTotal = 0L;
        long estimatedTotal = 0L;
        for (Map.Entry<String, Long> entry : snapshot.remainingTokensByModel().entrySet()) {
            String monitorModelId = entry.getKey();
            long baselineRemaining = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            long consumedSinceSync = Math.max(0L, consumedTokensByModel.getOrDefault(monitorModelId, 0L));
            long estimatedRemaining = Math.max(0L, baselineRemaining - consumedSinceSync);
            modelBalances.put(monitorModelId, new RewardQuotaModelBalance(
                    monitorModelId,
                    baselineRemaining,
                    consumedSinceSync,
                    estimatedRemaining,
                    estimatedRemaining > 0L
            ));
            baselineTotal += baselineRemaining;
            consumedTotal += consumedSinceSync;
            estimatedTotal += estimatedRemaining;
        }
        boolean stale = age.compareTo(freshnessLimit) > 0;
        String status;
        String message;
        if (stale) {
            status = "STALE";
            message = "供应商额度基线已过期；先手动同步新快照，再继续真实模型任务。";
        } else if (estimatedTotal <= 0L) {
            status = "EXHAUSTED";
            message = "服务器估算当前可用免费额度已经耗尽；需要先同步新快照或等待额度恢复。";
        } else {
            status = "OK";
            message = "当前运行按服务端本地额度账本判定，不再依赖运行时自动刷新。";
        }
        return new RewardQuotaAccounting(
                snapshot.path(),
                snapshot.fetchedAt(),
                age.toMinutes(),
                freshnessLimit.toMinutes(),
                status,
                message,
                runtimeAutoRefreshEnabled(),
                baselineTotal,
                consumedTotal,
                estimatedTotal,
                Map.copyOf(modelBalances)
        );
    }

    public Path resolveManagedSnapshotPath() {
        return VolcengineRewardSnapshotReader.resolveConfiguredPath();
    }

    private RewardAdmissionLease validatePlan(RewardDependencyPlan plan,
                                              RewardQuotaAccounting accounting,
                                              String scene) {
        LinkedHashSet<String> allowedMonitorModelIds = new LinkedHashSet<>();
        for (RewardDependency dependency : plan.requiredDependencies()) {
            ensureDependencyAllowed(accounting, dependency, scene);
            allowedMonitorModelIds.add(dependency.monitorModelId());
        }
        for (RewardCandidateGroup group : plan.candidateGroups()) {
            List<RewardDependency> admitted = group.candidates().stream()
                    .filter(candidate -> balanceAllows(accounting, candidate.monitorModelId()))
                    .toList();
            if (admitted.isEmpty()) {
                String candidates = group.candidates().stream()
                        .map(candidate -> firstNonBlank(candidate.runtimeModelId(), candidate.monitorModelId(), "-"))
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException(
                        "供应商额度基线无法覆盖可用候选池。scene=%s, group=%s, candidates=[%s], snapshot=%s"
                                .formatted(firstNonBlank(scene, "unknown"), group.groupCode(), candidates, accounting.snapshotPath())
                );
            }
            admitted.stream()
                    .map(RewardDependency::monitorModelId)
                    .forEach(allowedMonitorModelIds::add);
        }
        return new RewardAdmissionLease(accounting.snapshotPath(), accounting.fetchedAt(), Set.copyOf(allowedMonitorModelIds));
    }

    private void validateIdentityAllowed(VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity,
                                         String reason) throws IOException {
        if (!StringUtils.hasText(identity.monitorModelId())) {
            throw new IllegalStateException("无法为运行模型映射奖励监控模型");
        }
        RewardAdmissionLease lease = resolveActiveLease();
        if (lease != null) {
            lease.requireAllowed(identity);
            return;
        }
        RewardQuotaAccounting accounting = loadRuntimeAccounting(reason);
        if (!balanceExists(accounting, identity.monitorModelId())) {
            throw new IllegalStateException(
                    "目标模型不在供应商额度基线中。provider=%s, runtimeModel=%s, monitorModel=%s, snapshot=%s"
                            .formatted(
                                    identity.providerCode(),
                                    firstNonBlank(identity.runtimeModelId(), "-"),
                                    identity.monitorModelId(),
                                    accounting.snapshotPath()
                            )
            );
        }
        RewardQuotaModelBalance balance = accounting.modelBalances().get(identity.monitorModelId());
        if (balance == null || !balance.runnable()) {
            throw new IllegalStateException(
                    "目标模型的服务端估算额度不足。provider=%s, runtimeModel=%s, monitorModel=%s, estimatedRemainingTokens=%s, snapshot=%s"
                            .formatted(
                                    identity.providerCode(),
                                    firstNonBlank(identity.runtimeModelId(), "-"),
                                    identity.monitorModelId(),
                                    balance == null ? 0L : balance.estimatedRemainingTokens(),
                                    accounting.snapshotPath()
                            )
            );
        }
    }

    private RewardDependencyPlan buildPlan(FrontDeskTaskOrder taskOrder, String scene) {
        V42TaskOrderExecutionPolicy executionPolicy =
                taskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderHints(taskOrder.businessHints(), scene);
        Node3TargetEnginePolicy targetEnginePolicy =
                node3TargetEnginePolicyResolver.resolveRequiredFromTaskOrder(taskOrder, scene);
        return buildPlan(executionPolicy.realtimeSearchPolicy(), targetEnginePolicy, scene);
    }

    private RewardDependencyPlan buildPlan(Map<String, Object> taskOrderSnapshot, String scene) {
        V42TaskOrderExecutionPolicy executionPolicy =
                taskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, scene);
        Node3TargetEnginePolicy targetEnginePolicy =
                node3TargetEnginePolicyResolver.resolveRequiredFromTaskOrderSnapshot(taskOrderSnapshot, scene);
        return buildPlan(executionPolicy.realtimeSearchPolicy(), targetEnginePolicy, scene);
    }

    private RewardDependencyPlan buildPlan(RealtimeSearchPolicy realtimeSearchPolicy,
                                           Node3TargetEnginePolicy targetEnginePolicy,
                                           String scene) {
        validateNode3Compatibility(realtimeSearchPolicy, targetEnginePolicy, scene);
        LinkedHashMap<String, RewardDependency> required = new LinkedHashMap<>();
        addRequired(required, newDependency("deepseek_main_chain", "deepseek", modelIdentityResolver.defaultDeepseekRuntimeModelId()));

        List<RewardCandidateGroup> candidateGroups = new ArrayList<>();
        if (realtimeSearchPolicy != null && realtimeSearchPolicy.enabled()) {
            List<RewardDependency> doubaoCandidates = resolveDoubaoRealtimeCandidates();
            if (!doubaoCandidates.isEmpty()) {
                candidateGroups.add(new RewardCandidateGroup("node3_doubao_realtime_pool", doubaoCandidates));
            }
        } else if (targetEnginePolicy != null) {
            RewardDependency targetDependency = newDependency("node3_target_engine", targetEnginePolicy.provider(), targetEnginePolicy.modelId());
            addRequired(required, targetDependency);
        }
        return new RewardDependencyPlan(List.copyOf(required.values()), List.copyOf(candidateGroups));
    }

    private List<RewardDependency> resolveDoubaoRealtimeCandidates() {
        LinkedHashMap<String, RewardDependency> ordered = new LinkedHashMap<>();
        addCandidate(ordered, newDependency("node3_doubao_realtime_primary", "doubao",
                modelIdentityResolver.defaultDoubaoRealtimeRuntimeModelId()));
        for (String fallback : parseFallbackCandidates()) {
            addCandidate(ordered, newDependency("node3_doubao_realtime_fallback", "doubao", fallback));
        }
        return List.copyOf(ordered.values());
    }

    private RewardDependency newDependency(String stageCode, String providerCode, String runtimeModelId) {
        VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity =
                modelIdentityResolver.resolve(providerCode, runtimeModelId);
        return new RewardDependency(
                stageCode,
                identity.providerCode(),
                identity.runtimeModelId(),
                identity.monitorModelId(),
                identity.rewardManaged()
        );
    }

    private void addRequired(Map<String, RewardDependency> collector, RewardDependency dependency) {
        if (dependency == null || !dependency.rewardManaged()) {
            return;
        }
        collector.putIfAbsent(dependencyKey(dependency), dependency);
    }

    private void addCandidate(Map<String, RewardDependency> collector, RewardDependency dependency) {
        if (dependency == null || !dependency.rewardManaged()) {
            return;
        }
        collector.putIfAbsent(dependencyKey(dependency), dependency);
    }

    private String dependencyKey(RewardDependency dependency) {
        return "%s|%s|%s".formatted(
                firstNonBlank(dependency.providerCode(), "unknown"),
                firstNonBlank(dependency.runtimeModelId(), "-"),
                firstNonBlank(dependency.monitorModelId(), "-")
        );
    }

    private void ensureDependencyAllowed(RewardQuotaAccounting accounting,
                                         RewardDependency dependency,
                                         String scene) {
        if (!StringUtils.hasText(dependency.monitorModelId())) {
            throw new IllegalStateException(
                    "奖励依赖缺少监控模型映射。scene=%s, stage=%s, provider=%s, runtimeModel=%s"
                            .formatted(
                                    firstNonBlank(scene, "unknown"),
                                    dependency.stageCode(),
                                    firstNonBlank(dependency.providerCode(), "unknown"),
                                    firstNonBlank(dependency.runtimeModelId(), "-")
                            )
            );
        }
        if (!balanceExists(accounting, dependency.monitorModelId())) {
            throw new IllegalStateException(
                    "供应商额度基线未覆盖主链必需模型。scene=%s, stage=%s, provider=%s, runtimeModel=%s, monitorModel=%s, snapshot=%s"
                            .formatted(
                                    firstNonBlank(scene, "unknown"),
                                    dependency.stageCode(),
                                    firstNonBlank(dependency.providerCode(), "unknown"),
                                    firstNonBlank(dependency.runtimeModelId(), "-"),
                                    dependency.monitorModelId(),
                                    accounting.snapshotPath()
                            )
            );
        }
        if (!balanceAllows(accounting, dependency.monitorModelId())) {
            RewardQuotaModelBalance balance = accounting.modelBalances().get(dependency.monitorModelId());
            throw new IllegalStateException(
                    "主链必需模型的服务端估算额度不足。scene=%s, stage=%s, provider=%s, runtimeModel=%s, monitorModel=%s, estimatedRemainingTokens=%s, snapshot=%s"
                            .formatted(
                                    firstNonBlank(scene, "unknown"),
                                    dependency.stageCode(),
                                    firstNonBlank(dependency.providerCode(), "unknown"),
                                    firstNonBlank(dependency.runtimeModelId(), "-"),
                                    dependency.monitorModelId(),
                                    balance == null ? 0L : balance.estimatedRemainingTokens(),
                                    accounting.snapshotPath()
                            )
            );
        }
    }

    private RewardAdmissionLease resolveActiveLease() {
        LlmCallContext context = LlmCallContext.get();
        if (context == null || !StringUtils.hasText(context.getDiagnosisId())) {
            return null;
        }
        return activeLeases.get(context.getDiagnosisId().trim());
    }

    private boolean requiresRewardGate(VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity) {
        if (identity == null || !identity.rewardManaged()) {
            return false;
        }
        if (StringUtils.hasText(identity.providerCode()) && parseGatedProviders().contains(identity.providerCode())) {
            return true;
        }
        return looksLikeVolcengineModel(identity.runtimeModelId()) || looksLikeVolcengineModel(identity.monitorModelId());
    }

    private boolean looksLikeVolcengineModel(String modelId) {
        String normalized = VolcengineRewardSnapshotReader.normalizeModelId(modelId);
        return normalized.startsWith("doubao-")
                || normalized.startsWith("deepseek-")
                || normalized.startsWith("kimi-")
                || normalized.startsWith("glm-");
    }

    private void validateNode3Compatibility(RealtimeSearchPolicy realtimeSearchPolicy,
                                            Node3TargetEnginePolicy targetEnginePolicy,
                                            String scene) {
        if (realtimeSearchPolicy != null
                && realtimeSearchPolicy.enabled()
                && targetEnginePolicy != null
                && !"doubao".equalsIgnoreCase(targetEnginePolicy.provider())) {
            throw new IllegalStateException(
                    "samplingRealtimeSearch 当前仅支持 doubao。scene=%s, provider=%s"
                            .formatted(firstNonBlank(scene, "unknown"), targetEnginePolicy.provider())
            );
        }
    }

    private List<String> parseFallbackCandidates() {
        if (!StringUtils.hasText(doubaoRealtimeFallbackModelIdsText)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : doubaoRealtimeFallbackModelIdsText.split("[,;\\s]+")) {
            String normalized = VolcengineRewardSnapshotReader.normalizeModelId(token);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private Set<String> parseGatedProviders() {
        if (!StringUtils.hasText(gatedProvidersText)) {
            return DEFAULT_GATED_PROVIDERS;
        }
        LinkedHashSet<String> providers = new LinkedHashSet<>();
        for (String token : gatedProvidersText.split("[,;]")) {
            String normalized = normalizeProvider(token);
            if (StringUtils.hasText(normalized)) {
                providers.add(normalized);
            }
        }
        return providers.isEmpty() ? DEFAULT_GATED_PROVIDERS : Set.copyOf(providers);
    }

    private String normalizeProvider(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return "";
        }
        return providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private RewardQuotaAccounting loadRuntimeAccountingUnchecked(String reason) {
        try {
            return loadRuntimeAccounting(reason);
        } catch (IOException | IllegalStateException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private RewardQuotaAccounting loadRuntimeAccounting(String reason) throws IOException {
        RewardQuotaAccounting accounting = readQuotaAccounting();
        if ("STALE".equals(accounting.status())) {
            throw new IllegalStateException("供应商额度基线已过期；社区包不包含运营快照采集与自动刷新。reason="
                    + firstNonBlank(reason, "unknown"));
        }
        return accounting;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private LlmRunAdmissionBlockedException blocked(String providerCode, String modelId, String reason) {
        log.warn("Volcengine reward admission blocked. provider={}, model={}, reason={}", providerCode, modelId, reason);
        return new LlmRunAdmissionBlockedException(providerCode, modelId, reason);
    }

    private LlmRunAdmissionBlockedException planBlocked(String scene, IllegalStateException ex) {
        return blocked("main_chain", "",
                "运行准入门拒绝放行：主链依赖未就绪。当前已切换为低频快照 + 服务端本地额度账本。scene=%s。根因: %s"
                        .formatted(firstNonBlank(scene, "unknown"), ex.getMessage()));
    }

    private boolean balanceExists(RewardQuotaAccounting accounting, String monitorModelId) {
        return accounting != null
                && StringUtils.hasText(monitorModelId)
                && accounting.modelBalances().containsKey(monitorModelId);
    }

    private boolean balanceAllows(RewardQuotaAccounting accounting, String monitorModelId) {
        RewardQuotaModelBalance balance = accounting == null ? null : accounting.modelBalances().get(monitorModelId);
        return balance != null && balance.runnable();
    }

    private boolean runtimeAutoRefreshEnabled() {
        return false;
    }

    private Map<String, Long> loadConsumedTokensSince(OffsetDateTime fetchedAt) {
        if (!databaseV1Enabled || fetchedAt == null) {
            return Map.of();
        }
        ModelInvocationRecordMapper mapper = modelInvocationRecordMapperProvider.getIfAvailable();
        if (mapper == null) {
            return Map.of();
        }
        LocalDateTime startAt = fetchedAt.atZoneSameInstant(resolveAccountingZoneId()).toLocalDateTime();
        LocalDateTime endAt = LocalDateTime.now(resolveAccountingZoneId());
        List<ModelInvocationRecord> records;
        try {
            records = mapper.selectList(new LambdaQueryWrapper<ModelInvocationRecord>()
                    .ge(ModelInvocationRecord::getCreatedAt, startAt)
                    .lt(ModelInvocationRecord::getCreatedAt, endAt)
                    .in(ModelInvocationRecord::getProvider, parseGatedProviders())
                    .select(
                            ModelInvocationRecord::getProvider,
                            ModelInvocationRecord::getModelId,
                            ModelInvocationRecord::getTotalTokens
                    ));
        } catch (RuntimeException ex) {
            log.warn("Failed to load provider quota accounting records. reason={}", ex.getMessage());
            return Map.of();
        }
        LinkedHashMap<String, Long> consumed = new LinkedHashMap<>();
        for (ModelInvocationRecord record : records) {
            if (record == null || record.getTotalTokens() == null || record.getTotalTokens() <= 0) {
                continue;
            }
            VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity =
                    modelIdentityResolver.resolve(record.getProvider(), record.getModelId());
            if (!identity.rewardManaged() || !StringUtils.hasText(identity.monitorModelId())) {
                continue;
            }
            consumed.merge(identity.monitorModelId(), record.getTotalTokens().longValue(), Long::sum);
        }
        return Map.copyOf(consumed);
    }

    private ZoneId resolveAccountingZoneId() {
        if (!StringUtils.hasText(accountingZoneIdText)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(accountingZoneIdText.trim());
        } catch (DateTimeException ex) {
            log.warn("Invalid reward quota accounting zone id '{}', fallback to Asia/Shanghai", accountingZoneIdText);
            return ZoneId.of("Asia/Shanghai");
        }
    }

    record RewardDependency(String stageCode,
                            String providerCode,
                            String runtimeModelId,
                            String monitorModelId,
                            boolean rewardManaged) {
    }

    record RewardCandidateGroup(String groupCode,
                                List<RewardDependency> candidates) {
    }

    record RewardDependencyPlan(List<RewardDependency> requiredDependencies,
                                List<RewardCandidateGroup> candidateGroups) {
    }

    record RewardAdmissionLease(Path snapshotPath,
                                java.time.OffsetDateTime snapshotFetchedAt,
                                Set<String> allowedMonitorModelIds) {

        void requireAllowed(VolcengineRewardModelIdentityResolver.ResolvedModelIdentity identity) {
            if (!StringUtils.hasText(identity.monitorModelId()) || !allowedMonitorModelIds.contains(identity.monitorModelId())) {
                throw new IllegalStateException(
                        "当前任务租约未覆盖该模型。runtimeModel=%s, monitorModel=%s, snapshot=%s, fetchedAt=%s"
                                .formatted(
                                        identity.runtimeModelId(),
                                        identity.monitorModelId(),
                                        snapshotPath,
                                        snapshotFetchedAt
                                )
                );
            }
        }
    }

    public record RewardQuotaAccounting(Path snapshotPath,
                                        OffsetDateTime fetchedAt,
                                        long ageMinutes,
                                        long freshnessLimitMinutes,
                                        String status,
                                        String message,
                                        boolean runtimeAutoRefreshEnabled,
                                        long baselineRemainingTokens,
                                        long consumedTokensSinceSync,
                                        long estimatedRemainingTokens,
                                        Map<String, RewardQuotaModelBalance> modelBalances) {
    }

    public record RewardQuotaModelBalance(String monitorModelId,
                                          long baselineRemainingTokens,
                                          long consumedTokensSinceSync,
                                          long estimatedRemainingTokens,
                                          boolean runnable) {
    }
}

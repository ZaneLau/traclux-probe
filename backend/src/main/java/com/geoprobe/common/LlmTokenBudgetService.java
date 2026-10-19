package com.geoprobe.common;

import com.geoprobe.mapper.LlmCallLogMapper;
import com.geoprobe.mapper.v1.ModelInvocationRecordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmTokenBudgetService {

    private static final int MODULE_NODE3 = 3;
    private static final String NODE_CODE_NODE3_SAMPLING = "NODE3_SAMPLING";
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final LlmCallLogMapper llmCallLogMapper;
    private final ObjectProvider<ModelInvocationRecordMapper> modelInvocationRecordMapperProvider;
    private final VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;

    @Value("${app.llm-budget.enabled:true}")
    private boolean enabled;

    @Value("${app.llm-budget.default-daily-token-budget-per-model:${app.engine.sampling.realtime-search.model-routing.daily-token-budget-per-model:1900000}}")
    private long defaultDailyTokenBudgetPerModel;

    @Value("${app.llm-budget.default-reservation-tokens:12000}")
    private long defaultReservationTokens;

    @Value("${app.llm-budget.zone-id:Asia/Shanghai}")
    private String zoneIdText;

    @Value("${app.llm-budget.daily-token-budget-overrides:}")
    private String budgetOverridesText;

    @Value("${app.database.v1.enabled:true}")
    private boolean databaseV1Enabled;

    private final Map<BudgetKey, BudgetCounter> counters = new ConcurrentHashMap<>();

    private volatile ZoneId zoneId = DEFAULT_ZONE_ID;
    private volatile LocalDate currentDate = LocalDate.now(DEFAULT_ZONE_ID);
    private volatile Map<String, Long> parsedBudgetOverrides = Map.of();

    @PostConstruct
    void initialize() {
        refreshRuntimeConfig();
    }

    public BudgetLease reserveOrThrow(String budgetProvider, String modelId) {
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        if (key == null) {
            return BudgetLease.noop();
        }
        if (volcengineRewardRunAdmissionService != null) {
            volcengineRewardRunAdmissionService.requireAllowed(key.provider(), key.modelId());
        }
        long budgetLimit = resolveBudgetLimit(key);
        if (!enabled || budgetLimit <= 0L) {
            return BudgetLease.noop();
        }
        BudgetCounter counter = counterFor(key);
        synchronized (counter.monitor()) {
            long consumed = counter.actualTokens().get() + counter.reservedTokens().get();
            long remaining = budgetLimit - consumed;
            if (remaining <= 0L) {
                throw new LlmTokenBudgetExceededException(key.provider(), key.modelId(), budgetLimit, consumed);
            }
            long reservation = Math.min(resolveReservationTokens(), remaining);
            counter.reservedTokens().addAndGet(reservation);
            return new BudgetLease(key.provider(), key.modelId(), reservation, budgetLimit, false);
        }
    }

    public void commit(BudgetLease lease, Integer actualTokens) {
        if (lease == null || lease.noOp()) {
            return;
        }
        BudgetCounter counter = counterFor(new BudgetKey(lease.provider(), lease.modelId()));
        synchronized (counter.monitor()) {
            long reserved = counter.reservedTokens().updateAndGet(current -> Math.max(0L, current - lease.reservedTokens()));
            long delta = Math.max(0L, actualTokens == null ? 0L : actualTokens.longValue());
            counter.actualTokens().addAndGet(delta);
            if (log.isDebugEnabled()) {
                log.debug("LLM token budget committed. provider={}, model={}, delta={}, reservedRemaining={}, actual={}",
                        lease.provider(),
                        lease.modelId(),
                        delta,
                        reserved,
                        counter.actualTokens().get());
            }
        }
    }

    public void release(BudgetLease lease) {
        if (lease == null || lease.noOp()) {
            return;
        }
        BudgetCounter counter = counterFor(new BudgetKey(lease.provider(), lease.modelId()));
        synchronized (counter.monitor()) {
            counter.reservedTokens().updateAndGet(current -> Math.max(0L, current - lease.reservedTokens()));
        }
    }

    public void markExhausted(String budgetProvider, String modelId) {
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        if (key == null) {
            return;
        }
        long budgetLimit = resolveBudgetLimit(key);
        if (!enabled || budgetLimit <= 0L) {
            return;
        }
        BudgetCounter counter = counterFor(key);
        synchronized (counter.monitor()) {
            counter.actualTokens().updateAndGet(current -> Math.max(current, budgetLimit));
        }
    }

    public boolean isBudgetAvailable(String budgetProvider, String modelId) {
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        if (key == null) {
            return true;
        }
        if (volcengineRewardRunAdmissionService != null
                && !volcengineRewardRunAdmissionService.isAllowed(key.provider(), key.modelId())) {
            return false;
        }
        long budgetLimit = resolveBudgetLimit(key);
        if (!enabled || budgetLimit <= 0L) {
            return true;
        }
        return readEffectiveUsage(key) < budgetLimit;
    }

    public long readRemainingBudget(String budgetProvider, String modelId) {
        return recommendConcurrency(budgetProvider, modelId, 1).remainingTokens();
    }

    public int recommendMaxConcurrentReservations(String budgetProvider, String modelId, int desiredConcurrency) {
        return recommendConcurrency(budgetProvider, modelId, desiredConcurrency).effectiveConcurrency();
    }

    public ConcurrencyRecommendation recommendConcurrency(String budgetProvider, String modelId, int desiredConcurrency) {
        int normalizedDesired = Math.max(1, desiredConcurrency);
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        if (key == null) {
            return ConcurrencyRecommendation.unmanaged(normalizedDesired);
        }
        if (volcengineRewardRunAdmissionService != null
                && !volcengineRewardRunAdmissionService.isAllowed(key.provider(), key.modelId())) {
            return new ConcurrencyRecommendation(
                    key.provider(),
                    key.modelId(),
                    normalizedDesired,
                    1,
                    0L,
                    resolveBudgetLimit(key),
                    resolveReservationTokens(),
                    true
            );
        }
        long budgetLimit = resolveBudgetLimit(key);
        if (!enabled || budgetLimit <= 0L) {
            return ConcurrencyRecommendation.unmanaged(key.provider(), key.modelId(), normalizedDesired);
        }
        long remaining = Math.max(0L, budgetLimit - readEffectiveUsage(key));
        long reservation = resolveReservationTokens();
        int effectiveConcurrency = normalizedDesired;
        if (remaining <= 0L) {
            effectiveConcurrency = 1;
        } else if (reservation > 0L) {
            long safeParallelism = remaining / reservation;
            if (safeParallelism <= 0L) {
                safeParallelism = 1L;
            }
            effectiveConcurrency = (int) Math.max(1L, Math.min(normalizedDesired, safeParallelism));
        }
        return new ConcurrencyRecommendation(
                key.provider(),
                key.modelId(),
                normalizedDesired,
                effectiveConcurrency,
                remaining,
                budgetLimit,
                reservation,
                true
        );
    }

    public long readEffectiveUsage(String budgetProvider, String modelId) {
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        if (key == null) {
            return 0L;
        }
        return readEffectiveUsage(key);
    }

    long resolveBudgetLimitForTest(String budgetProvider, String modelId) {
        BudgetKey key = normalizeKey(budgetProvider, modelId);
        return key == null ? 0L : resolveBudgetLimit(key);
    }

    void refreshRuntimeConfig() {
        zoneId = parseZoneId(zoneIdText);
        currentDate = LocalDate.now(zoneId);
        parsedBudgetOverrides = parseOverrides(budgetOverridesText);
        counters.clear();
    }

    private long readEffectiveUsage(BudgetKey key) {
        BudgetCounter counter = counterFor(key);
        return Math.max(0L, counter.actualTokens().get() + counter.reservedTokens().get());
    }

    private BudgetCounter counterFor(BudgetKey key) {
        rollDayIfNeeded();
        return counters.computeIfAbsent(key, this::loadBudgetCounter);
    }

    private BudgetCounter loadBudgetCounter(BudgetKey key) {
        LocalDateTime startAt = currentDate.atStartOfDay();
        LocalDateTime endAt = startAt.plusDays(1);
        long nonNode3 = safeLong(llmCallLogMapper.sumTokensByModelExcludingModuleBetween(
                key.modelId(),
                MODULE_NODE3,
                startAt,
                endAt
        ));
        long node3FromLlmLogs = safeLong(llmCallLogMapper.sumTokensByModelAndModuleBetween(
                key.modelId(),
                MODULE_NODE3,
                startAt,
                endAt
        ));
        long node3FromInvocations = 0L;
        if (databaseV1Enabled) {
            ModelInvocationRecordMapper mapper = modelInvocationRecordMapperProvider.getIfAvailable();
            if (mapper != null) {
                try {
                    node3FromInvocations = safeLong(mapper.sumTotalTokensByModelAndNodeBetween(
                            key.modelId(),
                            NODE_CODE_NODE3_SAMPLING,
                            startAt,
                            endAt
                    ));
                } catch (RuntimeException ex) {
                    log.warn("Failed to read model_invocations budget baseline. model={}, reason={}", key.modelId(), ex.getMessage());
                }
            }
        }
        long baseline = nonNode3 + Math.max(node3FromLlmLogs, node3FromInvocations);
        return new BudgetCounter(new Object(), new AtomicLong(Math.max(0L, baseline)), new AtomicLong(0L));
    }

    private void rollDayIfNeeded() {
        LocalDate today = LocalDate.now(zoneId);
        if (today.equals(currentDate)) {
            return;
        }
        synchronized (this) {
            LocalDate refreshed = LocalDate.now(zoneId);
            if (refreshed.equals(currentDate)) {
                return;
            }
            currentDate = refreshed;
            counters.clear();
        }
    }

    private long resolveBudgetLimit(BudgetKey key) {
        if (!enabled) {
            return 0L;
        }
        Long exact = parsedBudgetOverrides.get(key.overrideKey());
        if (exact != null) {
            return Math.max(0L, exact);
        }
        Long byModel = parsedBudgetOverrides.get(key.modelId());
        if (byModel != null) {
            return Math.max(0L, byModel);
        }
        Long byProvider = parsedBudgetOverrides.get(key.provider());
        if (byProvider != null) {
            return Math.max(0L, byProvider);
        }
        return Math.max(0L, defaultDailyTokenBudgetPerModel);
    }

    private long resolveReservationTokens() {
        return Math.max(1L, defaultReservationTokens);
    }

    private BudgetKey normalizeKey(String budgetProvider, String modelId) {
        if (!StringUtils.hasText(modelId)) {
            return null;
        }
        String normalizedModelId = modelId.trim();
        String normalizedProvider = StringUtils.hasText(budgetProvider)
                ? budgetProvider.trim().toLowerCase(Locale.ROOT)
                : inferProviderFromModel(normalizedModelId);
        return new BudgetKey(normalizedProvider, normalizedModelId);
    }

    private ZoneId parseZoneId(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ZONE_ID;
        }
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException ex) {
            log.warn("Invalid llm budget zone id '{}', fallback to {}", value, DEFAULT_ZONE_ID);
            return DEFAULT_ZONE_ID;
        }
    }

    private Map<String, Long> parseOverrides(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        Map<String, Long> overrides = new HashMap<>();
        String[] entries = raw.split("[,;]");
        for (String entry : entries) {
            if (!StringUtils.hasText(entry) || !entry.contains("=")) {
                continue;
            }
            String[] parts = entry.split("=", 2);
            String key = normalizeOverrideKey(parts[0]);
            String valueText = parts[1] == null ? "" : parts[1].trim();
            if (!StringUtils.hasText(key) || !StringUtils.hasText(valueText)) {
                continue;
            }
            try {
                overrides.put(key, Math.max(0L, Long.parseLong(valueText)));
            } catch (NumberFormatException ex) {
                log.warn("Ignoring invalid llm budget override '{}'", entry.trim());
            }
        }
        return Collections.unmodifiableMap(overrides);
    }

    private String normalizeOverrideKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.contains(":")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        String[] parts = trimmed.split(":", 2);
        if (!StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            return null;
        }
        return parts[0].trim().toLowerCase(Locale.ROOT) + ":" + parts[1].trim();
    }

    private String inferProviderFromModel(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("doubao") || normalized.startsWith("ep-")) {
            return "doubao";
        }
        if (normalized.startsWith("glm")) {
            return "glm";
        }
        if (normalized.startsWith("kimi")) {
            return "kimi";
        }
        if (normalized.startsWith("gpt-") || normalized.startsWith("o1") || normalized.startsWith("o3") || normalized.startsWith("o4")) {
            return "chatgpt";
        }
        if (normalized.startsWith("minimax")) {
            return "minimax";
        }
        if (normalized.startsWith("deepseek")) {
            return "deepseek";
        }
        return "default";
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private record BudgetKey(String provider, String modelId) {

        private String overrideKey() {
            return provider + ":" + modelId;
        }
    }

    private record BudgetCounter(Object monitor, AtomicLong actualTokens, AtomicLong reservedTokens) {
    }

    public record ConcurrencyRecommendation(String provider,
                                            String modelId,
                                            int desiredConcurrency,
                                            int effectiveConcurrency,
                                            long remainingTokens,
                                            long budgetLimit,
                                            long reservationTokens,
                                            boolean budgetManaged) {

        public ConcurrencyRecommendation {
            desiredConcurrency = Math.max(1, desiredConcurrency);
            effectiveConcurrency = Math.max(1, Math.min(desiredConcurrency, effectiveConcurrency));
            remainingTokens = Math.max(0L, remainingTokens);
            budgetLimit = Math.max(0L, budgetLimit);
            reservationTokens = Math.max(0L, reservationTokens);
        }

        public boolean limited() {
            return budgetManaged && effectiveConcurrency < desiredConcurrency;
        }

        private static ConcurrencyRecommendation unmanaged(int desiredConcurrency) {
            return unmanaged(null, null, desiredConcurrency);
        }

        private static ConcurrencyRecommendation unmanaged(String provider, String modelId, int desiredConcurrency) {
            int normalizedDesired = Math.max(1, desiredConcurrency);
            return new ConcurrencyRecommendation(
                    provider,
                    modelId,
                    normalizedDesired,
                    normalizedDesired,
                    Long.MAX_VALUE,
                    0L,
                    0L,
                    false
            );
        }
    }

    public record BudgetLease(String provider, String modelId, long reservedTokens, long budgetLimit, boolean noOp) {

        public static BudgetLease noop() {
            return new BudgetLease("noop", "noop", 0L, 0L, true);
        }
    }
}

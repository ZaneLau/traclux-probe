package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpaceProbePlanExecuteOnceResponse;
import com.geoprobe.dto.brandspace.BrandSpaceProbePlanView;
import com.geoprobe.dto.request.BrandSpaceProbePlanConfirmRequest;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.entity.v1.ProbePlanRecord;
import com.geoprobe.entity.v1.ProbePlanRecordItem;
import com.geoprobe.entity.v1.ProbePlanVersion;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordItemMapper;
import com.geoprobe.mapper.v1.ProbePlanRecordMapper;
import com.geoprobe.mapper.v1.ProbePlanVersionMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BrandSpaceProbePlanReadService {

    private static final String CONTRACT_VERSION = "brand_space_probe_plan_b1";
    private static final int STATUS_ACTIVE = 1;
    private static final int RECORD_LIMIT = 20;
    private static final String ACTION_KEEP = "KEEP";
    private static final String EXECUTE_MODE_RECURRING = "RECURRING";
    private static final String EXECUTE_MODE_ONE_TIME = "ONE_TIME";

    private final ServiceAccessService serviceAccessService;
    private final BrandScopeMapper brandScopeMapper;
    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;
    private final ProbePlanVersionMapper probePlanVersionMapper;
    private final ProbePlanRecordMapper probePlanRecordMapper;
    private final ProbePlanRecordItemMapper probePlanRecordItemMapper;
    private final ProbePlanService probePlanService;
    private final BrandSpaceRunConfigService brandSpaceRunConfigService;
    private final BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    private final BrandSpaceTrackingSchedulerService brandSpaceTrackingSchedulerService;

    public BrandSpaceProbePlanView getProbePlan(String userId, String brandSpaceId) {
        BrandScope scope = loadScope(userId, brandSpaceId);
        return buildView(scope, loadCurrentVersion(scope.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandSpaceProbePlanView confirmCurrentPlan(String userId,
                                                      String brandSpaceId,
                                                      BrandSpaceProbePlanConfirmRequest request) {
        BrandScope scope = loadScope(userId, brandSpaceId);
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(scope, null);
        String targetModelProduct = StringUtils.hasText(request == null ? null : request.getTargetModelProduct())
                ? request.getTargetModelProduct().trim()
                : currentRunConfig.targetModelProduct();
        int creditsPerProbe = brandSpaceRunCreditPolicy.resolveCreditsPerProbe(serviceAccessService.getServiceAccess(userId));
        ProbePlanService.SnapshotCommand command = new ProbePlanService.SnapshotCommand(
                scope.getOrganizationId(),
                scope.getBrandId(),
                scope.getId(),
                scope.getScopeName(),
                userId,
                null,
                request == null ? null : request.getPlannedProbeCount(),
                targetModelProduct,
                creditsPerProbe
        );
        if (isOneTimeMode(request == null ? null : request.getExecuteModeCode())) {
            probePlanService.confirmCurrentConfigPlanOnce(command);
        } else {
            probePlanService.confirmCurrentConfigPlan(command);
        }
        return buildView(scope, loadCurrentVersion(scope.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public BrandSpaceProbePlanExecuteOnceResponse executeCurrentPlanOnce(String userId, String brandSpaceId) {
        BrandScope scope = loadScope(userId, brandSpaceId);
        ProbePlanVersion currentVersion = loadCurrentVersion(scope.getId());
        if (currentVersion == null || !StringUtils.hasText(currentVersion.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先确认探针方案，再立即执行一次");
        }
        if (!Objects.equals(defaultInt(currentVersion.getConfigGenerationSnapshot()), defaultInt(scope.getConfigGeneration()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前配置已变化，请先确认新的探针方案");
        }
        BrandSpaceTrackingSchedulerService.ReplayScheduledRunResult result =
                brandSpaceTrackingSchedulerService.executeBrandSpaceOnce(scope.getId());
        BrandSpaceProbePlanView latestView = buildView(scope, loadCurrentVersion(scope.getId()));
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(scope, latestView == null || latestView.getCurrentPlan() == null
                        ? null
                        : latestView.getCurrentPlan().getActiveRecurringProbeCount());
        int resolvedTargetProbeCount = result.targetProbeCount() == null
                ? currentRunConfig.targetProbeCount()
                : result.targetProbeCount();
        BrandSpaceRunCreditPolicy.CreditQuote quote = brandSpaceRunCreditPolicy.quote(
                userId,
                resolvedTargetProbeCount,
                serviceAccessService.getServiceAccess(userId)
        );
        boolean skipped = Boolean.TRUE.equals(result.skipped());
        return BrandSpaceProbePlanExecuteOnceResponse.builder()
                .brandSpaceId(scope.getId())
                .runId(result.runId())
                .requestId(result.requestId())
                .targetProbeCount(resolvedTargetProbeCount)
                .estimatedCredits(quote.estimatedCredits())
                .deductedCredits(skipped ? 0 : quote.estimatedCredits())
                .creditsRemainingAfterRun(skipped ? quote.creditsRemaining() : quote.balanceAfterRun())
                .skipped(result.skipped())
                .skippedReason(result.reason())
                .probePlan(latestView)
                .build();
    }

    private BrandScope loadScope(String userId, String brandSpaceId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(brandSpaceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        if (!StringUtils.hasText(organizationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        BrandScope scope = brandScopeMapper.selectById(brandSpaceId.trim());
        if (scope == null || !Objects.equals(scope.getOrganizationId(), organizationId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在或无权访问");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
        }
        return scope;
    }

    private ProbePlanVersion loadCurrentVersion(String brandSpaceId) {
        return probePlanVersionMapper.selectOne(new LambdaQueryWrapper<ProbePlanVersion>()
                .eq(ProbePlanVersion::getBrandScopeId, brandSpaceId)
                .eq(ProbePlanVersion::getIsCurrent, true)
                .eq(ProbePlanVersion::getStatusCode, STATUS_ACTIVE)
                .last("limit 1"));
    }

    private BrandSpaceProbePlanView buildView(BrandScope scope, ProbePlanVersion currentVersion) {
        List<ProbePlanRecord> records = probePlanRecordMapper.selectList(new LambdaQueryWrapper<ProbePlanRecord>()
                .eq(ProbePlanRecord::getBrandScopeId, scope.getId())
                .orderByDesc(ProbePlanRecord::getRecordNo)
                .last("limit " + RECORD_LIMIT));
        boolean currentVersionMatchesConfig = currentVersion != null
                && Objects.equals(defaultInt(currentVersion.getConfigGenerationSnapshot()), defaultInt(scope.getConfigGeneration()));
        String currentRecordId = !currentVersionMatchesConfig ? null : records.stream()
                .filter(record -> Objects.equals(record.getPlanVersionId(), currentVersion.getId()))
                .map(ProbePlanRecord::getId)
                .findFirst()
                .orElse(null);
        List<ProbePlanRecordItem> items = !StringUtils.hasText(currentRecordId) ? List.of()
                : probePlanRecordItemMapper.selectList(new LambdaQueryWrapper<ProbePlanRecordItem>()
                .eq(ProbePlanRecordItem::getRecordId, currentRecordId)
                .orderByAsc(ProbePlanRecordItem::getSeqNoSnapshot)
                .last("limit " + RECORD_LIMIT));
        BrandSpaceProbePlanView.CurrentPlanView currentPlan = !currentVersionMatchesConfig
                ? buildDraftPlan(scope)
                : toCurrentPlan(currentVersion, items);
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.resolveCurrentRunConfig(
                        scope,
                        currentPlan == null ? null : currentPlan.getActiveRecurringProbeCount()
                );
        return BrandSpaceProbePlanView.builder()
                .contractVersion(CONTRACT_VERSION)
                .brandSpaceId(scope.getId())
                .currentRunConfig(BrandSpaceConfigView.RunConfigView.builder()
                        .targetProbeCount(currentRunConfig.targetProbeCount())
                        .targetModelProduct(currentRunConfig.targetModelProduct())
                        .autoRunEnabled(currentRunConfig.autoRunEnabled())
                        .build())
                .currentPlan(currentPlan)
                .recentRecords(records.stream().map(this::toRecordView).toList())
                .build();
    }

    private BrandSpaceProbePlanView.CurrentPlanView buildDraftPlan(BrandScope scope) {
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, scope.getId())
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        List<BrandSpacePrompt> activePrompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, scope.getId())
                .eq(BrandSpacePrompt::getStatusCode, STATUS_ACTIVE)
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt));
        if (activePrompts == null || activePrompts.isEmpty()) {
            return null;
        }
        int enabledProbeCount = activePrompts.size();
        return BrandSpaceProbePlanView.CurrentPlanView.builder()
                .activeRecurringProbeCount(enabledProbeCount)
                .estimatedCreditsPerCycle(enabledProbeCount)
                .configGeneration(scope.getConfigGeneration())
                .snapshot(buildDraftSnapshot(scope, topics, activePrompts))
                .items(activePrompts.stream().map(this::toDraftItemView).toList())
                .build();
    }

    private Map<String, Object> buildDraftSnapshot(BrandScope scope,
                                                   List<BrandSpaceTopic> topics,
                                                   List<BrandSpacePrompt> activePrompts) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("contractVersion", CONTRACT_VERSION);
        snapshot.put("mode", "DRAFT_FROM_CURRENT_CONFIG");
        snapshot.put("brandSpaceId", scope.getId());
        snapshot.put("brandId", scope.getBrandId());
        snapshot.put("brandName", scope.getScopeName());
        snapshot.put("configGeneration", scope.getConfigGeneration());
        snapshot.put("topicCount", topics == null ? 0 : topics.size());
        snapshot.put("enabledProbeCount", activePrompts == null ? 0 : activePrompts.size());
        snapshot.put("pricing", Map.of(
                "unit", "CREDIT_PER_PROBE",
                "defaultCreditsPerProbe", 1
        ));
        return snapshot;
    }

    private BrandSpaceProbePlanView.CurrentPlanView toCurrentPlan(ProbePlanVersion version,
                                                                  List<ProbePlanRecordItem> items) {
        return BrandSpaceProbePlanView.CurrentPlanView.builder()
                .planVersionId(version.getId())
                .versionNo(version.getVersionNo())
                .activeRecurringProbeCount(version.getActiveRecurringProbeCount())
                .estimatedCreditsPerCycle(version.getEstimatedRecurringCreditsPerCycle())
                .configGeneration(version.getConfigGenerationSnapshot())
                .snapshot(defaultMap(version.getSnapshotJson()))
                .items(items.stream().map(this::toItemView).toList())
                .build();
    }

    private BrandSpaceProbePlanView.RecordView toRecordView(ProbePlanRecord record) {
        return BrandSpaceProbePlanView.RecordView.builder()
                .planRecordId(record.getId())
                .planVersionId(record.getPlanVersionId())
                .recordNo(record.getRecordNo())
                .changeTypeCode(record.getChangeTypeCode())
                .executeModeCode(record.getExecuteModeCode())
                .immediateEstimatedCredits(record.getImmediateEstimatedCredits())
                .recurringEstimatedCreditsPerCycle(record.getRecurringEstimatedCreditsPerCycle())
                .triggeredRunId(record.getTriggeredRunId())
                .summary(defaultMap(record.getSummaryJson()))
                .createdAt(toText(record.getCreatedAt()))
                .build();
    }

    private BrandSpaceProbePlanView.ItemView toItemView(ProbePlanRecordItem item) {
        return BrandSpaceProbePlanView.ItemView.builder()
                .probeId(item.getProbeId())
                .topicId(item.getTopicId())
                .seqNo(item.getSeqNoSnapshot())
                .promptText(item.getProbeTextSnapshot())
                .actionCode(item.getActionCode())
                .executeModeCode(item.getExecuteModeCode())
                .build();
    }

    private BrandSpaceProbePlanView.ItemView toDraftItemView(BrandSpacePrompt prompt) {
        return BrandSpaceProbePlanView.ItemView.builder()
                .probeId(prompt.getId())
                .topicId(prompt.getTopicId())
                .seqNo(prompt.getSeqNo())
                .promptText(prompt.getPromptText())
                .actionCode(ACTION_KEEP)
                .executeModeCode(EXECUTE_MODE_RECURRING)
                .build();
    }

    private boolean isOneTimeMode(String value) {
        return EXECUTE_MODE_ONE_TIME.equalsIgnoreCase(StringUtils.trimWhitespace(value));
    }

    private Map<String, Object> defaultMap(Map<String, Object> source) {
        return source == null ? Map.of() : source;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}

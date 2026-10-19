package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.LlmRunAdmissionBlockedException;
import com.geoprobe.dto.brandspace.BrandSpaceBasicProfileView;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpaceArchiveListResponse;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpaceArchiveView;
import com.geoprobe.dto.brandspace.BrandSpaceListItemView;
import com.geoprobe.dto.brandspace.BrandSpaceListResponse;
import com.geoprobe.dto.brandspace.BrandSpacePreviewResponse;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.dto.brandspace.BrandSpaceScheduleExplanationView;
import com.geoprobe.dto.brandspace.BrandSpaceScheduleView;
import com.geoprobe.dto.brandspace.BrandSpaceSystemRefreshView;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.dto.brandspace.CreateBrandSpaceResponse;
import com.geoprobe.dto.brandspace.EstimatedRunView;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.request.BrandSpaceBasicProfileUpdateRequest;
import com.geoprobe.dto.request.BrandSpaceCreateRequest;
import com.geoprobe.dto.request.BrandSpaceConfigUpdateRequest;
import com.geoprobe.dto.request.BrandSpacePreviewRequest;
import com.geoprobe.dto.request.BrandSpaceTrackingModeUpdateRequest;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.dto.request.CreateDiagnosisRequest;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.DiagnosisProbeRecord;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.ProbeBrandMentionRecord;
import com.geoprobe.knowledge.repository.KnowledgeAssetEvidenceRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetHistorySyncService;
import com.geoprobe.knowledge.service.KnowledgeAssetScheduleStateMaterializationService;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisProbeRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.ProbeBrandMentionRecordMapper;
import com.geoprobe.service.shared.DiagnosisIdempotencyService;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BrandSpaceService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_QUEUED = 5;
    private static final String TRACKING_MODE_ACTIVE = "ACTIVE";
    private static final String TRACKING_MODE_PAUSED = "PAUSED";
    private static final String TRACKING_STATUS_NEEDS_ATTENTION = "NEEDS_ATTENTION";
    private static final String HEALTH_STATUS_HEALTHY = "HEALTHY";
    private static final String HEALTH_STATUS_DEGRADED = "DEGRADED";
    private static final String HEALTH_STATUS_ERROR = "ERROR";
    private static final String HEALTH_STATUS_CONFIG_INCOMPLETE = "CONFIG_INCOMPLETE";
    private static final String ARCHIVE_STATUS_ACTIVE = "ACTIVE";
    private static final String ARCHIVE_STATUS_USER_RESTORE = "USER_SELF_RESTORE";
    private static final String ARCHIVE_STATUS_INTERNAL_RESTORE = "INTERNAL_COLD_ARCHIVE";
    private static final String ARCHIVE_STATUS_PURGED = "PURGED";
    private static final String EXECUTE_MODE_RECURRING = "RECURRING";
    private static final String EXECUTE_MODE_ONE_TIME = "ONE_TIME";
    private static final String TARGET_MODEL_DOUBAO = "doubao";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DiagnosisService diagnosisService;
    private final FrontDeskPreviewTokenService frontDeskPreviewTokenService;
    private final V42TaskOrderExecutionPolicyResolver executionPolicyResolver;
    private final ServiceAccessService serviceAccessService;
    private final BrandSpaceExecutionGateService brandSpaceExecutionGateService;
    private final BrandSpaceRecognitionService brandSpaceRecognitionService;
    private final BrandSpaceConfigService brandSpaceConfigService;
    private final ProbePlanService probePlanService;
    private final LegacyCreditMirrorService legacyCreditMirrorService;
    private final BrandSpaceSchedulingService brandSpaceSchedulingService;
    private final BrandSpaceRunConfigService brandSpaceRunConfigService;
    private final BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;
    private final DiagnosisTenantContextService diagnosisTenantContextService;
    private final DiagnosisIdempotencyService diagnosisIdempotencyService;
    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final DiagnosisProbeRecordMapper diagnosisProbeRecordMapper;
    private final BrandScopeMapper brandScopeMapper;
    private final BrandMapper brandMapper;
    private final ProbeBrandMentionRecordMapper probeBrandMentionRecordMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<KnowledgeAssetHistorySyncService> knowledgeAssetHistorySyncServiceProvider;
    private final ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetEvidenceRepository> knowledgeAssetEvidenceRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetScheduleStateMaterializationService> knowledgeAssetScheduleStateMaterializationServiceProvider;

    @Value("${app.brand-space.create.initial-run-enabled:true}")
    private boolean createInitialRunEnabled = true;

    public BrandSpacePreviewResponse preview(String userId, BrandSpacePreviewRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        BrandSpaceDraftPayload userDraft = normalizeDraft(request == null ? null : request.toDraft());
        BrandSpaceRecognitionView recognition = resolvePreviewRecognition(userDraft, request);
        BrandSpaceDraftPayload draft = applyRecognitionToDraft(userDraft, recognition);
        List<BrandSpaceTopicPayload> topics = brandSpaceConfigService.resolvePreviewTopics(
                draft,
                request == null ? null : request.getTopics()
        );
        BrandSpaceExecutionGateService.AuthoritySnapshot authority =
                brandSpaceExecutionGateService.requirePreviewAuthority(userId);
        BrandSpaceSchedulingService.ExecutionPlan executionPlan =
                brandSpaceSchedulingService.planPreview(topics, List.of(), authority.serviceAccess());
        brandSpaceExecutionGateService.assertPlannedProbeCountAllowed(
                executionPlan.targetProbeCount(),
                authority.serviceAccess(),
                "brand_space_preview"
        );
        DiagnosisService.TaskOrderPreview preview = diagnosisService.previewTaskOrder(
                userId,
                buildPreviewDiagnosisRequest(
                        draft,
                        topics,
                        List.of(),
                        request == null ? null : request.getIdempotencyKey(),
                        authority,
                        executionPlan
                )
        );
        return BrandSpacePreviewResponse.builder()
                .previewToken(preview.previewToken())
                .brandSpaceDraft(draft)
                .recognition(recognition)
                .topics(topics)
                .prompts(List.of())
                .estimatedRun(EstimatedRunView.builder()
                        .targetProbeCount(preview.targetProbeCount())
                        .estimatedDurationSeconds(Math.max(60, preview.targetProbeCount() * 6))
                        .realtimeSearchEnabled(
                                executionPolicyResolver.resolveRealtimeSearchPolicyFromTaskOrderSnapshot(preview.frontDeskTaskOrder()).enabled()
                        )
                        .schedule(toScheduleView(executionPlan))
                        .build())
                .serviceAccess(authority.serviceAccess())
                .build();
    }

    public CreateBrandSpaceResponse create(String userId, BrandSpaceCreateRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        String idempotencyKey = request.getIdempotencyKey().trim();
        Diagnosis existing = diagnosisIdempotencyService.findIdempotentDiagnosis(userId, idempotencyKey);
        if (existing != null) {
            return buildCreateResponseFromDiagnosis(existing);
        }
        FrontDeskPreviewTokenService.StoredPreviewSnapshot previewSnapshot =
                frontDeskPreviewTokenService.load(userId, request.getPreviewToken());
        if (previewSnapshot == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "previewToken 无效或已过期，请重新确认");
        }
        BrandSpaceDraftPayload draft = normalizeDraft(request.getBrandSpaceDraft());
        List<BrandSpaceTopicPayload> topics = defaultTopicsIfNeeded(draft, request.getTopics());
        List<BrandSpacePromptPayload> prompts = brandSpaceConfigService.generatePrompts(draft, topics);
        BrandSpaceExecutionGateService.AuthoritySnapshot authority =
                brandSpaceExecutionGateService.requireCreateAuthority(userId);
        String executeModeCode = normalizeCreateExecuteModeCode(request.getExecuteModeCode());
        String targetModelProduct = normalizeCreateTargetModelProduct(request.getTargetModelProduct());
        BrandSpaceSchedulingService.ExecutionPlan executionPlan =
                brandSpaceSchedulingService.planCreateInitialRun(
                        topics,
                        prompts,
                        authority.serviceAccess(),
                        request.getTargetProbeCount()
                );
        brandSpaceExecutionGateService.assertPlannedProbeCountAllowed(
                executionPlan.targetProbeCount(),
                authority.serviceAccess(),
                "brand_space_create_initial_run"
        );
        BrandSpaceRunCreditPolicy.CreditQuote createQuote = brandSpaceRunCreditPolicy.quote(
                userId,
                executionPlan.targetProbeCount(),
                authority.serviceAccess()
        );
        BrandSpaceAccessContext createContext = ensureCreateAccessContext(userId, draft);
        persistBasicProfile(createContext, draft);
        updateBrandScopeDraft(createContext.scope().getId(), draft);
        brandSpaceConfigService.replaceConfig(
                createContext.organizationId(),
                createContext.scope().getBrandId(),
                createContext.scope().getId(),
                draft,
                topics,
                prompts
        );
        boolean oneTimeCreate = isOneTimeMode(executeModeCode);
        brandSpaceRunConfigService.saveCurrentRunConfig(
                createContext.scope().getId(),
                executionPlan.targetProbeCount(),
                targetModelProduct,
                !oneTimeCreate,
                executionPlan.targetProbeCount()
        );
        incrementConfigGeneration(
                createContext.scope().getId(),
                oneTimeCreate ? TRACKING_MODE_PAUSED : TRACKING_MODE_ACTIVE
        );
        refreshScheduleState(createContext.scope().getId());
        if (!createQuote.sufficient()) {
            return buildBlockedCreateResponse(
                    createContext,
                    "CREDITS_INSUFFICIENT",
                    "品牌空间已创建，但首跑暂未发起：当前积分不足，请补足后再手动运行。"
            );
        }
        Map<String, Object> taskOrder = buildCreateTaskOrder(
                previewSnapshot.frontDeskTaskOrder(),
                draft,
                topics,
                prompts,
                authority,
                createContext,
                idempotencyKey,
                executionPlan,
                executeModeCode,
                targetModelProduct
        );
        if (!createInitialRunEnabled) {
            return buildBlockedCreateResponse(
                    createContext,
                    "INITIAL_RUN_DISABLED",
                    "品牌空间已创建，但首跑暂未发起：当前环境禁用了真实主链执行。"
            );
        }

        MainChainRunTicket runTicket;
        try {
            runTicket = createMainChainRun(userId, idempotencyKey, taskOrder);
        } catch (LlmRunAdmissionBlockedException ex) {
            return buildBlockedCreateResponse(
                    createContext,
                    "RUN_ADMISSION_BLOCKED",
                    "品牌空间已创建，但首跑暂未发起：当前供应商免费额度基线不可用，请先同步快照后再手动运行。"
            );
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runTicket.runId());
        if (task == null || !StringUtils.hasText(task.getBrandScopeId()) || !StringUtils.hasText(task.getBrandId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "品牌空间创建成功，但运行主链任务底座未落库");
        }
        ProbePlanService.SnapshotCommand snapshotCommand = new ProbePlanService.SnapshotCommand(
                task.getOrganizationId(),
                task.getBrandId(),
                task.getBrandScopeId(),
                draft.getBrandName(),
                userId,
                task.getId(),
                executionPlan.targetProbeCount(),
                targetModelProduct,
                resolveCreditsPerProbe(authority == null ? null : authority.serviceAccess())
        );
        ProbePlanService.SnapshotRef planSnapshot = oneTimeCreate
                ? probePlanService.snapshotInitialConfirmedPlanOnce(snapshotCommand)
                : probePlanService.snapshotInitialConfirmedPlan(snapshotCommand);
        syncInitialRunExecutionHints(task, draft.getBrandName(), planSnapshot);
        legacyCreditMirrorService.attachDiagnosisTaskAttribution(task);
        return CreateBrandSpaceResponse.builder()
                .brandSpaceId(task.getBrandScopeId())
                .brandId(task.getBrandId())
                .brandScopeId(task.getBrandScopeId())
                .initialRunId(task.getId())
                .initialRunStatus(mapRunStatus(task, null))
                .status(mapBrandSpaceStatus(task, findLatestReportHead(task.getOrganizationId(), task.getBrandScopeId())))
                .nextView("/brands")
                .build();
    }

    private BrandSpaceDraftPayload applyRecognitionToDraft(BrandSpaceDraftPayload draft,
                                                           BrandSpaceRecognitionView recognition) {
        if (draft == null || recognition == null) {
            return draft;
        }
        String resolvedCategory = firstText(recognition.getSuggestedCategory(), draft.getCategory());
        List<String> resolvedCompetitors = normalizeCompetitors(draft.getCompetitors(), recognition.getCompetitors());
        return BrandSpaceDraftPayload.builder()
                .brandName(draft.getBrandName())
                .category(resolvedCategory)
                .officialWebsite(firstText(draft.getOfficialWebsite(), recognition.getOfficialWebsite()))
                .regionCode(draft.getRegionCode())
                .locale(draft.getLocale())
                .language(draft.getLanguage())
                .competitors(resolvedCompetitors)
                .supplementalBrief(draft.getSupplementalBrief())
                .build();
    }

    private BrandSpaceRecognitionView resolvePreviewRecognition(BrandSpaceDraftPayload draft,
                                                                BrandSpacePreviewRequest request) {
        BrandSpaceRecognitionView providedRecognition = request == null ? null : request.getRecognition();
        if (providedRecognition == null) {
            return brandSpaceRecognitionService.recognize(draft);
        }
        List<String> directCompetitors = normalizeCompetitors(
                providedRecognition.getDirectCompetitors(),
                List.of()
        );
        List<String> indirectCandidates = normalizeCompetitors(
                providedRecognition.getIndirectCompetitors(),
                List.of()
        );
        List<String> indirectCompetitors = new ArrayList<>();
        for (String item : indirectCandidates) {
            if (!directCompetitors.contains(item)) {
                indirectCompetitors.add(item);
            }
        }
        List<String> mergedCompetitors = normalizeCompetitors(
                mergeCompetitors(directCompetitors, indirectCompetitors, providedRecognition.getCompetitors()),
                draft.getCompetitors()
        );
        if (directCompetitors.isEmpty() && indirectCompetitors.isEmpty() && !mergedCompetitors.isEmpty()) {
            directCompetitors = List.copyOf(mergedCompetitors.subList(0, Math.min(5, mergedCompetitors.size())));
            indirectCompetitors = mergedCompetitors.size() <= directCompetitors.size()
                    ? List.of()
                    : List.copyOf(mergedCompetitors.subList(directCompetitors.size(), mergedCompetitors.size()));
        }
        return BrandSpaceRecognitionView.builder()
                .suggestedCategory(firstText(providedRecognition.getSuggestedCategory(), draft.getCategory()))
                .officialWebsite(firstText(providedRecognition.getOfficialWebsite(), draft.getOfficialWebsite()))
                .directCompetitors(directCompetitors)
                .indirectCompetitors(indirectCompetitors)
                .competitors(mergedCompetitors)
                .brandSummary(firstText(providedRecognition.getBrandSummary(), draft.getSupplementalBrief()))
                .source(firstText(providedRecognition.getSource(), "frontend_confirmed"))
                .build();
    }

    private List<String> mergeCompetitors(List<String> directCompetitors,
                                          List<String> indirectCompetitors,
                                          List<String> legacyCompetitors) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (directCompetitors != null) {
            merged.addAll(directCompetitors);
        }
        if (indirectCompetitors != null) {
            merged.addAll(indirectCompetitors);
        }
        if (legacyCompetitors != null) {
            merged.addAll(legacyCompetitors);
        }
        return List.copyOf(merged);
    }

    private List<String> normalizeCompetitors(List<String> preferred, List<String> fallback) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        List<String> source = preferred != null && !preferred.isEmpty() ? preferred : fallback;
        if (source == null) {
            return List.of();
        }
        for (String item : source) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            deduplicated.add(item.trim());
        }
        return List.copyOf(deduplicated);
    }

    public BrandSpaceListResponse list(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        if (!StringUtils.hasText(organizationId)) {
            return BrandSpaceListResponse.builder()
                    .items(List.of())
                    .total(0L)
                    .build();
        }
        ServiceAccessView serviceAccess = serviceAccessService.getServiceAccess(userId);
        return buildBrandSpaceListForOrganization(organizationId, serviceAccess.getStatus());
    }

    private BrandSpaceListResponse buildBrandSpaceListForOrganization(String organizationId, String serviceAccessStatus) {
        List<BrandScope> scopes = brandScopeMapper.selectList(new LambdaQueryWrapper<BrandScope>()
                .eq(BrandScope::getOrganizationId, organizationId)
                .and(wrapper -> wrapper
                        .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ACTIVE)
                        .or()
                        .isNull(BrandScope::getScopeStatusCode))
                .orderByDesc(BrandScope::getUpdatedAt)
                .orderByDesc(BrandScope::getCreatedAt));
        if (scopes.isEmpty()) {
            return BrandSpaceListResponse.builder()
                    .items(List.of())
                    .total(0L)
                    .build();
        }
        Map<String, Brand> brandsById = loadBrandsById(scopes);
        Map<String, DiagnosisTask> latestTasksByScopeId = loadLatestTasksByScopeId(scopes);
        Map<String, DiagnosisReportRecord> latestReportsByScopeId = loadLatestReportsByScopeId(organizationId, scopes);
        Map<String, BrandSpaceConfigService.ConfigSummarySnapshot> configSummariesByScopeId =
                brandSpaceConfigService.summarizeConfigs(extractScopeIds(scopes));
        List<BrandSpaceListItemView> items = scopes.stream()
                .map(scope -> toBrandSpaceItem(
                        scope,
                        brandsById.get(scope.getBrandId()),
                        latestTasksByScopeId.get(scope.getId()),
                        latestReportsByScopeId.get(scope.getId()),
                        serviceAccessStatus,
                        configSummariesByScopeId.get(scope.getId())
                ))
                .toList();
        return BrandSpaceListResponse.builder()
                .items(items)
                .total(items.size())
                .build();
    }

    public BrandSpaceArchiveListResponse listArchived(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        if (!StringUtils.hasText(organizationId)) {
            return BrandSpaceArchiveListResponse.builder()
                    .items(List.of())
                    .total(0L)
                    .build();
        }
        LocalDateTime now = LocalDateTime.now();
        List<BrandScope> archivedScopes = loadArchivedScopesForOrganization(organizationId);
        Map<String, Brand> brandsById = loadBrandsById(archivedScopes);
        List<BrandSpaceArchiveView> items = archivedScopes.stream()
                .map(scope -> toArchiveView(scope, brandsById.get(scope.getBrandId()), now))
                .filter(item -> Boolean.TRUE.equals(item.getCanUserRestore()))
                .toList();
        return BrandSpaceArchiveListResponse.builder()
                .items(items)
                .total((long) items.size())
                .build();
    }



    public BrandSpaceBasicProfileView getBasicProfile(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        return toBasicProfileView(context.scope(), context.brand(), resolveDraftCompetitors(context));
    }

    public BrandSpaceBasicProfileView updateBasicProfile(String userId,
                                                         String brandSpaceId,
                                                         BrandSpaceBasicProfileUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        BrandSpaceDraftPayload normalizedDraft = normalizeDraft(BrandSpaceDraftPayload.builder()
                .brandName(request.getBrandName())
                .category(request.getCategory())
                .officialWebsite(request.getOfficialWebsite())
                .competitors(request.getCompetitors())
                .supplementalBrief(request.getSupplementalBrief())
                .regionCode(defaultIfBlank(context.scope().getRegionCode(), "CN"))
                .locale(defaultIfBlank(context.scope().getLocale(), "zh-CN"))
                .language(defaultIfBlank(context.scope().getLanguage(), "zh-CN"))
                .build());

        persistBasicProfile(context, normalizedDraft);
        incrementConfigGeneration(context.scope().getId(), null);

        BrandScope refreshedScope = brandScopeMapper.selectById(context.scope().getId());
        Brand brand = refreshedScope == null || !StringUtils.hasText(refreshedScope.getBrandId())
                ? context.brand()
                : brandMapper.selectById(refreshedScope.getBrandId());
        BrandScope scope = refreshedScope == null ? context.scope() : refreshedScope;
        return toBasicProfileView(scope, brand, resolveStoredCompetitors(scope));
    }

    public BrandSpaceConfigView getConfig(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        return brandSpaceConfigService.loadConfigView(
                context.scope().getId(),
                context.scope().getBrandId(),
                firstText(context.brand() == null ? null : context.brand().getBrandName(), context.scope().getScopeName()),
                context.scope().getConfigGeneration()
        );
    }

    public BrandSpaceConfigView updateConfig(String userId,
                                             String brandSpaceId,
                                             BrandSpaceConfigUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        BrandSpaceDraftPayload draft = buildMutableDraft(
                context,
                defaultIfBlank(request.getRegionCode(), context.scope().getRegionCode(), "CN"),
                defaultIfBlank(request.getLocale(), context.scope().getLocale(), "zh-CN"),
                defaultIfBlank(request.getLanguage(), context.scope().getLanguage(), "zh-CN")
        );
        updateBrandScopeDraft(context.scope().getId(), draft);
        brandSpaceConfigService.replaceConfig(
                context.scope().getOrganizationId(),
                context.scope().getBrandId(),
                context.scope().getId(),
                draft,
                request.getTopics(),
                request.getPrompts()
        );
        brandSpaceRunConfigService.saveCurrentRunConfig(
                context.scope().getId(),
                request.getTargetProbeCount(),
                request.getTargetModelProduct(),
                request.getAutoRunEnabled(),
                countEnabledPrompts(request.getPrompts())
        );
        incrementConfigGeneration(context.scope().getId(), null);
        refreshScheduleState(context.scope().getId());

        BrandScope refreshedScope = brandScopeMapper.selectById(context.scope().getId());
        Brand brand = refreshedScope == null || !StringUtils.hasText(refreshedScope.getBrandId())
                ? context.brand()
                : brandMapper.selectById(refreshedScope.getBrandId());
        BrandScope scope = refreshedScope == null ? context.scope() : refreshedScope;
        return brandSpaceConfigService.loadConfigView(
                scope.getId(),
                scope.getBrandId(),
                firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName()),
                scope.getConfigGeneration()
        );
    }

    public BrandSpaceConfigView regenerateSystemGeneratedConfig(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        BrandSpaceConfigView currentConfig = brandSpaceConfigService.loadConfigView(
                context.scope().getId(),
                context.scope().getBrandId(),
                firstText(context.brand() == null ? null : context.brand().getBrandName(), context.scope().getScopeName()),
                context.scope().getConfigGeneration()
        );
        if (!Boolean.TRUE.equals(currentConfig.getCapabilities() == null ? null : currentConfig.getCapabilities().getCanRegenerateSystemConfig())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前品牌空间配置包含人工修改内容，暂不支持自动重生成");
        }
        BrandSpaceDraftPayload draft = buildMutableDraft(
                context,
                defaultIfBlank(context.scope().getRegionCode(), "CN"),
                defaultIfBlank(context.scope().getLocale(), "zh-CN"),
                defaultIfBlank(context.scope().getLanguage(), "zh-CN")
        );
        List<BrandSpaceTopicPayload> topics = brandSpaceConfigService.generateTopics(draft);
        List<BrandSpacePromptPayload> prompts = brandSpaceConfigService.generatePrompts(draft, topics);
        updateBrandScopeDraft(context.scope().getId(), draft);
        brandSpaceConfigService.replaceConfig(
                context.scope().getOrganizationId(),
                context.scope().getBrandId(),
                context.scope().getId(),
                draft,
                topics,
                prompts
        );
        incrementConfigGeneration(context.scope().getId(), null);
        refreshScheduleState(context.scope().getId());
        BrandScope refreshedScope = brandScopeMapper.selectById(context.scope().getId());
        Brand brand = refreshedScope == null || !StringUtils.hasText(refreshedScope.getBrandId())
                ? context.brand()
                : brandMapper.selectById(refreshedScope.getBrandId());
        BrandScope scope = refreshedScope == null ? context.scope() : refreshedScope;
        return brandSpaceConfigService.loadConfigView(
                scope.getId(),
                scope.getBrandId(),
                firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName()),
                scope.getConfigGeneration()
        );
    }

    public BrandSpaceSystemRefreshView refreshSystemProfileAndConfig(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        BrandSpaceDraftPayload seedDraft = normalizeDraft(BrandSpaceDraftPayload.builder()
                .brandName(firstText(
                        context.brand() == null ? null : context.brand().getBrandName(),
                        context.scope() == null ? null : context.scope().getScopeName()
                ))
                .category(firstText(
                        context.scope() == null ? null : context.scope().getCategorySnapshot(),
                        context.brand() == null ? null : context.brand().getCategoryName()
                ))
                .regionCode(defaultIfBlank(context.scope().getRegionCode(), "CN"))
                .locale(defaultIfBlank(context.scope().getLocale(), "zh-CN"))
                .language(defaultIfBlank(context.scope().getLanguage(), "zh-CN"))
                .build());

        BrandSpaceRecognitionView recognition = brandSpaceRecognitionService.recognize(seedDraft);
        BrandSpaceDraftPayload rebuiltDraft = normalizeDraft(BrandSpaceDraftPayload.builder()
                .brandName(seedDraft.getBrandName())
                .category(seedDraft.getCategory())
                .officialWebsite(defaultIfBlank(recognition.getOfficialWebsite(), null))
                .competitors(normalizeCompetitors(recognition.getCompetitors(), List.of()))
                .supplementalBrief(defaultIfBlank(recognition.getBrandSummary(), null))
                .regionCode(seedDraft.getRegionCode())
                .locale(seedDraft.getLocale())
                .language(seedDraft.getLanguage())
                .build());

        persistBasicProfile(context, rebuiltDraft);
        updateBrandScopeDraft(context.scope().getId(), rebuiltDraft);

        List<BrandSpaceTopicPayload> topics = brandSpaceConfigService.generateTopics(rebuiltDraft);
        List<BrandSpacePromptPayload> prompts = brandSpaceConfigService.generatePrompts(rebuiltDraft, topics);
        brandSpaceConfigService.replaceConfig(
                context.organizationId(),
                context.scope().getBrandId(),
                context.scope().getId(),
                rebuiltDraft,
                topics,
                prompts
        );
        incrementConfigGeneration(context.scope().getId(), null);
        refreshScheduleState(context.scope().getId());

        BrandScope refreshedScope = brandScopeMapper.selectById(context.scope().getId());
        BrandScope scope = refreshedScope == null ? context.scope() : refreshedScope;
        Brand brand = scope.getBrandId() == null
                ? context.brand()
                : brandMapper.selectById(scope.getBrandId());
        DiagnosisTask latestTask = loadLatestTasksByScopeId(List.of(scope)).get(scope.getId());

        return BrandSpaceSystemRefreshView.builder()
                .basicProfile(toBasicProfileView(scope, brand, resolveStoredCompetitors(scope)))
                .config(brandSpaceConfigService.loadConfigView(
                        scope.getId(),
                        scope.getBrandId(),
                        firstText(brand == null ? null : brand.getBrandName(), scope.getScopeName()),
                        scope.getConfigGeneration()
                ))
                .latestSchedule(readLatestSchedule(latestTask))
                .build();
    }

    public BrandSpaceScheduleExplanationView getScheduleExplanation(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        BrandScope scope = context.scope();
        Brand brand = context.brand();
        String brandName = firstText(
                brand == null ? null : brand.getBrandName(),
                scope == null ? null : scope.getScopeName()
        );
        BrandSpaceConfigView configView = brandSpaceConfigService.loadConfigView(
                scope.getId(),
                scope.getBrandId(),
                brandName,
                scope.getConfigGeneration()
        );
        DiagnosisTask latestTask = loadLatestTasksByScopeId(List.of(scope)).get(scope.getId());
        DiagnosisReportRecord latestReport = loadLatestReportsByScopeId(scope.getOrganizationId(), List.of(scope)).get(scope.getId());
        BrandSpaceSchedulingService.ExecutionPlan executionPlan =
                latestTask == null ? null : BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(latestTask.getTaskOrderJson());
        List<BrandSpaceScheduleExplanationView.TopicIssueView> blockedTopics = buildBlockedScheduleTopics(configView);
        BrandSpaceScheduleExplanationView.ConfigSnapshotView configSnapshot = buildScheduleConfigSnapshot(configView, blockedTopics);
        boolean configReady = configSnapshot != null
                && defaultInt(configSnapshot.getExecutableTopicCount()) > 0
                && defaultInt(configSnapshot.getEnabledPromptCount()) > 0;
        return BrandSpaceScheduleExplanationView.builder()
                .brandSpaceId(scope.getId())
                .brandId(scope.getBrandId())
                .brandName(brandName)
                .trackingStatus(resolveTrackingStatus(scope, configReady))
                .configReady(configReady)
                .lastRunId(latestTask == null ? null : latestTask.getId())
                .lastRunAt(formatDateTime(resolveLastRunAt(latestTask, latestReport)))
                .snapshotAvailable(executionPlan != null && executionPlan.targetProbeCount() > 0)
                .snapshotMessage(buildScheduleSnapshotMessage(executionPlan, blockedTopics))
                .latestSchedule(toScheduleView(executionPlan))
                .configSnapshot(configSnapshot)
                .selectedTopics(toScheduleTopicDecisionViews(executionPlan == null ? List.of() : executionPlan.selectedTopics()))
                .deferredTopics(toScheduleTopicDecisionViews(executionPlan == null ? List.of() : executionPlan.deferredTopics()))
                .blockedTopics(blockedTopics)
                .build();
    }

    public BrandSpaceListItemView updateTrackingMode(String userId,
                                                     String brandSpaceId,
                                                     BrandSpaceTrackingModeUpdateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, false);
        String trackingMode = normalizeTrackingMode(request.getTrackingMode());
        BrandScope scope = context.scope();
        scope.setTrackingMode(trackingMode);
        brandScopeMapper.updateById(scope);

        DiagnosisTask latestTask = loadLatestTasksByScopeId(List.of(scope)).get(scope.getId());
        DiagnosisReportRecord latestReport = loadLatestReportsByScopeId(scope.getOrganizationId(), List.of(scope)).get(scope.getId());
        BrandSpaceConfigService.ConfigSummarySnapshot configSummary =
                brandSpaceConfigService.summarizeConfigs(List.of(scope.getId())).get(scope.getId());
        return toBrandSpaceItem(
                scope,
                context.brand(),
                latestTask,
                latestReport,
                serviceAccessService.getServiceAccess(userId).getStatus(),
                configSummary
        );
    }

    public BrandSpaceArchiveView archive(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, true);
        BrandScope scope = context.scope();
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            return toArchiveView(scope, context.brand(), LocalDateTime.now());
        }
        LocalDateTime archivedAt = LocalDateTime.now();
        scope.setScopeStatusCode(BrandSpaceLifecyclePolicy.SCOPE_STATUS_ARCHIVED);
        scope.setTrackingMode(TRACKING_MODE_PAUSED);
        scope.setArchivedAt(archivedAt);
        scope.setArchivedByMembershipId(firstText(
                serviceAccessService.resolveMembershipId(userId, context.organizationId()),
                scope.getCreatedByMembershipId()
        ));
        scope.setRetentionUntil(BrandSpaceLifecyclePolicy.resolveRetentionUntil(archivedAt));
        scope.setInternalRestoreUntil(BrandSpaceLifecyclePolicy.resolveInternalRestoreUntil(archivedAt));
        scope.setPurgedAt(null);
        brandScopeMapper.updateById(scope);
        return toArchiveView(scope, context.brand(), archivedAt);
    }

    public BrandSpaceListItemView restore(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, true);
        BrandScope scope = context.scope();
        if (!BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            return toBrandSpaceItem(
                    scope,
                    context.brand(),
                    loadLatestTasksByScopeId(List.of(scope)).get(scope.getId()),
                    loadLatestReportsByScopeId(scope.getOrganizationId(), List.of(scope)).get(scope.getId()),
                    serviceAccessService.getServiceAccess(userId).getStatus(),
                    brandSpaceConfigService.summarizeConfigs(List.of(scope.getId())).get(scope.getId())
            );
        }
        if (!canUserRestore(scope, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该品牌已超过自助恢复期限，请联系平台支持");
        }
        assertNoActiveRestoreConflict(context);
        restoreScope(scope);
        DiagnosisTask latestTask = loadLatestTasksByScopeId(List.of(scope)).get(scope.getId());
        DiagnosisReportRecord latestReport = loadLatestReportsByScopeId(scope.getOrganizationId(), List.of(scope)).get(scope.getId());
        BrandSpaceConfigService.ConfigSummarySnapshot configSummary =
                brandSpaceConfigService.summarizeConfigs(List.of(scope.getId())).get(scope.getId());
        return toBrandSpaceItem(
                scope,
                context.brand(),
                latestTask,
                latestReport,
                serviceAccessService.getServiceAccess(userId).getStatus(),
                configSummary
        );
    }

    public BrandSpaceArchiveView deleteArchived(String userId, String brandSpaceId) {
        BrandSpaceAccessContext context = requireBrandSpaceContext(userId, brandSpaceId, true);
        BrandScope scope = context.scope();
        if (!BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先将品牌空间移出当前账号后再删除");
        }
        LocalDateTime now = LocalDateTime.now();
        scope.setTrackingMode(TRACKING_MODE_PAUSED);
        scope.setRetentionUntil(now.minusSeconds(1));
        if (scope.getInternalRestoreUntil() == null) {
            scope.setInternalRestoreUntil(BrandSpaceLifecyclePolicy.resolveInternalRestoreUntil(resolveArchiveAnchor(scope)));
        }
        brandScopeMapper.updateById(scope);
        return toArchiveView(scope, context.brand(), now);
    }



    private BrandSpaceAccessContext requireBrandSpaceContext(String userId,
                                                             String brandSpaceId,
                                                             boolean includeArchived) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!StringUtils.hasText(brandSpaceId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        String organizationId = serviceAccessService.resolveOrganizationId(userId);
        BrandScope scope = brandScopeMapper.selectById(brandSpaceId.trim());
        if (scope == null || !StringUtils.hasText(organizationId) || !Objects.equals(organizationId, scope.getOrganizationId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        if (!includeArchived && BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
        }
        Brand brand = StringUtils.hasText(scope.getBrandId()) ? brandMapper.selectById(scope.getBrandId()) : null;
        return new BrandSpaceAccessContext(organizationId, scope, brand);
    }

    private List<BrandScope> loadArchivedScopesForOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return List.of();
        }
        return brandScopeMapper.selectList(new LambdaQueryWrapper<BrandScope>()
                .eq(BrandScope::getOrganizationId, organizationId)
                .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ARCHIVED)
                .orderByDesc(BrandScope::getArchivedAt)
                .orderByDesc(BrandScope::getUpdatedAt));
    }

    private void restoreScope(BrandScope scope) {
        scope.setScopeStatusCode(BrandSpaceLifecyclePolicy.SCOPE_STATUS_ACTIVE);
        scope.setArchivedAt(null);
        scope.setArchivedByMembershipId(null);
        scope.setRetentionUntil(null);
        scope.setInternalRestoreUntil(null);
        scope.setPurgedAt(null);
        brandScopeMapper.updateById(scope);
    }

    private void assertNoActiveRestoreConflict(BrandSpaceAccessContext context) {
        if (context == null || context.scope() == null || !StringUtils.hasText(context.organizationId())) {
            return;
        }
        BrandScope archivedScope = context.scope();
        String archivedBrandId = defaultIfBlank(archivedScope.getBrandId(), null);
        String archivedBrandName = normalizeRestoreConflictKey(firstText(
                context.brand() == null ? null : context.brand().getBrandName(),
                archivedScope.getScopeName()
        ));
        List<BrandScope> activeScopes = brandScopeMapper.selectList(new LambdaQueryWrapper<BrandScope>()
                .eq(BrandScope::getOrganizationId, context.organizationId())
                .and(wrapper -> wrapper
                        .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ACTIVE)
                        .or()
                        .isNull(BrandScope::getScopeStatusCode))
                .ne(BrandScope::getId, archivedScope.getId()));
        if (activeScopes.isEmpty()) {
            return;
        }
        Map<String, Brand> brandsById = loadBrandsById(activeScopes);
        for (BrandScope activeScope : activeScopes) {
            String activeBrandId = defaultIfBlank(activeScope.getBrandId(), null);
            boolean sameBrandId = StringUtils.hasText(archivedBrandId) && Objects.equals(archivedBrandId, activeBrandId);
            String activeBrandName = normalizeRestoreConflictKey(firstText(
                    brandsById.get(activeScope.getBrandId()) == null ? null : brandsById.get(activeScope.getBrandId()).getBrandName(),
                    activeScope.getScopeName()
            ));
            boolean sameBrandName = StringUtils.hasText(archivedBrandName) && Objects.equals(archivedBrandName, activeBrandName);
            if (sameBrandId || sameBrandName) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "当前账号中已存在同名活跃品牌空间，请先处理现有品牌空间后再恢复");
            }
        }
    }

    private BrandSpaceBasicProfileView toBasicProfileView(BrandScope scope,
                                                          Brand brand,
                                                          List<String> competitors) {
        return BrandSpaceBasicProfileView.builder()
                .brandSpaceId(scope == null ? null : scope.getId())
                .brandId(brand == null ? null : brand.getId())
                .brandName(firstText(
                        brand == null ? null : brand.getBrandName(),
                        scope == null ? null : scope.getScopeName()
                ))
                .category(firstText(
                        scope == null ? null : scope.getCategorySnapshot(),
                        brand == null ? null : brand.getCategoryName()
                ))
                .officialWebsite(defaultIfBlank(brand == null ? null : brand.getOfficialWebsite(), null))
                .primaryDomain(firstText(
                        scope == null ? null : scope.getPrimaryDomain(),
                        brand == null ? null : brand.getCanonicalDomain()
                ))
                .competitors(competitors == null ? List.of() : List.copyOf(competitors))
                .supplementalBrief(BrandSpaceBasicProfileSupport.readStoredSupplementalBrief(scope))
                .build();
    }

    private BrandSpaceListItemView toBrandSpaceItem(BrandScope scope,
                                                    Brand brand,
                                                    DiagnosisTask latestTask,
                                                    DiagnosisReportRecord latestReport,
                                                    String serviceAccessStatus,
                                                    BrandSpaceConfigService.ConfigSummarySnapshot configSummary) {
        Map<String, Object> summaryKpis = readSummaryKpis(latestReport);
        int enabledTopicCount = configSummary == null ? 0 : configSummary.enabledTopicCount();
        int enabledPromptCount = configSummary == null ? 0 : configSummary.enabledPromptCount();
        boolean configReady = enabledTopicCount > 0 && enabledPromptCount > 0;
        String lastRunStatus = mapRunStatus(latestTask, latestReport);
        String healthStatus = deriveHealthStatus(configReady, lastRunStatus, latestReport);
        Double top3Rate = resolveListTop3Rate(scope, brand, latestTask, latestReport, summaryKpis);
        Double visibilityScore = resolveListVisibilityScore(latestReport, summaryKpis, top3Rate);
        ProbePlanService.PlanStatusSnapshot probePlanStatus = probePlanService.currentPlanStatus(
                scope == null ? null : scope.getId(),
                configReady,
                scope == null ? null : scope.getConfigGeneration()
        );
        if (probePlanStatus == null) {
            probePlanStatus = new ProbePlanService.PlanStatusSnapshot("unavailable", null, null, null, null);
        }
        return BrandSpaceListItemView.builder()
                .brandSpaceId(scope == null ? null : scope.getId())
                .brandId(brand == null ? null : brand.getId())
                .brandScopeId(scope == null ? null : scope.getId())
                .brandName(firstText(brand == null ? null : brand.getBrandName(),
                        latestTask == null ? null : latestTask.getBrandNameSnapshot(),
                        scope == null ? null : scope.getScopeName()))
                .category(firstText(scope == null ? null : scope.getCategorySnapshot(),
                        brand == null ? null : brand.getCategoryName()))
                .officialWebsite(brand != null
                        ? defaultIfBlank(brand.getOfficialWebsite(), null)
                        : defaultIfBlank(latestTask == null ? null : latestTask.getOfficialWebsiteSnapshot(), null))
                .primaryDomain(firstText(
                        scope == null ? null : scope.getPrimaryDomain(),
                        brand == null ? null : brand.getCanonicalDomain()
                ))
                .status(mapBrandSpaceStatus(latestTask, latestReport))
                .latestRunId(latestTask == null ? null : latestTask.getId())
                .latestReportId(latestReport == null ? null : latestReport.getId())
                .lastGeneratedAt(latestReport == null || latestReport.getGeneratedAt() == null
                        ? null
                        : DATE_TIME_FORMATTER.format(latestReport.getGeneratedAt()))
                .visibilityScore(visibilityScore)
                .recommendRate(resolveListRecommendRate(latestReport, summaryKpis))
                .mentionRate(resolveListMentionRate(latestReport, summaryKpis))
                .top3Rate(top3Rate)
                .serviceAccessStatus(serviceAccessStatus)
                .trackingStatus(resolveTrackingStatus(scope, configReady))
                .healthStatus(healthStatus)
                .configReady(configReady)
                .activePromptCount(enabledPromptCount)
                .probePlanStatus(probePlanStatus.status())
                .probePlanVersionNo(probePlanStatus.versionNo())
                .activeRecurringProbeCount(probePlanStatus.activeRecurringProbeCount())
                .estimatedCreditsPerCycle(probePlanStatus.estimatedCreditsPerCycle())
                .lastRunStatus(lastRunStatus)
                .lastRunAt(formatDateTime(resolveLastRunAt(latestTask, latestReport)))
                .latestSchedule(readLatestSchedule(latestTask))
                .build();
    }

    private BrandSpaceScheduleView readLatestSchedule(DiagnosisTask latestTask) {
        if (latestTask == null || latestTask.getTaskOrderJson() == null || latestTask.getTaskOrderJson().isEmpty()) {
            return null;
        }
        return toScheduleView(BrandSpaceTaskOrderSupport.readExecutionPlanFromTaskOrder(latestTask.getTaskOrderJson()));
    }

    private BrandSpaceScheduleView toScheduleView(BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        if (executionPlan == null || executionPlan.targetProbeCount() <= 0) {
            return null;
        }
        return BrandSpaceScheduleView.builder()
                .triggerMode(executionPlan.triggerMode())
                .targetProbeCount(executionPlan.targetProbeCount())
                .coverageStrategy(executionPlan.coverageStrategy())
                .schedulingReason(executionPlan.humanReason())
                .selectedTopicCount(executionPlan.selectedTopics().size())
                .selectedTopicNames(executionPlan.selectedTopicNames())
                .deferredTopicCount(executionPlan.deferredTopics().size())
                .deferredTopicNames(executionPlan.deferredTopicNames())
                .coldStart(executionPlan.coldStart())
                .justEdited(executionPlan.justEdited())
                .quotaLimited(executionPlan.quotaLimited())
                .accountBrandCount(executionPlan.accountBrandCount())
                .brandCoreProbeCount(executionPlan.brandCoreProbeCount())
                .brandExpansionProbeCount(executionPlan.brandExpansionProbeCount())
                .weeklyTargetTopicCount(executionPlan.weeklyTargetTopicCount())
                .weeklyCoveredTopicCount(executionPlan.weeklyCoveredTopicCount())
                .weeklyGapTopicCount(executionPlan.weeklyGapTopicCount())
                .weeklyCoverageRatio(executionPlan.weeklyCoverageRatio())
                .build();
    }

    private BrandSpaceScheduleExplanationView.ConfigSnapshotView buildScheduleConfigSnapshot(
            BrandSpaceConfigView configView,
            List<BrandSpaceScheduleExplanationView.TopicIssueView> blockedTopics) {
        BrandSpaceConfigView.SummaryView summary = configView == null ? null : configView.getSummary();
        List<BrandSpaceConfigView.TopicView> topics = configView == null || configView.getTopics() == null
                ? List.of()
                : configView.getTopics();
        int executableTopicCount = (int) topics.stream()
                .filter(topic -> topic != null
                        && Boolean.TRUE.equals(topic.getEnabled())
                        && defaultInt(topic.getEnabledPromptCount()) > 0)
                .count();
        return BrandSpaceScheduleExplanationView.ConfigSnapshotView.builder()
                .topicCount(summary == null ? topics.size() : summary.getTopicCount())
                .enabledTopicCount(summary == null ? null : summary.getEnabledTopicCount())
                .promptCount(summary == null ? null : summary.getPromptCount())
                .enabledPromptCount(summary == null ? null : summary.getEnabledPromptCount())
                .executableTopicCount(executableTopicCount)
                .blockedTopicCount(blockedTopics == null ? 0 : blockedTopics.size())
                .build();
    }

    private List<BrandSpaceScheduleExplanationView.TopicDecisionView> toScheduleTopicDecisionViews(
            List<BrandSpaceSchedulingService.TopicDecision> topicDecisions) {
        if (topicDecisions == null || topicDecisions.isEmpty()) {
            return List.of();
        }
        return topicDecisions.stream()
                .filter(Objects::nonNull)
                .map(topic -> BrandSpaceScheduleExplanationView.TopicDecisionView.builder()
                        .topicId(topic.topicId())
                        .topicName(topic.topicName())
                        .allocatedProbeCount(topic.allocatedProbeCount())
                        .priorityScore(topic.priorityScore())
                        .reasonTags(topic.reasonTags())
                        .build())
                .toList();
    }

    private List<BrandSpaceScheduleExplanationView.TopicIssueView> buildBlockedScheduleTopics(BrandSpaceConfigView configView) {
        if (configView == null || configView.getTopics() == null || configView.getTopics().isEmpty()) {
            return List.of();
        }
        List<BrandSpaceScheduleExplanationView.TopicIssueView> blockedTopics = new ArrayList<>();
        for (BrandSpaceConfigView.TopicView topic : configView.getTopics()) {
            if (topic == null || !Boolean.TRUE.equals(topic.getEnabled())) {
                continue;
            }
            if (defaultInt(topic.getEnabledPromptCount()) > 0) {
                continue;
            }
            blockedTopics.add(BrandSpaceScheduleExplanationView.TopicIssueView.builder()
                    .topicId(topic.getTopicId())
                    .topicName(topic.getName())
                    .issueCode("NO_ENABLED_PROMPTS")
                    .issueLabel("当前不可执行")
                    .issueDetail("该主题已启用，但没有任何启用中的提示词，因此不会进入调度。")
                    .build());
        }
        return List.copyOf(blockedTopics);
    }

    private String buildScheduleSnapshotMessage(BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                List<BrandSpaceScheduleExplanationView.TopicIssueView> blockedTopics) {
        if (executionPlan != null && executionPlan.targetProbeCount() > 0) {
            return "已读取最近一次真实任务单里的调度快照。";
        }
        if (blockedTopics != null && !blockedTopics.isEmpty()) {
            return "当前配置存在不可执行主题，且最近一轮任务单里还没有可读的调度快照。";
        }
        return "当前还没有可读的调度快照。";
    }

    private MainChainRunTicket createMainChainRun(String userId,
                                                  String idempotencyKey,
                                                  Map<String, Object> taskOrder) {
        CreateDiagnosisFromTaskOrderRequest request = new CreateDiagnosisFromTaskOrderRequest();
        request.setRequestId(idempotencyKey);
        request.setFrontDeskTaskOrder(taskOrder);
        return new MainChainRunTicket(diagnosisService.createDiagnosis(userId, request).getId());
    }

    private int resolveCreditsPerProbe(ServiceAccessView serviceAccess) {
        ServiceAccessView.Limits limits = serviceAccess == null ? null : serviceAccess.getLimits();
        Integer configured = limits == null ? null : limits.getCreditsPerProbe();
        return configured != null && configured > 0 ? configured : 1;
    }

    private CreateDiagnosisRequest buildPreviewDiagnosisRequest(BrandSpaceDraftPayload draft,
                                                               List<BrandSpaceTopicPayload> topics,
                                                               List<BrandSpacePromptPayload> prompts,
                                                               String idempotencyKey,
                                                               BrandSpaceExecutionGateService.AuthoritySnapshot authority,
                                                               BrandSpaceSchedulingService.ExecutionPlan executionPlan) {
        CreateDiagnosisRequest request = new CreateDiagnosisRequest();
        String normalizedIdempotencyKey = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
        request.setRequestId(normalizedIdempotencyKey);
        request.setFrontDeskTaskOrder(
                BrandSpaceTaskOrderSupport.buildPreviewTaskOrder(
                        draft,
                        topics,
                        prompts,
                        buildPreviewContractContext(normalizedIdempotencyKey, authority),
                        executionPlan
                )
        );
        return request;
    }

    private Map<String, Object> buildCreateTaskOrder(Map<String, Object> previewTaskOrder,
                                                     BrandSpaceDraftPayload draft,
                                                     List<BrandSpaceTopicPayload> topics,
                                                     List<BrandSpacePromptPayload> prompts,
                                                     BrandSpaceExecutionGateService.AuthoritySnapshot authority,
                                                     BrandSpaceAccessContext createContext,
                                                     String requestId,
                                                     BrandSpaceSchedulingService.ExecutionPlan executionPlan,
                                                     String executeModeCode,
                                                     String targetModelProduct) {
        return BrandSpaceTaskOrderSupport.mergeCreateTaskOrder(
                previewTaskOrder,
                draft,
                topics,
                prompts,
                buildCreateContractContext(
                        requestId,
                        authority,
                        createContext,
                        isOneTimeMode(executeModeCode) ? TRACKING_MODE_PAUSED : TRACKING_MODE_ACTIVE
                ),
                executionPlan,
                targetModelProduct,
                objectMapper
        );
    }

    private void syncInitialRunExecutionHints(DiagnosisTask task,
                                              String fallbackBrandName,
                                              ProbePlanService.SnapshotRef planSnapshot) {
        if (task == null || !StringUtils.hasText(task.getId()) || !StringUtils.hasText(task.getBrandScopeId())) {
            return;
        }
        String brandName = fallbackBrandName;
        if (StringUtils.hasText(task.getBrandId())) {
            Brand brand = brandMapper.selectById(task.getBrandId());
            brandName = firstText(brand == null ? null : brand.getBrandName(), fallbackBrandName);
        }
        BrandScope scope = brandScopeMapper.selectById(task.getBrandScopeId());
        BrandSpaceConfigView configView = brandSpaceConfigService.loadConfigView(
                task.getBrandScopeId(),
                task.getBrandId(),
                brandName,
                scope == null ? null : scope.getConfigGeneration()
        );
        Map<String, Object> refreshedTaskOrder = BrandSpaceTaskOrderSupport.refreshRuntimeExecutionHints(
                task.getTaskOrderJson(),
                configView,
                objectMapper
        );
        task.setTaskOrderJson(attachProbePlanBinding(refreshedTaskOrder, planSnapshot));
        task.setTaskMetadataJson(attachProbePlanRuntimeMetadata(task.getTaskMetadataJson(), planSnapshot));
        diagnosisTaskMapper.updateById(task);
    }

    private Map<String, Object> attachProbePlanBinding(Map<String, Object> taskOrder,
                                                       ProbePlanService.SnapshotRef planSnapshot) {
        if (planSnapshot == null) {
            return taskOrder;
        }
        Map<String, Object> mutableTaskOrder = deepMutableCopy(taskOrder);
        Map<String, Object> businessHints = readMutableMap(mutableTaskOrder.get("businessHints"));
        businessHints.put("probePlanBinding", buildProbePlanBinding(planSnapshot));
        mutableTaskOrder.put("businessHints", businessHints);

        Map<String, Object> taskMetadata = readMutableMap(mutableTaskOrder.get("taskMetadata"));
        taskMetadata.putAll(buildProbePlanRuntimeMetadata(planSnapshot));
        mutableTaskOrder.put("taskMetadata", taskMetadata);
        return mutableTaskOrder;
    }

    private Map<String, Object> attachProbePlanRuntimeMetadata(Map<String, Object> metadata,
                                                               ProbePlanService.SnapshotRef planSnapshot) {
        Map<String, Object> mutableMetadata = deepMutableCopy(metadata);
        if (planSnapshot != null) {
            mutableMetadata.putAll(buildProbePlanRuntimeMetadata(planSnapshot));
        }
        return mutableMetadata;
    }

    private Map<String, Object> buildProbePlanBinding(ProbePlanService.SnapshotRef planSnapshot) {
        LinkedHashMap<String, Object> binding = new LinkedHashMap<>();
        binding.put("contractVersion", "probe_plan_b1");
        binding.put("planVersionId", planSnapshot.planVersionId());
        binding.put("planRecordId", planSnapshot.planRecordId());
        binding.put("versionNo", planSnapshot.versionNo());
        binding.put("recordNo", planSnapshot.recordNo());
        binding.put("enabledProbeCount", planSnapshot.enabledProbeCount());
        return binding;
    }

    private Map<String, Object> buildProbePlanRuntimeMetadata(ProbePlanService.SnapshotRef planSnapshot) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("billingSnapshotVersion", "probe_plan_b1");
        metadata.put("planVersionId", planSnapshot.planVersionId());
        metadata.put("planRecordId", planSnapshot.planRecordId());
        metadata.put("probePlanVersionNo", planSnapshot.versionNo());
        metadata.put("probePlanRecordNo", planSnapshot.recordNo());
        metadata.put("runTypeCode", "INITIAL");
        metadata.put("triggerSource", "brand_space_create");
        return metadata;
    }

    private int resolvePromptProbeCount(List<BrandSpacePromptPayload> prompts) {
        return resolvePromptProbeCountFromTexts(brandSpaceConfigService.resolveEnabledPromptTexts(prompts));
    }

    private int resolvePromptProbeCountFromTexts(List<String> enabledPromptTexts) {
        int promptProbeCount = enabledPromptTexts == null ? 0 : enabledPromptTexts.size();
        if (promptProbeCount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间至少需要 1 条启用 prompt/probe");
        }
        return promptProbeCount;
    }

    private BrandSpaceTaskOrderSupport.ContractContext buildPreviewContractContext(String requestId,
                                                                                   BrandSpaceExecutionGateService.AuthoritySnapshot authority) {
        return new BrandSpaceTaskOrderSupport.ContractContext(
                "brand_space_preview",
                "brand_space_preview",
                "interactive_preview",
                "draft_preview",
                requestId,
                authority == null ? null : authority.organizationId(),
                authority == null ? null : authority.membershipId(),
                null,
                null,
                null,
                null,
                null,
                authority == null ? null : authority.serviceAccess()
        );
    }

    private BrandSpaceTaskOrderSupport.ContractContext buildCreateContractContext(String requestId,
                                                                                  BrandSpaceExecutionGateService.AuthoritySnapshot authority,
                                                                                  BrandSpaceAccessContext createContext,
                                                                                  String trackingMode) {
        return new BrandSpaceTaskOrderSupport.ContractContext(
                "brand_space_create_initial_run",
                "brand_space_create_initial_run",
                "interactive_create",
                "create_initial_run",
                StringUtils.hasText(requestId) ? requestId.trim() : null,
                authority == null ? null : authority.organizationId(),
                authority == null ? null : authority.membershipId(),
                createContext == null || createContext.brand() == null ? null : createContext.brand().getId(),
                createContext == null || createContext.scope() == null ? null : createContext.scope().getId(),
                null,
                StringUtils.hasText(trackingMode) ? trackingMode.trim() : TRACKING_MODE_ACTIVE,
                null,
                authority == null ? null : authority.serviceAccess()
        );
    }

    private BrandSpaceAccessContext ensureCreateAccessContext(String userId, BrandSpaceDraftPayload draft) {
        DiagnosisTenantContextService.TenantContext tenant = diagnosisTenantContextService.ensureBrandTenancy(
                userId,
                draft == null ? null : draft.getBrandName(),
                draft == null ? null : draft.getCategory(),
                draft == null ? null : draft.getOfficialWebsite()
        );
        if (tenant == null || tenant.organization() == null || tenant.brand() == null || tenant.brandScope() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "品牌空间创建成功，但品牌空间底座未落库");
        }
        return new BrandSpaceAccessContext(
                tenant.organization().getId(),
                tenant.brandScope(),
                tenant.brand()
        );
    }

    private CreateBrandSpaceResponse buildBlockedCreateResponse(BrandSpaceAccessContext context,
                                                                String blockCode,
                                                                String blockMessage) {
        return CreateBrandSpaceResponse.builder()
                .brandSpaceId(context == null || context.scope() == null ? null : context.scope().getId())
                .brandId(context == null || context.brand() == null ? null : context.brand().getId())
                .brandScopeId(context == null || context.scope() == null ? null : context.scope().getId())
                .initialRunId(null)
                .initialRunStatus("blocked")
                .initialRunBlockCode(blockCode)
                .initialRunBlockMessage(blockMessage)
                .status("active")
                .nextView("/brands")
                .build();
    }

    private void updateBrandScopeDraft(String brandScopeId, BrandSpaceDraftPayload draft) {
        BrandScope scope = brandScopeMapper.selectById(brandScopeId);
        if (scope == null) {
            return;
        }
        scope.setRegionCode(defaultIfBlank(draft.getRegionCode(), scope.getRegionCode(), "CN"));
        scope.setLocale(defaultIfBlank(draft.getLocale(), scope.getLocale(), "zh-CN"));
        scope.setLanguage(defaultIfBlank(draft.getLanguage(), scope.getLanguage(), "zh-CN"));
        scope.setPrimaryDomain(defaultIfBlank(extractHost(draft.getOfficialWebsite()), null));
        scope.setDefaultModelPolicyJson(BrandSpaceBasicProfileSupport.writeStoredProfile(
                scope.getDefaultModelPolicyJson(),
                draft.getCompetitors(),
                draft.getSupplementalBrief()
        ));
        brandScopeMapper.updateById(scope);
    }

    private void persistBasicProfile(BrandSpaceAccessContext context, BrandSpaceDraftPayload draft) {
        if (context == null || context.scope() == null) {
            return;
        }
        Brand brand = context.brand();
        String normalizedBrandName = draft.getBrandName().trim();
        String normalizedCategory = draft.getCategory().trim();
        String normalizedWebsite = defaultIfBlank(draft.getOfficialWebsite(), null);
        String primaryDomain = defaultIfBlank(extractHost(normalizedWebsite), null);

        if (brand != null) {
            String previousBrandName = defaultIfBlank(brand.getBrandName(), null);
            brand.setBrandName(normalizedBrandName);
            brand.setCategoryName(normalizedCategory);
            brand.setOfficialWebsite(normalizedWebsite);
            brand.setCanonicalDomain(primaryDomain);
            brand.setOfficialDomainsJson(primaryDomain == null ? List.of() : List.of(primaryDomain));
            brand.setBrandAliasesJson(mergeBrandAliases(normalizedBrandName, previousBrandName, brand.getBrandAliasesJson()));
            brandMapper.updateById(brand);
        }

        BrandScope scope = context.scope();
        scope.setScopeName(buildScopeName(normalizedBrandName, normalizedCategory));
        scope.setCategorySnapshot(normalizedCategory);
        scope.setPrimaryDomain(primaryDomain);
        scope.setDefaultModelPolicyJson(BrandSpaceBasicProfileSupport.writeStoredProfile(
                scope.getDefaultModelPolicyJson(),
                draft.getCompetitors(),
                draft.getSupplementalBrief()
        ));
        brandScopeMapper.updateById(scope);
    }

    private BrandSpaceDraftPayload buildMutableDraft(BrandSpaceAccessContext context,
                                                     String regionCode,
                                                     String locale,
                                                     String language) {
        return BrandSpaceDraftPayload.builder()
                .brandName(firstText(context == null || context.brand() == null ? null : context.brand().getBrandName(),
                        context == null || context.scope() == null ? null : context.scope().getScopeName()))
                .category(context == null || context.scope() == null ? null : context.scope().getCategorySnapshot())
                .officialWebsite(context == null || context.brand() == null ? null : context.brand().getOfficialWebsite())
                .regionCode(defaultIfBlank(regionCode, context == null || context.scope() == null ? null : context.scope().getRegionCode(), "CN"))
                .locale(defaultIfBlank(locale, context == null || context.scope() == null ? null : context.scope().getLocale(), "zh-CN"))
                .language(defaultIfBlank(language, context == null || context.scope() == null ? null : context.scope().getLanguage(), "zh-CN"))
                .competitors(resolveDraftCompetitors(context))
                .supplementalBrief(context == null || context.scope() == null
                        ? null
                        : BrandSpaceBasicProfileSupport.readStoredSupplementalBrief(context.scope()))
                .build();
    }

    private List<String> resolveDraftCompetitors(BrandSpaceAccessContext context) {
        if (context == null || context.scope() == null) {
            return List.of();
        }
        if (BrandSpaceBasicProfileSupport.hasStoredCompetitors(context.scope())) {
            return resolveStoredCompetitors(context.scope());
        }
        List<String> recoveredCompetitors = resolveHistoricalTaskCompetitors(context.scope().getId());
        if (!recoveredCompetitors.isEmpty()) {
            persistStoredCompetitors(context.scope(), recoveredCompetitors);
        }
        return recoveredCompetitors;
    }

    private List<String> resolveStoredCompetitors(BrandScope scope) {
        return BrandSpaceBasicProfileSupport.readStoredCompetitors(scope);
    }

    private Integer countEnabledPrompts(List<BrandSpacePromptPayload> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return null;
        }
        int enabledPromptCount = (int) prompts.stream()
                .filter(prompt -> prompt != null)
                .filter(prompt -> !Boolean.FALSE.equals(prompt.getEnabled()))
                .filter(prompt -> StringUtils.hasText(prompt.getText()))
                .count();
        return enabledPromptCount > 0 ? enabledPromptCount : null;
    }

    private List<String> resolveHistoricalTaskCompetitors(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return List.of();
        }
        List<DiagnosisTask> historicalTasks = diagnosisTaskMapper.selectList(new LambdaQueryWrapper<DiagnosisTask>()
                .eq(DiagnosisTask::getBrandScopeId, brandScopeId.trim())
                .orderByDesc(DiagnosisTask::getUpdatedAt)
                .orderByDesc(DiagnosisTask::getCreatedAt)
                .last("limit 50"));
        if (historicalTasks == null || historicalTasks.isEmpty()) {
            return List.of();
        }
        for (DiagnosisTask task : historicalTasks) {
            List<String> competitors = readTaskCompetitors(task);
            if (!competitors.isEmpty()) {
                return competitors;
            }
        }
        return List.of();
    }

    private List<String> readTaskCompetitors(DiagnosisTask task) {
        if (task == null || task.getTaskOrderJson() == null || task.getTaskOrderJson().isEmpty()) {
            return List.of();
        }
        Map<String, Object> taskOrder = readMap(task.getTaskOrderJson());
        return readStringList(taskOrder.get("competitors"));
    }

    private void persistStoredCompetitors(BrandScope scope, List<String> competitors) {
        if (scope == null || competitors == null || competitors.isEmpty()) {
            return;
        }
        if (BrandSpaceBasicProfileSupport.hasStoredCompetitors(scope)
                && !BrandSpaceBasicProfileSupport.readStoredCompetitors(scope).isEmpty()) {
            return;
        }
        scope.setDefaultModelPolicyJson(BrandSpaceBasicProfileSupport.writeStoredProfile(
                scope.getDefaultModelPolicyJson(),
                competitors,
                BrandSpaceBasicProfileSupport.readStoredSupplementalBrief(scope)
        ));
        brandScopeMapper.updateById(scope);
    }

    private void incrementConfigGeneration(String brandScopeId, String trackingMode) {
        BrandScope scope = brandScopeMapper.selectById(brandScopeId);
        if (scope == null) {
            return;
        }
        scope.setConfigGeneration(defaultInt(scope.getConfigGeneration()) + 1);
        if (StringUtils.hasText(trackingMode)) {
            scope.setTrackingMode(normalizeTrackingMode(trackingMode));
        } else if (!StringUtils.hasText(scope.getTrackingMode())) {
            scope.setTrackingMode(TRACKING_MODE_ACTIVE);
        }
        brandScopeMapper.updateById(scope);
    }

    private void refreshScheduleState(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return;
        }
        KnowledgeAssetScheduleStateMaterializationService materializationService =
                knowledgeAssetScheduleStateMaterializationServiceProvider.getIfAvailable();
        if (materializationService == null) {
            return;
        }
        materializationService.materializeScope(brandScopeId.trim());
    }

    private CreateBrandSpaceResponse buildCreateResponseFromDiagnosis(Diagnosis diagnosis) {
        DiagnosisTask task = diagnosis == null ? null : diagnosisTaskMapper.selectById(diagnosis.getId());
        if (task == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "幂等请求已存在，但品牌空间镜像缺失");
        }
        DiagnosisReportRecord latestReport = findLatestReportHead(task.getOrganizationId(), task.getBrandScopeId());
        return CreateBrandSpaceResponse.builder()
                .brandSpaceId(task.getBrandScopeId())
                .brandId(task.getBrandId())
                .brandScopeId(task.getBrandScopeId())
                .initialRunId(task.getId())
                .initialRunStatus(mapRunStatus(task, latestReport))
                .status(mapBrandSpaceStatus(task, latestReport))
                .nextView("/brands")
                .build();
    }

    private Map<String, Brand> loadBrandsById(List<BrandScope> scopes) {
        Set<String> brandIds = new LinkedHashSet<>();
        for (BrandScope scope : scopes) {
            if (scope != null && StringUtils.hasText(scope.getBrandId())) {
                brandIds.add(scope.getBrandId());
            }
        }
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Brand> brandsById = new HashMap<>();
        for (Brand brand : brandMapper.selectBatchIds(brandIds)) {
            if (brand != null && StringUtils.hasText(brand.getId())) {
                brandsById.put(brand.getId(), brand);
            }
        }
        return brandsById;
    }

    private Map<String, DiagnosisTask> loadLatestTasksByScopeId(List<BrandScope> scopes) {
        List<String> scopeIds = extractScopeIds(scopes);
        if (scopeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisTask> latestTasksByScopeId = new HashMap<>();
        for (DiagnosisTask task : diagnosisTaskMapper.selectLatestByBrandScopeIds(scopeIds)) {
            if (task != null && StringUtils.hasText(task.getBrandScopeId())) {
                latestTasksByScopeId.put(task.getBrandScopeId(), task);
            }
        }
        return latestTasksByScopeId;
    }

    private Map<String, DiagnosisReportRecord> loadLatestReportsByScopeId(String organizationId, List<BrandScope> scopes) {
        if (!StringUtils.hasText(organizationId)) {
            return Map.of();
        }
        List<String> scopeIds = extractScopeIds(scopes);
        if (scopeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisReportRecord> latestReportsByScopeId = new HashMap<>(loadLatestKnowledgeReportsByScopeId(scopeIds));
        List<String> missingScopeIds = scopeIds.stream()
                .filter(scopeId -> !latestReportsByScopeId.containsKey(scopeId))
                .toList();
        if (missingScopeIds.isEmpty()) {
            return latestReportsByScopeId;
        }
        for (DiagnosisReportRecord report : diagnosisReportRecordMapper.selectLatestHeadsByBrandScopeIds(organizationId, missingScopeIds)) {
            if (report != null && StringUtils.hasText(report.getBrandScopeId())) {
                latestReportsByScopeId.put(report.getBrandScopeId(), report);
            }
        }
        return latestReportsByScopeId;
    }

    private DiagnosisReportRecord findLatestReportHead(String organizationId, String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return null;
        }
        DiagnosisReportRecord knowledgeReport = loadLatestKnowledgeReport(brandScopeId);
        if (knowledgeReport != null) {
            return knowledgeReport;
        }
        if (!StringUtils.hasText(organizationId)) {
            return null;
        }
        return diagnosisReportRecordMapper.selectLatestHeadByBrandScopeId(organizationId, brandScopeId);
    }

    private List<String> extractScopeIds(List<BrandScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        return scopes.stream()
                .filter(Objects::nonNull)
                .map(BrandScope::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<BrandSpaceTopicPayload> defaultTopicsIfNeeded(BrandSpaceDraftPayload draft,
                                                               List<BrandSpaceTopicPayload> topics) {
        if (topics == null || topics.isEmpty()) {
            return brandSpaceConfigService.generateTopics(draft);
        }
        return topics;
    }

    private List<BrandSpacePromptPayload> defaultPromptsIfNeeded(BrandSpaceDraftPayload draft,
                                                                 List<BrandSpaceTopicPayload> topics,
                                                                 List<BrandSpacePromptPayload> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            return brandSpaceConfigService.generatePrompts(draft, topics);
        }
        return prompts;
    }

    private String mapBrandSpaceStatus(DiagnosisTask latestTask, DiagnosisReportRecord latestReport) {
        if (latestTask != null && isGenerating(latestTask.getStatusCode())) {
            return "generating";
        }
        if (latestReport != null) {
            return "active";
        }
        if (latestTask != null && intEquals(latestTask.getStatusCode(), STATUS_FAILED)) {
            return "failed";
        }
        if (latestTask != null && intEquals(latestTask.getStatusCode(), STATUS_CANCELLED)) {
            return "failed";
        }
        return "active";
    }

    private boolean isGenerating(Integer statusCode) {
        return intEquals(statusCode, STATUS_PENDING)
                || intEquals(statusCode, STATUS_RUNNING)
                || intEquals(statusCode, STATUS_QUEUED);
    }

    private String resolveTrackingStatus(BrandScope scope, boolean configReady) {
        String trackingMode = normalizeStoredTrackingMode(scope == null ? null : scope.getTrackingMode());
        if (TRACKING_MODE_PAUSED.equals(trackingMode)) {
            return TRACKING_MODE_PAUSED;
        }
        return configReady ? TRACKING_MODE_ACTIVE : TRACKING_STATUS_NEEDS_ATTENTION;
    }

    private String deriveHealthStatus(boolean configReady,
                                      String lastRunStatus,
                                      DiagnosisReportRecord latestReport) {
        if (!configReady) {
            return HEALTH_STATUS_CONFIG_INCOMPLETE;
        }
        if ("failed".equals(lastRunStatus) || "cancelled".equals(lastRunStatus)) {
            return HEALTH_STATUS_ERROR;
        }
        if ("partial".equals(lastRunStatus)) {
            return HEALTH_STATUS_DEGRADED;
        }
        if ("completed".equals(lastRunStatus)) {
            return HEALTH_STATUS_HEALTHY;
        }
        if (latestReport != null && !"created".equals(lastRunStatus) && !"running".equals(lastRunStatus)) {
            return HEALTH_STATUS_HEALTHY;
        }
        return null;
    }

    private String mapRunStatus(DiagnosisTask latestTask, DiagnosisReportRecord latestReport) {
        if (latestTask == null) {
            return latestReport == null ? null : "completed";
        }
        if (intEquals(latestTask.getStatusCode(), STATUS_COMPLETED)) {
            return "completed";
        }
        if (intEquals(latestTask.getStatusCode(), STATUS_FAILED)) {
            return "failed";
        }
        if (intEquals(latestTask.getStatusCode(), STATUS_CANCELLED)) {
            return "cancelled";
        }
        if (intEquals(latestTask.getStatusCode(), STATUS_RUNNING)) {
            return "running";
        }
        if (intEquals(latestTask.getStatusCode(), STATUS_PENDING) || intEquals(latestTask.getStatusCode(), STATUS_QUEUED)) {
            return "created";
        }
        return "partial";
    }

    private LocalDateTime resolveLastRunAt(DiagnosisTask latestTask, DiagnosisReportRecord latestReport) {
        if (latestTask != null) {
            if (latestTask.getCompletedAt() != null) {
                return latestTask.getCompletedAt();
            }
            if (latestTask.getStartedAt() != null) {
                return latestTask.getStartedAt();
            }
            if (latestTask.getCreatedAt() != null) {
                return latestTask.getCreatedAt();
            }
        }
        return latestReport == null ? null : latestReport.getGeneratedAt();
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private boolean canUserRestore(BrandScope scope, LocalDateTime now) {
        return BrandSpaceLifecyclePolicy.isArchived(scope == null ? null : scope.getScopeStatusCode())
                && !isPurged(scope)
                && BrandSpaceLifecyclePolicy.isWindowOpen(resolveUserRestoreUntil(scope), now);
    }

    private boolean canInternalRestore(BrandScope scope, LocalDateTime now) {
        return BrandSpaceLifecyclePolicy.isArchived(scope == null ? null : scope.getScopeStatusCode())
                && !isPurged(scope)
                && BrandSpaceLifecyclePolicy.isWindowOpen(resolveInternalRestoreUntil(scope), now);
    }

    private boolean isPurged(BrandScope scope) {
        return scope != null && scope.getPurgedAt() != null;
    }

    private LocalDateTime resolveArchiveAnchor(BrandScope scope) {
        if (scope == null) {
            return null;
        }
        if (scope.getArchivedAt() != null) {
            return scope.getArchivedAt();
        }
        if (scope.getUpdatedAt() != null) {
            return scope.getUpdatedAt();
        }
        return scope.getCreatedAt();
    }

    private String normalizeRestoreConflictKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LocalDateTime resolveUserRestoreUntil(BrandScope scope) {
        if (scope == null) {
            return null;
        }
        if (scope.getRetentionUntil() != null) {
            return scope.getRetentionUntil();
        }
        return BrandSpaceLifecyclePolicy.resolveRetentionUntil(resolveArchiveAnchor(scope));
    }

    private LocalDateTime resolveInternalRestoreUntil(BrandScope scope) {
        if (scope == null) {
            return null;
        }
        if (scope.getInternalRestoreUntil() != null) {
            return scope.getInternalRestoreUntil();
        }
        return BrandSpaceLifecyclePolicy.resolveInternalRestoreUntil(resolveArchiveAnchor(scope));
    }

    private String resolveArchiveLifecycleStatus(BrandSpaceArchiveView view) {
        if (view == null) {
            return ARCHIVE_STATUS_ACTIVE;
        }
        if (Boolean.TRUE.equals(view.getCanUserRestore())) {
            return ARCHIVE_STATUS_USER_RESTORE;
        }
        if (Boolean.TRUE.equals(view.getCanInternalRestore())) {
            return ARCHIVE_STATUS_INTERNAL_RESTORE;
        }
        if (view.getPurgedAt() != null) {
            return ARCHIVE_STATUS_PURGED;
        }
        return ARCHIVE_STATUS_ACTIVE;
    }

    private BrandSpaceArchiveView toArchiveView(BrandScope scope, Brand brand, LocalDateTime now) {
        LocalDateTime userRestoreUntil = resolveUserRestoreUntil(scope);
        LocalDateTime internalRestoreUntil = resolveInternalRestoreUntil(scope);
        BrandSpaceArchiveView view = BrandSpaceArchiveView.builder()
                .brandSpaceId(scope == null ? null : scope.getId())
                .brandName(firstText(
                        brand == null ? null : brand.getBrandName(),
                        scope == null ? null : scope.getScopeName()
                ))
                .archivedAt(formatDateTime(scope == null ? null : scope.getArchivedAt()))
                .retentionUntil(formatDateTime(userRestoreUntil))
                .internalRestoreUntil(formatDateTime(internalRestoreUntil))
                .purgedAt(formatDateTime(scope == null ? null : scope.getPurgedAt()))
                .canUserRestore(canUserRestore(scope, now))
                .canInternalRestore(canInternalRestore(scope, now))
                .build();
        view.setLifecycleStatus(resolveArchiveLifecycleStatus(view));
        return view;
    }

    private BrandSpaceArchiveView toArchiveView(BrandScope scope, Brand brand) {
        return toArchiveView(scope, brand, LocalDateTime.now());
    }

    private Map<String, Object> readSummaryKpis(DiagnosisReportRecord report) {
        if (report == null || report.getSummaryJson() == null) {
            return Map.of();
        }
        Object raw = report.getSummaryJson().get("kpis");
        return readMap(raw);
    }

    private Double resolveListVisibilityScore(DiagnosisReportRecord report,
                                              Map<String, Object> summaryKpis,
                                              Double top3Rate) {
        if (report == null) {
            return null;
        }
        Double mentionRate = resolveListMentionRate(report, summaryKpis);
        Double recommendRate = resolveListRecommendRate(report, summaryKpis);
        if (mentionRate != null && recommendRate != null && top3Rate != null) {
            return round2(mentionRate * 0.35d + recommendRate * 0.40d + top3Rate * 0.25d);
        }
        if (report.getAbvIndex() != null) {
            return round2(report.getAbvIndex().doubleValue());
        }
        return readNumber(firstNonNull(summaryKpis.get("visibilityScore"), summaryKpis.get("stabilityScore")));
    }

    private Double resolveListMentionRate(DiagnosisReportRecord report,
                                          Map<String, Object> summaryKpis) {
        if (report == null) {
            return null;
        }
        if (report.getCitationRate() != null) {
            return round2(report.getCitationRate().doubleValue() * 100.0d);
        }
        Object raw = firstNonNull(
                summaryKpis.get("overallMentionRate"),
                report.getSummaryJson() == null ? null : report.getSummaryJson().get("overallMentionRate")
        );
        return readFlexiblePercent(raw);
    }

    private Double resolveListRecommendRate(DiagnosisReportRecord report,
                                            Map<String, Object> summaryKpis) {
        if (report == null) {
            return null;
        }
        Object raw = firstNonNull(
                summaryKpis.get("recommendRate"),
                report.getSummaryJson() == null ? null : report.getSummaryJson().get("recommendRate")
        );
        Double resolved = readFlexiblePercent(raw);
        if (resolved != null) {
            return resolved;
        }
        if (report.getConfirmationResponseRate() != null) {
            return round2(report.getConfirmationResponseRate().doubleValue() * 100.0d);
        }
        return null;
    }

    private Double resolveListTop3Rate(BrandScope scope,
                                       Brand brand,
                                       DiagnosisTask latestTask,
                                       DiagnosisReportRecord latestReport,
                                       Map<String, Object> summaryKpis) {
        Double computed = computeTargetTop3Rate(scope, brand, latestTask, latestReport);
        if (computed != null) {
            return computed;
        }
        if (latestReport != null && latestReport.getSpontaneousMentionRate() != null) {
            return round2(latestReport.getSpontaneousMentionRate().doubleValue() * 100.0d);
        }
        Object raw = firstNonNull(
                summaryKpis.get("top3Rate"),
                firstNonNull(summaryKpis.get("l3Top3Rate"), summaryKpis.get("spontaneousMentionRate"))
        );
        if (raw == null && latestReport != null && latestReport.getSummaryJson() != null) {
            raw = firstNonNull(
                    latestReport.getSummaryJson().get("top3Rate"),
                    firstNonNull(
                            latestReport.getSummaryJson().get("l3Top3Rate"),
                            latestReport.getSummaryJson().get("spontaneousMentionRate")
                    )
            );
        }
        return readFlexiblePercent(raw);
    }

    private Double computeTargetTop3Rate(BrandScope scope,
                                         Brand brand,
                                         DiagnosisTask latestTask,
                                         DiagnosisReportRecord latestReport) {
        if (latestReport == null || !StringUtils.hasText(latestReport.getDiagnosisTaskId())) {
            return null;
        }
        int denominator = resolveTop3Denominator(latestReport.getDiagnosisTaskId(), latestReport.getSampleSize());
        if (denominator <= 0) {
            return null;
        }
        List<ProbeBrandMentionRecord> mentions = loadBrandMentions(latestReport.getDiagnosisTaskId()).stream()
                .filter(mention -> Boolean.TRUE.equals(mention.getMentionedFlag()))
                .filter(mention -> mention.getPositionIndex() != null && mention.getPositionIndex() <= 3)
                .toList();
        if (mentions == null || mentions.isEmpty()) {
            return null;
        }
        String targetBrandId = brand == null ? null : normalizeText(brand.getId());
        String targetBrandNameKey = normalizeBrandKey(firstText(
                brand == null ? null : brand.getBrandName(),
                latestTask == null ? null : latestTask.getBrandNameSnapshot(),
                scope == null ? null : scope.getScopeName()
        ));
        LinkedHashSet<String> probeKeys = new LinkedHashSet<>();
        for (ProbeBrandMentionRecord mention : mentions) {
            boolean matchesBrandId = StringUtils.hasText(targetBrandId)
                    && targetBrandId.equals(normalizeText(mention.getBrandId()));
            boolean matchesBrandName = StringUtils.hasText(targetBrandNameKey)
                    && targetBrandNameKey.equals(normalizeBrandKey(mention.getBrandName()));
            if (!matchesBrandId && !matchesBrandName) {
                continue;
            }
            String probeKey = firstText(mention.getProbeId(), mention.getDispatchAttemptId(), mention.getId());
            if (StringUtils.hasText(probeKey)) {
                probeKeys.add(probeKey);
            }
        }
        return round2(probeKeys.size() * 100.0d / denominator);
    }

    private int resolveTop3Denominator(String runId, Integer sampleSize) {
        if (sampleSize != null && sampleSize > 0) {
            return sampleSize;
        }
        if (!StringUtils.hasText(runId)) {
            return 0;
        }
        if (isKnowledgeRunId(runId)) {
            KnowledgeAssetEvidenceRepository repository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
            if (repository != null) {
                int knowledgeProbeCount = repository.listProbesByRunId(runId.trim()).size();
                if (knowledgeProbeCount > 0) {
                    return knowledgeProbeCount;
                }
            }
        }
        Long probeCount = diagnosisProbeRecordMapper.selectCount(new LambdaQueryWrapper<DiagnosisProbeRecord>()
                .eq(DiagnosisProbeRecord::getDiagnosisTaskId, runId.trim()));
        if (probeCount == null || probeCount <= 0) {
            return 0;
        }
        return probeCount.intValue();
    }

    private Map<String, DiagnosisReportRecord> loadLatestKnowledgeReportsByScopeId(List<String> scopeIds) {
        ensureKnowledgeHistoryReady(scopeIds);
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || scopeIds == null || scopeIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DiagnosisReportRecord> reportsByScopeId = new HashMap<>();
        for (KnowledgeAssetRunRepository.RunDetailView runDetail : repository.findLatestRunDetailsByScopeIds(scopeIds)) {
            DiagnosisReportRecord report = synthesizeReportFromKnowledgeRun(runDetail);
            if (report != null && StringUtils.hasText(report.getBrandScopeId())) {
                reportsByScopeId.put(report.getBrandScopeId(), report);
            }
        }
        return reportsByScopeId;
    }

    private DiagnosisReportRecord loadLatestKnowledgeReport(String brandScopeId) {
        ensureKnowledgeHistoryReady(brandScopeId);
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || !StringUtils.hasText(brandScopeId)) {
            return null;
        }
        return synthesizeReportFromKnowledgeRun(repository.findLatestRunDetailByScope(brandScopeId.trim()));
    }

    private DiagnosisReportRecord synthesizeReportFromKnowledgeRun(KnowledgeAssetRunRepository.RunDetailView runDetail) {
        if (runDetail == null || !StringUtils.hasText(runDetail.runId())) {
            return null;
        }
        return DiagnosisReportRecord.builder()
                .id(firstText(runDetail.sourceReportId(), runDetail.runId()))
                .diagnosisTaskId(runDetail.runId())
                .organizationId(runDetail.organizationId())
                .brandScopeId(runDetail.brandScopeId())
                .targetModelProduct(runDetail.targetModelProduct())
                .sampleSize(runDetail.sampleSize())
                .summaryJson(readObjectMapFromJson(runDetail.summaryPayloadJson()))
                .reportProjectionJson(readObjectMapFromJson(runDetail.reportProjectionJson()))
                .generatedAt(toLocalDateTime(runDetail.observedAt()))
                .createdAt(toLocalDateTime(runDetail.observedAt()))
                .build();
    }

    private List<ProbeBrandMentionRecord> loadBrandMentions(String runId) {
        if (!StringUtils.hasText(runId)) {
            return List.of();
        }
        if (isKnowledgeRunId(runId)) {
            KnowledgeAssetEvidenceRepository repository = knowledgeAssetEvidenceRepositoryProvider.getIfAvailable();
            if (repository != null) {
                List<ProbeBrandMentionRecord> knowledgeMentions = repository.listBrandMentionsByRunId(runId.trim()).stream()
                        .map(this::toProbeBrandMentionRecord)
                        .toList();
                if (!knowledgeMentions.isEmpty()) {
                    return knowledgeMentions;
                }
            }
        }
        return probeBrandMentionRecordMapper.selectList(new LambdaQueryWrapper<ProbeBrandMentionRecord>()
                .eq(ProbeBrandMentionRecord::getDiagnosisTaskId, runId.trim())
                .orderByAsc(ProbeBrandMentionRecord::getCreatedAt));
    }

    private ProbeBrandMentionRecord toProbeBrandMentionRecord(KnowledgeAssetEvidenceRepository.BrandMentionView mention) {
        if (mention == null) {
            return null;
        }
        return ProbeBrandMentionRecord.builder()
                .id(mention.id())
                .diagnosisTaskId(mention.runId())
                .probeId(mention.probeId())
                .dispatchAttemptId(mention.attemptId())
                .brandId(mention.brandKey())
                .brandName(mention.brandName())
                .mentionedFlag(mention.mentionedFlag())
                .recommendedFlag(mention.recommendedFlag())
                .sentimentCode(mention.sentimentCode())
                .positionIndex(mention.positionIndex())
                .confidenceScore(mention.confidenceScore())
                .mentionMetadataJson(readObjectMapFromJson(mention.mentionPayloadJson()))
                .createdAt(mention.createdAt())
                .build();
    }

    private Map<String, Object> readObjectMapFromJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return KnowledgeAssetTimeSupport.toProductLocalDateTime(value);
    }

    private void ensureKnowledgeHistoryReady(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return;
        }
        KnowledgeAssetHistorySyncService historySyncService = knowledgeAssetHistorySyncServiceProvider.getIfAvailable();
        if (historySyncService == null) {
            return;
        }
        historySyncService.ensureScopeHistoryUpToDate(brandScopeId.trim());
    }

    private void ensureKnowledgeHistoryReady(List<String> brandScopeIds) {
        if (brandScopeIds == null || brandScopeIds.isEmpty()) {
            return;
        }
        for (String brandScopeId : brandScopeIds.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
            ensureKnowledgeHistoryReady(brandScopeId);
        }
    }

    private boolean isKnowledgeRunId(String runId) {
        return StringUtils.hasText(runId) && runId.trim().startsWith("ka-run-");
    }

    private Double readPercent(Object raw, boolean ratioToPercent) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = null;
        if (raw instanceof Number number) {
            value = BigDecimal.valueOf(number.doubleValue());
        } else if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                value = new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value == null) {
            return null;
        }
        if (ratioToPercent) {
            value = value.multiply(BigDecimal.valueOf(100));
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double readFlexiblePercent(Object raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = readDecimal(raw);
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ONE) <= 0) {
            value = value.multiply(BigDecimal.valueOf(100));
        }
        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Double readNumber(Object raw) {
        BigDecimal value = readDecimal(raw);
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private BigDecimal readDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (raw instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BrandSpaceDraftPayload normalizeDraft(BrandSpaceDraftPayload draft) {
        if (draft == null || !StringUtils.hasText(draft.getBrandName()) || !StringUtils.hasText(draft.getCategory())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceDraft 不完整");
        }
        List<String> competitors = draft.getCompetitors() == null
                ? List.of()
                : draft.getCompetitors().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
        return BrandSpaceDraftPayload.builder()
                .brandName(draft.getBrandName().trim())
                .category(draft.getCategory().trim())
                .officialWebsite(defaultIfBlank(draft.getOfficialWebsite(), null))
                .regionCode(defaultIfBlank(draft.getRegionCode(), "CN"))
                .locale(defaultIfBlank(draft.getLocale(), "zh-CN"))
                .language(defaultIfBlank(draft.getLanguage(), "zh-CN"))
                .competitors(competitors)
                .supplementalBrief(defaultIfBlank(draft.getSupplementalBrief(), null))
                .build();
    }

    private Map<String, Object> deepMutableCopy(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private Map<String, Object> readMutableMap(Object value) {
        return value == null
                ? new LinkedHashMap<>()
                : objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Object firstNonNull(Object left, Object right) {
        return left != null ? left : right;
    }

    private String normalizeBrandKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replaceAll("\\s+", "");
        normalized = normalized.replace("（", "(").replace("）", ")");
        normalized = normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]", "");
        return normalized;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String asText(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private List<String> mergeBrandAliases(String brandName,
                                           String previousBrandName,
                                           List<String> existingAliases) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (StringUtils.hasText(brandName)) {
            aliases.add(brandName.trim());
        }
        if (StringUtils.hasText(previousBrandName)) {
            aliases.add(previousBrandName.trim());
        }
        if (existingAliases != null) {
            for (String alias : existingAliases) {
                if (StringUtils.hasText(alias)) {
                    aliases.add(alias.trim());
                }
            }
        }
        return List.copyOf(aliases);
    }

    private String buildScopeName(String brandName, String category) {
        if (!StringUtils.hasText(brandName) && !StringUtils.hasText(category)) {
            return null;
        }
        if (!StringUtils.hasText(category)) {
            return brandName == null ? null : brandName.trim();
        }
        if (!StringUtils.hasText(brandName)) {
            return category.trim();
        }
        return brandName.trim() + " / " + category.trim();
    }

    private String firstText(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    private String defaultIfBlank(String first, String second, String fallback) {
        return firstText(first, second, fallback);
    }

    private String normalizeTrackingMode(String trackingMode) {
        String normalized = StringUtils.hasText(trackingMode) ? trackingMode.trim().toUpperCase() : null;
        if (TRACKING_MODE_ACTIVE.equals(normalized) || TRACKING_MODE_PAUSED.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "trackingMode 仅支持 ACTIVE 或 PAUSED");
    }

    private String normalizeStoredTrackingMode(String trackingMode) {
        if (TRACKING_MODE_PAUSED.equalsIgnoreCase(String.valueOf(trackingMode))) {
            return TRACKING_MODE_PAUSED;
        }
        return TRACKING_MODE_ACTIVE;
    }

    private boolean isOneTimeMode(String executeModeCode) {
        return EXECUTE_MODE_ONE_TIME.equalsIgnoreCase(StringUtils.trimWhitespace(executeModeCode));
    }

    private String normalizeCreateExecuteModeCode(String executeModeCode) {
        if (!StringUtils.hasText(executeModeCode)) {
            return EXECUTE_MODE_RECURRING;
        }
        String normalized = executeModeCode.trim().toUpperCase(Locale.ROOT);
        if (EXECUTE_MODE_RECURRING.equals(normalized) || EXECUTE_MODE_ONE_TIME.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "运行方式仅支持单次或每日运行");
    }

    private String normalizeCreateTargetModelProduct(String targetModelProduct) {
        if (!StringUtils.hasText(targetModelProduct)) {
            return TARGET_MODEL_DOUBAO;
        }
        String normalized = targetModelProduct.trim().toLowerCase(Locale.ROOT);
        if (TARGET_MODEL_DOUBAO.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "当前创建品牌空间仅支持豆包模型");
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }

    private String extractHost(String website) {
        if (!StringUtils.hasText(website)) {
            return null;
        }
        try {
            URI uri = URI.create(website.trim());
            return StringUtils.hasText(uri.getHost()) ? uri.getHost().trim() : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record BrandSpaceAccessContext(String organizationId, BrandScope scope, Brand brand) {
    }
}

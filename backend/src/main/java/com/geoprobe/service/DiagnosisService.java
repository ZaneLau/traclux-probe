package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.DiagnosisEdition;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.VolcengineRewardRunAdmissionService;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.Node3TargetEnginePolicy;
import com.geoprobe.dto.engine.v42.V42ExecutionPlan;
import com.geoprobe.dto.request.CreateDiagnosisRequest;
import com.geoprobe.dto.request.CreateDiagnosisFromTaskOrderRequest;
import com.geoprobe.dto.request.InferContextRequest;
import com.geoprobe.dto.request.PreviewDiagnosisEditionsRequest;
import com.geoprobe.dto.response.DiagnosisEditionPreviewResponse;
import com.geoprobe.dto.response.DiagnosisOpsMetricsResponse;
import com.geoprobe.dto.response.DiagnosisProgressResponse;
import com.geoprobe.dto.response.DiagnosisResponse;
import com.geoprobe.dto.response.InferContextResponse;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.service.shared.DiagnosisCancellationRegistry;
import com.geoprobe.service.shared.DiagnosisConfig;
import com.geoprobe.service.shared.DiagnosisFieldNormalizer;
import com.geoprobe.service.shared.DiagnosisIdempotencyService;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.V42SnapshotSanitizer;
import com.geoprobe.service.shared.V42Node3TargetEnginePolicyResolver;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicy;
import com.geoprobe.service.shared.V42TaskOrderExecutionPolicyResolver;
import com.geoprobe.service.engine.v42.V42CategoryAnchorResolver;
import com.geoprobe.service.engine.v42.V42DiagnosisExecutionService;
import com.geoprobe.service.engine.v42.V42DiagnosisOrchestrator;
import com.geoprobe.service.engine.v42.V42ReportProjectionService;
import com.geoprobe.service.engine.v42.MinusOneContextAssemblyService;
import com.geoprobe.service.engine.v42.V42EngineVersionSupport;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {
    private static final String TASK_SOURCE_FRONTDESK_CREATE = "frontdesk_create";
    private static final String TASK_SOURCE_FRONTDESK_PREVIEW = "frontdesk_preview";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_QUEUED = 5;
    private static final int CREDIT_COST_PER_DIAGNOSIS = 1;
    private static final int SOURCE_MANUAL = 0;
    private static final int MAX_CATEGORY_LEN = 200;
    private static final int MAX_BRAND_WEBSITE_LEN = 500;
    private static final int MAX_USER_PROMPT_LEN = 1000;
    private static final int MAX_COMPETITOR_SIZE = 10;
    private static final String TASK_ORDER_CONTRACT_VERSION = "frontdesk_task_order_v1_1";
    private static final String BILLING_STATE_CHARGED = "charged";
    private static final String BILLING_STATE_REFUNDED = "refunded";
    private static final String BILLING_STATE_SERVICE_ACCESS_MANAGED = "service_access_managed";
    private static final String BILLING_MODE_USER_CREDITS = "user_credits";
    private static final String TASK_SOURCE_BRAND_SPACE_PREFIX = "brand_space_";
    private static final String TASK_METADATA_ORIGIN_BRAND_SPACE = "brand_space";
    private static final String DEFAULT_ENGINE_VERSION = V42EngineVersionSupport.DEFAULT_ENGINE_VERSION;

    private final DiagnosisMapper diagnosisMapper;
    private final CreditService creditService;
    private final V42TaskOrderExecutionPolicyResolver v42TaskOrderExecutionPolicyResolver;
    private final V42Node3TargetEnginePolicyResolver v42Node3TargetEnginePolicyResolver;
    private final V42DiagnosisExecutionService v42DiagnosisExecutionService;
    private final V42ReportProjectionService v42ReportProjectionService;
    private final DiagnosisRuntimeQueryService diagnosisRuntimeQueryService;
    private final DiagnosisRuntimeReadUseCaseService diagnosisRuntimeReadUseCaseService;
    private final DiagnosisAccessService diagnosisAccessService;
    private final DiagnosisIdempotencyService diagnosisIdempotencyService;
    private final DiagnosisMapSupport diagnosisMapSupport;
    private final MinusOneContextAssemblyService minusOneContextAssemblyService;
    private final DiagnosisCancellationRegistry diagnosisCancellationRegistry;
    private final DiagnosisInferenceService diagnosisInferenceService;
    private final DiagnosisEditionPreviewUseCaseService diagnosisEditionPreviewUseCaseService;
    private final DiagnosisBillingService diagnosisBillingService;
    private final DiagnosisSnapshotService diagnosisSnapshotService;
    private final FrontDeskPreviewTokenService frontDeskPreviewTokenService;
    private final VolcengineRewardRunAdmissionService volcengineRewardRunAdmissionService;
    @Autowired(required = false)
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    @Autowired(required = false)
    private LegacyDiagnosisMirrorService legacyDiagnosisMirrorService;
    private final ObjectMapper objectMapper;

    @Value("${app.engine.execution.enabled:true}")
    private boolean engineExecutionEnabled = true;

    public DiagnosisResponse createDiagnosis(String userId, CreateDiagnosisFromTaskOrderRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        if (!engineExecutionEnabled) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前环境禁用了真实主链执行");
        }

        String requestId = resolveCreateRequestId(userId, request);
        Diagnosis idempotentDiagnosis = diagnosisIdempotencyService.findIdempotentDiagnosis(userId, requestId);
        if (idempotentDiagnosis != null) {
            return toDiagnosisResponse(idempotentDiagnosis);
        }
        DiagnosisIdempotencyService.IdempotencyLock lock = diagnosisIdempotencyService.acquireLock(userId, requestId);
        if (requestId != null && lock == null) {
            Diagnosis existing = diagnosisIdempotencyService.waitForIdempotentDiagnosis(userId, requestId);
            if (existing != null) {
                return toDiagnosisResponse(existing);
            }
            throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);
        }

        try {
            PreparedDiagnosisCreateInput prepared = prepareCreateInputFromTaskOrder(userId, request, requestId);
            volcengineRewardRunAdmissionService.requireTaskOrderReady(
                    prepared.taskOrder(),
                    "diagnosis_create requestId=%s".formatted(
                            StringUtils.hasText(requestId) ? requestId.trim() : "unknown"
                    )
            );
            ChargeQuote chargeQuote = resolveChargeQuote(prepared.taskOrder());
            boolean serviceAccessManaged = chargeQuote.serviceAccessManaged();

            if (!serviceAccessManaged && !creditService.hasEnoughCredits(userId, chargeQuote.chargeAmount())) {
                throw new BusinessException(ErrorCode.CREDITS_INSUFFICIENT);
            }

            String diagnosisId = UUID.randomUUID().toString();
            LocalDateTime createdAt = LocalDateTime.now();
            Diagnosis diagnosis = Diagnosis.builder()
                    .id(diagnosisId)
                    .userId(userId)
                    .brandName(prepared.resolved().brandName())
                    .category(prepared.resolved().category())
                    .brandWebsite(prepared.resolved().brandWebsite())
                    .userPrompt(prepared.resolved().userPrompt())
                    .competitorsInput(prepared.resolved().competitors())
                    .negativeTerms(prepared.resolved().negativeTerms())
                    .status(STATUS_PENDING)
                    .edition(prepared.edition().getCode())
                    .isTrial(0)
                    .source(SOURCE_MANUAL)
                    .trackingId(null)
                    .variantsPerCorePrompt(prepared.diagnosisConfig().getVariantsPerCorePrompt())
                    .createdAt(createdAt)
                    .reportData(diagnosisSnapshotService.buildInitialReportData(
                            requestId,
                            prepared.resolved().brandName(),
                            prepared.resolved().category(),
                            prepared.edition(),
                            prepared.diagnosisConfig(),
                            prepared.engineVersion(),
                            prepared.resolvedCategoryAnchors().anchors(),
                            prepared.resolvedCategoryAnchors().fallbackAnchorMode(),
                            prepared.resolvedCategoryAnchors().source(),
                            prepared.brandAliases(),
                            prepared.competitorAliases(),
                            prepared.taskOrder(),
                            prepared.contextPackage(),
                            prepared.createDiagnosisPayloadSnapshot()
                    ))
                    .build();

            DiagnosisTask taskShell = ensureTaskShell(
                    diagnosis,
                    prepared.taskOrder(),
                    prepared.contextPackage(),
                    prepared.engineVersion(),
                    prepared.createDiagnosisPayloadSnapshot()
            );

            boolean legacyDiagnosisInserted = false;
            if (shouldAttemptLegacyDiagnosisWrite()) {
                try {
                    int insertedRows = diagnosisMapper.insert(diagnosis);
                    legacyDiagnosisInserted = insertedRows == 1 && diagnosis.getId() != null;
                    if (!legacyDiagnosisInserted && isLegacyDiagnosisWriteRequired()) {
                        cleanupTaskShell(diagnosis.getId());
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建诊断任务失败");
                    }
                    if (!legacyDiagnosisInserted) {
                        log.warn("Legacy diagnosis mirror insert did not complete, continuing with task-native create. diagnosisId={}",
                                diagnosis.getId());
                    }
                } catch (RuntimeException ex) {
                    if (isLegacyDiagnosisWriteRequired()) {
                        cleanupTaskShell(diagnosis.getId());
                        throw ex;
                    }
                    log.warn("Legacy diagnosis mirror write failed, continuing with task-native create. diagnosisId={}, reason={}",
                            diagnosis.getId(), ex.getMessage());
                }
            }

            if (serviceAccessManaged) {
                diagnosisBillingService.markDiagnosisBillingState(diagnosis.getId(), BILLING_STATE_SERVICE_ACCESS_MANAGED);
            } else {
                boolean deducted;
                try {
                    deducted = creditService.deductCredits(userId, diagnosis.getId(), chargeQuote.chargeAmount());
                } catch (RuntimeException ex) {
                    cleanupLegacyDiagnosisMirror(diagnosis.getId(), legacyDiagnosisInserted);
                    cleanupTaskShell(taskShell == null ? diagnosis.getId() : taskShell.getId());
                    throw ex;
                }
                if (!deducted) {
                    cleanupLegacyDiagnosisMirror(diagnosis.getId(), legacyDiagnosisInserted);
                    cleanupTaskShell(taskShell == null ? diagnosis.getId() : taskShell.getId());
                    throw new BusinessException(ErrorCode.CREDITS_INSUFFICIENT);
                }
                diagnosisBillingService.markDiagnosisBillingState(
                        diagnosis.getId(),
                        BILLING_STATE_CHARGED,
                        buildBillingMetadata(chargeQuote)
                );
            }
            mirrorTaskCreated(
                    diagnosis,
                    prepared.taskOrder(),
                    prepared.contextPackage(),
                    prepared.engineVersion(),
                    prepared.createDiagnosisPayloadSnapshot()
            );

            diagnosisIdempotencyService.cacheResult(userId, requestId, diagnosis.getId());
            v42DiagnosisExecutionService.enqueue(new V42ExecutionPlan(
                    diagnosis.getId(),
                    prepared.engineVersion(),
                    prepared.resolved().brandName(),
                    prepared.resolved().category(),
                    prepared.resolved().competitors(),
                    prepared.resolvedCategoryAnchors().anchors(),
                    prepared.resolvedCategoryAnchors().fallbackAnchorMode(),
                    prepared.brandAliases(),
                    prepared.competitorAliases(),
                    prepared.taskOrder(),
                    prepared.contextPackage()
            ));
            return toDiagnosisResponse(diagnosis);
        } finally {
            diagnosisIdempotencyService.releaseLock(lock);
        }
    }

    private DiagnosisTask ensureTaskShell(Diagnosis diagnosis,
                                          FrontDeskTaskOrder taskOrder,
                                          Object contextPackage,
                                          String engineVersion) {
        return ensureTaskShell(diagnosis, taskOrder, contextPackage, engineVersion, Map.of());
    }

    private DiagnosisTask ensureTaskShell(Diagnosis diagnosis,
                                          FrontDeskTaskOrder taskOrder,
                                          Object contextPackage,
                                          String engineVersion,
                                          Map<String, Object> createDiagnosisPayloadSnapshot) {
        if (diagnosisTaskNativeRuntimeService == null || diagnosis == null || taskOrder == null) {
            return null;
        }
        return diagnosisTaskNativeRuntimeService.ensureTaskShell(
                diagnosis.getId(),
                diagnosis.getUserId(),
                diagnosis.getBrandName(),
                diagnosis.getCategory(),
                diagnosis.getBrandWebsite(),
                diagnosis.getSource(),
                diagnosis.getStatus(),
                taskOrder,
                contextPackage,
                engineVersion,
                createDiagnosisPayloadSnapshot
        );
    }

    private void cleanupTaskShell(String diagnosisId) {
        if (diagnosisTaskNativeRuntimeService == null || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        diagnosisTaskNativeRuntimeService.removeTaskShell(diagnosisId);
    }

    private void cleanupLegacyDiagnosisMirror(String diagnosisId, boolean legacyDiagnosisInserted) {
        if (!legacyDiagnosisInserted || !StringUtils.hasText(diagnosisId)) {
            return;
        }
        diagnosisMapper.deleteById(diagnosisId);
    }

    private boolean shouldAttemptLegacyDiagnosisWrite() {
        if (legacyDiagnosisMirrorService == null) {
            return false;
        }
        return legacyDiagnosisMirrorService.shouldWriteLegacyDiagnosis();
    }

    private boolean isLegacyDiagnosisWriteRequired() {
        if (legacyDiagnosisMirrorService == null) {
            return false;
        }
        return legacyDiagnosisMirrorService.isLegacyDiagnosisWriteRequired();
    }

    private PreparedDiagnosisCreateInput prepareCreateInputFromTaskOrder(String userId,
                                                                         CreateDiagnosisFromTaskOrderRequest request,
                                                                         String requestId) {
        validateCreateTaskOrderRequest(request);
        FrontDeskTaskOrder explicitTaskOrder = resolveCreateTaskOrder(userId, request);
        ResolvedDiagnosisContext resolved = resolveDiagnosisContextFromTaskOrder(explicitTaskOrder);
        String engineVersion = resolveEngineVersion(request.getEngineVersion());
        DiagnosisEdition edition = resolveEdition(request.getEdition());
        DiagnosisConfig diagnosisConfig = edition.toConfig();
        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors =
                resolveCategoryAnchorsFromTaskOrder(resolved.category(), explicitTaskOrder);
        List<String> brandAliases = resolveFrontDeskBrandAliases(explicitTaskOrder);
        Map<String, List<String>> competitorAliases = resolveFrontDeskCompetitorAliases(
                explicitTaskOrder,
                resolved.competitors()
        );
        Map<String, Object> sanitizedBusinessHints = sanitizeFrontDeskUserBusinessHints(explicitTaskOrder.businessHints());
        V42TaskOrderExecutionPolicy executionPolicy;
        try {
            executionPolicy = v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderHints(
                    sanitizedBusinessHints,
                    "create_diagnosis"
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "frontDeskTaskOrder 非法: " + ex.getMessage());
        }
        Node3TargetEnginePolicy targetEnginePolicy = resolveCreateV42Node3TargetEnginePolicy(
                explicitTaskOrder,
                StringUtils.hasText(request == null ? null : request.getPreviewToken()),
                "create_diagnosis"
        );
        validateNode3CapabilityCompatibility(executionPolicy, targetEnginePolicy, "create_diagnosis");
        Map<String, Object> canonicalBusinessHints = buildCanonicalFrontDeskBusinessHints(
                resolved,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                v42Node3TargetEnginePolicyResolver.normalizeTaskOrderHints(
                        v42TaskOrderExecutionPolicyResolver.normalizeTaskOrderHints(
                                sanitizedBusinessHints,
                                executionPolicy
                        ),
                        targetEnginePolicy
                )
        );
        FrontDeskTaskOrder taskOrder = buildFrontDeskTaskOrder(
                resolved,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                targetEnginePolicy,
                requestId,
                null,
                engineVersion,
                edition,
                TASK_SOURCE_FRONTDESK_CREATE,
                canonicalBusinessHints,
                explicitTaskOrder
        );
        EngineTaskContextPackage contextPackage = buildContextPackage(
                resolved,
                resolvedCategoryAnchors.anchors(),
                resolvedCategoryAnchors.fallbackAnchorMode(),
                resolvedCategoryAnchors.source(),
                taskOrder,
                diagnosisMapSupport.mapOfNonNull(
                        "assemblySource", TASK_SOURCE_FRONTDESK_CREATE,
                        "requestId", requestId,
                        "engineVersion", engineVersion
                )
        );
        Map<String, Object> createDiagnosisPayloadSnapshot = diagnosisSnapshotService.buildCreateDiagnosisPayloadSnapshot(
                requestId,
                edition,
                engineVersion,
                taskOrder
        );
        return new PreparedDiagnosisCreateInput(
                resolved,
                engineVersion,
                edition,
                diagnosisConfig,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                taskOrder,
                contextPackage,
                createDiagnosisPayloadSnapshot
        );
    }

    private void validateCreateTaskOrderRequest(CreateDiagnosisFromTaskOrderRequest request) {
        if (request == null) {
            return;
        }
        if (request.getUnsupportedFields() == null || request.getUnsupportedFields().isEmpty()) {
            return;
        }
        List<String> fieldNames = request.getUnsupportedFields().keySet().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .sorted()
                .toList();
        throw new BusinessException(
                ErrorCode.BAD_REQUEST,
                "正式创建只接受 previewToken 或 frontDeskTaskOrder，不允许再提交其他业务字段: " + String.join(", ", fieldNames)
        );
    }

    private PreparedDiagnosisCreateInput preparePreviewInput(String userId,
                                                             CreateDiagnosisRequest request,
                                                             String requestId) {
        FrontDeskTaskOrder explicitTaskOrder = resolveExplicitFrontDeskTaskOrder(request);
        validateFrontDeskTaskOrderSingleSource(request, explicitTaskOrder);
        ResolvedDiagnosisContext resolved = resolveDiagnosisContext(userId, request, explicitTaskOrder);
        String engineVersion = resolveEngineVersion(request.getEngineVersion());
        DiagnosisEdition edition = resolveEdition(request.getEdition());
        DiagnosisConfig diagnosisConfig = edition.toConfig();
        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors = explicitTaskOrder == null
                ? resolveCategoryAnchors(resolved.category(), request.getCategoryAnchors())
                : resolveCategoryAnchorsFromTaskOrder(resolved.category(), explicitTaskOrder);
        List<String> brandAliases = explicitTaskOrder == null
                ? DiagnosisFieldNormalizer.normalizeAliasList(
                request.getBrandAliases(),
                MAX_COMPETITOR_SIZE,
                MAX_CATEGORY_LEN
        )
                : resolveFrontDeskBrandAliases(explicitTaskOrder);
        Map<String, List<String>> competitorAliases = explicitTaskOrder == null
                ? normalizeFrontDeskCompetitorAliases(
                request.getCompetitorAliases(),
                resolved.competitors()
        )
                : resolveFrontDeskCompetitorAliases(explicitTaskOrder, resolved.competitors());
        Map<String, Object> sanitizedTaskOrderHints = explicitTaskOrder == null
                ? sanitizeFrontDeskUserBusinessHints(request.getTaskOrderHints())
                : sanitizeFrontDeskUserBusinessHints(explicitTaskOrder.businessHints());
        V42TaskOrderExecutionPolicy executionPolicy = resolveV42TaskOrderExecutionPolicy(sanitizedTaskOrderHints);
        Node3TargetEnginePolicy targetEnginePolicy = resolvePreviewV42Node3TargetEnginePolicy(sanitizedTaskOrderHints);
        validateNode3CapabilityCompatibility(executionPolicy, targetEnginePolicy, TASK_SOURCE_FRONTDESK_PREVIEW);
        Map<String, Object> canonicalTaskOrderHints = v42TaskOrderExecutionPolicyResolver.normalizeTaskOrderHints(
                sanitizedTaskOrderHints,
                executionPolicy
        );
        canonicalTaskOrderHints = v42Node3TargetEnginePolicyResolver.normalizeTaskOrderHints(
                canonicalTaskOrderHints,
                targetEnginePolicy
        );
        FrontDeskTaskOrder taskOrder = buildFrontDeskTaskOrder(
                resolved,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                targetEnginePolicy,
                requestId,
                request.getContextId(),
                engineVersion,
                edition,
                TASK_SOURCE_FRONTDESK_PREVIEW,
                canonicalTaskOrderHints,
                explicitTaskOrder
        );
        EngineTaskContextPackage contextPackage = buildContextPackage(
                resolved,
                resolvedCategoryAnchors.anchors(),
                resolvedCategoryAnchors.fallbackAnchorMode(),
                resolvedCategoryAnchors.source(),
                taskOrder,
                diagnosisMapSupport.mapOfNonNull(
                        "assemblySource", TASK_SOURCE_FRONTDESK_PREVIEW,
                        "requestId", requestId,
                        "contextId", DiagnosisFieldNormalizer.normalizeOptional(request.getContextId(), 64),
                        "engineVersion", engineVersion
                )
        );
        Map<String, Object> createDiagnosisPayloadSnapshot = diagnosisSnapshotService.buildCreateDiagnosisPayloadSnapshot(
                requestId,
                edition,
                engineVersion,
                taskOrder
        );
        return new PreparedDiagnosisCreateInput(
                resolved,
                engineVersion,
                edition,
                diagnosisConfig,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                taskOrder,
                contextPackage,
                createDiagnosisPayloadSnapshot
        );
    }

    public TaskOrderPreview previewTaskOrder(String userId, CreateDiagnosisRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        String requestId = resolvePreviewRequestId(request);
        PreparedDiagnosisCreateInput prepared = preparePreviewInput(userId, request, requestId);
        int targetProbeCount = v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderHints(
                prepared.taskOrder().businessHints(),
                TASK_SOURCE_FRONTDESK_PREVIEW
        ).targetProbeCount();
        String previewToken = frontDeskPreviewTokenService.issue(
                userId,
                targetProbeCount,
                diagnosisMapSupport.toSnapshotMap(prepared.taskOrder()),
                diagnosisMapSupport.toSnapshotMap(prepared.contextPackage()),
                prepared.createDiagnosisPayloadSnapshot()
        );
        return new TaskOrderPreview(
                targetProbeCount,
                diagnosisMapSupport.toSnapshotMap(prepared.taskOrder()),
                diagnosisMapSupport.toSnapshotMap(prepared.contextPackage()),
                prepared.createDiagnosisPayloadSnapshot(),
                previewToken
        );
    }

    public TaskOrderPreview readTaskOrderPreview(String userId, String diagnosisId) {
        if (diagnosisTaskNativeRuntimeService != null && diagnosisTaskNativeRuntimeService.isEnabled()) {
            Diagnosis runtimeDiagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
            Map<String, Object> taskOrderSnapshot = readObjectMap(diagnosisTaskNativeRuntimeService.readTaskOrderSnapshot(diagnosisId));
            Map<String, Object> taskMetadataSnapshot = readObjectMap(diagnosisTaskNativeRuntimeService.readTaskMetadataSnapshot(diagnosisId));
            Map<String, Object> taskContextSnapshot = readObjectMap(diagnosisTaskNativeRuntimeService.readTaskContextSnapshot(diagnosisId));
            Map<String, Object> taskCreatePayloadSnapshot = readObjectMap(
                    diagnosisTaskNativeRuntimeService.readTaskCreateDiagnosisPayloadSnapshot(diagnosisId)
            );
            if (!taskOrderSnapshot.isEmpty()) {
                return new TaskOrderPreview(
                        v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderSnapshot(
                                taskOrderSnapshot,
                                "task_order_preview"
                        ).targetProbeCount(),
                        taskOrderSnapshot,
                        taskContextSnapshot,
                        diagnosisSnapshotService.buildCanonicalCreateDiagnosisPayloadSnapshot(
                                taskOrderSnapshot,
                                taskCreatePayloadSnapshot,
                                taskMetadataSnapshot
                        ),
                        null
                );
            }
            Map<String, Object> meta = readReportMeta(runtimeDiagnosis);
            if (!meta.isEmpty()) {
                Map<String, Object> storedTaskOrderSnapshot = readObjectMap(meta.get("taskOrderSnapshot"));
                return new TaskOrderPreview(
                        v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderSnapshot(
                                storedTaskOrderSnapshot,
                                "task_order_preview"
                        ).targetProbeCount(),
                        storedTaskOrderSnapshot,
                        readObjectMap(meta.get("contextPackageSnapshot")),
                        diagnosisSnapshotService.buildCanonicalCreateDiagnosisPayloadSnapshot(
                                storedTaskOrderSnapshot,
                                readObjectMap(meta.get("createDiagnosisPayloadSnapshot")),
                                meta
                        ),
                        null
                );
            }
        }
        Diagnosis diagnosis = getByIdAndUserId(diagnosisId, userId);
        Map<String, Object> meta = readReportMeta(diagnosis);
        Map<String, Object> taskOrderSnapshot = readObjectMap(meta.get("taskOrderSnapshot"));
        return new TaskOrderPreview(
                v42TaskOrderExecutionPolicyResolver.resolveRequiredFromTaskOrderSnapshot(
                        taskOrderSnapshot,
                        "task_order_preview"
                ).targetProbeCount(),
                taskOrderSnapshot,
                readObjectMap(meta.get("contextPackageSnapshot")),
                diagnosisSnapshotService.buildCanonicalCreateDiagnosisPayloadSnapshot(
                        taskOrderSnapshot,
                        readObjectMap(meta.get("createDiagnosisPayloadSnapshot")),
                        meta
                ),
                null
        );
    }

    public InferContextResponse inferContext(String userId, InferContextRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return diagnosisInferenceService.inferContext(userId, request);
    }

    public DiagnosisEditionPreviewResponse previewEditions(String userId, PreviewDiagnosisEditionsRequest request) {
        return diagnosisEditionPreviewUseCaseService.previewEditions(userId, request);
    }

    public Page<DiagnosisResponse> listDiagnoses(String userId, long page, long size) {
        Page<Diagnosis> queryPage = new Page<>(page, size);
        LambdaQueryWrapper<Diagnosis> queryWrapper = new LambdaQueryWrapper<Diagnosis>()
                .select(
                        Diagnosis::getId,
                        Diagnosis::getStatus,
                        Diagnosis::getBrandName,
                        Diagnosis::getCategory,
                        Diagnosis::getBrandWebsite,
                        Diagnosis::getUserPrompt,
                        Diagnosis::getCompetitorsInput,
                        Diagnosis::getIsTrial,
                        Diagnosis::getCreatedAt
                )
                .eq(Diagnosis::getUserId, userId)
                .orderByDesc(Diagnosis::getCreatedAt);

        Page<Diagnosis> diagnosisPage = diagnosisMapper.selectPage(queryPage, queryWrapper);

        List<DiagnosisResponse> records = diagnosisPage.getRecords().stream()
                .map(this::toDiagnosisResponse)
                .toList();

        Page<DiagnosisResponse> responsePage = new Page<>(diagnosisPage.getCurrent(), diagnosisPage.getSize(), diagnosisPage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    public DiagnosisProgressResponse getDiagnosisProgress(String userId, String diagnosisId) {
        return diagnosisRuntimeReadUseCaseService.getDiagnosisProgress(userId, diagnosisId);
    }

    public DiagnosisProgressResponse cancelDiagnosis(String userId, String diagnosisId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Diagnosis diagnosis = diagnosisAccessService.getRuntimeReadByIdAndUserId(diagnosisId, userId);
        if (isStatus(diagnosis.getStatus(), STATUS_CANCELLED)) {
            return getDiagnosisProgress(userId, diagnosisId);
        }
        if (!isCancelableStatus(diagnosis.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前诊断状态不支持终止");
        }

        diagnosisCancellationRegistry.requestCancellation(diagnosisId);
        diagnosisMapper.updateById(Diagnosis.builder()
                .id(diagnosisId)
                .status(STATUS_CANCELLED)
                .completedAt(LocalDateTime.now())
                .errorMessage("用户取消")
                .build());
        if (diagnosisBillingService.shouldRefundOnCancellation(diagnosisId, diagnosis)) {
            creditService.refundCredits(
                    userId,
                    diagnosisId,
                    diagnosisBillingService.readChargedCredits(diagnosisId, diagnosis)
            );
            diagnosisBillingService.markDiagnosisBillingState(diagnosisId, BILLING_STATE_REFUNDED);
        }
        diagnosisRuntimeQueryService.writeTerminalProgress(diagnosisId, "CANCELLED");
        if (diagnosisTaskNativeRuntimeService != null) {
            diagnosisTaskNativeRuntimeService.onTaskTerminal(diagnosisId, STATUS_CANCELLED, "用户取消", LocalDateTime.now());
            diagnosisTaskNativeRuntimeService.onProgress(
                    diagnosisId,
                    "CANCELLED",
                    "诊断已取消",
                    0,
                    11,
                    0,
                    0,
                    0,
                    0,
                    false,
                    0.0d
            );
        }
        return getDiagnosisProgress(userId, diagnosisId);
    }

    private void mirrorTaskCreated(Diagnosis diagnosis,
                                   FrontDeskTaskOrder taskOrder,
                                   Object contextPackage,
                                   String engineVersion) {
        mirrorTaskCreated(diagnosis, taskOrder, contextPackage, engineVersion, Map.of());
    }

    private void mirrorTaskCreated(Diagnosis diagnosis,
                                   FrontDeskTaskOrder taskOrder,
                                   Object contextPackage,
                                   String engineVersion,
                                   Map<String, Object> createDiagnosisPayloadSnapshot) {
        if (diagnosisTaskNativeRuntimeService == null) {
            return;
        }
        diagnosisTaskNativeRuntimeService.onTaskCreated(
                diagnosis,
                taskOrder,
                contextPackage,
                engineVersion,
                createDiagnosisPayloadSnapshot
        );
    }

    public ProgressStreamSnapshot getProgressStreamSnapshot(String userId, String diagnosisId) {
        return diagnosisRuntimeReadUseCaseService.getProgressStreamSnapshot(userId, diagnosisId);
    }

    public ProgressStreamFrame getProgressStreamFrame(String userId,
                                                      String diagnosisId,
                                                      Long afterSeq,
                                                      Integer limit) {
        return diagnosisRuntimeReadUseCaseService.getProgressStreamFrame(userId, diagnosisId, afterSeq, limit);
    }

    public ProbeEventPage getProbeEvents(String userId, String diagnosisId, Long afterSeq, Integer limit) {
        return diagnosisRuntimeReadUseCaseService.getProbeEvents(userId, diagnosisId, afterSeq, limit);
    }

    public DiagnosisOpsMetricsResponse getOpsMetrics(String userId, String diagnosisId) {
        return diagnosisRuntimeReadUseCaseService.getOpsMetrics(userId, diagnosisId);
    }

    public Diagnosis getByIdAndUserId(String diagnosisId, String userId) {
        return diagnosisAccessService.getByIdAndUserId(diagnosisId, userId);
    }

    public Diagnosis getReportReadByIdAndUserId(String diagnosisId, String userId) {
        return diagnosisAccessService.getReportReadByIdAndUserId(diagnosisId, userId);
    }

    public Diagnosis getByIdOrThrow(String diagnosisId) {
        return diagnosisAccessService.getByIdOrThrow(diagnosisId);
    }

    public void markDiagnosisRefunded(String diagnosisId) {
        diagnosisBillingService.markDiagnosisBillingState(diagnosisId, BILLING_STATE_REFUNDED);
    }

    private FrontDeskTaskOrder buildFrontDeskTaskOrder(ResolvedDiagnosisContext context,
                                                       V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors,
                                                       List<String> brandAliases,
                                                       Map<String, List<String>> competitorAliases,
                                                       Node3TargetEnginePolicy targetEnginePolicy,
                                                       String requestId,
                                                       String contextId,
                                                       String engineVersion,
                                                       DiagnosisEdition edition,
                                                       String sourceTag,
                                                       Map<String, Object> taskOrderHints,
                                                       FrontDeskTaskOrder explicitTaskOrder) {
        Map<String, Object> businessHints = buildCanonicalFrontDeskBusinessHints(
                context,
                resolvedCategoryAnchors,
                brandAliases,
                competitorAliases,
                taskOrderHints
        );
        Map<String, Object> explicitTaskMetadata = explicitTaskOrder == null ? Map.of() : explicitTaskOrder.taskMetadata();
        Object explicitSource = explicitTaskMetadata.get("source");
        String normalizedSource = explicitSource instanceof String text && StringUtils.hasText(text)
                ? text.trim()
                : sourceTag;
        Map<String, Object> systemTaskMetadata = diagnosisMapSupport.mapOfNonNull(
                "source", normalizedSource,
                "requestId", requestId,
                "contextId", contextId,
                "engineVersion", engineVersion,
                "edition", edition == null ? null : edition.name(),
                "contractVersion", TASK_ORDER_CONTRACT_VERSION
        );
        Map<String, Object> taskMetadata = explicitTaskOrder == null
                ? systemTaskMetadata
                : diagnosisMapSupport.mergeMaps(explicitTaskMetadata, systemTaskMetadata);
        return new FrontDeskTaskOrder(
                context.brandName(),
                context.category(),
                context.competitors(),
                context.brandWebsite(),
                explicitTaskOrder == null || !StringUtils.hasText(explicitTaskOrder.runGoal())
                        ? "geo_brand_visibility_diagnosis"
                        : explicitTaskOrder.runGoal(),
                explicitTaskOrder == null ? null : explicitTaskOrder.locale(),
                explicitTaskOrder == null ? null : explicitTaskOrder.language(),
                targetEnginePolicy == null ? null : targetEnginePolicy.modelId(),
                businessHints,
                taskMetadata
        );
    }

    private EngineTaskContextPackage buildContextPackage(ResolvedDiagnosisContext context,
                                                         List<String> categoryAnchors,
                                                         boolean fallbackAnchorMode,
                                                         String categoryAnchorSource,
                                                         FrontDeskTaskOrder taskOrder,
                                                         Map<String, Object> extensibleSignals) {
        Map<String, Object> mergedSignals = diagnosisMapSupport.mergeMaps(
                diagnosisMapSupport.mapOfNonNull(
                        "brandWebsite", context.brandWebsite(),
                        "frontDeskUserPrompt", context.userPrompt(),
                        "negativeTerms", context.negativeTerms().isEmpty() ? null : context.negativeTerms(),
                        "categoryAnchorSource", categoryAnchorSource,
                        "contextAssemblyStage", "production_entry"
                ),
                extensibleSignals
        );
        if (!taskOrder.businessHints().isEmpty()) {
            mergedSignals = diagnosisMapSupport.mergeMaps(
                    mergedSignals,
                    diagnosisMapSupport.mapOfNonNull("frontDeskBusinessHints", taskOrder.businessHints())
            );
        }
        return minusOneContextAssemblyService.assemble(
                taskOrder,
                categoryAnchors,
                fallbackAnchorMode,
                categoryAnchorSource,
                mergedSignals
        );
    }

    private V42TaskOrderExecutionPolicy resolveV42TaskOrderExecutionPolicy(Map<String, Object> taskOrderHints) {
        try {
            return v42TaskOrderExecutionPolicyResolver.resolveForFrontDeskPlanning(taskOrderHints);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    private Node3TargetEnginePolicy resolvePreviewV42Node3TargetEnginePolicy(Map<String, Object> taskOrderHints) {
        try {
            return v42Node3TargetEnginePolicyResolver.resolveForFrontDeskPlanning(taskOrderHints);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    private Node3TargetEnginePolicy resolveCreateV42Node3TargetEnginePolicy(FrontDeskTaskOrder taskOrder,
                                                                            boolean strictSnapshot,
                                                                            String scene) {
        try {
            if (!strictSnapshot) {
                return v42Node3TargetEnginePolicyResolver.resolveForFrontDeskPlanning(
                        taskOrder == null ? Map.of() : taskOrder.businessHints()
                );
            }
            return v42Node3TargetEnginePolicyResolver.resolveRequiredFromTaskOrder(taskOrder, scene);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "frontDeskTaskOrder 非法: " + ex.getMessage());
        }
    }

    private void validateNode3CapabilityCompatibility(V42TaskOrderExecutionPolicy executionPolicy,
                                                      Node3TargetEnginePolicy targetEnginePolicy,
                                                      String scene) {
        if (executionPolicy == null || targetEnginePolicy == null) {
            return;
        }
        if (executionPolicy.realtimeSearchPolicy().enabled()
                && !"doubao".equals(targetEnginePolicy.provider())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "samplingRealtimeSearch 当前仅支持 doubao，scene=" + scene + ", provider=" + targetEnginePolicy.provider()
            );
        }
    }

    private ResolvedDiagnosisContext resolveDiagnosisContext(String userId,
                                                             CreateDiagnosisRequest request,
                                                             FrontDeskTaskOrder explicitTaskOrder) {
        if (explicitTaskOrder != null) {
            return resolveDiagnosisContextFromTaskOrder(explicitTaskOrder);
        }

        String brandName = DiagnosisFieldNormalizer.normalizeRequired(request.getBrandName(), "品牌名不能为空", MAX_CATEGORY_LEN);
        String brandWebsite = DiagnosisFieldNormalizer.normalizeOptional(request.getBrandWebsite(), MAX_BRAND_WEBSITE_LEN);
        String userPrompt = DiagnosisFieldNormalizer.normalizeOptional(request.getUserPrompt(), MAX_USER_PROMPT_LEN);
        String overrideCategory = DiagnosisFieldNormalizer.normalizeOptional(request.getCategory(), MAX_CATEGORY_LEN);
        List<String> overrideCompetitors = DiagnosisFieldNormalizer.normalizeCompetitors(
                request.getCompetitors(),
                brandName,
                MAX_COMPETITOR_SIZE,
                MAX_CATEGORY_LEN
        );

        // Explicit user override has highest priority and should not be blocked by inference failures.
        if (StringUtils.hasText(overrideCategory) && !overrideCompetitors.isEmpty()) {
            return new ResolvedDiagnosisContext(
                    brandName,
                    overrideCategory,
                    brandWebsite,
                    userPrompt,
                    overrideCompetitors,
                    diagnosisInferenceService.fallbackNegativeTerms()
            );
        }

        CreativeContextResult inferred = diagnosisInferenceService.resolveInferredContext(userId, request.getContextId(), brandName);
        if (inferred == null) {
            inferred = diagnosisInferenceService.inferAndValidate(
                    brandName,
                    brandWebsite,
                    userPrompt,
                    "create_diagnosis"
            );
        }

        String finalCategory = StringUtils.hasText(overrideCategory) ? overrideCategory : inferred.category();
        List<String> finalCompetitors = !overrideCompetitors.isEmpty() ? overrideCompetitors : inferred.competitors();
        if (!StringUtils.hasText(finalCategory)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品类不能为空");
        }

        List<String> finalNegativeTerms = inferred.negativeTerms();
        if (finalNegativeTerms == null || finalNegativeTerms.isEmpty()) {
            finalNegativeTerms = diagnosisInferenceService.fallbackNegativeTerms();
        }

        return new ResolvedDiagnosisContext(
                brandName,
                finalCategory,
                brandWebsite,
                userPrompt,
                finalCompetitors,
                finalNegativeTerms
        );
    }

    private ResolvedDiagnosisContext resolveDiagnosisContextFromTaskOrder(FrontDeskTaskOrder explicitTaskOrder) {
        String brandName = DiagnosisFieldNormalizer.normalizeRequired(explicitTaskOrder.brandName(), "前台任务单 brandName 不能为空", MAX_CATEGORY_LEN);
        String category = DiagnosisFieldNormalizer.normalizeRequired(explicitTaskOrder.category(), "前台任务单 category 不能为空", MAX_CATEGORY_LEN);
        List<String> competitors = DiagnosisFieldNormalizer.normalizeCompetitors(
                explicitTaskOrder.competitors(),
                brandName,
                MAX_COMPETITOR_SIZE,
                MAX_CATEGORY_LEN
        );
        List<String> negativeTerms = resolveFrontDeskNegativeTerms(explicitTaskOrder);
        if (negativeTerms.isEmpty()) {
            negativeTerms = diagnosisInferenceService.fallbackNegativeTerms();
        }
        return new ResolvedDiagnosisContext(
                brandName,
                category,
                DiagnosisFieldNormalizer.normalizeOptional(explicitTaskOrder.brandWebsite(), MAX_BRAND_WEBSITE_LEN),
                resolveFrontDeskUserPrompt(explicitTaskOrder),
                competitors,
                negativeTerms
        );
    }

    private void validateFrontDeskTaskOrderSingleSource(CreateDiagnosisRequest request,
                                                        FrontDeskTaskOrder explicitTaskOrder) {
        if (request == null || explicitTaskOrder == null) {
            return;
        }
        List<String> conflictingFields = new ArrayList<>();
        collectConflict(conflictingFields, "brandName", StringUtils.hasText(request.getBrandName()));
        collectConflict(conflictingFields, "category", StringUtils.hasText(request.getCategory()));
        collectConflict(conflictingFields, "brandWebsite", StringUtils.hasText(request.getBrandWebsite()));
        collectConflict(conflictingFields, "userPrompt", StringUtils.hasText(request.getUserPrompt()));
        collectConflict(conflictingFields, "contextId", StringUtils.hasText(request.getContextId()));
        collectConflict(conflictingFields, "competitors", request.getCompetitors() != null && !request.getCompetitors().isEmpty());
        collectConflict(conflictingFields, "categoryAnchors", request.getCategoryAnchors() != null && !request.getCategoryAnchors().isEmpty());
        collectConflict(conflictingFields, "brandAliases", request.getBrandAliases() != null && !request.getBrandAliases().isEmpty());
        collectConflict(conflictingFields, "competitorAliases", request.getCompetitorAliases() != null && !request.getCompetitorAliases().isEmpty());
        collectConflict(conflictingFields, "taskOrderHints", request.getTaskOrderHints() != null && !request.getTaskOrderHints().isEmpty());
        if (!conflictingFields.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "frontDeskTaskOrder 已提供时，不允许再同时提交业务碎片字段: " + String.join(", ", conflictingFields)
            );
        }
    }

    private FrontDeskTaskOrder requireExplicitFrontDeskTaskOrder(Map<String, Object> taskOrderSnapshot) {
        FrontDeskTaskOrder explicitTaskOrder = resolveExplicitFrontDeskTaskOrder(taskOrderSnapshot);
        if (explicitTaskOrder == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "正式创建只接受 frontDeskTaskOrder，请先通过品牌空间预览链路生成前台任务单"
            );
        }
        return explicitTaskOrder;
    }

    private FrontDeskTaskOrder resolveCreateTaskOrder(String userId,
                                                      CreateDiagnosisFromTaskOrderRequest request) {
        String previewToken = request == null
                ? null
                : DiagnosisFieldNormalizer.normalizeOptional(request.getPreviewToken(), 64);
        Map<String, Object> submittedTaskOrder = request == null ? Map.of() : sanitizeFrontDeskTaskOrder(request.getFrontDeskTaskOrder());
        if (!StringUtils.hasText(previewToken)) {
            return requireExplicitFrontDeskTaskOrder(submittedTaskOrder);
        }
        FrontDeskPreviewTokenService.StoredPreviewSnapshot previewSnapshot =
                frontDeskPreviewTokenService.load(userId, previewToken);
        if (previewSnapshot == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "previewToken 无效或已过期，请重新预览");
        }
        if (!submittedTaskOrder.isEmpty()) {
            Map<String, Object> canonicalSnapshotTaskOrder = V42SnapshotSanitizer.sanitizeObjectMap(previewSnapshot.frontDeskTaskOrder());
            if (!canonicalSnapshotTaskOrder.equals(submittedTaskOrder)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST,
                        "previewToken 与 frontDeskTaskOrder 不一致，请重新预览后创建"
                );
            }
        }
        return requireExplicitFrontDeskTaskOrder(previewSnapshot.frontDeskTaskOrder());
    }

    private void collectConflict(List<String> conflicts, String fieldName, boolean present) {
        if (present) {
            conflicts.add(fieldName);
        }
    }

    private Map<String, Object> sanitizeFrontDeskUserBusinessHints(Map<String, Object> taskOrderHints) {
        Map<String, Object> sanitized = diagnosisMapSupport.sanitizeTaskOrderHints(taskOrderHints);
        if (sanitized.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> filtered = new LinkedHashMap<>();
        sanitized.forEach((key, value) -> {
            if (isCanonicalFrontDeskBusinessHintKey(key)) {
                return;
            }
            filtered.put(key, value);
        });
        return filtered.isEmpty() ? Map.of() : Map.copyOf(filtered);
    }

    private Map<String, Object> buildCanonicalFrontDeskBusinessHints(ResolvedDiagnosisContext context,
                                                                     V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors,
                                                                     List<String> brandAliases,
                                                                     Map<String, List<String>> competitorAliases,
                                                                     Map<String, Object> taskOrderHints) {
        LinkedHashMap<String, Object> businessHints = new LinkedHashMap<>();
        if (taskOrderHints != null && !taskOrderHints.isEmpty()) {
            businessHints.putAll(sanitizeFrontDeskUserBusinessHints(taskOrderHints));
        }
        putFrontDeskBusinessHint(businessHints, "userPrompt", context.userPrompt());
        putFrontDeskBusinessHint(businessHints, "negativeTerms", context.negativeTerms().isEmpty() ? null : context.negativeTerms());
        putFrontDeskBusinessHint(businessHints, "categoryAnchors", resolvedCategoryAnchors.anchors());
        putFrontDeskBusinessHint(businessHints, "fallbackAnchorMode", resolvedCategoryAnchors.fallbackAnchorMode());
        putFrontDeskBusinessHint(businessHints, "categoryAnchorSource", resolvedCategoryAnchors.source());
        putFrontDeskBusinessHint(
                businessHints,
                "brandAliases",
                brandAliases == null ? List.of() : List.copyOf(brandAliases)
        );
        putFrontDeskBusinessHint(
                businessHints,
                "competitorAliases",
                competitorAliases == null ? Map.of() : Map.copyOf(competitorAliases)
        );
        return businessHints.isEmpty() ? Map.of() : Map.copyOf(businessHints);
    }

    private boolean isCanonicalFrontDeskBusinessHintKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        return switch (key.trim()) {
            case "userPrompt",
                    "negativeTerms",
                    "categoryAnchors",
                    "fallbackAnchorMode",
                    "categoryAnchorSource",
                    "brandAliases",
                    "competitorAliases" -> true;
            default -> false;
        };
    }

    private void putFrontDeskBusinessHint(Map<String, Object> businessHints, String key, Object value) {
        if (businessHints == null || !StringUtils.hasText(key) || value == null) {
            return;
        }
        businessHints.put(key.trim(), value);
    }

    private String resolvePreviewRequestId(CreateDiagnosisRequest request) {
        String requestId = DiagnosisFieldNormalizer.normalizeOptional(
                request == null ? null : request.getRequestId(),
                64
        );
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        requestId = extractRequestIdFromTaskOrderSnapshot(
                request == null ? null : sanitizeFrontDeskTaskOrder(request.getFrontDeskTaskOrder())
        );
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        return generateRequestId("frontdesk-preview");
    }

    private String resolveCreateRequestId(String userId, CreateDiagnosisFromTaskOrderRequest request) {
        String requestId = DiagnosisFieldNormalizer.normalizeOptional(
                request == null ? null : request.getRequestId(),
                64
        );
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        requestId = extractRequestIdFromTaskOrderSnapshot(
                request == null ? null : sanitizeFrontDeskTaskOrder(request.getFrontDeskTaskOrder())
        );
        if (StringUtils.hasText(requestId)) {
            return requestId;
        }
        String previewToken = DiagnosisFieldNormalizer.normalizeOptional(
                request == null ? null : request.getPreviewToken(),
                64
        );
        if (StringUtils.hasText(userId) && StringUtils.hasText(previewToken)) {
            FrontDeskPreviewTokenService.StoredPreviewSnapshot previewSnapshot =
                    frontDeskPreviewTokenService.load(userId, previewToken);
            requestId = extractRequestIdFromTaskOrderSnapshot(
                    previewSnapshot == null ? null : previewSnapshot.frontDeskTaskOrder()
            );
            if (StringUtils.hasText(requestId)) {
                return requestId;
            }
        }
        return generateRequestId("frontdesk-create");
    }

    @SuppressWarnings("unchecked")
    private String extractRequestIdFromTaskOrderSnapshot(Map<String, Object> taskOrderSnapshot) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return null;
        }
        Object taskMetadata = taskOrderSnapshot.get("taskMetadata");
        if (!(taskMetadata instanceof Map<?, ?> metadata)) {
            return null;
        }
        Object requestId = ((Map<String, Object>) metadata).get("requestId");
        if (!(requestId instanceof String text) || !StringUtils.hasText(text)) {
            return null;
        }
        return DiagnosisFieldNormalizer.normalizeOptional(text, 64);
    }

    private String generateRequestId(String prefix) {
        String normalizedPrefix = StringUtils.hasText(prefix) ? prefix.trim() : "frontdesk";
        return normalizedPrefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveFrontDeskUserPrompt(FrontDeskTaskOrder explicitTaskOrder) {
        if (explicitTaskOrder == null || explicitTaskOrder.businessHints().isEmpty()) {
            return null;
        }
        Object raw = explicitTaskOrder.businessHints().get("userPrompt");
        if (raw instanceof String text && StringUtils.hasText(text)) {
            return DiagnosisFieldNormalizer.normalizeOptional(text, MAX_USER_PROMPT_LEN);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveFrontDeskBrandAliases(FrontDeskTaskOrder explicitTaskOrder) {
        if (explicitTaskOrder == null || explicitTaskOrder.businessHints().isEmpty()) {
            return List.of();
        }
        Object raw = explicitTaskOrder.businessHints().get("brandAliases");
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> brandAliases = rawList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        return DiagnosisFieldNormalizer.normalizeAliasList(
                brandAliases,
                MAX_COMPETITOR_SIZE,
                MAX_CATEGORY_LEN
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveFrontDeskCategoryAnchors(FrontDeskTaskOrder explicitTaskOrder) {
        if (explicitTaskOrder == null || explicitTaskOrder.businessHints().isEmpty()) {
            return List.of();
        }
        Object raw = explicitTaskOrder.businessHints().get("categoryAnchors");
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<String> categoryAnchors = rawList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        return DiagnosisFieldNormalizer.normalizeCategoryAnchors(
                categoryAnchors,
                MAX_COMPETITOR_SIZE,
                MAX_CATEGORY_LEN
        );
    }

    private boolean resolveFrontDeskFallbackAnchorMode(FrontDeskTaskOrder explicitTaskOrder,
                                                       List<String> categoryAnchors) {
        if (explicitTaskOrder != null && !explicitTaskOrder.businessHints().isEmpty()) {
            Object fallbackAnchorMode = explicitTaskOrder.businessHints().get("fallbackAnchorMode");
            if (fallbackAnchorMode instanceof Boolean boolValue) {
                return boolValue;
            }
            if (fallbackAnchorMode instanceof String textValue && StringUtils.hasText(textValue)) {
                String normalized = textValue.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(normalized)) {
                    return true;
                }
                if ("false".equals(normalized)) {
                    return false;
                }
            }
            Object categoryAnchorSource = explicitTaskOrder.businessHints().get("categoryAnchorSource");
            if (categoryAnchorSource instanceof String textValue && StringUtils.hasText(textValue)) {
                String normalized = textValue.trim().toLowerCase(Locale.ROOT);
                if ("backend_fallback".equals(normalized)) {
                    return true;
                }
                if ("explicit_input".equals(normalized)) {
                    return false;
                }
            }
        }
        return categoryAnchors != null && categoryAnchors.size() < 3;
    }

    private V42CategoryAnchorResolver.ResolvedCategoryAnchors resolveCategoryAnchorsFromTaskOrder(String category,
                                                                                                   FrontDeskTaskOrder explicitTaskOrder) {
        List<String> categoryAnchors = resolveFrontDeskCategoryAnchors(explicitTaskOrder);
        if (categoryAnchors.isEmpty()) {
            return resolveCategoryAnchors(category, null);
        }
        boolean fallbackAnchorMode = resolveFrontDeskFallbackAnchorMode(explicitTaskOrder, categoryAnchors);
        try {
            return V42CategoryAnchorResolver.reconcile(category, categoryAnchors, fallbackAnchorMode);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> resolveFrontDeskCompetitorAliases(FrontDeskTaskOrder explicitTaskOrder,
                                                                        List<String> competitors) {
        if (explicitTaskOrder == null || explicitTaskOrder.businessHints().isEmpty()) {
            return Map.of();
        }
        Object raw = explicitTaskOrder.businessHints().get("competitorAliases");
        if (!(raw instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, List<String>> competitorAliases = new LinkedHashMap<>();
        rawMap.forEach((rawKey, rawValue) -> {
            if (!(rawKey instanceof String key) || !StringUtils.hasText(key) || !(rawValue instanceof List<?> rawList)) {
                return;
            }
            List<String> aliases = rawList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
            competitorAliases.put(key.trim(), aliases);
        });
        return validateFrontDeskCompetitorAliases(
                DiagnosisFieldNormalizer.normalizeCompetitorAliases(
                        competitorAliases,
                        MAX_COMPETITOR_SIZE,
                        MAX_CATEGORY_LEN
                ),
                competitors
        );
    }

    private Map<String, List<String>> normalizeFrontDeskCompetitorAliases(Map<String, List<String>> competitorAliases,
                                                                          List<String> competitors) {
        return validateFrontDeskCompetitorAliases(
                DiagnosisFieldNormalizer.normalizeCompetitorAliases(
                        competitorAliases,
                        MAX_COMPETITOR_SIZE,
                        MAX_CATEGORY_LEN
                ),
                competitors
        );
    }

    private Map<String, List<String>> validateFrontDeskCompetitorAliases(Map<String, List<String>> competitorAliases,
                                                                         List<String> competitors) {
        if (competitorAliases == null || competitorAliases.isEmpty()) {
            return Map.of();
        }
        List<String> invalidKeys = competitorAliases.keySet().stream()
                .filter(key -> competitors == null || competitors.stream().noneMatch(key::equals))
                .sorted()
                .toList();
        if (!invalidKeys.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "competitorAliases 包含未声明竞品: " + String.join(", ", invalidKeys)
            );
        }
        return Map.copyOf(competitorAliases);
    }

    private List<String> resolveFrontDeskNegativeTerms(FrontDeskTaskOrder explicitTaskOrder) {
        if (explicitTaskOrder == null || explicitTaskOrder.businessHints().isEmpty()) {
            return List.of();
        }
        Object raw = explicitTaskOrder.businessHints().get("negativeTerms");
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        return DiagnosisFieldNormalizer.normalizeFreeTextList(rawList, MAX_COMPETITOR_SIZE, MAX_CATEGORY_LEN);
    }

    private FrontDeskTaskOrder resolveExplicitFrontDeskTaskOrder(CreateDiagnosisRequest request) {
        if (request == null) {
            return null;
        }
        return resolveExplicitFrontDeskTaskOrder(request.getFrontDeskTaskOrder());
    }

    private FrontDeskTaskOrder resolveExplicitFrontDeskTaskOrder(Map<String, Object> taskOrderSnapshot) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = sanitizeFrontDeskTaskOrder(taskOrderSnapshot);
        if (sanitized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(sanitized, FrontDeskTaskOrder.class);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "frontDeskTaskOrder 非法: " + ex.getMessage());
        }
    }

    private Map<String, Object> sanitizeFrontDeskTaskOrder(Map<String, Object> frontDeskTaskOrder) {
        return diagnosisMapSupport.sanitizeTaskOrderHints(frontDeskTaskOrder);
    }

    private boolean isServiceAccessManagedTaskOrder(FrontDeskTaskOrder taskOrder) {
        if (taskOrder == null) {
            return false;
        }
        String billingMode = readTaskMetadataText(taskOrder.taskMetadata(), "billingMode");
        if (BILLING_MODE_USER_CREDITS.equalsIgnoreCase(String.valueOf(billingMode))) {
            return false;
        }
        if (BILLING_STATE_SERVICE_ACCESS_MANAGED.equalsIgnoreCase(String.valueOf(billingMode))) {
            return true;
        }
        String source = readTaskMetadataText(taskOrder.taskMetadata(), "source");
        if (StringUtils.hasText(source) && source.startsWith(TASK_SOURCE_BRAND_SPACE_PREFIX)) {
            return true;
        }
        String origin = readTaskMetadataText(taskOrder.taskMetadata(), "origin");
        return TASK_METADATA_ORIGIN_BRAND_SPACE.equals(origin);
    }

    private ChargeQuote resolveChargeQuote(FrontDeskTaskOrder taskOrder) {
        if (isServiceAccessManagedTaskOrder(taskOrder)) {
            return new ChargeQuote(0, 0, 0, true, BILLING_STATE_SERVICE_ACCESS_MANAGED);
        }
        String billingMode = readTaskMetadataText(taskOrder == null ? null : taskOrder.taskMetadata(), "billingMode");
        if (BILLING_MODE_USER_CREDITS.equalsIgnoreCase(String.valueOf(billingMode))) {
            int targetProbeCount = Math.max(1, readBrandSpaceRequestedProbeCount(taskOrder));
            int creditsPerProbe = readBrandSpaceCreditsPerProbe(taskOrder);
            return new ChargeQuote(
                    Math.max(1, targetProbeCount * creditsPerProbe),
                    creditsPerProbe,
                    targetProbeCount,
                    false,
                    BILLING_MODE_USER_CREDITS
            );
        }
        return new ChargeQuote(CREDIT_COST_PER_DIAGNOSIS, 1, 1, false, "diagnosis_fixed");
    }

    private Map<String, Object> buildBillingMetadata(ChargeQuote chargeQuote) {
        if (chargeQuote == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("billingMode", chargeQuote.billingMode());
        metadata.put("chargedCredits", chargeQuote.chargeAmount());
        metadata.put("creditsPerProbe", chargeQuote.creditsPerProbe());
        metadata.put("targetProbeCount", chargeQuote.targetProbeCount());
        metadata.put("chargeUnit", "CREDIT_PER_PROBE");
        return metadata;
    }

    private String readTaskMetadataText(Map<String, Object> taskMetadata, String key) {
        if (taskMetadata == null || taskMetadata.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = taskMetadata.get(key.trim());
        return value instanceof String text && StringUtils.hasText(text) ? text.trim() : null;
    }

    private int readBrandSpaceCreditsPerProbe(FrontDeskTaskOrder taskOrder) {
        Map<String, Object> businessHints = taskOrder == null ? Map.of() : readMap(taskOrder.businessHints());
        Map<String, Object> brandSpaceContext = readMap(businessHints.get("brandSpaceContext"));
        Map<String, Object> serviceAccess = readMap(brandSpaceContext.get("serviceAccess"));
        Integer creditsPerProbe = readInteger(serviceAccess.get("creditsPerProbe"));
        return creditsPerProbe != null && creditsPerProbe > 0 ? creditsPerProbe : 1;
    }

    private int readBrandSpaceRequestedProbeCount(FrontDeskTaskOrder taskOrder) {
        Map<String, Object> businessHints = taskOrder == null ? Map.of() : readMap(taskOrder.businessHints());
        Map<String, Object> brandSpaceContext = readMap(businessHints.get("brandSpaceContext"));
        Integer requestedProbeCount = readInteger(brandSpaceContext.get("requestedProbeCount"));
        if (requestedProbeCount != null && requestedProbeCount > 0) {
            return requestedProbeCount;
        }
        Map<String, Object> scheduleDecision = readMap(businessHints.get("scheduleDecision"));
        Integer plannedProbeCount = readInteger(scheduleDecision.get("targetProbeCount"));
        if (plannedProbeCount != null && plannedProbeCount > 0) {
            return plannedProbeCount;
        }
        return 1;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private DiagnosisEdition resolveEdition(DiagnosisEdition edition) {
        if (edition == null) {
            return DiagnosisEdition.STANDARD;
        }
        return edition;
    }

    private String resolveEngineVersion(String requestedEngineVersion) {
        String normalized = DiagnosisFieldNormalizer.normalizeOptional(requestedEngineVersion, 16);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_ENGINE_VERSION;
        }
        if (!V42EngineVersionSupport.isSupported(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "当前仅支持 engineVersion=" + V42EngineVersionSupport.supportedVersionsLabel()
            );
        }
        return normalized;
    }

    private V42CategoryAnchorResolver.ResolvedCategoryAnchors resolveCategoryAnchors(String category,
                                                                                     List<String> requestedCategoryAnchors) {
        try {
            return V42CategoryAnchorResolver.fromRequest(
                    category,
                    DiagnosisFieldNormalizer.normalizeCategoryAnchors(
                            requestedCategoryAnchors,
                            MAX_COMPETITOR_SIZE,
                            MAX_CATEGORY_LEN
                    )
            );
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    private DiagnosisResponse toDiagnosisResponse(Diagnosis item) {
        return DiagnosisResponse.builder()
                .id(item.getId())
                .status(item.getStatus())
                .brandName(item.getBrandName())
                .category(item.getCategory())
                .competitors(item.getCompetitorsInput())
                .brandWebsite(item.getBrandWebsite())
                .userPrompt(item.getUserPrompt())
                .isTrial(item.getIsTrial())
                .createdAt(item.getCreatedAt())
                .build();
    }

    private Map<String, Object> readReportMeta(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return Map.of();
        }
        try {
            Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<Map<String, Object>>() {
            });
            if (reportData == null) {
                return Map.of();
            }
            return reportData.get("meta") == null
                    ? Map.of()
                    : objectMapper.convertValue(reportData.get("meta"), new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> readObjectMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {
            });
            return V42SnapshotSanitizer.sanitizeObjectMap(map);
        } catch (IllegalArgumentException ex) {
            return Map.of();
        }
    }

    private boolean isCancelableStatus(Integer status) {
        return isStatus(status, STATUS_PENDING)
                || isStatus(status, STATUS_RUNNING)
                || isStatus(status, STATUS_QUEUED);
    }

    private boolean isStatus(Integer status, int expected) {
        return status != null && status == expected;
    }

    public record ProgressStreamSnapshot(String diagnosisId,
                                         Integer status,
                                         String errorMessage,
                                         String currentStep,
                                         String currentStepLabel,
                                         String stage,
                                         int stageIndex,
                                         int stageTotal,
                                         String stageLabel,
                                         Double percent,
                                         Integer samplingCompleted,
                                         Integer samplingTotal,
                                         Integer samplingExpectedTotal,
                                         Integer samplingGeneratedTotal,
                                         Integer samplingMissingTotal,
                                         Boolean samplingVariantShortfall,
                                         Integer etaSeconds) {
    }

    public record ProgressStreamFrame(ProgressStreamSnapshot snapshot,
                                      ProbeEventPage probeEventPage) {
    }

    public record ProbeEventPage(List<ProbeEventItem> events, long nextSeq) {
        public ProbeEventPage {
            events = events == null ? List.of() : List.copyOf(events);
            nextSeq = Math.max(0L, nextSeq);
        }
    }

    public record ProbeEventItem(long seq,
                                 String eventType,
                                 boolean provisional,
                                 String createdAt,
                                 Map<String, Object> payload) {
        public ProbeEventItem {
            seq = Math.max(0L, seq);
            eventType = eventType == null ? "" : eventType.trim();
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }

    private record ResolvedDiagnosisContext(String brandName,
                                            String category,
                                            String brandWebsite,
                                            String userPrompt,
                                            List<String> competitors,
                                            List<String> negativeTerms) {
    }

    public record TaskOrderPreview(int targetProbeCount,
                                   Map<String, Object> frontDeskTaskOrder,
                                   Map<String, Object> contextPackageSnapshot,
                                   Map<String, Object> createDiagnosisPayloadSnapshot,
                                   String previewToken) {
        public TaskOrderPreview {
            frontDeskTaskOrder = V42SnapshotSanitizer.sanitizeObjectMap(frontDeskTaskOrder);
            if (targetProbeCount <= 0) {
                throw new IllegalArgumentException("targetProbeCount 必须是正整数");
            }
            contextPackageSnapshot = V42SnapshotSanitizer.sanitizeObjectMap(contextPackageSnapshot);
            createDiagnosisPayloadSnapshot = V42SnapshotSanitizer.sanitizeObjectMap(createDiagnosisPayloadSnapshot);
            previewToken = DiagnosisFieldNormalizer.normalizeOptional(previewToken, 64);
        }
    }

    private record PreparedDiagnosisCreateInput(ResolvedDiagnosisContext resolved,
                                                String engineVersion,
                                                DiagnosisEdition edition,
                                                DiagnosisConfig diagnosisConfig,
                                                V42CategoryAnchorResolver.ResolvedCategoryAnchors resolvedCategoryAnchors,
                                                List<String> brandAliases,
                                                Map<String, List<String>> competitorAliases,
                                                FrontDeskTaskOrder taskOrder,
                                                EngineTaskContextPackage contextPackage,
                                                Map<String, Object> createDiagnosisPayloadSnapshot) {
    }

    private record ChargeQuote(int chargeAmount,
                               int creditsPerProbe,
                               int targetProbeCount,
                               boolean serviceAccessManaged,
                               String billingMode) {
    }

}

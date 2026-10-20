package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.RunEventPageView;
import com.geoprobe.dto.brandspace.RunEventView;
import com.geoprobe.dto.brandspace.RunView;
import com.geoprobe.entity.v1.DiagnosisEventRecord;
import com.geoprobe.entity.v1.DiagnosisProgressSnapshot;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunEventRepository;
import com.geoprobe.knowledge.repository.KnowledgeAssetRunRepository;
import com.geoprobe.knowledge.service.KnowledgeAssetHistorySyncService;
import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.DiagnosisEventRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisProgressSnapshotMapper;
import com.geoprobe.mapper.v1.DiagnosisReportRecordMapper;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RunReadService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_QUEUED = 5;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DiagnosisTaskMapper diagnosisTaskMapper;
    private final BrandScopeMapper brandScopeMapper;
    private final DiagnosisProgressSnapshotMapper diagnosisProgressSnapshotMapper;
    private final DiagnosisEventRecordMapper diagnosisEventRecordMapper;
    private final DiagnosisReportRecordMapper diagnosisReportRecordMapper;
    private final ObjectProvider<KnowledgeAssetHistorySyncService> knowledgeAssetHistorySyncServiceProvider;
    private final ObjectProvider<KnowledgeAssetRunRepository> knowledgeAssetRunRepositoryProvider;
    private final ObjectProvider<KnowledgeAssetRunEventRepository> knowledgeAssetRunEventRepositoryProvider;

    public RunView getRun(String userId, String runId) {
        DiagnosisTask task = requireTask(userId, runId);
        return buildRunView(task);
    }

    public RunView getRunForAdmin(String runId) {
        DiagnosisTask task = requireTaskForAdmin(runId);
        return buildRunView(task);
    }

    private RunView buildRunView(DiagnosisTask task) {
        DiagnosisProgressSnapshot progress = diagnosisProgressSnapshotMapper.selectById(task.getId());
        long latestEventSeq = buildTimeline(task, progress).stream()
                .map(RunEventView::getSeq)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        return RunView.builder()
                .runId(task.getId())
                .brandSpaceId(task.getBrandScopeId())
                .brandName(task.getBrandNameSnapshot())
                .status(mapRunStatus(task.getStatusCode()))
                .progressPercent(readProgressPercent(task, progress))
                .currentStep(mapCurrentStep(progress == null ? null : progress.getCurrentStepCode(), task.getStatusCode()))
                .currentStepLabel(resolveCurrentStepLabel(progress, task))
                .startedAt(formatDateTime(task.getStartedAt()))
                .completedAt(formatDateTime(task.getCompletedAt()))
                .latestEventSeq(latestEventSeq)
                .build();
    }

    public RunEventPageView getRunEvents(String userId, String runId, Long afterSeq, Integer limit) {
        DiagnosisTask task = requireTask(userId, runId);
        return buildRunEventPage(task, afterSeq, limit);
    }

    public RunEventPageView getRunEventsForAdmin(String runId, Long afterSeq, Integer limit) {
        DiagnosisTask task = requireTaskForAdmin(runId);
        return buildRunEventPage(task, afterSeq, limit);
    }

    private RunEventPageView buildRunEventPage(DiagnosisTask task, Long afterSeq, Integer limit) {
        DiagnosisProgressSnapshot progress = diagnosisProgressSnapshotMapper.selectById(task.getId());
        List<RunEventView> timeline = buildTimeline(task, progress);
        long cursor = afterSeq == null ? 0L : Math.max(0L, afterSeq);
        int effectiveLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 300));
        List<RunEventView> filtered = timeline.stream()
                .filter(item -> item.getSeq() != null && item.getSeq() > cursor)
                .limit(effectiveLimit)
                .toList();
        Long nextSeq = filtered.isEmpty() ? cursor : filtered.get(filtered.size() - 1).getSeq();
        return RunEventPageView.builder()
                .events(filtered)
                .nextSeq(nextSeq)
                .build();
    }

    public RunView getRunForBrandSpace(String organizationId, String brandSpaceId, String runId) {
        DiagnosisTask task = requireTaskForBrandSpace(organizationId, brandSpaceId, runId);
        DiagnosisProgressSnapshot progress = diagnosisProgressSnapshotMapper.selectById(task.getId());
        long latestEventSeq = buildTimeline(task, progress).stream()
                .map(RunEventView::getSeq)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        return RunView.builder()
                .runId(task.getId())
                .brandSpaceId(task.getBrandScopeId())
                .brandName(task.getBrandNameSnapshot())
                .status(mapRunStatus(task.getStatusCode()))
                .progressPercent(readProgressPercent(task, progress))
                .currentStep(mapCurrentStep(progress == null ? null : progress.getCurrentStepCode(), task.getStatusCode()))
                .currentStepLabel(resolveCurrentStepLabel(progress, task))
                .startedAt(formatDateTime(task.getStartedAt()))
                .completedAt(formatDateTime(task.getCompletedAt()))
                .latestEventSeq(latestEventSeq)
                .build();
    }

    public RunEventPageView getRunEventsForBrandSpace(String organizationId,
                                                      String brandSpaceId,
                                                      String runId,
                                                      Long afterSeq,
                                                      Integer limit) {
        DiagnosisTask task = requireTaskForBrandSpace(organizationId, brandSpaceId, runId);
        DiagnosisProgressSnapshot progress = diagnosisProgressSnapshotMapper.selectById(task.getId());
        List<RunEventView> timeline = buildTimeline(task, progress);
        long cursor = afterSeq == null ? 0L : Math.max(0L, afterSeq);
        int effectiveLimit = Math.max(1, Math.min(limit == null ? 100 : limit, 300));
        List<RunEventView> filtered = timeline.stream()
                .filter(item -> item.getSeq() != null && item.getSeq() > cursor)
                .limit(effectiveLimit)
                .toList();
        Long nextSeq = filtered.isEmpty() ? cursor : filtered.get(filtered.size() - 1).getSeq();
        return RunEventPageView.builder()
                .events(filtered)
                .nextSeq(nextSeq)
                .build();
    }

    private List<RunEventView> buildTimeline(DiagnosisTask task, DiagnosisProgressSnapshot progress) {
        List<RawRunEvent> rawEvents = loadRawRunEvents(task == null ? null : task.getId());
        DiagnosisReportRecord report = loadCompletionReport(task);
        List<RunEventView> timeline = new ArrayList<>();
        LocalDateTime startedAt = task.getStartedAt() == null ? task.getCreatedAt() : task.getStartedAt();
        timeline.add(RunEventView.builder()
                .seq(1L)
                .type("run_started")
                .provisional(false)
                .createdAt(formatDateTime(startedAt))
                .payload(payloadMap(
                        "runId", task.getId(),
                        "brandSpaceId", task.getBrandScopeId(),
                        "brandName", task.getBrandNameSnapshot()
                ))
                .build());
        timeline.add(RunEventView.builder()
                .seq(2L)
                .type("step_changed")
                .provisional(false)
                .createdAt(formatDateTime(progress == null ? startedAt : progress.getUpdatedAt()))
                .payload(payloadMap(
                        "currentStep", mapCurrentStep(progress == null ? null : progress.getCurrentStepCode(), task.getStatusCode()),
                        "currentStepLabel", resolveCurrentStepLabel(progress, task),
                        "progressPercent", readProgressPercent(task, progress)
                ))
                .build());

        for (RawRunEvent record : rawEvents) {
            long baseSeq = record.seq() == null ? 100L : record.seq() * 10L + 100L;
            if ("probe_result".equals(record.eventType())) {
                Map<String, Object> payload = defaultMap(record.payloadJson());
                timeline.add(newEvent(baseSeq, "probe_generated", record.createdAt(), true, payloadMap(
                        "probeId", asText(payload.get("probeId")),
                        "renderedPrompt", asText(payload.get("renderedPrompt")),
                        "renderFamily", asText(payload.get("renderFamily")),
                        "status", asText(payload.get("status"))
                )));
                timeline.add(newEvent(baseSeq + 1L, "model_called", record.createdAt(), record.provisional(), payloadMap(
                        "probeId", asText(payload.get("probeId")),
                        "modelId", asText(payload.get("modelId")),
                        "status", asText(payload.get("status")),
                        "latencyMs", payload.get("latencyMs"),
                        "totalTokenCount", payload.get("totalTokenCount"),
                        "searchRequested", payload.get("searchRequested"),
                        "searchSatisfied", payload.get("searchSatisfied")
                )));
                int evidenceCount = readEvidenceCount(payload.get("searchEvidence"));
                if (evidenceCount > 0 || Boolean.TRUE.equals(payload.get("searchSatisfied"))) {
                    timeline.add(newEvent(baseSeq + 2L, "citations_collected", record.createdAt(), record.provisional(), payloadMap(
                            "probeId", asText(payload.get("probeId")),
                            "evidenceCount", evidenceCount,
                            "domains", readEvidenceDomains(payload.get("searchEvidence")),
                            "searchQueries", defaultList(payload.get("searchQueries"))
                    )));
                }
                continue;
            }
            if ("round_summary".equals(record.eventType())) {
                Map<String, Object> payload = defaultMap(record.payloadJson());
                timeline.add(newEvent(baseSeq, "citations_collected", record.createdAt(), record.provisional(), payloadMap(
                        "round", payload.get("round"),
                        "requestedProbeCount", payload.get("requestedProbeCount"),
                        "satisfiedProbeCount", payload.get("satisfiedProbeCount"),
                        "evidenceSuccessRate", payload.get("evidenceSuccessRate"),
                        "qualityGatePassed", payload.get("qualityGatePassed")
                )));
                continue;
            }
            timeline.add(newEvent(baseSeq, "step_changed", record.createdAt(), record.provisional(), payloadMap(
                    "rawEventType", record.eventType(),
                    "payload", defaultMap(record.payloadJson())
            )));
        }

        long nextSeq = timeline.stream()
                .map(RunEventView::getSeq)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(10L) + 10L;
        if (report != null) {
            timeline.add(newEvent(nextSeq, "aggregation_completed", report.getGeneratedAt(), false, payloadMap(
                    "reportId", report.getId(),
                    "generatedAt", formatDateTime(report.getGeneratedAt())
            )));
            nextSeq += 10L;
        }
        if (intEquals(task.getStatusCode(), STATUS_COMPLETED)) {
            timeline.add(newEvent(nextSeq, "run_completed", task.getCompletedAt(), false, payloadMap(
                    "runId", task.getId(),
                    "brandSpaceId", task.getBrandScopeId()
            )));
        } else if (intEquals(task.getStatusCode(), STATUS_FAILED)) {
            timeline.add(newEvent(nextSeq, "run_failed", task.getCompletedAt(), false, payloadMap(
                    "runId", task.getId(),
                    "failureMessage", task.getFailureMessage()
            )));
        }
        return timeline.stream()
                .sorted((left, right) -> Long.compare(left.getSeq(), right.getSeq()))
                .toList();
    }

    private List<RawRunEvent> loadRawRunEvents(String sourceTaskId) {
        if (!StringUtils.hasText(sourceTaskId)) {
            return List.of();
        }
        KnowledgeAssetRunEventRepository runEventRepository = knowledgeAssetRunEventRepositoryProvider.getIfAvailable();
        if (runEventRepository != null) {
            List<KnowledgeAssetRunEventRepository.RunEventView> eventViews =
                    runEventRepository.listBySourceTaskId(sourceTaskId.trim());
            if (eventViews != null && !eventViews.isEmpty()) {
                return eventViews.stream()
                        .filter(Objects::nonNull)
                        .map(eventView -> new RawRunEvent(
                                eventView.sourceEventSeq(),
                                eventView.eventType(),
                                Boolean.TRUE.equals(eventView.provisional()),
                                defaultMap(eventView.payloadJson()),
                                eventView.eventCreatedAt()
                        ))
                        .toList();
            }
        }
        List<DiagnosisEventRecord> rawEvents = diagnosisEventRecordMapper.selectList(new LambdaQueryWrapper<DiagnosisEventRecord>()
                .eq(DiagnosisEventRecord::getDiagnosisTaskId, sourceTaskId.trim())
                .orderByAsc(DiagnosisEventRecord::getSeq));
        if (rawEvents == null || rawEvents.isEmpty()) {
            return List.of();
        }
        return rawEvents.stream()
                .filter(Objects::nonNull)
                .map(record -> new RawRunEvent(
                        record.getSeq(),
                        record.getEventType(),
                        Boolean.TRUE.equals(record.getProvisional()),
                        defaultMap(record.getPayloadJson()),
                        record.getCreatedAt()
                ))
                .toList();
    }

    private DiagnosisTask requireTask(String userId, String runId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        if (task == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        if (!Objects.equals(task.getRequestedByUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (StringUtils.hasText(task.getBrandScopeId())) {
            BrandScope scope = brandScopeMapper.selectById(task.getBrandScopeId());
            if (scope != null && BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
            }
        }
        return task;
    }

    private DiagnosisTask requireTaskForAdmin(String runId) {
        if (!StringUtils.hasText(runId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "运行记录不存在");
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId.trim());
        if (task == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        return task;
    }

    private DiagnosisTask requireTaskForBrandSpace(String organizationId, String brandSpaceId, String runId) {
        if (!StringUtils.hasText(organizationId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        DiagnosisTask task = diagnosisTaskMapper.selectById(runId);
        if (task == null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_FOUND);
        }
        if (!Objects.equals(task.getOrganizationId(), organizationId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(task.getBrandScopeId()) || !task.getBrandScopeId().trim().equals(brandSpaceId == null ? null : brandSpaceId.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "运行记录不属于当前品牌空间");
        }
        BrandScope scope = brandScopeMapper.selectById(task.getBrandScopeId());
        if (scope == null || !Objects.equals(scope.getOrganizationId(), organizationId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        if (BrandSpaceLifecyclePolicy.isArchived(scope.getScopeStatusCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间已移出当前账号");
        }
        return task;
    }

    private DiagnosisReportRecord loadCompletionReport(DiagnosisTask task) {
        String sourceTaskId = task == null ? null : task.getId();
        DiagnosisReportRecord knowledgeReport = loadKnowledgeCompletionReport(
                sourceTaskId,
                task == null ? null : task.getBrandScopeId()
        );
        if (knowledgeReport != null) {
            return knowledgeReport;
        }
        return diagnosisReportRecordMapper.selectOne(new LambdaQueryWrapper<DiagnosisReportRecord>()
                .eq(DiagnosisReportRecord::getDiagnosisTaskId, sourceTaskId)
                .last("limit 1"));
    }

    private DiagnosisReportRecord loadKnowledgeCompletionReport(String sourceTaskId, String brandScopeId) {
        ensureKnowledgeHistoryReady(brandScopeId);
        KnowledgeAssetRunRepository repository = knowledgeAssetRunRepositoryProvider.getIfAvailable();
        if (repository == null || !StringUtils.hasText(sourceTaskId)) {
            return null;
        }
        KnowledgeAssetRunRepository.RunDetailView runDetail = repository.findRunDetailBySourceTaskId(sourceTaskId.trim());
        if (runDetail == null || runDetail.observedAt() == null) {
            return null;
        }
        return DiagnosisReportRecord.builder()
                .id(firstText(runDetail.sourceReportId(), runDetail.runId()))
                .diagnosisTaskId(runDetail.runId())
                .brandScopeId(runDetail.brandScopeId())
                .organizationId(runDetail.organizationId())
                .generatedAt(toLocalDateTime(runDetail.observedAt()))
                .createdAt(toLocalDateTime(runDetail.observedAt()))
                .build();
    }

    private RunEventView newEvent(long seq,
                                  String type,
                                  LocalDateTime createdAt,
                                  Boolean provisional,
                                  Map<String, Object> payload) {
        return RunEventView.builder()
                .seq(seq)
                .type(type)
                .provisional(Boolean.TRUE.equals(provisional))
                .createdAt(formatDateTime(createdAt))
                .payload(payload)
                .build();
    }

    private record RawRunEvent(Long seq,
                               String eventType,
                               Boolean provisional,
                               Map<String, Object> payloadJson,
                               LocalDateTime createdAt) {
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

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String mapRunStatus(Integer statusCode) {
        if (intEquals(statusCode, STATUS_COMPLETED)) {
            return "completed";
        }
        if (intEquals(statusCode, STATUS_FAILED)) {
            return "failed";
        }
        if (intEquals(statusCode, STATUS_CANCELLED)) {
            return "cancelled";
        }
        if (intEquals(statusCode, STATUS_RUNNING)) {
            return "running";
        }
        if (intEquals(statusCode, STATUS_PENDING) || intEquals(statusCode, STATUS_QUEUED)) {
            return "created";
        }
        return "partial";
    }

    private String mapCurrentStep(String currentStepCode, Integer statusCode) {
        String step = StringUtils.hasText(currentStepCode) ? currentStepCode.trim().toUpperCase() : null;
        if (!StringUtils.hasText(step)) {
            return mapRunStatus(statusCode);
        }
        return switch (step) {
            case "CREATED", "QUEUED", "PENDING" -> "created";
            case "NODE0_STATE_SPACE" -> "understanding";
            case "NODE1_BLUEPRINTS", "NODE2_RENDERING", "QUALITY_GATE" -> "building";
            case "NODE3_SAMPLING", "SAMPLING" -> "sampling";
            case "NODE4_PARSING", "NODE5A_KPI", "NODE5B_INSIGHTS", "NODE5C_DASHBOARD" -> "analyzing";
            case "REPORT_ASSEMBLY", "REPORT_PROJECTION" -> "aggregation";
            case "COMPLETED" -> "completed";
            case "FAILED" -> "failed";
            case "CANCELLED" -> "cancelled";
            default -> step.toLowerCase();
        };
    }

    private String resolveCurrentStepLabel(DiagnosisProgressSnapshot progress, DiagnosisTask task) {
        if (progress != null && StringUtils.hasText(progress.getCurrentStepLabel())) {
            return progress.getCurrentStepLabel().trim();
        }
        return switch (mapCurrentStep(progress == null ? null : progress.getCurrentStepCode(), task.getStatusCode())) {
            case "created" -> "任务已创建，等待引擎执行";
            case "sampling" -> "采集回答";
            case "analyzing" -> "聚合分析";
            case "aggregation" -> "生成聚合结果";
            case "completed" -> "运行已完成";
            case "failed" -> "运行失败";
            case "cancelled" -> "运行已取消";
            default -> "运行中";
        };
    }

    private Double readProgressPercent(DiagnosisTask task, DiagnosisProgressSnapshot progress) {
        BigDecimal percent = progress != null && progress.getPercent() != null
                ? progress.getPercent()
                : task.getProgressPercent();
        return percent == null ? 0.0d : percent.doubleValue();
    }

    private int readEvidenceCount(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return 0;
        }
        return list.size();
    }

    private List<String> readEvidenceDomains(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> evidence)) {
                continue;
            }
            Object domain = evidence.get("domain");
            if (domain instanceof String text && StringUtils.hasText(text)) {
                domains.add(text.trim());
            }
        }
        return List.copyOf(domains);
    }

    private Map<String, Object> defaultMap(Map<String, Object> payload) {
        return payload == null ? Map.of() : payload;
    }

    private Map<String, Object> payloadMap(Object... entries) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object rawKey = entries[index];
            Object value = entries[index + 1];
            if (!(rawKey instanceof String key) || value == null) {
                continue;
            }
            payload.put(key, value);
        }
        return payload;
    }

    private List<?> defaultList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private String asText(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : DATE_TIME_FORMATTER.format(dateTime);
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }
}

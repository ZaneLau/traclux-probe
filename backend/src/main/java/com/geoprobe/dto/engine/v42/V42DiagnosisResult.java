package com.geoprobe.dto.engine.v42;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record V42DiagnosisResult(
        Task task,
        Report report
) {

    public V42DiagnosisResult {
        task = Objects.requireNonNull(task, "task 不能为空");
        report = Objects.requireNonNull(report, "report 不能为空");
    }

    public record Task(
            String taskId,
            String reportId,
            String engineVersion,
            String status,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            int sampleSize,
            String modelProvider,
            long successfulProbes,
            long failedProbes,
            RealtimeSearchSummary realtimeSearchSummary,
            Node3UsageCostSummary usageCostSummary,
            List<ProbeTrace> probeTrace,
            List<ExecutionSignal> executionSignals
    ) {

        public Task {
            taskId = normalizeRequired(taskId, "taskId 不能为空");
            reportId = normalizeRequired(reportId, "reportId 不能为空");
            engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
            status = normalizeRequired(status, "status 不能为空");
            createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
            completedAt = Objects.requireNonNull(completedAt, "completedAt 不能为空");
            if (sampleSize <= 0) {
                throw new IllegalArgumentException("sampleSize 必须大于 0");
            }
            modelProvider = normalizeRequired(modelProvider, "modelProvider 不能为空");
            if (successfulProbes < 0L || failedProbes < 0L || successfulProbes + failedProbes != sampleSize) {
                throw new IllegalArgumentException("successfulProbes + failedProbes 必须等于 sampleSize");
            }
            realtimeSearchSummary = realtimeSearchSummary == null
                    ? RealtimeSearchSummary.disabled("doubao")
                    : realtimeSearchSummary;
            usageCostSummary = usageCostSummary == null
                    ? Node3UsageCostSummary.empty()
                    : usageCostSummary;
            probeTrace = immutableObjectList(probeTrace, "probeTrace 不能为空");
            if (probeTrace.size() != sampleSize) {
                throw new IllegalArgumentException("probeTrace.size() 必须等于 sampleSize");
            }
            executionSignals = immutableObjectList(executionSignals == null ? List.of() : executionSignals, "executionSignals 不能为空");
        }

        public Task(String taskId,
                    String reportId,
                    String engineVersion,
                    String status,
                    LocalDateTime createdAt,
                    LocalDateTime completedAt,
                    int sampleSize,
                    String modelProvider,
                    long successfulProbes,
                    long failedProbes,
                    List<ProbeTrace> probeTrace) {
            this(
                    taskId,
                    reportId,
                    engineVersion,
                    status,
                    createdAt,
                    completedAt,
                    sampleSize,
                    modelProvider,
                    successfulProbes,
                    failedProbes,
                    RealtimeSearchSummary.disabled("doubao"),
                    Node3UsageCostSummary.empty(),
                    probeTrace,
                    List.of()
            );
        }

        public Task(String taskId,
                    String reportId,
                    String engineVersion,
                    String status,
                    LocalDateTime createdAt,
                    LocalDateTime completedAt,
                    int sampleSize,
                    String modelProvider,
                    long successfulProbes,
                    long failedProbes,
                    RealtimeSearchSummary realtimeSearchSummary,
                    List<ProbeTrace> probeTrace) {
            this(
                    taskId,
                    reportId,
                    engineVersion,
                    status,
                    createdAt,
                    completedAt,
                    sampleSize,
                    modelProvider,
                    successfulProbes,
                    failedProbes,
                    realtimeSearchSummary,
                    Node3UsageCostSummary.empty(),
                    probeTrace,
                    List.of()
            );
        }

        public Task(String taskId,
                    String reportId,
                    String engineVersion,
                    String status,
                    LocalDateTime createdAt,
                    LocalDateTime completedAt,
                    int sampleSize,
                    String modelProvider,
                    long successfulProbes,
                    long failedProbes,
                    RealtimeSearchSummary realtimeSearchSummary,
                    Node3UsageCostSummary usageCostSummary,
                    List<ProbeTrace> probeTrace) {
            this(
                    taskId,
                    reportId,
                    engineVersion,
                    status,
                    createdAt,
                    completedAt,
                    sampleSize,
                    modelProvider,
                    successfulProbes,
                    failedProbes,
                    realtimeSearchSummary,
                    usageCostSummary,
                    probeTrace,
                    List.of()
            );
        }
    }

    public record Report(
            String taskId,
            String reportId,
            String engineVersion,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            int sampleSize,
            String modelProvider,
            String methodology,
            String disclaimer,
            KpiSummary kpiSummary,
            CommercialInsights commercialInsights,
            DefenseCompletenessSummary defenseCompleteness,
            DashboardCharts dashboardCharts,
            List<DisplayNameMapping> displayNameMappings,
            List<ExecutionSignal> executionSignals
    ) {

        public Report {
            taskId = normalizeRequired(taskId, "taskId 不能为空");
            reportId = normalizeRequired(reportId, "reportId 不能为空");
            engineVersion = normalizeRequired(engineVersion, "engineVersion 不能为空");
            createdAt = Objects.requireNonNull(createdAt, "createdAt 不能为空");
            completedAt = Objects.requireNonNull(completedAt, "completedAt 不能为空");
            if (sampleSize <= 0) {
                throw new IllegalArgumentException("sampleSize 必须大于 0");
            }
            modelProvider = normalizeRequired(modelProvider, "modelProvider 不能为空");
            methodology = normalizeRequired(methodology, "methodology 不能为空");
            disclaimer = normalizeRequired(disclaimer, "disclaimer 不能为空");
            kpiSummary = Objects.requireNonNull(kpiSummary, "kpiSummary 不能为空");
            commercialInsights = Objects.requireNonNull(commercialInsights, "commercialInsights 不能为空");
            defenseCompleteness = Objects.requireNonNull(defenseCompleteness, "defenseCompleteness 不能为空");
            dashboardCharts = Objects.requireNonNull(dashboardCharts, "dashboardCharts 不能为空");
            displayNameMappings = immutableObjectList(displayNameMappings, "displayNameMappings 不能为空");
            if (displayNameMappings.isEmpty()) {
                throw new IllegalArgumentException("displayNameMappings 不能为空");
            }
            executionSignals = immutableObjectList(executionSignals == null ? List.of() : executionSignals, "executionSignals 不能为空");
        }

        public Report(String taskId,
                      String reportId,
                      String engineVersion,
                      LocalDateTime createdAt,
                      LocalDateTime completedAt,
                      int sampleSize,
                      String modelProvider,
                      String methodology,
                      String disclaimer,
                      KpiSummary kpiSummary,
                      CommercialInsights commercialInsights,
                      DefenseCompletenessSummary defenseCompleteness,
                      DashboardCharts dashboardCharts,
                      List<DisplayNameMapping> displayNameMappings) {
            this(
                    taskId,
                    reportId,
                    engineVersion,
                    createdAt,
                    completedAt,
                    sampleSize,
                    modelProvider,
                    methodology,
                    disclaimer,
                    kpiSummary,
                    commercialInsights,
                    defenseCompleteness,
                    dashboardCharts,
                    displayNameMappings,
                    List.of()
            );
        }
    }

    public record ProbeTrace(
            String probeId,
            BucketType bucketType,
            Map<String, String> abstractTags,
            Map<String, String> translatedTags,
            String surfaceIntent,
            String interactionPosture,
            String conversationMode,
            ProbeExposurePolicy exposurePolicy,
            String renderedPrompt,
            String renderFamily,
            ProbeDispatchStatus dispatchStatus,
            ProbeType probeType,
            boolean mentioned,
            String responseText,
            String fullResponseText,
            String thinkContent,
            List<String> citations,
            int promptTokenCount,
            int responseTokenCount,
            int totalTokenCount,
            long latencyMs,
            String responseLengthBucket,
            String detectionLevel,
            String failureReason,
            boolean searchRequested,
            boolean searchSatisfied,
            boolean searchFallbackFlag,
            List<SearchEvidence> searchEvidence,
            int searchCallCount,
            List<String> searchQueries,
            String modelId,
            String routingIntent,
            List<String> routingCandidates,
            int roundNo,
            int attemptNo,
            Map<String, Object> triggeredRulesJson,
            BrandDetectionResult brandDetectionResult
    ) {

        public ProbeTrace {
            probeId = normalizeRequired(probeId, "probeId 不能为空");
            bucketType = Objects.requireNonNull(bucketType, "bucketType 不能为空");
            abstractTags = immutableOrderedMap(abstractTags, "abstractTags 不能为空");
            translatedTags = immutableOrderedMap(translatedTags, "translatedTags 不能为空");
            surfaceIntent = normalizeOptional(surfaceIntent);
            interactionPosture = normalizeOptional(interactionPosture);
            conversationMode = normalizeOptional(conversationMode);
            exposurePolicy = Objects.requireNonNull(exposurePolicy, "exposurePolicy 不能为空");
            renderedPrompt = normalizeRequired(renderedPrompt, "renderedPrompt 不能为空");
            renderFamily = normalizeOptional(renderFamily);
            dispatchStatus = Objects.requireNonNull(dispatchStatus, "dispatchStatus 不能为空");
            probeType = Objects.requireNonNull(probeType, "probeType 不能为空");
            responseText = normalizeOptional(responseText);
            fullResponseText = normalizeOptional(fullResponseText);
            thinkContent = normalizeOptional(thinkContent);
            citations = immutableObjectList(citations, "citations 不能为空");
            if (promptTokenCount < 0) {
                throw new IllegalArgumentException("promptTokenCount 不能为负数");
            }
            if (responseTokenCount < 0) {
                throw new IllegalArgumentException("responseTokenCount 不能为负数");
            }
            if (totalTokenCount < 0) {
                throw new IllegalArgumentException("totalTokenCount 不能为负数");
            }
            if (latencyMs < 0L) {
                throw new IllegalArgumentException("latencyMs 不能为负数");
            }
            responseLengthBucket = normalizeRequired(responseLengthBucket, "responseLengthBucket 不能为空");
            detectionLevel = normalizeOptional(detectionLevel);
            failureReason = normalizeOptional(failureReason);
            searchEvidence = immutableObjectList(searchEvidence, "searchEvidence 不能为空");
            if (searchCallCount < 0) {
                throw new IllegalArgumentException("searchCallCount 不能为负数");
            }
            searchQueries = immutableObjectList(searchQueries, "searchQueries 不能为空");
            modelId = normalizeOptional(modelId);
            routingIntent = normalizeOptional(routingIntent);
            routingCandidates = immutableObjectList(routingCandidates, "routingCandidates 不能为空");
            if (roundNo < 0) {
                throw new IllegalArgumentException("roundNo 不能为负数");
            }
            if (attemptNo < 0) {
                throw new IllegalArgumentException("attemptNo 不能为负数");
            }
            triggeredRulesJson = immutableObjectMap(triggeredRulesJson);
            if (dispatchStatus == ProbeDispatchStatus.SUCCESS && failureReason != null) {
                throw new IllegalArgumentException("SUCCESS probeTrace 不应包含 failureReason");
            }
            if (dispatchStatus == ProbeDispatchStatus.FAILED && failureReason == null) {
                throw new IllegalArgumentException("FAILED probeTrace 必须包含 failureReason");
            }
        }

        public ProbeTrace(String probeId,
                          BucketType bucketType,
                          Map<String, String> abstractTags,
                          Map<String, String> translatedTags,
                          ProbeExposurePolicy exposurePolicy,
                          String renderedPrompt,
                          ProbeDispatchStatus dispatchStatus,
                          ProbeType probeType,
                          boolean mentioned,
                          String responseText,
                          int responseTokenCount,
                          String responseLengthBucket,
                          String detectionLevel,
                          String failureReason) {
            this(
                    probeId,
                    bucketType,
                    abstractTags,
                    translatedTags,
                    null,
                    null,
                    null,
                    exposurePolicy,
                    renderedPrompt,
                    null,
                    dispatchStatus,
                    probeType,
                    mentioned,
                    responseText,
                    responseText,
                    null,
                    List.of(),
                    0,
                    responseTokenCount,
                    responseTokenCount,
                    0L,
                    responseLengthBucket,
                    detectionLevel,
                    failureReason,
                    false,
                    false,
                    false,
                    List.of(),
                    0,
                    List.of(),
                    null,
                    null,
                    List.of(),
                    0,
                    1,
                    Map.of(),
                    null
            );
        }
    }

    public record DisplayNameMapping(
            String internalKey,
            String displayName
    ) {

        public DisplayNameMapping {
            internalKey = normalizeRequired(internalKey, "internalKey 不能为空");
            displayName = normalizeRequired(displayName, "displayName 不能为空");
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source, String message) {
        Objects.requireNonNull(source, message);
        LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), message);
            String value = normalizeRequired(entry.getValue(), message);
            sanitized.put(key, value);
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private static <T> List<T> immutableObjectList(List<T> source, String message) {
        Objects.requireNonNull(source, message);
        List<T> sanitized = new ArrayList<>(source.size());
        for (T item : source) {
            sanitized.add(Objects.requireNonNull(item, message));
        }
        return List.copyOf(sanitized);
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), "triggeredRulesJson 不能为空");
            sanitized.put(key, Objects.requireNonNull(entry.getValue(), "triggeredRulesJson 不能为空"));
        }
        return Collections.unmodifiableMap(sanitized);
    }
}

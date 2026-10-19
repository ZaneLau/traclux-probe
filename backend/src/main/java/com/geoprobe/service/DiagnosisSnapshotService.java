package com.geoprobe.service;

import com.geoprobe.ai.ModelCapability;
import com.geoprobe.ai.ModelCapabilityRegistry;
import com.geoprobe.common.DiagnosisEdition;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.service.shared.DiagnosisConfig;
import com.geoprobe.service.shared.DiagnosisMapSupport;
import com.geoprobe.service.shared.V42CreateDiagnosisPayloadSnapshotFactory;
import com.geoprobe.service.shared.V42SnapshotSanitizer;
import com.geoprobe.service.engine.v42.V42EngineManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagnosisSnapshotService {
    private static final String BILLING_STATE_RESERVED = "reserved";

    private final DiagnosisMapSupport diagnosisMapSupport;
    private final ModelCapabilityRegistry modelCapabilityRegistry;

    @Value("${app.engine.sampling.model-timeout-seconds:150}")
    private long samplingModelTimeoutSeconds = 150L;

    @Value("${app.engine.sampling.task-timeout-seconds:180}")
    private long samplingTaskTimeoutSeconds = 180L;

    @Value("${app.engine.sampling.model-max-completion-tokens:4096}")
    private int samplingMaxCompletionTokens = 4096;

    @Value("${app.engine.sampling.disable-reasoning:true}")
    private boolean samplingDisableReasoning = true;

    @Value("${app.engine.sampling.max-concurrency:8}")
    private int samplingMaxConcurrency = 8;

    @Value("${app.engine.sampling.max-attempts:2}")
    private int samplingMaxAttempts = 2;

    @Value("${app.engine.sampling.success-threshold-strict:0.8}")
    private double samplingStrictThreshold = 0.8d;

    @Value("${app.engine.sampling.success-threshold-floor:0.5}")
    private double samplingFloorThreshold = 0.5d;

    @Value("${app.engine.creative.step-timeout-seconds:0}")
    private long creativeStepTimeoutSeconds = 0L;

    @Value("${app.engine.extraction.model-timeout-seconds:90}")
    private long extractionModelTimeoutSeconds = 90L;

    @Value("${app.engine.alias.llm-timeout-seconds:12}")
    private long aliasLlmTimeoutSeconds = 12L;

    @Value("${llm.volcengine.model:doubao-seed-1.8}")
    private String samplingModel = "doubao-seed-1.8";

    @Value("${llm.volcengine.endpoint-id:}")
    private String samplingEndpointId;

    public Map<String, Object> buildInitialReportData(String requestId,
                                                      String brandName,
                                                      String category,
                                                      DiagnosisEdition edition,
                                                      DiagnosisConfig diagnosisConfig,
                                                      String engineVersion,
                                                      List<String> categoryAnchors,
                                                      boolean fallbackAnchorMode,
                                                      String categoryAnchorSource,
                                                      List<String> brandAliases,
                                                      Map<String, List<String>> competitorAliases,
                                                      FrontDeskTaskOrder taskOrder,
                                                      EngineTaskContextPackage contextPackage,
                                                      Map<String, Object> createDiagnosisPayloadSnapshot) {
        LocalDateTime snapshotTime = LocalDateTime.now();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("reportVersion", engineVersion);
        meta.put("engineVersion", engineVersion);
        meta.put("diagnosisConfigSnapshot", buildDiagnosisConfigSnapshot(
                edition,
                diagnosisConfig,
                engineVersion,
                categoryAnchors,
                fallbackAnchorMode,
                categoryAnchorSource,
                brandAliases,
                competitorAliases
        ));
        meta.put("engineComponents", V42EngineManifest.components());
        meta.put("requestId", requestId);
        meta.put("brandName", brandName);
        meta.put("category", category);
        meta.put("edition", edition.name());
        meta.put("snapshotAt", snapshotTime.toString());
        meta.put("categoryAnchors", categoryAnchors);
        meta.put("fallbackAnchorMode", fallbackAnchorMode);
        meta.put("categoryAnchorSource", categoryAnchorSource);
        meta.put("brandAliases", brandAliases);
        meta.put("competitorAliases", competitorAliases);
        meta.put("taskOrderSnapshot", diagnosisMapSupport.toSnapshotMap(taskOrder));
        meta.put("contextPackageSnapshot", diagnosisMapSupport.toSnapshotMap(contextPackage));
        meta.put("createDiagnosisPayloadSnapshot",
                createDiagnosisPayloadSnapshot == null ? Map.of() : Map.copyOf(createDiagnosisPayloadSnapshot));
        meta.put("aliasConfigVersion", "1");
        meta.put("generatedSamples", 0);
        meta.put("billingState", BILLING_STATE_RESERVED);
        meta.put("billingTimeline", List.of(Map.of("state", BILLING_STATE_RESERVED, "at", snapshotTime.toString())));

        Map<String, Object> reportSeed = new LinkedHashMap<>();
        reportSeed.put("meta", meta);
        return reportSeed;
    }

    public Map<String, Object> buildCreateDiagnosisPayloadSnapshot(String requestId,
                                                                   DiagnosisEdition edition,
                                                                   String engineVersion,
                                                                   FrontDeskTaskOrder taskOrder) {
        return buildCreateDiagnosisPayloadSnapshot(
                requestId,
                edition == null ? null : edition.name(),
                engineVersion,
                taskOrder == null ? Map.of() : diagnosisMapSupport.toSnapshotMap(taskOrder)
        );
    }

    public Map<String, Object> buildCanonicalCreateDiagnosisPayloadSnapshot(Map<String, Object> taskOrderSnapshot,
                                                                            Map<String, Object> storedPayloadSnapshot,
                                                                            Map<String, Object> meta) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty()) {
            return V42SnapshotSanitizer.sanitizeObjectMap(storedPayloadSnapshot);
        }
        Map<String, Object> payloadSnapshot = storedPayloadSnapshot == null ? Map.of() : V42SnapshotSanitizer.sanitizeObjectMap(storedPayloadSnapshot);
        String engineVersion = firstText(
                payloadSnapshot.get("engineVersion"),
                meta == null ? null : meta.get("engineVersion"),
                readTaskMetadataField(taskOrderSnapshot, "engineVersion")
        );
        String requestId = firstText(
                payloadSnapshot.get("requestId"),
                meta == null ? null : meta.get("requestId"),
                readTaskMetadataField(taskOrderSnapshot, "requestId")
        );
        String edition = firstText(
                payloadSnapshot.get("edition"),
                meta == null ? null : meta.get("edition"),
                readTaskMetadataField(taskOrderSnapshot, "edition")
        );
        return buildCreateDiagnosisPayloadSnapshot(requestId, edition, engineVersion, taskOrderSnapshot);
    }

    private Map<String, Object> buildCreateDiagnosisPayloadSnapshot(String requestId,
                                                                    String edition,
                                                                    String engineVersion,
                                                                    Map<String, Object> frontDeskTaskOrderSnapshot) {
        return V42CreateDiagnosisPayloadSnapshotFactory.build(
                requestId,
                edition,
                engineVersion,
                frontDeskTaskOrderSnapshot == null ? Map.of() : V42SnapshotSanitizer.sanitizeObjectMap(frontDeskTaskOrderSnapshot)
        );
    }

    private Map<String, Object> buildDiagnosisConfigSnapshot(DiagnosisEdition edition,
                                                             DiagnosisConfig diagnosisConfig,
                                                             String engineVersion,
                                                             List<String> categoryAnchors,
                                                             boolean fallbackAnchorMode,
                                                             String categoryAnchorSource,
                                                             List<String> brandAliases,
                                                             Map<String, List<String>> competitorAliases) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("engineVersion", engineVersion);
        snapshot.put("edition", edition.name());
        snapshot.put("totalCorePrompts", diagnosisConfig.getTotalCorePrompts());
        snapshot.put("minCorePromptsPerCluster", diagnosisConfig.getMinCorePromptsPerCluster());
        snapshot.put("variantsPerCorePrompt", diagnosisConfig.getVariantsPerCorePrompt());
        snapshot.put("samplingModelTimeoutSeconds", Math.max(30L, samplingModelTimeoutSeconds));
        snapshot.put("samplingTaskTimeoutSeconds", Math.max(1L, samplingTaskTimeoutSeconds));
        snapshot.put("samplingMaxCompletionTokens", Math.max(32, samplingMaxCompletionTokens));
        snapshot.put("samplingDisableReasoning", samplingDisableReasoning);
        snapshot.put("samplingMaxConcurrency", Math.max(1, samplingMaxConcurrency));
        snapshot.put("samplingMaxAttempts", Math.max(1, samplingMaxAttempts));
        snapshot.put("samplingStrictThreshold", round4(samplingStrictThreshold));
        snapshot.put("samplingFloorThreshold", round4(samplingFloorThreshold));
        snapshot.put("creativeStepTimeoutSeconds", Math.max(0L, creativeStepTimeoutSeconds));
        snapshot.put("extractionModelTimeoutSeconds", Math.max(30L, extractionModelTimeoutSeconds));
        snapshot.put("aliasLlmTimeoutSeconds", Math.max(1L, aliasLlmTimeoutSeconds));
        snapshot.put("samplingCapabilities", resolveSamplingCapabilities().toMap());
        snapshot.put("samplingModelRef", resolveSamplingModelRef());
        snapshot.put("engineComponents", V42EngineManifest.components());
        snapshot.put("categoryAnchors", categoryAnchors);
        snapshot.put("fallbackAnchorMode", fallbackAnchorMode);
        snapshot.put("categoryAnchorSource", categoryAnchorSource);
        snapshot.put("brandAliases", brandAliases);
        snapshot.put("competitorAliases", competitorAliases);
        return snapshot;
    }

    private ModelCapability resolveSamplingCapabilities() {
        return modelCapabilityRegistry.resolve("volcengine", resolveSamplingModelRef());
    }

    private String resolveSamplingModelRef() {
        if (StringUtils.hasText(samplingEndpointId)) {
            return samplingEndpointId.trim();
        }
        if (StringUtils.hasText(samplingModel)) {
            return samplingModel.trim();
        }
        return "doubao-seed-1.8";
    }

    private String asText(Object value) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readTaskMetadataField(Map<String, Object> taskOrderSnapshot, String key) {
        if (taskOrderSnapshot == null || taskOrderSnapshot.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        Object rawTaskMetadata = taskOrderSnapshot.get("taskMetadata");
        if (!(rawTaskMetadata instanceof Map<?, ?> taskMetadata)) {
            return null;
        }
        return ((Map<String, Object>) taskMetadata).get(key.trim());
    }

    private String firstText(Object... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (Object value : values) {
            String text = asText(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private double round4(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        return Math.round(value * 10000.0d) / 10000.0d;
    }
}

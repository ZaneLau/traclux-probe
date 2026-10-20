package com.geoprobe.service.shared;

import com.geoprobe.ai.IntentClusterAiService;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.dto.engine.CreativeExecutionContext;
import com.geoprobe.dto.engine.IntentCluster;
import com.geoprobe.dto.engine.IntentClusterResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClusterService {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final int IDR_K = 3;
    private static final int MAX_RETRIES = 3;
    private static final String DEFAULT_EDITION = "STANDARD";
    private static final int MAX_NON_DISCRIMINATING_KEYWORDS = 20;
    private final IntentClusterAiService intentClusterAiService;

    public IntentClusterResult generate(String diagnosisId, CreativeExecutionContext context) {
        return generate(diagnosisId, context, DiagnosisConfig.standardEdition(), DEFAULT_EDITION);
    }

    public IntentClusterResult generate(String diagnosisId,
                                        CreativeExecutionContext context,
                                        DiagnosisConfig config,
                                        String edition) {
        DiagnosisConfig safeConfig = config == null ? DiagnosisConfig.standardEdition() : config;
        String safeEdition = StringUtils.hasText(edition)
                ? edition.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_EDITION;

        FcbClassification classification = classify(diagnosisId, context);
        return generateFromClassification(classification, safeConfig, safeEdition);
    }

    public FcbClassification classify(String diagnosisId, CreativeExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("creative context 不能为空");
        }
        LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_CREATIVE, PROVIDER_DEEPSEEK));
        try {
            return retryWithBackoff(() -> inferClassification(context));
        } finally {
            LlmCallContext.clear();
        }
    }

    public IntentClusterResult generateFromClassification(FcbClassification classification,
                                                          DiagnosisConfig config,
                                                          String edition) {
        if (classification == null) {
            throw new IllegalArgumentException("fcbClassification 不能为空");
        }
        DiagnosisConfig safeConfig = config == null ? DiagnosisConfig.standardEdition() : config;
        String safeEdition = StringUtils.hasText(edition)
                ? edition.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_EDITION;
        List<IntentCluster> clusters = generateClusters(classification, safeConfig);
        log.info("[DIAG-CHECK] clusters.size={}, clusterIds={}",
                clusters.size(),
                clusters.stream().map(IntentCluster::clusterId).toList());
        for (IntentCluster cluster : clusters) {
            log.info("[DIAG-CHECK] cluster={} weight={} corePromptCount={}",
                    cluster.clusterId(),
                    cluster.weight(),
                    cluster.corePromptCount());
        }
        return new IntentClusterResult(
                clusters,
                classification,
                safeEdition,
                safeConfig.getTotalCorePrompts(),
                false,
                "LLM"
        );
    }

    private FcbClassification inferClassification(CreativeExecutionContext context) {
        String brandName = safeRequired(context.brandName(), "brandName 不能为空");
        FcbClassification raw = intentClusterAiService.inferClassification(
                brandName,
                safeOptional(context.brandWebsite()),
                safeOptional(context.userPrompt())
        );
        if (raw == null) {
            throw new IllegalStateException("FCB 分类输出为空");
        }
        return normalizeClassification(raw, brandName);
    }

    private String safeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String safeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private FcbClassification normalizeClassification(FcbClassification raw, String brandName) {
        String category = normalizeCategory(raw.getCategory(), brandName);
        String involvement = normalizeInvolvement(raw.getInvolvement());

        boolean parameterValid = normalizePreferenceValid(raw.getParameterValid(), "parameter_valid");
        boolean experienceValid = normalizePreferenceValid(raw.getExperienceValid(), "experience_valid");
        boolean trustValid = normalizePreferenceValid(raw.getTrustValid(), "trust_valid");

        List<String> attributes = normalizeStringList(raw.getCategoryAttributes());
        if (attributes.isEmpty()) {
            attributes = List.of("（品类属性待补充）");
            log.info("[DIAG-CHECK] fcb.categoryAttributes.fallback");
        }

        List<String> competitors = normalizeStringList(raw.getCompetitors());
        if (competitors.isEmpty()) {
            competitors = List.of("竞品A", "竞品B", "竞品C");
        }
        List<String> nonDiscriminatingKeywords = normalizeNonDiscriminatingKeywords(raw.getNonDiscriminatingKeywords());
        if (nonDiscriminatingKeywords.isEmpty()) {
            log.info("[DIAG-CHECK] fcb.nonDiscriminatingKeywords.empty");
        }

        return FcbClassification.builder()
                .category(category)
                .involvement(involvement)
                .parameterValid(parameterValid)
                .experienceValid(experienceValid)
                .trustValid(trustValid)
                .categoryAttributes(attributes)
                .competitors(competitors)
                .nonDiscriminatingKeywords(nonDiscriminatingKeywords)
                .build();
    }

    private String normalizeCategory(String rawCategory, String brandName) {
        if (StringUtils.hasText(rawCategory)) {
            return rawCategory.trim();
        }
        log.info("[DIAG-CHECK] fcb.category.fallback={}", brandName);
        return brandName;
    }

    private String normalizeInvolvement(String involvement) {
        if (!StringUtils.hasText(involvement)) {
            log.info("[DIAG-CHECK] fcb.involvement.fallback=HIGH (raw={})", involvement);
            return "HIGH";
        }
        String normalized = involvement.trim().toUpperCase(Locale.ROOT);
        if ("LOW".equals(normalized) || "HIGH".equals(normalized)) {
            return normalized;
        }
        log.info("[DIAG-CHECK] fcb.involvement.fallback=HIGH (raw={})", involvement);
        return "HIGH";
    }

    private boolean normalizePreferenceValid(Object rawValue, String fieldName) {
        if (rawValue instanceof Boolean boolValue) {
            return boolValue;
        }
        log.info("[DIAG-CHECK] fcb.{}.fallback=true (raw={})", fieldName, rawValue);
        return true;
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<IntentCluster> generateClusters(FcbClassification classification, DiagnosisConfig config) {
        List<IntentCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, FcbGeoFramework.ClusterSkeleton> entry : FcbGeoFramework.CLUSTER_SKELETONS.entrySet()) {
            String clusterId = entry.getKey();
            FcbGeoFramework.ClusterSkeleton skeleton = entry.getValue();
            if (!isPreferenceValid(classification, skeleton.eeatDimension())) {
                continue;
            }
            clusters.add(buildCluster(clusterId, skeleton, classification.getCategory()));
        }

        if (clusters.isEmpty()) {
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "意图簇生成未达到生产标准: 没有有效偏好维度");
        }

        List<IntentCluster> weighted = applyIdrWeights(clusters);
        return allocateCorePrompts(weighted, config);
    }

    /**
     * 仅按 preferenceValid 过滤，不按 involvement 过滤。
     */
    private boolean isPreferenceValid(FcbClassification classification, String preference) {
        return switch (preference) {
            case "PARAMETER" -> classification.isParameterValid();
            case "EXPERIENCE" -> classification.isExperienceValid();
            case "TRUST" -> classification.isTrustValid();
            default -> false;
        };
    }

    private IntentCluster buildCluster(String clusterId,
                                       FcbGeoFramework.ClusterSkeleton skeleton,
                                       String category) {
        return new IntentCluster(
                clusterId,
                skeleton.clusterName(),
                skeleton.description(),
                buildSeedQuestions(category, skeleton.clusterName()),
                skeleton.eeatDimension(),
                skeleton.involvementLevel(),
                0.0d,
                0
        );
    }

    private List<String> buildSeedQuestions(String category, String clusterName) {
        String safeCategory = StringUtils.hasText(category) ? category.trim() : "该品类产品";
        return List.of(
                safeCategory + " 在「" + clusterName + "」角度该怎么判断？",
                "如果重点看「" + clusterName + "」，" + safeCategory + " 应该先比较什么？",
                "围绕「" + clusterName + "」，请给我 " + safeCategory + " 的决策建议"
        );
    }

    private List<String> normalizeNonDiscriminatingKeywords(List<String> rawKeywords) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (rawKeywords != null) {
            for (String keyword : rawKeywords) {
                if (!StringUtils.hasText(keyword)) {
                    continue;
                }
                normalized.add(keyword.trim());
            }
        }

        List<String> result = new ArrayList<>(normalized);
        if (result.size() > MAX_NON_DISCRIMINATING_KEYWORDS) {
            return result.subList(0, MAX_NON_DISCRIMINATING_KEYWORDS);
        }
        return result;
    }

    /**
     * IDR 权重公式（固定 HIGH:LOW = k:1，k=3）：
     * HIGH 簇相关度 = k
     * LOW  簇相关度 = 1
     */
    private List<IntentCluster> applyIdrWeights(List<IntentCluster> clusters) {
        List<Double> relevances = new ArrayList<>(clusters.size());
        for (IntentCluster cluster : clusters) {
            double relevance = "HIGH".equalsIgnoreCase(cluster.involvementLevel()) ? IDR_K : 1.0d;
            relevances.add(relevance);
        }

        double totalRelevance = relevances.stream().mapToDouble(Double::doubleValue).sum();
        if (totalRelevance <= 0) {
            throw new IllegalStateException("IDR 权重计算失败：totalRelevance<=0");
        }

        List<IntentCluster> weighted = new ArrayList<>(clusters.size());
        for (int i = 0; i < clusters.size(); i++) {
            IntentCluster cluster = clusters.get(i);
            double weight = relevances.get(i) / totalRelevance;
            weighted.add(new IntentCluster(
                    cluster.clusterId(),
                    cluster.clusterName(),
                    cluster.description(),
                    cluster.questions(),
                    cluster.eeatDimension(),
                    cluster.involvementLevel(),
                    weight,
                    cluster.corePromptCount()
            ));
        }
        return weighted;
    }

    private List<IntentCluster> allocateCorePrompts(List<IntentCluster> clusters, DiagnosisConfig config) {
        int total = config.getTotalCorePrompts();
        int min = config.getMinCorePromptsPerCluster();

        List<IntentCluster> working = new ArrayList<>(clusters);
        if (working.size() * min > total) {
            working.sort(Comparator.comparingDouble(IntentCluster::weight));
            while (working.size() * min > total) {
                working.remove(0);
            }
            working = recalculateWeights(working);
        }

        List<IntentCluster> allocated = working.stream()
                .map(cluster -> new IntentCluster(
                        cluster.clusterId(),
                        cluster.clusterName(),
                        cluster.description(),
                        cluster.questions(),
                        cluster.eeatDimension(),
                        cluster.involvementLevel(),
                        cluster.weight(),
                        min
                ))
                .toList();

        int remaining = Math.max(0, total - allocated.size() * min);
        List<IntentCluster> mutable = new ArrayList<>(allocated);
        List<Integer> highIndexes = new ArrayList<>();
        for (int i = 0; i < mutable.size(); i++) {
            if ("HIGH".equalsIgnoreCase(mutable.get(i).involvementLevel())) {
                highIndexes.add(i);
            }
        }
        highIndexes.sort((left, right) -> {
            IntentCluster a = mutable.get(left);
            IntentCluster b = mutable.get(right);
            int weightCompare = Double.compare(b.weight(), a.weight());
            if (weightCompare != 0) {
                return weightCompare;
            }
            return String.valueOf(a.clusterId()).compareTo(String.valueOf(b.clusterId()));
        });

        if (!highIndexes.isEmpty()) {
            int cursor = 0;
            while (remaining > 0) {
                int idx = highIndexes.get(cursor % highIndexes.size());
                IntentCluster cluster = mutable.get(idx);
                mutable.set(idx, new IntentCluster(
                        cluster.clusterId(),
                        cluster.clusterName(),
                        cluster.description(),
                        cluster.questions(),
                        cluster.eeatDimension(),
                        cluster.involvementLevel(),
                        cluster.weight(),
                        cluster.corePromptCount() + 1
                ));
                remaining--;
                cursor++;
            }
        }

        int sum = mutable.stream().mapToInt(IntentCluster::corePromptCount).sum();
        if (sum != total) {
            throw new IllegalStateException("核心Prompt分配尾差校验失败: expect=" + total + ", actual=" + sum);
        }

        return mutable.stream()
                .sorted(Comparator.comparing(IntentCluster::clusterId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<IntentCluster> recalculateWeights(List<IntentCluster> clusters) {
        List<IntentCluster> stripped = clusters.stream()
                .map(cluster -> new IntentCluster(
                        cluster.clusterId(),
                        cluster.clusterName(),
                        cluster.description(),
                        cluster.questions(),
                        cluster.eeatDimension(),
                        cluster.involvementLevel(),
                        0.0d,
                        cluster.corePromptCount()
                ))
                .toList();
        return applyIdrWeights(stripped);
    }

    private <T> T retryWithBackoff(Supplier<T> task) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return task.get();
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt == MAX_RETRIES) {
                    break;
                }
                sleepWithBackoff(attempt);
            }
        }

        String message = lastException != null ? lastException.getMessage() : "未知错误";
        throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "意图簇生成失败: " + message);
    }

    private void sleepWithBackoff(int attempt) {
        long sleepMillis = (long) Math.pow(2, attempt - 1) * 1000L;
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("IntentClusterService retry sleep interrupted");
        }
    }

}

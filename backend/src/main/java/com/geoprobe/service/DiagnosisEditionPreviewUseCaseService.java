package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.DiagnosisEdition;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.CreativeExecutionContext;
import com.geoprobe.dto.engine.IntentCluster;
import com.geoprobe.dto.engine.IntentClusterResult;
import com.geoprobe.dto.request.PreviewDiagnosisEditionsRequest;
import com.geoprobe.dto.response.DiagnosisEditionPreviewResponse;
import com.geoprobe.service.shared.DiagnosisConfig;
import com.geoprobe.service.shared.DiagnosisFieldNormalizer;
import com.geoprobe.service.shared.FcbClassification;
import com.geoprobe.service.shared.IntentClusterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiagnosisEditionPreviewUseCaseService {

    private static final int MAX_CATEGORY_LEN = 200;
    private static final int MAX_BRAND_WEBSITE_LEN = 500;
    private static final int MAX_USER_PROMPT_LEN = 1000;
    private static final int MAX_COMPETITOR_SIZE = 10;
    private static final int CREDIT_COST_PER_DIAGNOSIS = 1;

    private final IntentClusterService intentClusterService;
    private final DiagnosisInferenceService diagnosisInferenceService;

    public DiagnosisEditionPreviewResponse previewEditions(String userId, PreviewDiagnosisEditionsRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
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

        CreativeContextResult inferred = null;
        if (!StringUtils.hasText(overrideCategory) || overrideCompetitors.isEmpty()) {
            inferred = diagnosisInferenceService.inferAndValidate(
                    brandName,
                    brandWebsite,
                    userPrompt,
                    "preview_editions"
            );
        }

        String finalCategory = StringUtils.hasText(overrideCategory)
                ? overrideCategory
                : inferred == null ? null : inferred.category();
        List<String> finalCompetitors = !overrideCompetitors.isEmpty()
                ? overrideCompetitors
                : inferred == null || inferred.competitors() == null ? List.of() : List.copyOf(inferred.competitors());

        if (!StringUtils.hasText(finalCategory)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品类不能为空");
        }
        if (finalCompetitors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "竞品列表不能为空");
        }

        CreativeExecutionContext context = new CreativeExecutionContext(
                brandName,
                finalCategory,
                finalCompetitors,
                brandWebsite,
                userPrompt
        );
        FcbClassification classification = intentClusterService.classify("preview-" + UUID.randomUUID(), context);

        List<DiagnosisEditionPreviewResponse.EditionPreview> editions = List.of(
                buildEditionPreview(DiagnosisEdition.EXPERIENCE, classification),
                buildEditionPreview(DiagnosisEdition.STANDARD, classification),
                buildEditionPreview(DiagnosisEdition.DEEP, classification)
        );

        return DiagnosisEditionPreviewResponse.builder()
                .editions(editions)
                .build();
    }

    private DiagnosisEditionPreviewResponse.EditionPreview buildEditionPreview(DiagnosisEdition edition,
                                                                              FcbClassification classification) {
        DiagnosisConfig config = edition.toConfig();
        IntentClusterResult clusterResult = intentClusterService.generateFromClassification(
                classification,
                config,
                edition.name()
        );

        List<DiagnosisEditionPreviewResponse.ClusterPreview> clusterPreview = clusterResult.clusters().stream()
                .sorted((left, right) -> {
                    int promptDiff = Integer.compare(right.corePromptCount(), left.corePromptCount());
                    if (promptDiff != 0) {
                        return promptDiff;
                    }
                    String leftName = left.clusterName() == null ? "" : left.clusterName();
                    String rightName = right.clusterName() == null ? "" : right.clusterName();
                    return leftName.compareToIgnoreCase(rightName);
                })
                .map(this::toClusterPreview)
                .toList();

        return DiagnosisEditionPreviewResponse.EditionPreview.builder()
                .edition(edition.name())
                .tokenCost(CREDIT_COST_PER_DIAGNOSIS)
                .sampleTarget(0)
                .corePromptCount(config.getTotalCorePrompts())
                .variationPerPrompt(0)
                .clusterPreview(clusterPreview)
                .source("LIVE")
                .build();
    }

    private DiagnosisEditionPreviewResponse.ClusterPreview toClusterPreview(IntentCluster cluster) {
        return DiagnosisEditionPreviewResponse.ClusterPreview.builder()
                .clusterId(cluster.clusterId())
                .clusterName(cluster.clusterName())
                .corePromptCount(cluster.corePromptCount())
                .involvementLevel(cluster.involvementLevel())
                .eeatDimension(cluster.eeatDimension())
                .build();
    }
}

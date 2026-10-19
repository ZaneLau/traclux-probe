package com.geoprobe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.request.InferContextRequest;
import com.geoprobe.dto.response.InferContextResponse;
import com.geoprobe.service.shared.CreativeContextService;
import com.geoprobe.service.shared.DiagnosisFieldNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisInferenceService {

    private static final int MAX_CATEGORY_LEN = 200;
    private static final int MAX_BRAND_WEBSITE_LEN = 500;
    private static final int MAX_USER_PROMPT_LEN = 1000;
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);
    private static final String INFERENCE_SOURCE_LLM = "LLM";

    private final CreativeContextService creativeContextService;
    private final RedisSupportService redisSupportService;

    public InferContextResponse inferContext(String userId, InferContextRequest request) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请求参数错误");
        }
        String brandName = DiagnosisFieldNormalizer.normalizeRequired(request.getBrandName(), "品牌名不能为空", MAX_CATEGORY_LEN);
        String brandWebsite = DiagnosisFieldNormalizer.normalizeOptional(request.getBrandWebsite(), MAX_BRAND_WEBSITE_LEN);
        String userPrompt = DiagnosisFieldNormalizer.normalizeOptional(request.getUserPrompt(), MAX_USER_PROMPT_LEN);

        CreativeContextResult inferred = inferAndValidate(
                brandName,
                brandWebsite,
                userPrompt,
                "infer_context"
        );
        String contextId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        cacheInferContext(contextId, new InferContextCachePayload(
                userId,
                inferred.brandName(),
                brandWebsite,
                userPrompt,
                inferred.category(),
                inferred.competitors(),
                inferred.negativeTerms(),
                inferred.source(),
                now
        ));

        return InferContextResponse.builder()
                .contextId(contextId)
                .brandName(inferred.brandName())
                .inferredCategory(inferred.category())
                .inferredCompetitors(inferred.competitors())
                .inferredNegativeTerms(inferred.negativeTerms())
                .source(inferred.source())
                .expiresAt(now.plusMinutes(CONTEXT_TTL.toMinutes()))
                .build();
    }

    public CreativeContextResult resolveInferredContext(String userId,
                                                        String contextId,
                                                        String brandName) {
        if (!StringUtils.hasText(contextId)) {
            return null;
        }
        InferContextCachePayload cached = readCachedInferContext(contextId.trim());
        if (cached == null) {
            return null;
        }
        if (!Objects.equals(userId, cached.userId())) {
            return null;
        }
        if (!brandName.equalsIgnoreCase(cached.brandName())) {
            return null;
        }
        return new CreativeContextResult(
                cached.brandName(),
                cached.inferredCategory(),
                cached.inferredCompetitors(),
                cached.inferredNegativeTerms(),
                null,
                cached.inferredSource()
        );
    }

    public CreativeContextResult inferAndValidate(String brandName,
                                                  String brandWebsite,
                                                  String userPrompt,
                                                  String scene) {
        CreativeContextResult inferred = inferWhenPossible(brandName, brandWebsite, userPrompt);
        return enforceProductionInference(inferred, scene);
    }

    public CreativeContextResult inferForDrafting(String brandName,
                                                  String brandWebsite,
                                                  String userPrompt) {
        return inferWhenPossible(brandName, brandWebsite, userPrompt);
    }

    public List<String> fallbackNegativeTerms() {
        return creativeContextService.fallbackNegativeTerms();
    }

    private CreativeContextResult inferWhenPossible(String brandName,
                                                    String brandWebsite,
                                                    String userPrompt) {
        try {
            return creativeContextService.infer(brandName, brandWebsite, userPrompt);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                throw new BusinessException(ErrorCode.LLM_TIMEOUT, "上下文推断超时: " + message);
            }
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断失败: " + message);
        }
    }

    private CreativeContextResult enforceProductionInference(CreativeContextResult inferred, String scene) {
        if (inferred == null) {
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断失败: 未返回有效结果");
        }
        if (!StringUtils.hasText(inferred.category())) {
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断失败: 未返回有效品类");
        }
        if (inferred.competitors() == null || inferred.competitors().isEmpty()) {
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断失败: 未返回有效竞品");
        }
        String source = DiagnosisFieldNormalizer.normalizeOptional(inferred.source(), 32);
        if (!INFERENCE_SOURCE_LLM.equalsIgnoreCase(source)) {
            log.warn("Blocked non-LLM inference result in scene={}, source={}", scene, source);
            throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断未达到生产发布标准，请稍后重试");
        }
        return inferred;
    }

    private void cacheInferContext(String contextId, InferContextCachePayload payload) {
        if (!StringUtils.hasText(contextId) || payload == null) {
            return;
        }
        redisSupportService.setJson(inferContextKey(contextId), payload, CONTEXT_TTL, "infer-context cache");
    }

    private InferContextCachePayload readCachedInferContext(String contextId) {
        if (!StringUtils.hasText(contextId)) {
            return null;
        }
        return redisSupportService.getJson(
                inferContextKey(contextId),
                new TypeReference<InferContextCachePayload>() {
                },
                "infer-context cache"
        );
    }

    private String inferContextKey(String contextId) {
        return "diagnosis:context:" + contextId;
    }

    private record InferContextCachePayload(String userId,
                                            String brandName,
                                            String brandWebsite,
                                            String userPrompt,
                                            String inferredCategory,
                                            List<String> inferredCompetitors,
                                            List<String> inferredNegativeTerms,
                                            String inferredSource,
                                            LocalDateTime createdAt) {
    }
}

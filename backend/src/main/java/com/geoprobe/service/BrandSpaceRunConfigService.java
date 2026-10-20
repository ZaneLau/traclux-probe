package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrandSpaceRunConfigService {

    private static final String RUN_CONFIG_KEY = "brandSpaceRunConfig";
    private static final String TARGET_PROBE_COUNT_KEY = "targetProbeCount";
    private static final String TARGET_MODEL_PRODUCT_KEY = "targetModelProduct";
    private static final String TRACKING_MODE_ACTIVE = "ACTIVE";
    private static final String TRACKING_MODE_PAUSED = "PAUSED";
    private static final String DEFAULT_TARGET_MODEL_PRODUCT = "doubao";
    private static final int DEFAULT_TARGET_PROBE_COUNT = 12;
    private static final int MAX_TARGET_PROBE_COUNT = 100;

    private final BrandScopeMapper brandScopeMapper;

    public CurrentRunConfig loadCurrentRunConfig(String brandScopeId, Integer fallbackTargetProbeCount) {
        if (!StringUtils.hasText(brandScopeId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        BrandScope scope = brandScopeMapper.selectById(brandScopeId.trim());
        if (scope == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }
        return resolveCurrentRunConfig(scope, fallbackTargetProbeCount);
    }

    public CurrentRunConfig resolveCurrentRunConfig(BrandScope scope, Integer fallbackTargetProbeCount) {
        if (scope == null) {
            return new CurrentRunConfig(
                    normalizeTargetProbeCount(fallbackTargetProbeCount),
                    DEFAULT_TARGET_MODEL_PRODUCT,
                    true
            );
        }

        Map<String, Object> runConfig = readRunConfigMap(scope.getDefaultModelPolicyJson());
        Integer storedTargetProbeCount = readInteger(runConfig.get(TARGET_PROBE_COUNT_KEY));
        String storedTargetModelProduct = normalizeText(runConfig.get(TARGET_MODEL_PRODUCT_KEY));

        int targetProbeCount = normalizeTargetProbeCount(
                storedTargetProbeCount != null ? storedTargetProbeCount : fallbackTargetProbeCount
        );
        String targetModelProduct = StringUtils.hasText(storedTargetModelProduct)
                ? storedTargetModelProduct
                : DEFAULT_TARGET_MODEL_PRODUCT;
        boolean autoRunEnabled = !TRACKING_MODE_PAUSED.equalsIgnoreCase(String.valueOf(scope.getTrackingMode()));

        return new CurrentRunConfig(targetProbeCount, targetModelProduct, autoRunEnabled);
    }

    public CurrentRunConfig saveCurrentRunConfig(String brandScopeId,
                                                 Integer targetProbeCount,
                                                 String targetModelProduct,
                                                 Boolean autoRunEnabled,
                                                 Integer fallbackTargetProbeCount) {
        if (!StringUtils.hasText(brandScopeId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        BrandScope scope = brandScopeMapper.selectById(brandScopeId.trim());
        if (scope == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "品牌空间不存在");
        }

        CurrentRunConfig current = resolveCurrentRunConfig(scope, fallbackTargetProbeCount);
        int resolvedTargetProbeCount = normalizeTargetProbeCount(
                targetProbeCount != null ? targetProbeCount : current.targetProbeCount()
        );
        String resolvedTargetModelProduct = normalizeModelProduct(
                StringUtils.hasText(targetModelProduct) ? targetModelProduct : current.targetModelProduct()
        );

        Map<String, Object> policy = mutablePolicy(scope.getDefaultModelPolicyJson());
        LinkedHashMap<String, Object> runConfig = new LinkedHashMap<>(readRunConfigMap(policy));
        runConfig.put(TARGET_PROBE_COUNT_KEY, resolvedTargetProbeCount);
        runConfig.put(TARGET_MODEL_PRODUCT_KEY, resolvedTargetModelProduct);
        policy.put(RUN_CONFIG_KEY, runConfig);
        scope.setDefaultModelPolicyJson(policy);
        if (autoRunEnabled != null) {
            scope.setTrackingMode(autoRunEnabled ? TRACKING_MODE_ACTIVE : TRACKING_MODE_PAUSED);
        } else if (!StringUtils.hasText(scope.getTrackingMode())) {
            scope.setTrackingMode(current.autoRunEnabled() ? TRACKING_MODE_ACTIVE : TRACKING_MODE_PAUSED);
        }
        brandScopeMapper.updateById(scope);

        return resolveCurrentRunConfig(scope, resolvedTargetProbeCount);
    }

    private int normalizeTargetProbeCount(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_TARGET_PROBE_COUNT;
        }
        return Math.max(1, Math.min(value, MAX_TARGET_PROBE_COUNT));
    }

    private String normalizeModelProduct(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_TARGET_MODEL_PRODUCT;
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readRunConfigMap(Map<String, Object> policy) {
        if (policy == null || policy.isEmpty()) {
            return Map.of();
        }
        Object raw = policy.get(RUN_CONFIG_KEY);
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private Map<String, Object> mutablePolicy(Map<String, Object> policy) {
        return policy == null ? new LinkedHashMap<>() : new LinkedHashMap<>(policy);
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

    public record CurrentRunConfig(int targetProbeCount,
                                   String targetModelProduct,
                                   boolean autoRunEnabled) {
    }
}

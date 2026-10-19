package com.geoprobe.service.shared;

import com.geoprobe.ai.CreativeContextAiService;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.dto.engine.CreativeContextAiResult;
import com.geoprobe.dto.engine.CreativeContextResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreativeContextService {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final int MAX_RETRIES = 3;
    private static final int MIN_COMPETITOR_COUNT = 2;
    private static final int MAX_COMPETITOR_COUNT = 5;
    private static final int MIN_NEGATIVE_TERMS = 10;
    private static final int MAX_NEGATIVE_TERMS = 20;
    private static final List<HeuristicProfile> HEURISTIC_PROFILES = List.of(
            new HeuristicProfile(
                    "休闲食品",
                    List.of("辣条", "零食", "休闲食品", "膨化", "卤味", "饮料", "食品", "零嘴"),
                    List.of("麻辣王子", "良品铺子", "三只松鼠", "百草味"),
                    List.of("配料争议", "热量负担", "口味偏重", "价格对比不清")
            ),
            new HeuristicProfile(
                    "家居五金",
                    List.of("花洒", "卫浴", "五金", "浴室", "龙头", "家居五金", "厨卫", "门锁"),
                    List.of("九牧", "箭牌", "恒洁", "四季沐歌"),
                    List.of("安装门槛高", "耐用度存疑", "低水压适配不清", "售后响应慢")
            ),
            new HeuristicProfile(
                    "运动相机",
                    List.of("gopro", "insta360", "action camera", "运动相机", "相机", "影像", "dji action", "akaso"),
                    List.of("Insta360", "DJI Action", "AKASO", "GoPro"),
                    List.of("画质发虚", "防抖一般", "续航偏短", "发热明显", "价格偏高")
            ),
            new HeuristicProfile(
                    "影像配件",
                    List.of("peak design", "相机包", "摄影包", "camera bag", "tripod", "三脚架", "背包", "相机配件"),
                    List.of("Manfrotto", "PGYTECH", "Lowepro", "Peak Design"),
                    List.of("配件兼容性一般", "收纳动线不顺", "价格偏高", "缺少高频场景展示")
            ),
            new HeuristicProfile(
                    "消费电子",
                    List.of("耳机", "音箱", "智能手表", "手机", "笔记本", "consumer electronics", "smart device"),
                    List.of("Apple", "Sony", "华为", "小米"),
                    List.of("配置对比不清", "价格敏感", "售后顾虑", "参数卖点重复")
            ),
            new HeuristicProfile(
                    "企业软件",
                    List.of("saas", "crm", "协作", "workflow", "automation", "企业软件", "project management"),
                    List.of("Salesforce", "HubSpot", "Notion", "Monday.com"),
                    List.of("上手门槛高", "实施周期长", "价值表达偏抽象", "集成成本高")
            ),
            new HeuristicProfile(
                    "美妆个护",
                    List.of("护肤", "彩妆", "精华", "beauty", "skincare", "cosmetic"),
                    List.of("雅诗兰黛", "兰蔻", "欧莱雅", "珀莱雅"),
                    List.of("功效不明确", "成分争议", "肤质适配不清", "价格偏高")
            )
    );

    private final CreativeContextAiService creativeContextAiService;

    public CreativeContextResult infer(String brandName, String brandWebsite, String userPrompt) {
        String normalizedBrandName = normalizeRequired(brandName, "品牌名不能为空", 200);

        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            LlmCallContext.set(new LlmCallContext(null, MODULE_CREATIVE, PROVIDER_DEEPSEEK));
            try {
                CreativeContextAiResult raw = creativeContextAiService.inferContext(
                        normalizedBrandName,
                        safeOptional(brandWebsite),
                        safeOptional(userPrompt)
                );
                return normalize(raw, normalizedBrandName);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES) {
                    sleepWithBackoff(attempt);
                }
            } finally {
                LlmCallContext.clear();
            }
        }

        String message = lastException != null ? lastException.getMessage() : "未知错误";
        log.warn("CreativeContextService inference failed after retries. brand={}, reason={}", normalizedBrandName, message);
        throw new BusinessException(ErrorCode.LLM_CALL_FAILED, "上下文推断未达到生产标准: " + message);
    }

    public List<String> fallbackNegativeTerms() {
        return EngineDefaults.NEGATIVE_TERMS;
    }

    private CreativeContextResult normalize(CreativeContextAiResult raw, String brandName) {
        if (raw == null) {
            throw new IllegalStateException("上下文推断输出为空");
        }

        String category = normalizeRequired(raw.category(), "推断品类为空", 200);
        List<String> competitors = normalizeCompetitors(raw.competitors(), brandName);
        List<String> negativeTerms = normalizeNegativeTerms(raw.negativeTerms());
        String officialWebsite = normalizeOfficialWebsite(raw.officialWebsite());

        return new CreativeContextResult(
                brandName,
                category,
                competitors,
                negativeTerms,
                officialWebsite,
                "LLM"
        );
    }

    private List<String> normalizeCompetitors(List<String> rawCompetitors, String brandName) {
        Set<String> deduplicated = new LinkedHashSet<>();
        if (rawCompetitors != null) {
            for (String item : rawCompetitors) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                String value = item.trim();
                if (value.equalsIgnoreCase(brandName)) {
                    continue;
                }
                deduplicated.add(value);
            }
        }

        if (deduplicated.size() < MIN_COMPETITOR_COUNT) {
            throw new IllegalStateException("竞品数量不足");
        }

        List<String> normalized = new ArrayList<>(deduplicated);
        if (normalized.size() > MAX_COMPETITOR_COUNT) {
            return normalized.subList(0, MAX_COMPETITOR_COUNT);
        }
        return normalized;
    }

    private List<String> normalizeNegativeTerms(List<String> rawNegativeTerms) {
        Set<String> deduplicated = new LinkedHashSet<>();
        if (rawNegativeTerms != null) {
            for (String item : rawNegativeTerms) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                deduplicated.add(item.trim());
            }
        }

        for (String fallback : EngineDefaults.NEGATIVE_TERMS) {
            if (deduplicated.size() >= MIN_NEGATIVE_TERMS) {
                break;
            }
            deduplicated.add(fallback);
        }

        List<String> normalized = new ArrayList<>(deduplicated);
        if (normalized.size() > MAX_NEGATIVE_TERMS) {
            return normalized.subList(0, MAX_NEGATIVE_TERMS);
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message, int maxLen) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLen) {
            return trimmed.substring(0, maxLen);
        }
        return trimmed;
    }

    private String safeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private CreativeContextResult fallbackContext(String brandName, String brandWebsite, String userPrompt) {
        String combinedSignal = String.join(" ",
                brandName,
                safeOptional(brandWebsite),
                safeOptional(userPrompt)
        ).toLowerCase(Locale.ROOT);

        for (HeuristicProfile profile : HEURISTIC_PROFILES) {
            if (profile.matches(combinedSignal)) {
                return buildHeuristicResult(brandName, profile);
            }
        }

        return new CreativeContextResult(
                brandName,
                "消费品牌",
                normalizeFallbackCompetitors(List.of("竞品 A", "竞品 B", "竞品 C"), brandName),
                normalizeNegativeTerms(List.of("定位不清", "价值表达偏弱", "推荐理由不足")),
                normalizeOfficialWebsite(brandWebsite),
                "HEURISTIC"
        );
    }

    private CreativeContextResult buildHeuristicResult(String brandName, HeuristicProfile profile) {
        return new CreativeContextResult(
                brandName,
                profile.category(),
                normalizeFallbackCompetitors(profile.competitors(), brandName),
                normalizeNegativeTerms(profile.negativeTerms()),
                null,
                "HEURISTIC"
        );
    }

    private String normalizeOfficialWebsite(String rawOfficialWebsite) {
        if (!StringUtils.hasText(rawOfficialWebsite)) {
            return null;
        }
        String trimmed = rawOfficialWebsite.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null;
        }
        return trimmed;
    }

    private List<String> normalizeFallbackCompetitors(List<String> rawCompetitors, String brandName) {
        List<String> normalized = new ArrayList<>();
        for (String item : rawCompetitors) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String trimmed = item.trim();
            if (trimmed.equalsIgnoreCase(brandName)) {
                continue;
            }
            normalized.add(trimmed);
        }

        if (normalized.size() < MIN_COMPETITOR_COUNT) {
            normalized.add("竞品 A");
            normalized.add("竞品 B");
        }

        if (normalized.size() > MAX_COMPETITOR_COUNT) {
            return normalized.subList(0, MAX_COMPETITOR_COUNT);
        }
        return normalized;
    }

    private void sleepWithBackoff(int attempt) {
        long sleepMillis = (long) Math.pow(2, Math.max(0, attempt - 1)) * 1000L;
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.warn("CreativeContextService retry sleep interrupted");
        }
    }

    private record HeuristicProfile(
            String category,
            List<String> keywords,
            List<String> competitors,
            List<String> negativeTerms
    ) {
        private boolean matches(String combinedSignal) {
            return keywords.stream().anyMatch(combinedSignal::contains);
        }
    }
}

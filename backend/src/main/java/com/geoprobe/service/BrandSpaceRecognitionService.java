package com.geoprobe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.dto.engine.CreativeContextResult;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.Node3ProbeInput;
import com.geoprobe.dto.engine.v42.Node3SearchGroundingContext;
import com.geoprobe.dto.engine.v42.ProbeExposurePolicy;
import com.geoprobe.dto.engine.v42.RawResponse;
import com.geoprobe.service.engine.v42.ModelAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class BrandSpaceRecognitionService {

    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("(?s)\\{.*\\}");
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_COMPETITOR_COUNT = 10;
    private static final int MAX_DIRECT_COMPETITOR_COUNT = 5;
    private static final int MAX_INDIRECT_COMPETITOR_COUNT = 5;
    private static final int STRICT_CJK_BRAND_MIN_LENGTH = 3;

    private final ModelAdapter doubaoAdapter;
    private final DiagnosisInferenceService diagnosisInferenceService;
    private final ObjectMapper objectMapper;
    private long realtimeTimeoutSeconds = 75L;

    public BrandSpaceRecognitionService(@Qualifier("v42DoubaoCoreAdapter") ModelAdapter doubaoAdapter,
                                        DiagnosisInferenceService diagnosisInferenceService,
                                        ObjectMapper objectMapper) {
        this.doubaoAdapter = doubaoAdapter;
        this.diagnosisInferenceService = diagnosisInferenceService;
        this.objectMapper = objectMapper;
    }

    @Value("${app.brand-space.recognition.realtime-timeout-seconds:75}")
    void setRealtimeTimeoutSeconds(long realtimeTimeoutSeconds) {
        this.realtimeTimeoutSeconds = Math.max(1L, realtimeTimeoutSeconds);
    }

    public BrandSpaceRecognitionView recognize(BrandSpaceDraftPayload draft) {
        Objects.requireNonNull(draft, "draft 不能为空");
        String realtimeFailureReason = null;

        try {
            BrandSpaceRecognitionView realtime = recognizeViaDoubao(draft);
            if (realtime != null) {
                return realtime;
            }
        } catch (RuntimeException ex) {
            realtimeFailureReason = summarizeFailure(ex);
            log.warn("Brand-space recognition fallback for brand={}, reason={}",
                    draft.getBrandName(),
                    ex.getMessage());
        }

        try {
            BrandSpaceRecognitionView fallback = recognizeViaCreativeFallback(draft);
            fallback.setFallbackReason(realtimeFailureReason);
            return fallback;
        } catch (RuntimeException ex) {
            String deterministicReason = firstText(summarizeFailure(ex), realtimeFailureReason);
            log.warn("Brand-space recognition deterministic fallback for brand={}, reason={}",
                    draft.getBrandName(),
                    ex.getMessage());
            return BrandSpaceRecognitionView.builder()
                    .suggestedCategory(draft.getCategory())
                    .officialWebsite(normalizeWebsite(draft.getOfficialWebsite()))
                    .directCompetitors(normalizeCompetitors(draft.getCompetitors(), draft.getBrandName(), draft.getCompetitors()))
                    .indirectCompetitors(List.of())
                    .competitors(normalizeCompetitors(draft.getCompetitors(), draft.getBrandName(), draft.getCompetitors()))
                    .brandSummary(buildFallbackSummary(
                            draft.getBrandName(),
                            draft.getCategory(),
                            normalizeWebsite(draft.getOfficialWebsite()),
                            normalizeCompetitors(draft.getCompetitors(), draft.getBrandName(), draft.getCompetitors())
                    ))
                    .source("deterministic_fallback")
                    .fallbackReason(deterministicReason)
                    .build();
        }
    }

    private BrandSpaceRecognitionView recognizeViaDoubao(BrandSpaceDraftPayload draft) {
        RawResponse rawResponse = executeRealtimeRecognition(draft);

        RecognitionPayload payload = parseRecognitionPayload(rawResponse.fullText());
        CompetitorBuckets competitorBuckets = resolveCompetitorBuckets(payload, draft);
        String suggestedCategory = firstText(payload.suggestedCategory(), draft.getCategory());
        String officialWebsite = firstText(normalizeWebsite(payload.officialWebsite()), normalizeWebsite(draft.getOfficialWebsite()));
        String brandSummary = normalizeSummary(payload.brandSummary());

        if (!StringUtils.hasText(suggestedCategory) || competitorBuckets.all().isEmpty()) {
            throw new IllegalStateException("豆包识别结果不完整");
        }
        validateBrandAnchorOrThrow(draft.getBrandName(), brandSummary, competitorBuckets.all());

        return BrandSpaceRecognitionView.builder()
                .suggestedCategory(suggestedCategory)
                .officialWebsite(officialWebsite)
                .directCompetitors(competitorBuckets.direct())
                .indirectCompetitors(competitorBuckets.indirect())
                .competitors(competitorBuckets.all())
                .brandSummary(brandSummary)
                .source(resolveRealtimeSource(rawResponse))
                .build();
    }

    private RawResponse executeRealtimeRecognition(BrandSpaceDraftPayload draft) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RawResponse> future = executor.submit(() -> doubaoAdapter.sendProbe(
                    buildRecognitionProbe(draft),
                    ModelAdapter.DispatchControl.withRealtimeSearch(true, "doubao")
            ));
            long timeoutSeconds = Math.max(1L, realtimeTimeoutSeconds);
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                future.cancel(true);
                throw new IllegalStateException("品牌识别联网超时", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                throw new IllegalStateException("品牌识别被中断", ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("品牌识别联网失败", cause);
            }
        }
    }

    private BrandSpaceRecognitionView recognizeViaCreativeFallback(BrandSpaceDraftPayload draft) {
        CreativeContextResult inferred = diagnosisInferenceService.inferForDrafting(
                draft.getBrandName(),
                draft.getOfficialWebsite(),
                buildInferencePrompt(draft)
        );

        List<String> competitors = normalizeCompetitors(
                inferred == null ? null : inferred.competitors(),
                draft.getBrandName(),
                draft.getCompetitors()
        );
        String suggestedCategory = firstText(
                inferred == null ? null : inferred.category(),
                draft.getCategory()
        );
        String officialWebsite = firstText(
                normalizeWebsite(inferred == null ? null : inferred.officialWebsite()),
                normalizeWebsite(draft.getOfficialWebsite())
        );
        String brandSummary = buildFallbackSummary(draft.getBrandName(), suggestedCategory, officialWebsite, competitors);
        validateBrandAnchorOrThrow(
                draft.getBrandName(),
                firstText(inferred == null ? null : inferred.brandName(), brandSummary),
                competitors
        );

        return BrandSpaceRecognitionView.builder()
                .suggestedCategory(suggestedCategory)
                .officialWebsite(officialWebsite)
                .directCompetitors(competitors)
                .indirectCompetitors(List.of())
                .competitors(competitors)
                .brandSummary(brandSummary)
                .source("creative_context_" + normalizeFallbackSource(inferred == null ? null : inferred.source()))
                .build();
    }

    private Node3ProbeInput buildRecognitionProbe(BrandSpaceDraftPayload draft) {
        return new Node3ProbeInput(
                "brand_space_recognition",
                BucketType.ORTHOGONAL,
                Map.of("scene", "brand_space_recognition"),
                Map.of("scene", "brand_space_recognition"),
                null,
                ProbeExposurePolicy.BRAND_VISIBLE,
                buildRecognitionPrompt(draft),
                "brand_space_recognition",
                "brand_space_recognition",
                Map.of(),
                new Node3SearchGroundingContext(
                        defaultIfBlank(draft.getCategory(), null),
                        buildCategoryAnchors(draft.getCategory()),
                        draft.getBrandName()
                )
        );
    }

    private String buildRecognitionPrompt(BrandSpaceDraftPayload draft) {
        List<String> lines = new ArrayList<>();
        lines.add("请先联网搜索并识别下面这个品牌，确认它最接近的真实业务语义。");
        lines.add("目标品牌：" + draft.getBrandName());
        if (StringUtils.hasText(draft.getCategory())) {
            lines.add("用户自填品类：" + draft.getCategory().trim());
        }
        if (StringUtils.hasText(draft.getOfficialWebsite())) {
            lines.add("用户已知官网：" + draft.getOfficialWebsite().trim());
        }
        if (draft.getCompetitors() != null && !draft.getCompetitors().isEmpty()) {
            lines.add("用户已知竞品：" + String.join("、", draft.getCompetitors()));
        }
        if (StringUtils.hasText(draft.getSupplementalBrief())) {
            lines.add("用户补充信息：" + draft.getSupplementalBrief().trim());
        }
        lines.add("输出严格 JSON，不要输出任何解释或 Markdown。");
        lines.add("JSON 字段固定为：suggestedCategory, officialWebsite, directCompetitors, indirectCompetitors, brandSummary。");
        lines.add("规则：");
        lines.add("1. suggestedCategory 返回最准确、最可诊断的单一品类，用中文。");
        lines.add("2. officialWebsite 必须是完整官网 URL；无法确认时返回空字符串。");
        lines.add("3. directCompetitors 返回 2 到 5 个最直接的同层竞品品牌名，不要包含目标品牌。");
        lines.add("4. indirectCompetitors 返回 0 到 5 个间接竞品、替代品牌或相邻价格带品牌名，不要包含目标品牌，也不要与 directCompetitors 重复。");
        lines.add("5. directCompetitors 与 indirectCompetitors 合计最多 10 个品牌名。");
        lines.add("6. brandSummary 用 500 字以内概括品牌定位、品类归属、关键经营事实和识别依据。");
        lines.add("7. 如果无法可靠区分直接/间接竞品，优先保证 directCompetitors，indirectCompetitors 返回空数组。");
        lines.add("8. 不要返回 competitors、negativeTerms、不要解释推理过程、不要附带额外字段。");
        lines.add("9. 品牌名必须精确锚定输入值；如果搜到的是近似品牌、短名、简称或系列名，不得冒充目标品牌。");
        lines.add("10. brandSummary 第一处提及品牌时，必须完整写出输入的目标品牌名。");
        return String.join("\n", lines);
    }

    private String buildInferencePrompt(BrandSpaceDraftPayload draft) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(draft.getCategory())) {
            lines.add("用户自填品类：" + draft.getCategory().trim());
        }
        if (draft.getCompetitors() != null && !draft.getCompetitors().isEmpty()) {
            lines.add("用户已知竞品：" + String.join("、", draft.getCompetitors()));
        }
        if (StringUtils.hasText(draft.getSupplementalBrief())) {
            lines.add(draft.getSupplementalBrief().trim());
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private List<String> buildCategoryAnchors(String category) {
        if (!StringUtils.hasText(category)) {
            return List.of();
        }
        return List.of(category.trim());
    }

    private RecognitionPayload parseRecognitionPayload(String rawText) {
        String json = extractJsonObject(rawText);
        try {
            return objectMapper.readValue(json, RecognitionPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("品牌识别结果无法解析为 JSON", ex);
        }
    }

    private String extractJsonObject(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalStateException("品牌识别返回空文本");
        }
        Matcher matcher = JSON_OBJECT_PATTERN.matcher(rawText.trim());
        if (!matcher.find()) {
            throw new IllegalStateException("品牌识别未返回 JSON 对象");
        }
        return matcher.group();
    }

    private List<String> normalizeCompetitors(List<String> rawCompetitors,
                                              String brandName,
                                              List<String> fallbackCompetitors) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        List<String> source = rawCompetitors == null || rawCompetitors.isEmpty()
                ? fallbackCompetitors
                : rawCompetitors;
        if (source != null) {
            for (String item : source) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                String normalized = item.trim();
                if (normalized.equalsIgnoreCase(brandName)) {
                    continue;
                }
                deduplicated.add(normalized);
                if (deduplicated.size() >= MAX_COMPETITOR_COUNT) {
                    break;
                }
            }
        }
        return List.copyOf(deduplicated);
    }

    private CompetitorBuckets resolveCompetitorBuckets(RecognitionPayload payload, BrandSpaceDraftPayload draft) {
        List<String> fallbackMerged = normalizeCompetitors(payload.competitors(), draft.getBrandName(), draft.getCompetitors());
        LinkedHashSet<String> occupied = new LinkedHashSet<>();
        List<String> direct = normalizeCompetitorBucket(payload.directCompetitors(), draft.getBrandName(), occupied, MAX_DIRECT_COMPETITOR_COUNT);
        List<String> indirect = normalizeCompetitorBucket(payload.indirectCompetitors(), draft.getBrandName(), occupied, MAX_INDIRECT_COMPETITOR_COUNT);

        if (direct.isEmpty() && indirect.isEmpty()) {
            int directCount = Math.min(MAX_DIRECT_COMPETITOR_COUNT, fallbackMerged.size());
            List<String> fallbackDirect = List.copyOf(fallbackMerged.subList(0, directCount));
            List<String> fallbackIndirect = directCount >= fallbackMerged.size()
                    ? List.of()
                    : List.copyOf(fallbackMerged.subList(directCount, fallbackMerged.size()));
            return new CompetitorBuckets(fallbackDirect, fallbackIndirect, fallbackMerged);
        }

        List<String> mergedSeed = new ArrayList<>(direct);
        mergedSeed.addAll(indirect);
        mergedSeed.addAll(fallbackMerged);
        List<String> merged = normalizeCompetitors(mergedSeed, draft.getBrandName(), draft.getCompetitors());
        return new CompetitorBuckets(direct, indirect, merged);
    }

    private List<String> normalizeCompetitorBucket(List<String> rawCompetitors,
                                                   String brandName,
                                                   LinkedHashSet<String> occupied,
                                                   int limit) {
        if (rawCompetitors == null || rawCompetitors.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : rawCompetitors) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String candidate = item.trim();
            if (candidate.equalsIgnoreCase(brandName) || occupied.contains(candidate)) {
                continue;
            }
            occupied.add(candidate);
            normalized.add(candidate);
            if (normalized.size() >= limit) {
                break;
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizeWebsite(String officialWebsite) {
        if (!StringUtils.hasText(officialWebsite)) {
            return null;
        }
        String trimmed = officialWebsite.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        return null;
    }

    private String normalizeSummary(String brandSummary) {
        if (!StringUtils.hasText(brandSummary)) {
            return null;
        }
        String normalized = brandSummary.trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH);
    }

    private void validateBrandAnchorOrThrow(String brandName,
                                            String brandSummary,
                                            List<String> competitors) {
        if (!requiresStrictExactBrandAnchor(brandName)) {
            return;
        }
        if (!containsExactBrandAnchor(brandSummary, brandName)) {
            throw new IllegalStateException("品牌识别未锚定到完整品牌名");
        }
        if (competitors == null || competitors.isEmpty()) {
            return;
        }
        for (String competitor : competitors) {
            if (isLikelySameBrand(competitor, brandName)) {
                throw new IllegalStateException("品牌识别把近似品牌误当成竞品");
            }
        }
    }

    private boolean requiresStrictExactBrandAnchor(String brandName) {
        String normalized = normalizeAnchorText(brandName);
        if (!StringUtils.hasText(normalized) || normalized.length() < STRICT_CJK_BRAND_MIN_LENGTH) {
            return false;
        }
        return containsCjk(normalized);
    }

    private boolean containsExactBrandAnchor(String brandSummary, String brandName) {
        String normalizedSummary = normalizeAnchorText(brandSummary);
        String normalizedBrandName = normalizeAnchorText(brandName);
        if (!StringUtils.hasText(normalizedSummary) || !StringUtils.hasText(normalizedBrandName)) {
            return false;
        }
        return normalizedSummary.contains(normalizedBrandName);
    }

    private boolean isLikelySameBrand(String candidate, String brandName) {
        String normalizedCandidate = normalizeAnchorText(candidate);
        String normalizedBrandName = normalizeAnchorText(brandName);
        if (!StringUtils.hasText(normalizedCandidate) || !StringUtils.hasText(normalizedBrandName)) {
            return false;
        }
        if (normalizedCandidate.equals(normalizedBrandName)) {
            return true;
        }
        if (!containsCjk(normalizedCandidate) || !containsCjk(normalizedBrandName)) {
            return false;
        }
        return normalizedBrandName.startsWith(normalizedCandidate)
                || normalizedCandidate.startsWith(normalizedBrandName);
    }

    private boolean containsCjk(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN
        );
    }

    private String normalizeAnchorText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String buildFallbackSummary(String brandName,
                                        String suggestedCategory,
                                        String officialWebsite,
                                        List<String> competitors) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(suggestedCategory)) {
            parts.add(brandName + "当前按“" + suggestedCategory + "”识别");
        } else {
            parts.add(brandName + "已完成基础品牌识别");
        }
        if (StringUtils.hasText(officialWebsite)) {
            parts.add("官网候选为" + officialWebsite);
        }
        if (competitors != null && !competitors.isEmpty()) {
            parts.add("主要对比品牌包括" + String.join("、", competitors.stream().limit(3).toList()));
        }
        return normalizeSummary(String.join("，", parts) + "。");
    }

    private String resolveRealtimeSource(RawResponse rawResponse) {
        if (rawResponse == null) {
            return "doubao_web_search";
        }
        if (rawResponse.searchRequested() && rawResponse.searchSatisfied()) {
            return "doubao_web_search";
        }
        if (rawResponse.searchRequested()) {
            return "doubao_web_search_partial";
        }
        return "doubao";
    }

    private String normalizeFallbackSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "fallback";
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private String summarizeFailure(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        if (!StringUtils.hasText(message) && throwable.getCause() != null) {
            message = throwable.getCause().getMessage();
        }
        if (!StringUtils.hasText(message)) {
            return throwable.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
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

    private String defaultIfBlank(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim();
    }

    private record RecognitionPayload(
            String suggestedCategory,
            String officialWebsite,
            List<String> directCompetitors,
            List<String> indirectCompetitors,
            List<String> competitors,
            String brandSummary
    ) {
    }

    private record CompetitorBuckets(
            List<String> direct,
            List<String> indirect,
            List<String> all
    ) {
    }
}

package com.geoprobe.service.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.BrandAliasAiService;
import com.geoprobe.common.LlmCallContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandAliasService {

    private static final int MODULE_ALIAS_EXPANSION = 4;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final Pattern WRAP_PUNCTUATION = Pattern.compile("^[\\s\\p{Punct}“”‘’\"'`《》〈〉【】\\[\\]（）()]+|[\\s\\p{Punct}“”‘’\"'`《》〈〉【】\\[\\]（）()]+$");
    private static final Set<String> GENERIC_STOP_WORDS = Set.of(
            "直播", "培训", "电商", "教育", "课程", "品牌", "机构", "平台", "主播", "老师", "行业", "服务"
    );

    private final BrandAliasAiService brandAliasAiService;
    private final ObjectMapper objectMapper;
    @Value("${app.engine.alias.llm-timeout-seconds:12}")
    private long aliasLlmTimeoutSeconds = 12L;

    public DiagnosisAliasConfigData generateAliasConfig(String diagnosisId,
                                                        String brandName,
                                                        String category,
                                                        List<String> competitors) {
        List<String> safeCompetitors = safeList(competitors);
        Map<String, List<String>> competitorAliases = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<String>> brandFuture = submitAliasTask(
                    executor,
                    diagnosisId,
                    brandName,
                    category,
                    safeCompetitors,
                    mergeExclusions(List.of(brandName), safeCompetitors, extractCategoryStopWords(category))
            );

            LinkedHashMap<String, Future<List<String>>> competitorFutures = new LinkedHashMap<>();
            for (String competitor : safeCompetitors) {
                List<String> competitorExclusions = mergeExclusions(
                        List.of(competitor, brandName),
                        safeCompetitors.stream().filter(item -> !normalize(item).equals(normalize(competitor))).toList(),
                        extractCategoryStopWords(category)
                );
                competitorFutures.put(
                        competitor,
                        submitAliasTask(
                                executor,
                                diagnosisId,
                                competitor,
                                category,
                                buildOtherBrands(brandName, safeCompetitors, competitor),
                                competitorExclusions
                        )
                );
            }

            List<String> brandAliases = awaitAliasResult(brandFuture, diagnosisId, brandName);
            for (Map.Entry<String, Future<List<String>>> entry : competitorFutures.entrySet()) {
                competitorAliases.put(entry.getKey(), awaitAliasResult(entry.getValue(), diagnosisId, entry.getKey()));
            }

            return new DiagnosisAliasConfigData(brandAliases, competitorAliases);
        }
    }

    private Future<List<String>> submitAliasTask(ExecutorService executor,
                                                 String diagnosisId,
                                                 String brandName,
                                                 String category,
                                                 List<String> competitors,
                                                 List<String> exclusions) {
        return executor.submit(() -> generateAliases(diagnosisId, brandName, category, competitors, exclusions));
    }

    private List<String> awaitAliasResult(Future<List<String>> future,
                                          String diagnosisId,
                                          String brandName) {
        if (future == null) {
            return List.of();
        }
        long timeoutSeconds = Math.max(1L, aliasLlmTimeoutSeconds);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Alias generation exceeded budget, diagnosisId={}, brand={}, timeoutSeconds={}", diagnosisId, brandName, timeoutSeconds);
            return List.of();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("Alias generation interrupted, diagnosisId={}, brand={}", diagnosisId, brandName);
            return List.of();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("Alias generation task failed, diagnosisId={}, brand={}, reason={}", diagnosisId, brandName, cause.getMessage());
            return List.of();
        }
    }

    private List<String> generateAliases(String diagnosisId,
                                         String brandName,
                                         String category,
                                         List<String> competitors,
                                         List<String> exclusions) {
        if (!StringUtils.hasText(brandName)) {
            return List.of();
        }

        try {
            LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_ALIAS_EXPANSION, PROVIDER_DEEPSEEK));
            String rawJson = brandAliasAiService.generateAliases(
                    brandName.trim(),
                    StringUtils.hasText(category) ? category.trim() : "",
                    competitors.isEmpty() ? "[]" : objectMapper.writeValueAsString(competitors)
            );
            List<String> parsed = objectMapper.readValue(normalizeAliasJson(rawJson), new TypeReference<List<String>>() {
            });
            return sanitizeAliases(parsed, exclusions);
        } catch (Exception ex) {
            log.warn("Alias generation failed, diagnosisId={}, brand={}, reason={}", diagnosisId, brandName, ex.getMessage());
            return List.of();
        } finally {
            LlmCallContext.clear();
        }
    }

    private List<String> sanitizeAliases(List<String> aliases, List<String> exclusions) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }

        Set<String> excluded = new LinkedHashSet<>();
        for (String value : exclusions) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                excluded.add(normalized);
                excluded.add(compactNormalize(value));
            }
        }

        LinkedHashMap<String, String> deduplicated = new LinkedHashMap<>();
        for (String alias : aliases) {
            if (!StringUtils.hasText(alias)) {
                continue;
            }
            String display = alias.trim();
            String normalized = normalize(display);
            String compact = compactNormalize(display);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (excluded.contains(normalized) || excluded.contains(compact)) {
                continue;
            }
            if (GENERIC_STOP_WORDS.contains(normalized) || GENERIC_STOP_WORDS.contains(compact)) {
                continue;
            }
            deduplicated.putIfAbsent(compact, display);
        }
        return List.copyOf(deduplicated.values());
    }

    private String normalizeAliasJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return "[]";
        }
        String normalized = rawJson.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        int arrayStart = normalized.indexOf('[');
        int arrayEnd = normalized.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd >= arrayStart) {
            normalized = normalized.substring(arrayStart, arrayEnd + 1);
        }
        return normalized.trim();
    }

    private List<String> mergeExclusions(List<String> primary, List<String> brands, List<String> categoryTerms) {
        List<String> merged = new ArrayList<>();
        merged.addAll(primary);
        merged.addAll(brands);
        merged.addAll(categoryTerms);
        return merged;
    }

    private List<String> buildOtherBrands(String brandName, List<String> competitors, String currentBrand) {
        List<String> others = new ArrayList<>();
        if (StringUtils.hasText(brandName)) {
            others.add(brandName.trim());
        }
        for (String competitor : competitors) {
            if (!StringUtils.hasText(competitor) || normalize(competitor).equals(normalize(currentBrand))) {
                continue;
            }
            others.add(competitor.trim());
        }
        return others;
    }

    private List<String> extractCategoryStopWords(String category) {
        if (!StringUtils.hasText(category)) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : category.split("[/、，,\\s]+")) {
            String normalized = normalize(token);
            if (StringUtils.hasText(normalized)) {
                values.add(token.trim());
            }
        }
        return List.copyOf(values);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        normalized = WRAP_PUNCTUATION.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private String compactNormalize(String text) {
        String normalized = normalize(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.replaceAll("[\\s\\p{Punct}]+", "");
    }
}

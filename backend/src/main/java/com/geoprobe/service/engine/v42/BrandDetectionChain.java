package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.BrandDetectionL3AiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.dto.engine.v42.BrandDetectionResult;
import com.geoprobe.dto.engine.v42.CompetitorMention;
import com.geoprobe.dto.engine.v42.Node3ProbeOutput;
import com.geoprobe.dto.engine.v42.Node4ParsingRequest;
import com.geoprobe.dto.engine.v42.Node4ParsingResult;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.ProbeType;
import com.geoprobe.dto.engine.v42.RawResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service("v42BrandDetectionChain")
@RequiredArgsConstructor
public class BrandDetectionChain {

    private static final int MODULE_EXTRACTION = 2;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final Set<String> POSITIVE_KEYWORDS = Set.of(
            "推荐", "首选", "最推荐", "首推", "值得", "靠谱", "优先", "闭眼", "放心", "更值", "更稳", "top1", "第一"
    );
    private static final Set<String> NEGATIVE_KEYWORDS = Set.of(
            "不推荐", "别买", "避雷", "踩坑", "问题很多", "劝退", "翻车", "吹出来", "失望", "别提", "负面"
    );
    private static final Set<String> TOP1_KEYWORDS = Set.of(
            "首选", "第一", "top1", "最推荐", "首推", "优先选", "闭眼买", "闭眼入", "更推荐"
    );
    private static final Set<String> COMPARISON_KEYWORDS = Set.of(
            "和", "比", "相比", "对比", "还是", "vs", "pk", "哪个更", "谁更"
    );
    private static final String SENTIMENT_POSITIVE = "positive";
    private static final String SENTIMENT_NEUTRAL = "neutral";
    private static final String SENTIMENT_NEGATIVE = "negative";
    private static final Pattern INNER_PUNCTUATION = Pattern.compile("[\\s\\p{Punct}·•、，。！？：；“”‘’（）()【】《》]+");

    private final BrandDetectionL3AiService brandDetectionL3AiService;
    private final AliasMatcher aliasMatcher;
    private final ObjectMapper objectMapper;

    public Node4ParsingResult parse(Node4ParsingRequest request) {
        Objects.requireNonNull(request, "Node4ParsingRequest 不能为空");
        ParsingContext context = ParsingContext.from(request);
        List<Node4ProbeOutput> outputs = new ArrayList<>(request.probes().size());
        for (Node3ProbeOutput probe : request.probes()) {
            outputs.add(parseOne(context, probe));
        }
        return new Node4ParsingResult(outputs);
    }

    public Node4ProbeOutput parseOne(Node4ParsingRequest request, Node3ProbeOutput probe) {
        Objects.requireNonNull(request, "Node4ParsingRequest 不能为空");
        return parseOne(ParsingContext.from(request), probe);
    }

    private Node4ProbeOutput parseOne(ParsingContext context, Node3ProbeOutput probe) {
        Objects.requireNonNull(probe, "probe 不能为空");

        RawResponse rawResponse = probe.rawResponse();
        String responseText = rawResponse.fullText();
        ProbeType probeType = resolveProbeType(probe);
        List<CompetitorMention> competitorMentions = detectCompetitors(context, responseText);
        BrandHit brandHit = detectBrand(context, responseText, competitorMentions);
        int responseTokenCount = rawResponse.responseTokenCount();
        String responseLengthBucket = bucketFor(responseTokenCount);

        BrandDetectionResult detectionResult = brandHit.mentioned()
                ? BrandDetectionResult.detected(
                brandHit.detectionLevel(),
                brandHit.position(),
                brandHit.sentiment(),
                brandHit.strength(),
                probeType,
                responseTokenCount,
                responseLengthBucket,
                competitorMentions,
                rawResponse.citations(),
                rawResponse.thinkContent()
        )
                : BrandDetectionResult.noMention(
                probeType,
                responseTokenCount,
                responseLengthBucket,
                competitorMentions,
                rawResponse.citations(),
                rawResponse.thinkContent()
        );

        return Node4ProbeOutput.fromNode3(probe, detectionResult);
    }

    private BrandHit detectBrand(ParsingContext context, String responseText, List<CompetitorMention> competitorMentions) {
        if (!StringUtils.hasText(responseText)) {
            return BrandHit.absent();
        }

        AliasMatcher.Match l1Match = aliasMatcher.findFirstExact(responseText, context.brandName());
        if (l1Match != null) {
            return toHeuristicHit(responseText, l1Match, "L1_EXACT", competitorMentions, 0.96d);
        }

        AliasMatcher.Match l2Match = aliasMatcher.findFirstAlias(responseText, context.brandName(), context.brandAliases());
        if (l2Match != null) {
            return toHeuristicHit(responseText, l2Match, "L2_ALIAS", competitorMentions, 0.88d);
        }

        return detectSemanticMention(context, responseText, competitorMentions);
    }

    private ProbeType resolveProbeType(Node3ProbeOutput probe) {
        return ProbeType.fromExposurePolicy(probe.exposurePolicy());
    }

    private BrandHit detectSemanticMention(ParsingContext context, String responseText, List<CompetitorMention> competitorMentions) {
        try {
            LlmCallContext.set(new LlmCallContext(context.diagnosisId(), MODULE_EXTRACTION, PROVIDER_DEEPSEEK));
            String rawJson = brandDetectionL3AiService.detect(
                    context.brandName(),
                    toJsonLikeList(context.brandAliases()),
                    toJsonLikeList(context.competitors()),
                    responseText
            );
            if (!StringUtils.hasText(rawJson)) {
                return BrandHit.absent();
            }
            JsonNode payload = objectMapper.readTree(normalizeJsonPayload(rawJson));
            boolean mentioned = payload.path("mentioned").asBoolean(false);
            if (!mentioned) {
                return BrandHit.absent();
            }
            String position = normalizeAllowed(payload.path("position").isNull() ? null : payload.path("position").asText(), Set.of(
                    "top1_recommendation", "comparison", "passing_mention", "negative"
            ));
            String sentiment = normalizeAllowed(payload.path("sentiment").isNull() ? null : payload.path("sentiment").asText(), Set.of(
                    SENTIMENT_POSITIVE, SENTIMENT_NEUTRAL, SENTIMENT_NEGATIVE
            ));
            double strength = clamp(payload.path("strength").asDouble(0.65d));
            if (position == null || sentiment == null) {
                return BrandHit.absent();
            }
            if ("comparison".equals(position) && competitorMentions.isEmpty() && !containsComparisonCue(responseText)) {
                position = "passing_mention";
            }
            return new BrandHit(true, "L3_SEMANTIC", position, sentiment, strength);
        } catch (Exception ex) {
            log.warn("V4.2 Node 4 L3 semantic detection failed. brand={}, reason={}", context.brandName(), ex.getMessage());
            return BrandHit.absent();
        } finally {
            LlmCallContext.clear();
        }
    }

    private BrandHit toHeuristicHit(String responseText,
                                    AliasMatcher.Match match,
                                    String detectionLevel,
                                    List<CompetitorMention> competitorMentions,
                                    double baseStrength) {
        String contextText = extractSentence(responseText, match.start(), match.end());
        String sentiment = determineSentiment(contextText, responseText);
        String position = determinePosition(contextText, responseText, sentiment, competitorMentions);
        double strength = clamp(adjustStrength(baseStrength, position, sentiment));
        return new BrandHit(true, detectionLevel, position, sentiment, strength);
    }

    private List<CompetitorMention> detectCompetitors(ParsingContext context, String responseText) {
        if (!StringUtils.hasText(responseText) || context.competitors().isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, CompetitorCandidate> candidates = new LinkedHashMap<>();
        for (String competitor : context.competitors()) {
            AliasMatcher.Match exactMatch = aliasMatcher.findFirstExact(responseText, competitor);
            if (exactMatch != null) {
                candidates.put(competitor, toCompetitorCandidate(competitor, exactMatch, "L1_EXACT", responseText, 0.92d));
                continue;
            }
            AliasMatcher.Match aliasMatch = aliasMatcher.findFirstAlias(responseText, competitor, context.competitorAliases().get(competitor));
            if (aliasMatch != null) {
                candidates.put(competitor, toCompetitorCandidate(competitor, aliasMatch, "L2_ALIAS", responseText, 0.84d));
            }
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingInt(CompetitorCandidate::start))
                .map(CompetitorCandidate::dto)
                .toList();
    }

    private CompetitorCandidate toCompetitorCandidate(String competitor,
                                                      AliasMatcher.Match match,
                                                      String detectionLevel,
                                                      String responseText,
                                                      double baseStrength) {
        String contextText = extractSentence(responseText, match.start(), match.end());
        String sentiment = determineSentiment(contextText, responseText);
        double strength = clamp(adjustStrength(baseStrength, "passing_mention", sentiment));
        return new CompetitorCandidate(match.start(), new CompetitorMention(competitor, detectionLevel, sentiment, strength));
    }

    private String determinePosition(String contextText,
                                     String responseText,
                                     String sentiment,
                                     List<CompetitorMention> competitorMentions) {
        String normalizedContext = normalize(contextText);
        String normalizedResponse = normalize(responseText);

        if (SENTIMENT_NEGATIVE.equals(sentiment)) {
            return "negative";
        }
        if (containsAny(normalizedContext, TOP1_KEYWORDS) || containsAny(normalizedResponse, TOP1_KEYWORDS)) {
            return "top1_recommendation";
        }
        if (!competitorMentions.isEmpty() || containsComparisonCue(contextText) || containsComparisonCue(responseText)) {
            return "comparison";
        }
        return "passing_mention";
    }

    private boolean containsComparisonCue(String text) {
        return containsAny(normalize(text), COMPARISON_KEYWORDS);
    }

    private String determineSentiment(String contextText, String responseText) {
        String normalizedContext = normalize(contextText);
        String normalizedResponse = normalize(responseText);
        if (containsAny(normalizedContext, NEGATIVE_KEYWORDS) || containsAny(normalizedResponse, NEGATIVE_KEYWORDS)) {
            return SENTIMENT_NEGATIVE;
        }
        if (containsAny(normalizedContext, POSITIVE_KEYWORDS) || containsAny(normalizedResponse, POSITIVE_KEYWORDS)) {
            return SENTIMENT_POSITIVE;
        }
        return SENTIMENT_NEUTRAL;
    }

    private boolean containsAny(String normalizedText, Set<String> keywords) {
        if (!StringUtils.hasText(normalizedText)) {
            return false;
        }
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractSentence(String text, int start, int end) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int left = Math.max(0, start);
        while (left > 0 && !isBoundary(text.charAt(left - 1))) {
            left--;
        }
        int right = Math.min(text.length(), end);
        while (right < text.length() && !isBoundary(text.charAt(right))) {
            right++;
        }
        return text.substring(left, right).trim();
    }

    private boolean isBoundary(char value) {
        return value == '\n' || value == '\r'
                || value == '。' || value == '！' || value == '？' || value == '；'
                || value == '.' || value == '!' || value == '?' || value == ';';
    }

    private String bucketFor(int responseTokenCount) {
        if (responseTokenCount < 80) {
            return "Short";
        }
        if (responseTokenCount <= 200) {
            return "Medium";
        }
        return "Long";
    }

    private String toJsonLikeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> safe = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                safe.add("\"" + value.trim().replace("\"", "\\\"") + "\"");
            }
        }
        return "[" + String.join(", ", safe) + "]";
    }

    private String normalizeAllowed(String value, Set<String> allowedValues) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return allowedValues.contains(normalized) ? normalized : null;
    }

    private String normalizeJsonPayload(String rawJson) {
        String normalized = rawJson == null ? "" : rawJson.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }

    private double adjustStrength(double baseStrength, String position, String sentiment) {
        double result = baseStrength;
        if ("top1_recommendation".equals(position)) {
            result += 0.04d;
        }
        if (SENTIMENT_NEGATIVE.equals(sentiment)) {
            result -= 0.06d;
        }
        return result;
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String compact(String text) {
        return INNER_PUNCTUATION.matcher(normalize(text)).replaceAll("");
    }

    private record BrandHit(
            boolean mentioned,
            String detectionLevel,
            String position,
            String sentiment,
            double strength
    ) {
        static BrandHit absent() {
            return new BrandHit(false, null, null, null, 0.0d);
        }
    }

    private record CompetitorCandidate(int start, CompetitorMention dto) {
    }

    private record ParsingContext(
            String diagnosisId,
            String brandName,
            List<String> brandAliases,
            List<String> competitors,
            Map<String, List<String>> competitorAliases
    ) {
        static ParsingContext from(Node4ParsingRequest request) {
            List<String> resolvedBrandAliases = V42DeterministicAliasResolver.resolveAliasesForName(
                    request.brandName(),
                    request.brandAliases()
            );
            LinkedHashSet<String> brandAliases = new LinkedHashSet<>();
            brandAliases.add(request.brandName());
            brandAliases.addAll(resolvedBrandAliases);

            LinkedHashMap<String, List<String>> competitorAliases = new LinkedHashMap<>();
            for (String competitor : request.competitors()) {
                List<String> resolvedCompetitorAliases = V42DeterministicAliasResolver.resolveAliasesForName(
                        competitor,
                        request.competitorAliases().getOrDefault(competitor, List.of())
                );
                LinkedHashSet<String> aliases = new LinkedHashSet<>();
                aliases.add(competitor);
                aliases.addAll(resolvedCompetitorAliases);
                competitorAliases.put(competitor, List.copyOf(aliases));
            }

            return new ParsingContext(
                    request.diagnosisId(),
                    request.brandName(),
                    List.copyOf(brandAliases),
                    request.competitors(),
                    Map.copyOf(competitorAliases)
            );
        }
    }
}

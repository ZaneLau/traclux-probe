package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.CssmHiStateSpaceAiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.dto.engine.v42.CategoryVariablePool;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceRequest;
import com.geoprobe.dto.engine.v42.CssmHiStateSpaceResult;
import com.geoprobe.dto.engine.v42.DimensionVariables;
import com.geoprobe.dto.engine.v42.EngineTaskContextPackage;
import com.geoprobe.dto.engine.v42.FrontDeskTaskOrder;
import com.geoprobe.dto.engine.v42.MinusOneContextSlots;
import com.geoprobe.dto.engine.v42.MinusOneContextUses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service("v42CssmHiStateSpaceService")
@RequiredArgsConstructor
public class CssmHiStateSpaceService {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final int MAX_CATEGORY_ANCHORS = 10;
    private static final int MAX_VARIABLE_LENGTH = 15;
    static final String SOURCE_LLM = "LLM";
    static final String SOURCE_DETERMINISTIC_FALLBACK = "DETERMINISTIC_FALLBACK";

    private final CssmHiStateSpaceAiService cssmHiStateSpaceAiService;
    private final ObjectMapper objectMapper;

    public CssmHiStateSpaceResult generate(String diagnosisId, CssmHiStateSpaceRequest request) {
        SanitizedRequest sanitized = sanitizeRequest(request);
        try {
            LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_CREATIVE, PROVIDER_DEEPSEEK));
            String rawJson = cssmHiStateSpaceAiService.translateStateSpace(
                    buildTranslationCategoryPrompt(sanitized),
                    toJson(CssmHiAbstractStates.D1_COGNITIVE_ANOMALY),
                    toJson(CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL),
                    toJson(CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE),
                    toJson(CssmHiAbstractStates.D4_COGNITIVE_LOAD),
                    toJson(CssmHiAbstractStates.D5_TIME_CONSTRAINT),
                    toJson(CssmHiAbstractStates.D6_INTERACTION_MODALITY)
            );
            TranslationPayload payload = parse(rawJson);
            CategoryVariablePool pool = buildPool(sanitized, payload);
            return new CssmHiStateSpaceResult(validate(pool, sanitized.fallbackAnchorMode()), false, SOURCE_LLM, null);
        } catch (RuntimeException ex) {
            String fallbackReasonCode = V42CreativeDegradationReasons.resolve(ex);
            log.warn("V4.2 Node 0 deterministic fallback engaged. diagnosisId={}, category={}, reason={}",
                    diagnosisId, sanitized.category(), fallbackReasonCode, ex);
            CategoryVariablePool fallbackPool = buildDeterministicFallbackPool(sanitized);
            return new CssmHiStateSpaceResult(
                    validate(fallbackPool, sanitized.fallbackAnchorMode()),
                    true,
                    SOURCE_DETERMINISTIC_FALLBACK,
                    fallbackReasonCode
            );
        } finally {
            LlmCallContext.clear();
        }
    }

    CategoryVariablePool validate(CategoryVariablePool pool, boolean fallbackAnchorMode) {
        if (pool == null) {
            throw new IllegalStateException("CategoryVariablePool 不能为空");
        }
        validateCategoryAnchors(pool.categoryAnchors(), fallbackAnchorMode);

        // M4 触发条件固定为 D1=verification ∧ D4=optimal，Node 0 必须显式保留这两个抽象状态。
        if (!pool.d1CognitiveAnomaly().abstractCodes().contains("verification")) {
            throw new IllegalStateException("D1 必须包含 verification");
        }
        if (!pool.d4CognitiveLoad().abstractCodes().contains("optimal")) {
            throw new IllegalStateException("D4 必须包含 optimal");
        }

        validateDimension(
                pool.d1CognitiveAnomaly(),
                "D1",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D1"),
                CssmHiAbstractStates.D1_COGNITIVE_ANOMALY
        );
        validateDimension(
                pool.d2TeleologicalGoal(),
                "D2",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D2"),
                CssmHiAbstractStates.D2_TELEOLOGICAL_GOAL
        );
        validateDimension(
                pool.d3AxiologicalDrive(),
                "D3",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D3"),
                CssmHiAbstractStates.D3_AXIOLOGICAL_DRIVE
        );
        validateDimension(
                pool.d4CognitiveLoad(),
                "D4",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D4"),
                CssmHiAbstractStates.D4_COGNITIVE_LOAD
        );
        validateDimension(
                pool.d5TimeConstraint(),
                "D5",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D5"),
                CssmHiAbstractStates.D5_TIME_CONSTRAINT
        );
        validateDimension(
                pool.d6InteractionModality(),
                "D6",
                CssmHiAbstractStates.DIMENSION_NAMES.get("D6"),
                CssmHiAbstractStates.D6_INTERACTION_MODALITY
        );
        return pool;
    }

    private SanitizedRequest sanitizeRequest(CssmHiStateSpaceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CssmHiStateSpaceRequest 不能为空");
        }
        String brandName = normalizeRequired(request.brandName(), "brandName 不能为空");
        String category = normalizeRequired(request.category(), "category 不能为空");
        List<String> competitors = normalizeDistinctList(request.competitors());
        List<String> categoryAnchors = normalizeDistinctList(request.categoryAnchors());
        validateCategoryAnchors(categoryAnchors, request.fallbackAnchorMode());
        return new SanitizedRequest(
                brandName,
                category,
                competitors,
                categoryAnchors,
                request.fallbackAnchorMode(),
                request.taskOrder(),
                request.contextPackage()
        );
    }

    private void validateCategoryAnchors(List<String> categoryAnchors, boolean fallbackAnchorMode) {
        int minAnchors = fallbackAnchorMode ? 2 : 3;
        if (categoryAnchors.size() < minAnchors || categoryAnchors.size() > MAX_CATEGORY_ANCHORS) {
            throw new IllegalArgumentException(
                    "categoryAnchors 数量不合法，当前=%d，允许范围=%d-%d"
                            .formatted(categoryAnchors.size(), minAnchors, MAX_CATEGORY_ANCHORS)
            );
        }
    }

    private void validateDimension(DimensionVariables dimension,
                                   String expectedId,
                                   String expectedName,
                                   List<String> expectedCodes) {
        if (dimension == null) {
            throw new IllegalStateException("维度 %s 不能为空".formatted(expectedId));
        }
        if (!expectedId.equals(dimension.dimensionId())) {
            throw new IllegalStateException("维度 ID 不匹配: " + expectedId);
        }
        if (!expectedName.equals(dimension.dimensionName())) {
            throw new IllegalStateException("维度名称不匹配: " + expectedId);
        }
        if (!expectedCodes.equals(dimension.abstractCodes())) {
            throw new IllegalStateException("维度 %s 的 abstractCodes 与主设计文档不一致".formatted(expectedId));
        }
        if (dimension.abstractCodes().size() != dimension.variables().size()) {
            throw new IllegalStateException("维度 %s 的 abstractCodes 与 variables 数量不一致".formatted(expectedId));
        }
        for (String variable : dimension.variables()) {
            String normalized = normalizeRequired(variable, "翻译变量不能为空");
            if (normalized.length() > MAX_VARIABLE_LENGTH) {
                throw new IllegalStateException("维度 %s 存在超过 15 字的翻译变量".formatted(expectedId));
            }
        }
    }

    private CategoryVariablePool buildPool(SanitizedRequest request, TranslationPayload payload) {
        return new CategoryVariablePool(
                request.brandName(),
                request.category(),
                request.competitors(),
                request.categoryAnchors(),
                dimension("D1", payload.d1CognitiveAnomaly()),
                dimension("D2", payload.d2TeleologicalGoal()),
                dimension("D3", payload.d3AxiologicalDrive()),
                dimension("D4", payload.d4CognitiveLoad()),
                dimension("D5", payload.d5TimeConstraint()),
                dimension("D6", payload.d6InteractionModality()),
                request.contextPackage()
        );
    }

    private CategoryVariablePool buildDeterministicFallbackPool(SanitizedRequest request) {
        return new CategoryVariablePool(
                request.brandName(),
                request.category(),
                request.competitors(),
                request.categoryAnchors(),
                dimension("D1", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D1")),
                dimension("D2", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D2")),
                dimension("D3", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D3")),
                dimension("D4", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D4")),
                dimension("D5", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D5")),
                dimension("D6", CssmHiAbstractStates.DETERMINISTIC_FALLBACK_VARIABLES.get("D6")),
                request.contextPackage()
        );
    }

    private String buildTranslationCategoryPrompt(SanitizedRequest request) {
        List<String> categoryHypotheses = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.CATEGORY_HYPOTHESIS,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                3
        );
        List<String> productForms = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.PRODUCT_FORM_FACTOR,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                3
        );
        List<String> scenarios = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.USAGE_SCENARIO,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                3
        );
        List<String> decisionFactors = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.DECISION_FACTOR,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                3
        );
        List<String> originCues = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.GEO_OR_ORIGIN_CUE,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                2
        );
        List<String> brandEvidence = request.contextPackage().slotValuesForUse(
                MinusOneContextSlots.BRAND_EVIDENCE,
                MinusOneContextUses.NODE0_STATE_INFERENCE,
                1
        );

        List<String> lines = new ArrayList<>();
        lines.add(request.category());
        appendContextLine(lines, "相邻品类/形态", mergeContextValues(categoryHypotheses, productForms, 4));
        appendContextLine(lines, "典型使用场景", scenarios);
        appendContextLine(lines, "常见决策关注点", decisionFactors);
        appendContextLine(lines, "来源或产地线索", originCues);
        appendContextLine(lines, "品牌背景证据", brandEvidence);
        return String.join("\n", lines);
    }

    private DimensionVariables dimension(String dimensionId, List<String> variables) {
        return new DimensionVariables(
                dimensionId,
                CssmHiAbstractStates.DIMENSION_NAMES.get(dimensionId),
                CssmHiAbstractStates.ABSTRACT_CODE_MAP.get(dimensionId),
                normalizeVariables(variables)
        );
    }

    private TranslationPayload parse(String rawJson) {
        String sanitizedJson = sanitizeTranslationPayload(rawJson);
        try {
            JsonNode root = objectMapper.readTree(sanitizedJson);
            return new TranslationPayload(
                    readDimensionVariables(root, "d1CognitiveAnomaly", "D1"),
                    readDimensionVariables(root, "d2TeleologicalGoal", "D2"),
                    readDimensionVariables(root, "d3AxiologicalDrive", "D3"),
                    readDimensionVariables(root, "d4CognitiveLoad", "D4"),
                    readDimensionVariables(root, "d5TimeConstraint", "D5"),
                    readDimensionVariables(root, "d6InteractionModality", "D6")
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Node 0 LLM 输出 JSON 解析失败", ex);
        }
    }

    private String sanitizeTranslationPayload(String rawJson) {
        String normalized = normalizeRequired(rawJson, "Node 0 LLM 输出不能为空");
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        int firstBrace = normalized.indexOf('{');
        int lastBrace = normalized.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            normalized = normalized.substring(firstBrace, lastBrace + 1);
        }
        return normalized.trim();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Node 0 prompt JSON 序列化失败", ex);
        }
    }

    private List<String> readDimensionVariables(JsonNode root, String fieldName, String dimensionId) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode item : node) {
                values.add(normalizeRequired(item.asText(null), "维度 %s 存在空翻译变量".formatted(dimensionId)));
            }
            return List.copyOf(values);
        }
        if (node.isObject()) {
            List<String> expectedCodes = CssmHiAbstractStates.ABSTRACT_CODE_MAP.getOrDefault(dimensionId, List.of());
            List<String> values = new ArrayList<>();
            boolean matchedExpectedCodes = !expectedCodes.isEmpty();
            for (String code : expectedCodes) {
                JsonNode codeNode = node.get(code);
                if (codeNode == null || codeNode.isNull()) {
                    matchedExpectedCodes = false;
                    break;
                }
                values.add(normalizeRequired(codeNode.asText(null), "维度 %s 存在空翻译变量".formatted(dimensionId)));
            }
            if (matchedExpectedCodes) {
                return List.copyOf(values);
            }
            values = new ArrayList<>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                values.add(normalizeRequired(entry.getValue().asText(null), "维度 %s 存在空翻译变量".formatted(dimensionId)));
            }
            return List.copyOf(values);
        }
        throw new IllegalStateException("维度 %s 输出格式非法".formatted(dimensionId));
    }

    private List<String> normalizeVariables(List<String> variables) {
        if (variables == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>(variables.size());
        for (String variable : variables) {
            normalized.add(normalizeRequired(variable, "翻译变量不能为空"));
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeDistinctList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private record SanitizedRequest(
            String brandName,
            String category,
            List<String> competitors,
            List<String> categoryAnchors,
            boolean fallbackAnchorMode,
            FrontDeskTaskOrder taskOrder,
            EngineTaskContextPackage contextPackage
    ) {
    }

    private record TranslationPayload(
            List<String> d1CognitiveAnomaly,
            List<String> d2TeleologicalGoal,
            List<String> d3AxiologicalDrive,
            List<String> d4CognitiveLoad,
            List<String> d5TimeConstraint,
            List<String> d6InteractionModality
    ) {
    }

    private void appendContextLine(List<String> lines, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        lines.add(label + "：" + String.join(" / ", values));
    }

    private List<String> mergeContextValues(List<String> primary, List<String> secondary, int limit) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            merged.addAll(primary);
        }
        if (secondary != null) {
            merged.addAll(secondary);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        return merged.stream().limit(limit).toList();
    }
}

package com.geoprobe.service.engine.v42;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.ai.v42.DefenseCompletenessAiService;
import com.geoprobe.common.LlmCallContext;
import com.geoprobe.common.SensitiveDataSanitizer;
import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.DefenseCompletenessJudgement;
import com.geoprobe.dto.engine.v42.DefenseCompletenessResult;
import com.geoprobe.dto.engine.v42.DefenseCompletenessSummary;
import com.geoprobe.dto.engine.v42.Node4ProbeOutput;
import com.geoprobe.dto.engine.v42.ProbeDispatchStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service("v42DefenseCompletenessJudge")
@RequiredArgsConstructor
public class DefenseCompletenessJudge {

    private static final int MODULE_CREATIVE = 1;
    private static final String PROVIDER_DEEPSEEK = "deepseek";

    private final DefenseCompletenessAiService defenseCompletenessAiService;
    private final ObjectMapper objectMapper;

    public DefenseCompletenessSummary judge(String brandName, List<Node4ProbeOutput> probes) {
        return judge(null, brandName, probes);
    }

    public DefenseCompletenessSummary judge(String diagnosisId, String brandName, List<Node4ProbeOutput> probes) {
        String normalizedBrandName = normalizeRequired(brandName, "brandName 不能为空");
        Objects.requireNonNull(probes, "probes 不能为空");

        List<DefenseCompletenessResult> results = new ArrayList<>();
        for (Node4ProbeOutput probe : probes) {
            if (probe == null || probe.bucketType() != BucketType.ADVERSARIAL) {
                continue;
            }
            results.add(judgeSingleProbe(diagnosisId, normalizedBrandName, probe));
        }

        double averageCompositeScore = results.isEmpty()
                ? 0.0d
                : scale4(results.stream()
                .mapToDouble(DefenseCompletenessResult::compositeScore)
                .average()
                .orElse(0.0d));
        return new DefenseCompletenessSummary(averageCompositeScore, results.size(), List.copyOf(results));
    }

    private DefenseCompletenessResult judgeSingleProbe(String diagnosisId, String brandName, Node4ProbeOutput probe) {
        if (!isJudgeable(probe)) {
            return DefenseCompletenessResult.missing(probe.probeId());
        }

        String rawJson = null;
        try {
            LlmCallContext.set(new LlmCallContext(diagnosisId, MODULE_CREATIVE, PROVIDER_DEEPSEEK));
            rawJson = defenseCompletenessAiService.judge(
                    brandName,
                    probe.renderedPrompt(),
                    probe.rawResponse().fullText()
            );
            DefenseCompletenessJudgement judgement = parse(rawJson);
            double compositeScore = scale4(
                    judgement.evidence() * 0.4d
                            + judgement.comparison() * 0.3d
                            + judgement.conclusion() * 0.3d
            );
            return new DefenseCompletenessResult(
                    probe.probeId(),
                    judgement.evidence(),
                    judgement.comparison(),
                    judgement.conclusion(),
                    compositeScore
            );
        } catch (RuntimeException ex) {
            log.warn("V4.2 Node 5C defenseCompleteness fallback engaged. diagnosisId={}, probeId={}, reason={}, rawSnapshot={}",
                    diagnosisId, probe.probeId(), ex.getMessage(), snapshot(rawJson));
            return DefenseCompletenessResult.missing(probe.probeId());
        } finally {
            LlmCallContext.clear();
        }
    }

    private boolean isJudgeable(Node4ProbeOutput probe) {
        return probe.dispatchStatus() == ProbeDispatchStatus.SUCCESS
                && hasText(probe.renderedPrompt())
                && probe.rawResponse() != null
                && hasText(probe.rawResponse().fullText());
    }

    private DefenseCompletenessJudgement parse(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, DefenseCompletenessJudgement.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Node 5C LLM 输出 JSON 解析失败", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private double scale4(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String snapshot(String rawJson) {
        return SensitiveDataSanitizer.preview(rawJson, 160);
    }
}

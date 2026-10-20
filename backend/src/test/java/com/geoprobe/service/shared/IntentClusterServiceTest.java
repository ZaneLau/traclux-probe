package com.geoprobe.service.shared;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.geoprobe.ai.IntentClusterAiService;
import com.geoprobe.dto.engine.CreativeExecutionContext;
import com.geoprobe.dto.engine.IntentCluster;
import com.geoprobe.dto.engine.IntentClusterResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntentClusterServiceTest {

    @Mock
    private IntentClusterAiService intentClusterAiService;

    @InjectMocks
    private IntentClusterService intentClusterService;

    @Test
    void shouldGenerateClustersWithNormalizedWeights() {
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("浴室置物架")
                        .involvement("HIGH")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(false)
                        .categoryAttributes(List.of("材质", "承重", "安装方式"))
                        .competitors(List.of("竞品A", "竞品B", "竞品C"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-1", context());

        assertEquals(4, result.clusters().size());
        double sum = result.clusters().stream().mapToDouble(IntentCluster::weight).sum();
        assertEquals(1.0d, sum, 0.0001d);
        assertEquals("STANDARD", result.edition());
        assertEquals(11, result.totalCorePrompts());
        assertEquals(11, result.clusters().stream().mapToInt(IntentCluster::corePromptCount).sum());
    }

    @Test
    void shouldRetryWhenFirstCallThrows() {
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenThrow(new RuntimeException("first attempt failed"))
                .thenReturn(validClassification());

        IntentClusterResult result = intentClusterService.generate("diag-1", context());

        assertEquals(5, result.clusters().size());
        verify(intentClusterAiService, times(2)).inferClassification("探镜", "", "");
    }

    @Test
    void shouldThrowBusinessExceptionAfterRetriesExhausted() {
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenThrow(new RuntimeException("always failed"));

        assertThrows(com.geoprobe.common.BusinessException.class,
                () -> intentClusterService.generate("diag-1", context()));

        verify(intentClusterAiService, times(3)).inferClassification("探镜", "", "");
    }

    @Test
    void shouldThrowWhenAllPreferencesInvalid() {
        FcbClassification invalidPreference = FcbClassification.builder()
                .category("浴室置物架")
                .involvement("LOW")
                .parameterValid(false)
                .experienceValid(false)
                .trustValid(false)
                .categoryAttributes(List.of("材质"))
                .competitors(List.of("竞品A"))
                .build();

        assertThrows(com.geoprobe.common.BusinessException.class, () -> intentClusterService.generateFromClassification(
                invalidPreference,
                DiagnosisConfig.standardEdition(),
                "STANDARD"
        ));
    }

    @Test
    void shouldApplyHighToLowWeightRatioAsThreeToOne() {
        FcbClassification classification = FcbClassification.builder()
                .category("男士衬衫")
                .involvement("LOW")
                .parameterValid(true)
                .experienceValid(true)
                .trustValid(true)
                .categoryAttributes(List.of("面料", "版型"))
                .competitors(List.of("竞品A", "竞品B"))
                .build();

        IntentClusterResult result = intentClusterService.generateFromClassification(
                classification,
                DiagnosisConfig.deepEdition(),
                "DEEP"
        );

        Map<String, Double> weights = result.clusters().stream()
                .collect(java.util.stream.Collectors.toMap(IntentCluster::clusterId, IntentCluster::weight));
        double highWeight = weights.get("HIGH_PARAMETER");
        double lowWeight = weights.get("LOW_PARAMETER");
        assertEquals(3.0d, highWeight / lowWeight, 0.0001d);
        assertTrue(result.clusters().stream()
                .filter(cluster -> cluster.clusterId().startsWith("HIGH_"))
                .allMatch(cluster -> cluster.weight() > lowWeight));
    }

    @Test
    void shouldKeepNonDiscriminatingKeywordsEmptyWhenModelOutputMissing() {
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("肿瘤医院")
                        .involvement("HIGH")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(true)
                        .categoryAttributes(List.of("科室能力", "治疗方案"))
                        .competitors(List.of("竞品A", "竞品B"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-kw", context());

        assertTrue(result.fcbClassification().getNonDiscriminatingKeywords().isEmpty());
    }

    @Test
    void shouldFallbackInvolvementToHighWhenInvalidAndWriteDiagLog() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("肿瘤医院")
                        .involvement("MEDIUM")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(true)
                        .categoryAttributes(List.of("科室能力"))
                        .competitors(List.of("竞品A"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-involvement", context());

        assertEquals("HIGH", result.fcbClassification().getInvolvement());
        assertTrue(hasLog(appender, "fcb.involvement.fallback=HIGH"));
    }

    @Test
    void shouldFallbackPreferenceValidToTrueWhenNonBooleanAndWriteDiagLog() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("肿瘤医院")
                        .involvement("HIGH")
                        .parameterValid("yes")
                        .experienceValid("ok")
                        .trustValid(1)
                        .categoryAttributes(List.of("科室能力"))
                        .competitors(List.of("竞品A"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-valid", context());

        assertTrue(result.fcbClassification().isParameterValid());
        assertTrue(result.fcbClassification().isExperienceValid());
        assertTrue(result.fcbClassification().isTrustValid());
        assertTrue(hasLog(appender, "fcb.parameter_valid.fallback=true"));
        assertTrue(hasLog(appender, "fcb.experience_valid.fallback=true"));
        assertTrue(hasLog(appender, "fcb.trust_valid.fallback=true"));
    }

    @Test
    void shouldFallbackCategoryToBrandNameWhenMissingAndWriteDiagLog() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("")
                        .involvement("HIGH")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(true)
                        .categoryAttributes(List.of("科室能力"))
                        .competitors(List.of("竞品A"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-category", context());

        assertEquals("探镜", result.fcbClassification().getCategory());
        assertTrue(hasLog(appender, "fcb.category.fallback=探镜"));
    }

    @Test
    void shouldFallbackCategoryAttributesWhenMissingAndWriteDiagLog() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("肿瘤医院")
                        .involvement("HIGH")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(true)
                        .categoryAttributes(List.of())
                        .competitors(List.of("竞品A"))
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-attrs", context());

        assertEquals(List.of("（品类属性待补充）"), result.fcbClassification().getCategoryAttributes());
        assertTrue(hasLog(appender, "fcb.categoryAttributes.fallback"));
    }

    @Test
    void shouldLogWhenNonDiscriminatingKeywordsMissing() {
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        when(intentClusterAiService.inferClassification("探镜", "", ""))
                .thenReturn(FcbClassification.builder()
                        .category("肿瘤医院")
                        .involvement("HIGH")
                        .parameterValid(true)
                        .experienceValid(true)
                        .trustValid(true)
                        .categoryAttributes(List.of("科室能力"))
                        .competitors(List.of("竞品A"))
                        .nonDiscriminatingKeywords(List.of())
                        .build());

        IntentClusterResult result = intentClusterService.generate("diag-keywords", context());

        assertFalse(result.fcbClassification().getNonDiscriminatingKeywords() == null);
        assertTrue(result.fcbClassification().getNonDiscriminatingKeywords().isEmpty());
        assertTrue(hasLog(appender, "fcb.nonDiscriminatingKeywords.empty"));
    }

    private FcbClassification validClassification() {
        return FcbClassification.builder()
                .category("浴室置物架")
                .involvement("HIGH")
                .parameterValid(true)
                .experienceValid(true)
                .trustValid(true)
                .categoryAttributes(List.of("材质", "承重", "安装方式"))
                .competitors(List.of("竞品A", "竞品B", "竞品C"))
                .build();
    }

    private CreativeExecutionContext context() {
        return new CreativeExecutionContext(
                "探镜",
                "浴室置物架",
                List.of("竞品A", "竞品B"),
                null,
                null
        );
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(IntentClusterService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private boolean hasLog(ListAppender<ILoggingEvent> appender, String keyword) {
        return appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains(keyword));
    }
}

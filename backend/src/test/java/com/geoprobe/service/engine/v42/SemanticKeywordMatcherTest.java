package com.geoprobe.service.engine.v42;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticKeywordMatcherTest {

    @Test
    void shouldMatchByDimensionAndAbstractCode() {
        String rendered = "矿泉水现在买哪个更稳，不想踩坑？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "想确认后直接买"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "怕踩坑、怕出问题"));
        assertFalse(SemanticKeywordMatcher.matches(rendered, "D3", "identity_freedom", "就要与众不同"));
    }

    @Test
    void shouldProvideCompactFallbackKeyword() {
        assertEquals("现在买哪个更稳",
                SemanticKeywordMatcher.primaryKeyword("D2", "resource_acquisition", "想确认后直接买"));
        assertEquals("不想踩坑",
                SemanticKeywordMatcher.primaryKeyword("D3", "subsistence_protection", "怕踩坑、怕出问题"));
    }

    @Test
    void shouldAcceptNaturalParaphraseInsteadOfOnlyCanonicalPhrase() {
        String rendered = "这款水我已经准备直接下单了，就想确认别翻车。";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "确认后直接买"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "怕出问题"));
    }

    @Test
    void shouldAcceptBuyBeforeConfirmPhrasingAsResourceAcquisition() {
        String rendered = "准备买包装饮用水前，先确认哪一点更不容易出错？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "买前先确认"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "不容易出错"));
    }

    @Test
    void shouldAcceptSeeDifferencesBeforeDecidingAsAcquisitionAndUnderstanding() {
        String rendered = "包装饮用水准备定前，先看清哪几个差别更有用？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "准备定前"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "understanding_competence", "先看清差别"));
    }

    @Test
    void shouldAcceptFirstLookQuestionAsInformationalDirected() {
        String rendered = "想省点事的话，包装饮用水先看哪个？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想听个明确结论"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "creation_idleness", "想省点事"));
    }

    @Test
    void shouldAcceptLastCheckLanguageAsResourceAcquisition() {
        String rendered = "买天然水前，最后看哪点更稳？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "买前最后确认"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "更稳一点"));
    }

    @Test
    void shouldAcceptCompareBeforeDecisionLanguageAsUnderstandingCompetence() {
        String rendered = "买包装饮用水前，最后比哪一项差别就够了？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "下单前最后比一比"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "understanding_competence", "先分清差别"));
    }

    @Test
    void shouldAcceptDifferenceQuestionAsInformationalDirected() {
        String rendered = "现在买哪种饮用水好，差别在哪？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想听个明确结论"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "understanding_competence", "想分清差别"));
    }

    @Test
    void shouldAcceptCommonChoiceQuestionAsResourceAcquisition() {
        String rendered = "大家平时买饮用水一般选哪个牌子啊？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "买的话选哪个"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "affection_belonging_participation", "大家一般怎么选"));
    }

    @Test
    void shouldAcceptNaturalWorthBuyingQuestionAsResourceAcquisition() {
        String rendered = "这几个饮用水品牌里，哪个更值得买，不容易踩坑？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "临近购买前想确认后就定一个"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "怕踩坑"));
    }

    @Test
    void shouldAcceptNaturalSteadyChoiceQuestionAsResourceAcquisition() {
        String rendered = "这几个牌子的包装饮用水，哪个比较稳一点，怕买错？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "resource_acquisition", "现在买哪个更稳"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "怕买错"));
    }

    @Test
    void shouldAcceptNaturalIdentityFreedomQuestionAsInformationalDirected() {
        String rendered = "包装饮用水里，有没有什么牌子是既靠谱又不会太普通的？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想要个明确判断"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "identity_freedom", "不想太普通"));
    }

    @Test
    void shouldAcceptComparativeAdvantageQuestionAsInformationalDirected() {
        String rendered = "美的空调跟格力比，主要好在哪？我想尽快定个方向，又不想选得太普通？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想要个明确判断"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "identity_freedom", "不想太普通"));
    }

    @Test
    void shouldAcceptCommonChoiceQuestionAsInformationalDirectedWhenSeekingDirection() {
        String rendered = "现在买空调，大家一般会怎么选？我想尽快定一个方向？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想要个明确判断"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "affection_belonging_participation", "大家一般怎么选"));
    }

    @Test
    void shouldAcceptDifferenceWhereQuestionAsInformationalDirectedAndUnderstanding() {
        String rendered = "这几个空调牌子差别主要在哪儿，我大概有个方向了，想确认下？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想要个明确判断"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "understanding_competence", "想看明白"));
    }

    @Test
    void shouldAcceptBrowseThenDifferentiateQuestionAsUndirectedAndUnderstanding() {
        String rendered = "现在空调有哪些牌子可以先看看，顺便怎么区分它们？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_undirected", "先看看方向"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "understanding_competence", "想分清差别"));
    }

    @Test
    void shouldAcceptNaturalLowRiskQuestionAsInformationalDirected() {
        String rendered = "饮用水品牌里，哪个更不容易出问题？";

        assertTrue(SemanticKeywordMatcher.matches(rendered, "D2", "informational_directed", "想听个明确结论"));
        assertTrue(SemanticKeywordMatcher.matches(rendered, "D3", "subsistence_protection", "怕买错"));
    }
}

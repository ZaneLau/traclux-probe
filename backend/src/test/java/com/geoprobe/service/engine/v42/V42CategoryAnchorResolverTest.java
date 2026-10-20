package com.geoprobe.service.engine.v42;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V42CategoryAnchorResolverTest {

    @Test
    void shouldKeepExplicitAnchorsWhenFrontendProvidesThreeOrMore() {
        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolved =
                V42CategoryAnchorResolver.fromRequest("男装衬衫", List.of("男装衬衫", "衬衫", "通勤衬衫"));

        assertEquals(List.of("男装衬衫", "衬衫", "通勤衬衫"), resolved.anchors());
        assertFalse(resolved.fallbackAnchorMode());
        assertEquals("explicit_input", resolved.source());
    }

    @Test
    void shouldDeriveFallbackAnchorsFromCategoryWithoutBrandLeakage() {
        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolved =
                V42CategoryAnchorResolver.fromRequest("男装衬衫", List.of());

        assertEquals(List.of("男装衬衫", "衬衫", "男装衬衫品牌", "男装衬衫选购"), resolved.anchors());
        assertTrue(resolved.fallbackAnchorMode());
        assertEquals("backend_fallback", resolved.source());
    }

    @Test
    void shouldDeriveCompactTailAnchorForCompoundCategory() {
        V42CategoryAnchorResolver.ResolvedCategoryAnchors resolved =
                V42CategoryAnchorResolver.fromRequest("智能手机", List.of());

        assertEquals(List.of("智能手机", "手机", "智能手机品牌", "智能手机选购"), resolved.anchors());
        assertTrue(resolved.fallbackAnchorMode());
        assertEquals("backend_fallback", resolved.source());
    }

    @Test
    void shouldRejectUnderspecifiedExplicitAnchors() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> V42CategoryAnchorResolver.fromRequest("瓶装饮用水", List.of("瓶装饮用水", "饮用水")));

        assertTrue(ex.getMessage().contains("categoryAnchors"));
    }
}

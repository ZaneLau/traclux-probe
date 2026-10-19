package com.geoprobe.knowledge.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeAssetTimeSupportTest {

    @Test
    void shouldConvertUtcOffsetTimestampToProductLocalTime() {
        LocalDateTime value = KnowledgeAssetTimeSupport.toProductLocalDateTime(
                OffsetDateTime.parse("2026-05-03T16:02:29Z")
        );

        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 2, 29), value);
    }

    @Test
    void shouldConvertOffsetStringToProductLocalTime() {
        LocalDateTime value = KnowledgeAssetTimeSupport.toProductLocalDateTime("2026-05-03T16:02:29Z");

        assertEquals(LocalDateTime.of(2026, 5, 4, 0, 2, 29), value);
    }
}

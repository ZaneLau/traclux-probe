package com.geoprobe.knowledge.support;

import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class KnowledgeAssetTimeSupport {

    private static final ZoneId PRODUCT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private KnowledgeAssetTimeSupport() {
    }

    public static LocalDateTime toProductLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(PRODUCT_ZONE_ID).toLocalDateTime();
    }

    public static LocalDateTime toProductLocalDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (raw instanceof OffsetDateTime offsetDateTime) {
            return toProductLocalDateTime(offsetDateTime);
        }
        if (raw instanceof Timestamp timestamp) {
            return LocalDateTime.ofInstant(timestamp.toInstant(), PRODUCT_ZONE_ID);
        }
        if (raw instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), PRODUCT_ZONE_ID);
        }
        if (raw instanceof String text) {
            String normalized = text.trim();
            if (!StringUtils.hasText(normalized)) {
                return null;
            }
            try {
                return OffsetDateTime.parse(normalized).atZoneSameInstant(PRODUCT_ZONE_ID).toLocalDateTime();
            } catch (RuntimeException ignored) {
                try {
                    return LocalDateTime.parse(normalized);
                } catch (RuntimeException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }
}

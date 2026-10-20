package com.geoprobe.knowledge.repository;

import com.geoprobe.knowledge.support.KnowledgeAssetTimeSupport;

import java.time.LocalDateTime;

final class KnowledgeAssetJdbcReaders {

    private KnowledgeAssetJdbcReaders() {
    }

    static Integer toInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer integer) {
            return integer;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static LocalDateTime toLocalDateTime(Object raw) {
        return KnowledgeAssetTimeSupport.toProductLocalDateTime(raw);
    }
}

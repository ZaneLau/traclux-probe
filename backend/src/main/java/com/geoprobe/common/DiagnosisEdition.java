package com.geoprobe.common;

import com.geoprobe.service.shared.DiagnosisConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 诊断版本档位定义。
 */
public enum DiagnosisEdition {
    EXPERIENCE(0),
    STANDARD(1),
    DEEP(2);

    private final int code;

    DiagnosisEdition(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @JsonValue
    public String value() {
        return name();
    }

    public DiagnosisConfig toConfig() {
        return switch (this) {
            case EXPERIENCE -> DiagnosisConfig.experienceEdition();
            case STANDARD -> DiagnosisConfig.standardEdition();
            case DEEP -> DiagnosisConfig.deepEdition();
        };
    }

    @JsonCreator
    public static DiagnosisEdition fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return STANDARD;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (DiagnosisEdition edition : values()) {
            if (edition.name().equals(normalized)) {
                return edition;
            }
        }
        return STANDARD;
    }

    public static DiagnosisEdition fromCode(Integer code) {
        if (code == null) {
            return STANDARD;
        }
        for (DiagnosisEdition edition : values()) {
            if (edition.code == code) {
                return edition;
            }
        }
        return STANDARD;
    }
}

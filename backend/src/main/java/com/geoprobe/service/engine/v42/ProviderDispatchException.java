package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.RawResponse;
import org.springframework.util.StringUtils;

public class ProviderDispatchException extends RuntimeException {

    private final String errorCode;
    private final RawResponse rawResponse;

    public ProviderDispatchException(String errorCode, String message) {
        super(message);
        this.errorCode = normalizeErrorCode(errorCode);
        this.rawResponse = null;
    }

    public ProviderDispatchException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = normalizeErrorCode(errorCode);
        this.rawResponse = null;
    }

    public ProviderDispatchException(String errorCode, String message, RawResponse rawResponse) {
        super(message);
        this.errorCode = normalizeErrorCode(errorCode);
        this.rawResponse = rawResponse;
    }

    public String errorCode() {
        return errorCode;
    }

    public RawResponse rawResponse() {
        return rawResponse;
    }

    private static String normalizeErrorCode(String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return "UPSTREAM_PROVIDER_ERROR";
        }
        return errorCode.trim();
    }
}

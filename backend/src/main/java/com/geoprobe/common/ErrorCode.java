package com.geoprobe.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST, ErrorCategory.USER, false),
    UNAUTHORIZED(401, "未认证或Token过期", HttpStatus.UNAUTHORIZED, ErrorCategory.USER, false),
    FORBIDDEN(403, "无权限访问该资源", HttpStatus.FORBIDDEN, ErrorCategory.USER, false),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND, ErrorCategory.USER, false),
    USERNAME_EXISTS(1001, "用户名已存在", HttpStatus.BAD_REQUEST, ErrorCategory.USER, false),
    INVALID_CREDENTIALS(1002, "用户名或密码错误", HttpStatus.BAD_REQUEST, ErrorCategory.USER, false),
    AUTH_RATE_LIMITED(1003, "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS, ErrorCategory.RETRYABLE, true),
    CREDITS_INSUFFICIENT(2001, "Credits 不足", HttpStatus.PAYMENT_REQUIRED, ErrorCategory.USER, false),
    DIAGNOSIS_NOT_FOUND(2002, "诊断任务不存在", HttpStatus.NOT_FOUND, ErrorCategory.USER, false),
    REQUEST_IN_PROGRESS(2003, "请求处理中，请稍后重试", HttpStatus.CONFLICT, ErrorCategory.RETRYABLE, true),
    SERVICE_ACCESS_REQUIRED(2004, "当前账号未开通服务", HttpStatus.PAYMENT_REQUIRED, ErrorCategory.USER, false),
    LLM_CALL_FAILED(3001, "LLM 调用失败", HttpStatus.BAD_GATEWAY, ErrorCategory.MODEL, true),
    LLM_TIMEOUT(3002, "LLM 调用超时", HttpStatus.GATEWAY_TIMEOUT, ErrorCategory.RETRYABLE, true),
    INTERNAL_ERROR(9999, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.SYSTEM, false);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
    private final ErrorCategory category;
    private final boolean retryable;

    ErrorCode(int code, String message, HttpStatus httpStatus, ErrorCategory category, boolean retryable) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.category = category;
        this.retryable = retryable;
    }
}

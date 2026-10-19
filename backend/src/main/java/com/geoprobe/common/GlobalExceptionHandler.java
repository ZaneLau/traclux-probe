package com.geoprobe.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        if (errorCode.getCategory() == ErrorCategory.SYSTEM) {
            if (ex.getCause() == null) {
                log.error("BusinessException system error, code={}, retryable={}, msg={}",
                        errorCode.getCode(), errorCode.isRetryable(), ex.getMessage());
            } else {
                log.error("BusinessException system error, code={}, retryable={}, msg={}",
                        errorCode.getCode(), errorCode.isRetryable(), ex.getMessage(), ex);
            }
        } else {
            log.warn("BusinessException category={}, retryable={}, code={}, msg={}",
                    errorCode.getCategory(), errorCode.isRetryable(), errorCode.getCode(), ex.getMessage());
        }
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(Result.error(errorCode, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.BAD_REQUEST.getMessage();
        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.error(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.error(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        return ResponseEntity
                .status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.error(ErrorCode.INTERNAL_ERROR));
    }
}

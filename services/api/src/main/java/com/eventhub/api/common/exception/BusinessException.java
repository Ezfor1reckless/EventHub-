package com.eventhub.api.common.exception;

import lombok.Getter;

/**
 * 可预期的业务异常。由 {@link GlobalExceptionHandler} 翻译成统一响应，
 * 不打印堆栈（它是正常业务流转，不是故障）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }
}

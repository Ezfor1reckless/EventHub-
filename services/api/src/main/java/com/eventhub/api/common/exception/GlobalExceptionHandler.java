package com.eventhub.api.common.exception;

import com.eventhub.api.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常到统一响应的翻译层。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler} 是为了让 Spring MVC 内建的
 * 各类 4xx（参数缺失、方法不支持、消息不可读等）走同一套响应结构，
 * 而不是回落到默认的 {@code ProblemDetail}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 业务异常：预期内的流转，按 WARN 记录、不打堆栈。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("业务异常 code={} message={}", code.code(), ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.code(), ex.getMessage()));
    }

    /** 认证失败：通常是 Filter 之外的地方抛出的，兜底保持一致。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return build(ErrorCode.UNAUTHORIZED);
    }

    /** 鉴权失败：已登录但角色不够。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("鉴权失败: {}", ex.getMessage());
        return build(ErrorCode.FORBIDDEN);
    }

    /** 兜底：真正的故障，必须留下堆栈。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("未预期异常", ex);
        return build(ErrorCode.INTERNAL_ERROR);
    }

    /** Bean Validation 失败：把字段级错误放进 data，前端可直接标红。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        log.warn("参数校验失败: {}", fieldErrors);

        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.getStatus())
                .body(new ApiResponse<>(false, code.code(), code.getDefaultMessage(), fieldErrors));
    }

    /** 请求体不是合法 JSON，或枚举值无法转换。 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ErrorCode code = ErrorCode.MALFORMED_REQUEST;
        log.warn("请求体不可读: {}", ex.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.code(), code.getDefaultMessage()));
    }

    private ResponseEntity<ApiResponse<Object>> build(ErrorCode code) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.code(), code.getDefaultMessage()));
    }

    /** 保留给需要自定义状态码的测试用例。 */
    static ResponseEntity<ApiResponse<Object>> of(HttpStatus status, ErrorCode code) {
        return ResponseEntity.status(status).body(ApiResponse.error(code.code(), code.getDefaultMessage()));
    }
}

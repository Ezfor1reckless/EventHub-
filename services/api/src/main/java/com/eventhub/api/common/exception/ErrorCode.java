package com.eventhub.api.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 全部业务错误码集中在此。新增错误一律走这里，不要在业务代码里硬编码字符串。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "请求格式不正确"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未认证或凭证已失效"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "凭证已被注销"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "无权访问该资源"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "账号已被禁用"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),

    CONFLICT(HttpStatus.CONFLICT, "资源冲突"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "该邮箱已注册"),

    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    /** 错误码字符串即枚举名，前端据此做分支判断。 */
    public String code() {
        return name();
    }
}

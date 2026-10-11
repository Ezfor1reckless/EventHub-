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

    // ---- 活动 ----
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "活动不存在"),
    EVENT_NOT_OPEN(HttpStatus.CONFLICT, "活动当前不接受报名"),
    EVENT_CANNOT_EDIT(HttpStatus.CONFLICT, "当前状态的活动不可编辑"),
    EVENT_TIME_INVALID(HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间"),
    NOT_EVENT_ORGANIZER(HttpStatus.FORBIDDEN, "只有活动组织者可以执行该操作"),

    // ---- 报名 ----
    REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "报名记录不存在"),
    REGISTRATION_NOT_CANCELLABLE(HttpStatus.CONFLICT, "该报名当前状态不可取消"),

    // ---- 签到 ----
    INVALID_CHECKIN_TOKEN(HttpStatus.UNAUTHORIZED, "签到码无效或已过期"),
    REGISTRATION_NOT_CONFIRMED(HttpStatus.CONFLICT, "只有已确认的报名可以签到"),
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "该报名已签到，请勿重复签到"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    /** 错误码字符串即枚举名，前端据此做分支判断。 */
    public String code() {
        return name();
    }
}

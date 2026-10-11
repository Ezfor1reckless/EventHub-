package com.eventhub.api.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应包装。所有 Controller 一律返回该结构，前端只需判断 {@code success}。
 */
@Schema(description = "统一响应结构")
public record ApiResponse<T>(
        @Schema(description = "是否成功") boolean success,
        @Schema(description = "业务码，成功固定为 OK") String code,
        @Schema(description = "人类可读的提示") String message,
        @Schema(description = "业务数据，失败时为 null") T data
) {

    public static final String OK_CODE = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, OK_CODE, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, OK_CODE, "success", null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}

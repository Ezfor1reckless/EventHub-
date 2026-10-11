package com.eventhub.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "刷新令牌请求")
public record RefreshRequest(

        @Schema(description = "登录时返回的 refreshToken")
        @NotBlank(message = "refreshToken 不能为空")
        String refreshToken
) {
}

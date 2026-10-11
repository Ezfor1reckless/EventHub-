package com.eventhub.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(

        @Schema(example = "alice@example.com")
        @NotBlank(message = "邮箱不能为空")
        String email,

        @Schema(example = "s3cretPass")
        @NotBlank(message = "密码不能为空")
        String password
) {
}

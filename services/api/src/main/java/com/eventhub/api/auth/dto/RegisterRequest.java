package com.eventhub.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册请求")
public record RegisterRequest(

        @Schema(example = "alice@example.com")
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255")
        String email,

        @Schema(description = "至少 8 位，且需同时包含字母和数字", example = "s3cretPass")
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需在 8-72 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母和数字")
        String password,

        @Schema(example = "爱丽丝")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过 64")
        String nickname,

        @Schema(description = "可选，中国大陆手机号", example = "13800138000")
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone
) {
    /**
     * BCrypt 只取前 72 字节，超出部分会被静默忽略——那会让「长密码更安全」变成错觉。
     * 这里在入口就限制到 72，避免用户以为自己设了更长的密码。
     */
    public RegisterRequest {
        if (phone != null && phone.isBlank()) {
            phone = null;
        }
    }
}

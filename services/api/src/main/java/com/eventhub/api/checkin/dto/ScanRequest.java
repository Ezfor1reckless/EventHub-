package com.eventhub.api.checkin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "扫码签到请求")
public record ScanRequest(
        @Schema(description = "二维码里解出的签到码") @NotBlank(message = "签到码不能为空") String token
) {
}

package com.eventhub.api.checkin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "签到码会话")
public record CheckInSessionResponse(
        @Schema(description = "客户端据此渲染二维码") String token,
        @Schema(description = "有效期秒数") long expiresInSeconds
) {
}

package com.eventhub.api.checkin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "手工补签请求")
public record ManualCheckInRequest(
        @Schema(description = "用户的电子票券编号") @NotBlank(message = "票券码不能为空") String ticketCode
) {
}

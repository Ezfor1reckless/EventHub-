package com.eventhub.api.registration.dto;

import com.eventhub.api.event.dto.EventSummary;
import com.eventhub.api.registration.domain.Registration;
import com.eventhub.api.registration.domain.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "报名信息")
public record RegistrationResponse(
        Long id,
        EventSummary event,
        RegistrationStatus status,
        @Schema(description = "电子票券编号") String ticketCode,
        @Schema(description = "进入候补的时刻，非候补为 null") Instant waitlistedAt,
        Instant registeredAt,
        Instant cancelledAt
) {
    public static RegistrationResponse of(Registration registration, EventSummary event) {
        return new RegistrationResponse(
                registration.getId(),
                event,
                registration.getStatus(),
                registration.getTicketCode(),
                registration.getWaitlistedAt(),
                registration.getRegisteredAt(),
                registration.getCancelledAt()
        );
    }
}

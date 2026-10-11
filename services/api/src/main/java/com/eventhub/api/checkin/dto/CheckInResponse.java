package com.eventhub.api.checkin.dto;

import com.eventhub.api.checkin.domain.CheckIn;
import com.eventhub.api.checkin.domain.CheckInMethod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "签到结果")
public record CheckInResponse(
        Long id,
        Long eventId,
        Long registrationId,
        String ticketCode,
        Instant checkedInAt,
        CheckInMethod method
) {
    public static CheckInResponse of(CheckIn checkIn, String ticketCode) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getEventId(),
                checkIn.getRegistrationId(),
                ticketCode,
                checkIn.getCheckedInAt(),
                checkIn.getMethod()
        );
    }
}

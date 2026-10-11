package com.eventhub.api.event.dto;

import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.domain.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "活动信息")
public record EventResponse(
        Long id,
        Long organizerId,
        String title,
        String description,
        String coverUrl,
        String location,
        Instant startAt,
        Instant endAt,
        int capacity,
        EventStatus status,
        @Schema(description = "已确认名额数（含已签到）") long confirmedCount,
        @Schema(description = "候补人数") long waitlistedCount,
        @Schema(description = "剩余名额，不会为负") int seatsRemaining,
        Instant createdAt,
        Instant updatedAt
) {
    public static EventResponse of(Event event, long confirmedCount, long waitlistedCount) {
        return new EventResponse(
                event.getId(),
                event.getOrganizerId(),
                event.getTitle(),
                event.getDescription(),
                event.getCoverUrl(),
                event.getLocation(),
                event.getStartAt(),
                event.getEndAt(),
                event.getCapacity(),
                event.getStatus(),
                confirmedCount,
                waitlistedCount,
                // 容量可能被组织者下调到低于已确认人数，此时剩余按 0 显示而不是负数
                Math.max(0, event.getCapacity() - (int) confirmedCount),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}

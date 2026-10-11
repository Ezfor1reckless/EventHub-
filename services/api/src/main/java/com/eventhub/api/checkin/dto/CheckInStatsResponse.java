package com.eventhub.api.checkin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "活动签到统计")
public record CheckInStatsResponse(
        Long eventId,
        @Schema(description = "已确认名额数（含已签到）") long confirmedCount,
        @Schema(description = "已签到人数") long checkedInCount,
        @Schema(description = "候补人数") long waitlistedCount,
        @Schema(description = "签到率 = 已签到 / 已确认，已确认 0 时为 0") double checkInRate
) {
    public static CheckInStatsResponse of(Long eventId, long confirmed, long checkedIn, long waitlisted) {
        double rate = confirmed == 0 ? 0d : (double) checkedIn / confirmed;
        return new CheckInStatsResponse(eventId, confirmed, checkedIn, waitlisted, rate);
    }
}

package com.eventhub.api.event.dto;

import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.domain.EventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 活动摘要。用于嵌在「我的报名」这类列表里，避免为了显示一个标题
 * 就让客户端再逐条请求活动详情。
 */
@Schema(description = "活动摘要")
public record EventSummary(
        Long id,
        String title,
        String location,
        String coverUrl,
        Instant startAt,
        Instant endAt,
        EventStatus status
) {
    public static EventSummary from(Event event) {
        return new EventSummary(
                event.getId(),
                event.getTitle(),
                event.getLocation(),
                event.getCoverUrl(),
                event.getStartAt(),
                event.getEndAt(),
                event.getStatus()
        );
    }
}

package com.eventhub.api.event.web;

import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.event.dto.EventRequest;
import com.eventhub.api.event.dto.EventResponse;
import com.eventhub.api.event.service.EventService;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "活动")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @Operation(summary = "活动列表", description = "只返回已发布的活动，公开可访问")
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> list(
            @PageableDefault(size = 20, sort = "startAt") Pageable pageable) {
        return ApiResponse.ok(eventService.listPublished(pageable));
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{id}")
    public ApiResponse<EventResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventService.get(id));
    }

    @Operation(summary = "创建活动", description = "创建后为草稿状态，需再调 publish 才会对外可见")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(@Valid @RequestBody EventRequest request) {
        return ApiResponse.ok(eventService.create(SecurityUtils.requireUserId(), request));
    }

    @Operation(summary = "更新活动", description = "全量替换。注意用的是 PUT 而不是 PATCH")
    @PutMapping("/{id}")
    public ApiResponse<EventResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody EventRequest request) {
        return ApiResponse.ok(eventService.update(SecurityUtils.requireUserId(), id, request));
    }

    @Operation(summary = "发布活动", description = "只有草稿状态可以发布")
    @PostMapping("/{id}/publish")
    public ApiResponse<EventResponse> publish(@PathVariable Long id) {
        return ApiResponse.ok(eventService.publish(SecurityUtils.requireUserId(), id));
    }

    @Operation(summary = "取消活动", description = "同时清空该活动的候补队列")
    @PostMapping("/{id}/cancel")
    public ApiResponse<EventResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(eventService.cancel(SecurityUtils.requireUserId(), id));
    }
}

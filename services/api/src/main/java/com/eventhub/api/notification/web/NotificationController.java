package com.eventhub.api.notification.web;

import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.notification.dto.NotificationResponse;
import com.eventhub.api.notification.service.NotificationQueryService;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "站内信")
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @Operation(summary = "我的站内信")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(
                notificationQueryService.listMine(SecurityUtils.requireUserId(), pageable));
    }

    @Operation(summary = "未读数量", description = "前端用于显示角标")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationQueryService.unreadCount(SecurityUtils.requireUserId()));
    }

    @Operation(summary = "标记已读")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationQueryService.markRead(SecurityUtils.requireUserId(), id);
        return ApiResponse.ok();
    }
}

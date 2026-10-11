package com.eventhub.api.user.web;

import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.event.dto.EventResponse;
import com.eventhub.api.event.service.EventService;
import com.eventhub.api.security.SecurityUtils;
import com.eventhub.api.user.domain.User;
import com.eventhub.api.user.dto.UserResponse;
import com.eventhub.api.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "当前用户")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final EventService eventService;

    @Operation(summary = "获取当前登录用户")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<UserResponse> me() {
        Long userId = SecurityUtils.requireUserId();
        User user = userRepository.findById(userId)
                // 令牌有效但用户已被删除：当作未认证处理，让客户端重新登录
                .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(UserResponse.from(user));
    }

    /**
     * 我组织的活动。放在 /me 命名空间下而不是 /events/mine，
     * 免得与 /events/{id} 的路径变量产生歧义。
     */
    @Operation(summary = "我组织的活动")
    @GetMapping("/events")
    public ApiResponse<PageResponse<EventResponse>> myEvents(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(eventService.listMine(SecurityUtils.requireUserId(), pageable));
    }
}

package com.eventhub.api.registration.web;

import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.registration.dto.RegistrationResponse;
import com.eventhub.api.registration.service.RegistrationService;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "报名")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @Operation(summary = "报名活动",
            description = "幂等：已报名时原样返回既有记录，不会重复占位。满员则自动进入候补。")
    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationResponse> register(@PathVariable Long eventId) {
        return ApiResponse.ok(registrationService.register(SecurityUtils.requireUserId(), eventId));
    }

    @Operation(summary = "取消报名", description = "名额会立即递补给候补队列的队首")
    @DeleteMapping("/registrations/{registrationId}")
    public ApiResponse<Void> cancel(@PathVariable Long registrationId) {
        registrationService.cancel(SecurityUtils.requireUserId(), registrationId);
        return ApiResponse.ok();
    }

    @Operation(summary = "我在某活动的报名",
            description = "没有报名时 data 为 null。活动详情页据此决定按钮状态")
    @GetMapping("/events/{eventId}/registrations/me")
    public ApiResponse<RegistrationResponse> myRegistration(@PathVariable Long eventId) {
        return ApiResponse.ok(
                registrationService.findMine(SecurityUtils.requireUserId(), eventId));
    }

    @Operation(summary = "我的报名")
    @GetMapping("/me/registrations")
    public ApiResponse<PageResponse<RegistrationResponse>> myRegistrations(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(registrationService.listMine(SecurityUtils.requireUserId(), pageable));
    }

    @Operation(summary = "活动的报名列表", description = "仅活动组织者或管理员可见")
    @GetMapping("/events/{eventId}/registrations")
    public ApiResponse<PageResponse<RegistrationResponse>> listByEvent(
            @PathVariable Long eventId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(
                registrationService.listByEvent(SecurityUtils.requireUserId(), eventId, pageable));
    }
}

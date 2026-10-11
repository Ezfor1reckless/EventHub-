package com.eventhub.api.checkin.web;

import com.eventhub.api.checkin.dto.CheckInResponse;
import com.eventhub.api.checkin.dto.CheckInSessionResponse;
import com.eventhub.api.checkin.dto.CheckInStatsResponse;
import com.eventhub.api.checkin.dto.ManualCheckInRequest;
import com.eventhub.api.checkin.dto.ScanRequest;
import com.eventhub.api.checkin.service.CheckInService;
import com.eventhub.api.common.api.ApiResponse;
import com.eventhub.api.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "签到")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @Operation(summary = "生成签到二维码",
            description = "组织者调用。返回的 token 有效 60 秒，期间可被多人重复扫描。")
    @PostMapping("/events/{eventId}/checkin-session")
    public ApiResponse<CheckInSessionResponse> createSession(@PathVariable Long eventId) {
        return ApiResponse.ok(checkInService.createSession(SecurityUtils.requireUserId(), eventId));
    }

    @Operation(summary = "扫码签到", description = "重复签到返回 409 ALREADY_CHECKED_IN")
    @PostMapping("/checkins/scan")
    public ApiResponse<CheckInResponse> scan(@Valid @RequestBody ScanRequest request) {
        return ApiResponse.ok(checkInService.scan(SecurityUtils.requireUserId(), request.token()));
    }

    @Operation(summary = "手工补签", description = "组织者按票券码补签，用于扫码失败的兜底")
    @PostMapping("/events/{eventId}/checkins/manual")
    public ApiResponse<CheckInResponse> manualCheckIn(@PathVariable Long eventId,
                                                      @Valid @RequestBody ManualCheckInRequest request) {
        return ApiResponse.ok(checkInService.manualCheckIn(
                SecurityUtils.requireUserId(), eventId, request.ticketCode()));
    }

    @Operation(summary = "签到统计", description = "仅活动组织者或管理员可见")
    @GetMapping("/events/{eventId}/checkins")
    public ApiResponse<CheckInStatsResponse> stats(@PathVariable Long eventId) {
        return ApiResponse.ok(checkInService.stats(SecurityUtils.requireUserId(), eventId));
    }
}

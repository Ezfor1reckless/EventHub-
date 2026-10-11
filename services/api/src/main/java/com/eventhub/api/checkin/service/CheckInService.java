package com.eventhub.api.checkin.service;

import com.eventhub.api.checkin.domain.CheckIn;
import com.eventhub.api.checkin.domain.CheckInMethod;
import com.eventhub.api.checkin.dto.CheckInSessionResponse;
import com.eventhub.api.checkin.dto.CheckInStatsResponse;
import com.eventhub.api.checkin.dto.CheckInResponse;
import com.eventhub.api.audit.service.AuditService;
import com.eventhub.api.checkin.repository.CheckInRepository;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.common.ratelimit.RateLimitService;
import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.domain.EventStatus;
import com.eventhub.api.event.repository.EventRepository;
import com.eventhub.api.registration.domain.Registration;
import com.eventhub.api.registration.domain.RegistrationStatus;
import com.eventhub.api.outbox.OutboxEventTypes;
import com.eventhub.api.registration.repository.RegistrationRepository;
import com.eventhub.api.security.SecurityUtils;
import com.eventhub.api.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private static final EnumSet<RegistrationStatus> SEAT_STATUSES =
            EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.CHECKED_IN);

    /**
     * 扫码限流，按用户计。
     * 扫码失败重试很正常，所以额度给得比登录宽；目的是挡住拿脚本穷举签到码。
     */
    private static final int SCAN_LIMIT = 20;
    private static final Duration SCAN_WINDOW = Duration.ofMinutes(1);

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInTokenService checkInTokenService;
    private final AuditService auditService;
    private final RateLimitService rateLimitService;

    /** 组织者发起签到会话，拿到 60 秒有效的动态二维码令牌。 */
    @Transactional
    public CheckInSessionResponse createSession(Long actorId, Long eventId) {
        Event event = requireOrganizer(actorId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw BusinessException.of(ErrorCode.EVENT_NOT_OPEN, "活动已取消，无法开始签到");
        }

        CheckInTokenService.IssuedToken issued = checkInTokenService.issue(eventId);
        return new CheckInSessionResponse(issued.token(), issued.ttl().toSeconds());
    }

    /** 用户扫组织者投屏的二维码签到。 */
    @Transactional
    public CheckInResponse scan(Long userId, String token) {
        rateLimitService.check("checkin", String.valueOf(userId), SCAN_LIMIT, SCAN_WINDOW);

        Long eventId = checkInTokenService.resolveEventId(token)
                .orElseThrow(() -> BusinessException.of(ErrorCode.INVALID_CHECKIN_TOKEN));

        // 活动可能已被删除（令牌还有 TTL 时）
        eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        Registration registration = registrationRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND, "你没有报名该活动"));

        return performCheckIn(registration, CheckInMethod.QR_SCAN, null);
    }

    /** 组织者按票券码人工补签（用户手机没电、扫码失败时的兜底）。 */
    @Transactional
    public CheckInResponse manualCheckIn(Long actorId, Long eventId, String ticketCode) {
        requireOrganizer(actorId, eventId);

        Registration registration = registrationRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND));

        if (!registration.getEventId().equals(eventId)) {
            // 票券真实存在但属于别的活动，不能泄露它属于哪个活动
            throw BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND, "该票券不属于此活动");
        }

        return performCheckIn(registration, CheckInMethod.MANUAL, actorId);
    }

    @Transactional(readOnly = true)
    public CheckInStatsResponse stats(Long actorId, Long eventId) {
        requireOrganizer(actorId, eventId);

        long confirmed = registrationRepository.countByEventIdAndStatusIn(eventId, SEAT_STATUSES);
        long checkedIn = checkInRepository.countByEventId(eventId);
        long waitlisted = registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED);

        return CheckInStatsResponse.of(eventId, confirmed, checkedIn, waitlisted);
    }

    // ---------- 内部 ----------

    private CheckInResponse performCheckIn(Registration registration, CheckInMethod method, Long operatorId) {
        if (registration.getStatus() == RegistrationStatus.CHECKED_IN
                || checkInRepository.existsByRegistrationId(registration.getId())) {
            throw BusinessException.of(ErrorCode.ALREADY_CHECKED_IN);
        }
        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            // 候补者、已取消者都不能签到
            throw BusinessException.of(ErrorCode.REGISTRATION_NOT_CONFIRMED);
        }

        registration.checkIn();

        CheckIn checkIn;
        try {
            // saveAndFlush 是必需的：唯一索引冲突必须在此刻抛出。
            // 若等到事务提交时才 flush，异常会发生在 try/catch 之外，捕获不到。
            checkIn = checkInRepository.saveAndFlush(new CheckIn(
                    registration.getId(),
                    registration.getEventId(),
                    registration.getUserId(),
                    method,
                    operatorId));
        } catch (DataIntegrityViolationException ex) {
            // 同一个人两个并发签到请求，数据库唯一索引拦下了第二条。
            // 这是最后一道防线，前面 existsByRegistrationId 的判断在并发下不可靠。
            log.warn("并发重复签到被唯一索引拦下 registrationId={}", registration.getId());
            throw BusinessException.of(ErrorCode.ALREADY_CHECKED_IN);
        }

        // 自助扫码时操作者就是签到者本人；人工补签时是组织者
        Long actorId = operatorId != null ? operatorId : registration.getUserId();
        auditService.record(actorId, OutboxEventTypes.AUDIT_CHECK_IN,
                "Registration", registration.getId(), "method=" + method);

        return CheckInResponse.of(checkIn, registration.getTicketCode());
    }

    private Event requireOrganizer(Long actorId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        boolean isAdmin = SecurityUtils.currentPrincipal()
                .map(actor -> actor.hasRole(Role.ADMIN)).orElse(false);

        if (!event.isOwnedBy(actorId) && !isAdmin) {
            throw BusinessException.of(ErrorCode.NOT_EVENT_ORGANIZER);
        }
        return event;
    }
}

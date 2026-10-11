package com.eventhub.api.registration.service;

import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.dto.EventSummary;
import com.eventhub.api.event.repository.EventRepository;
import com.eventhub.api.registration.domain.Registration;
import com.eventhub.api.registration.domain.RegistrationStatus;
import com.eventhub.api.registration.dto.RegistrationResponse;
import com.eventhub.api.registration.repository.RegistrationRepository;
import com.eventhub.api.security.SecurityUtils;
import com.eventhub.api.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final EnumSet<RegistrationStatus> SEAT_STATUSES =
            EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.CHECKED_IN);

    /** Crockford Base32 字母表：去掉了 I / L / O / U，避免和 1 / 0 以及脏话混淆。 */
    private static final char[] TICKET_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TICKET_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;

    /**
     * 报名。满员则自动进入候补。
     *
     * <p><b>加锁顺序约定：永远先锁活动行，再锁报名行。</b>
     * 全项目统一这一个顺序是死锁的唯一防线——见 {@link #cancel} 的说明。
     */
    @Transactional
    public RegistrationResponse register(Long userId, Long eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        if (!event.getStatus().isRegistrationOpen()) {
            throw BusinessException.of(ErrorCode.EVENT_NOT_OPEN);
        }

        Registration registration = registrationRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElse(null);

        // 幂等：已有生效报名就原样返回。客户端重试、用户双击提交都落到这里，
        // 不会重复占位，也不会报错。
        if (registration != null && registration.getStatus().isActive()) {
            return toResponse(registration, event);
        }

        String ticketCode = generateTicketCode();
        if (registration == null) {
            registration = new Registration(eventId, userId, ticketCode);
        } else {
            // 取消后重新报名：复用同一行，票券码换新
            registration.reactivate(ticketCode);
        }

        // count 查询会先触发 flush，所以这里读到的是最新计数
        long confirmed = registrationRepository.countByEventIdAndStatusIn(eventId, SEAT_STATUSES);
        if (confirmed < event.getCapacity()) {
            registration.confirm();
        } else {
            registration.waitlist();
            log.info("活动 {} 已满，用户 {} 进入候补", eventId, userId);
        }

        registrationRepository.save(registration);
        return toResponse(registration, event);
    }

    /**
     * 取消报名。付费/候补的递补在这里触发。
     *
     * <p>调用者可以是报名本人，也可以是该活动的组织者。
     */
    @Transactional
    public void cancel(Long actorId, Long registrationId) {
        // 先做一次无锁读，只是为了知道该锁哪个活动行。
        // 它读到的状态不可信——拿到锁之后会重新读一遍。
        Registration snapshot = registrationRepository.findById(registrationId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND));

        // 与 register() 保持同样的加锁顺序：活动 → 报名。
        // 如果这里反过来（先锁报名再锁活动），两个并发事务会各持一把锁互相等待，
        // 直接死锁。顺序统一比锁本身更重要。
        Event event = eventRepository.findByIdForUpdate(snapshot.getEventId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        // 等锁期间状态可能已被别人改动，重新读
        Registration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND));

        boolean isSelf = registration.getUserId().equals(actorId);
        boolean isOrganizer = event.isOwnedBy(actorId);
        boolean isAdmin = SecurityUtils.currentPrincipal()
                .map(actor -> actor.hasRole(Role.ADMIN)).orElse(false);

        if (!isSelf && !isOrganizer && !isAdmin) {
            // 返回「不存在」而非「无权限」，避免泄露该报名 id 是否有效
            throw BusinessException.of(ErrorCode.REGISTRATION_NOT_FOUND);
        }

        if (registration.getStatus() == RegistrationStatus.CHECKED_IN) {
            throw BusinessException.of(ErrorCode.REGISTRATION_NOT_CANCELLABLE, "已签到的报名不能取消");
        }
        if (!registration.getStatus().isActive()) {
            throw BusinessException.of(ErrorCode.REGISTRATION_NOT_CANCELLABLE);
        }

        boolean heldSeat = registration.getStatus().occupiesSeat();
        registration.cancel();

        // 只有在确实空出一个名额时才需要递补。候补者取消不影响名额。
        if (heldSeat) {
            promoteFromWaitlist(event);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listMine(Long userId, Pageable pageable) {
        Page<Registration> page = registrationRepository.findByUserIdOrderByRegisteredAtDesc(userId, pageable);
        Map<Long, Event> events = loadEvents(page.getContent());
        return PageResponse.of(page, r -> toResponse(r, events.get(r.getEventId())));
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listByEvent(Long actorId, Long eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        boolean isOrganizer = event.isOwnedBy(actorId);
        boolean isAdmin = SecurityUtils.currentPrincipal()
                .map(actor -> actor.hasRole(Role.ADMIN)).orElse(false);
        if (!isOrganizer && !isAdmin) {
            throw BusinessException.of(ErrorCode.NOT_EVENT_ORGANIZER);
        }

        Page<Registration> page = registrationRepository.findByEventIdOrderByRegisteredAtAsc(eventId, pageable);
        return PageResponse.of(page, r -> toResponse(r, event));
    }

    @Transactional(readOnly = true)
    public long countWaitlist(Long eventId) {
        return registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED);
    }

    // ---------- 内部 ----------

    /**
     * 递补：名额空出后，按候补时间顺序把队首拉进 CONFIRMED。
     *
     * <p>用 while 而不是 if：容量可能被组织者上调、或者一次空出多个名额，
     * 一次调用应当把能补的都补上。
     */
    private void promoteFromWaitlist(Event event) {
        long confirmed = registrationRepository.countByEventIdAndStatusIn(event.getId(), SEAT_STATUSES);

        while (confirmed < event.getCapacity()) {
            Optional<Registration> next = registrationRepository
                    .findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(
                            event.getId(), RegistrationStatus.WAITLISTED);
            if (next.isEmpty()) {
                return;
            }
            Registration promoted = next.get();
            promoted.confirm();
            confirmed++;
            log.info("活动 {} 递补：用户 {} 由候补转为已确认", event.getId(), promoted.getUserId());
        }
    }

    /** 一次性把整页报名涉及的活动取回来，避免逐条查活动造成 N+1。 */
    private Map<Long, Event> loadEvents(List<Registration> registrations) {
        List<Long> eventIds = registrations.stream().map(Registration::getEventId).distinct().toList();
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
    }

    private static RegistrationResponse toResponse(Registration registration, Event event) {
        return RegistrationResponse.of(registration, EventSummary.from(event));
    }

    private static String generateTicketCode() {
        StringBuilder sb = new StringBuilder(TICKET_LENGTH);
        for (int i = 0; i < TICKET_LENGTH; i++) {
            sb.append(TICKET_ALPHABET[RANDOM.nextInt(TICKET_ALPHABET.length)]);
        }
        return sb.toString();
    }
}

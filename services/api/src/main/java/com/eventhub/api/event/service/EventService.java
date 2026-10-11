package com.eventhub.api.event.service;

import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.domain.EventStatus;
import com.eventhub.api.event.dto.EventRequest;
import com.eventhub.api.event.dto.EventResponse;
import com.eventhub.api.event.repository.EventRepository;
import com.eventhub.api.registration.domain.Registration;
import com.eventhub.api.registration.domain.RegistrationStatus;
import com.eventhub.api.registration.repository.EventRegistrationCount;
import com.eventhub.api.registration.repository.RegistrationRepository;
import com.eventhub.api.security.SecurityUtils;
import com.eventhub.api.user.domain.Role;
import com.eventhub.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final EnumSet<RegistrationStatus> SEAT_STATUSES =
            EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.CHECKED_IN);
    private static final EnumSet<RegistrationStatus> WAITLIST_STATUSES =
            EnumSet.of(RegistrationStatus.WAITLISTED);

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;

    @Transactional
    public EventResponse create(Long userId, EventRequest request) {
        validateTimeRange(request);
        grantOrganizerRole(userId);

        Event event = Event.builder()
                .organizerId(userId)
                .title(request.title().trim())
                .description(request.description())
                .coverUrl(request.coverUrl())
                .location(request.location().trim())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .capacity(request.capacity())
                .build();

        eventRepository.save(event);
        log.info("创建活动 eventId={} organizerId={}", event.getId(), userId);
        return EventResponse.of(event, 0L, 0L);
    }

    @Transactional
    public EventResponse update(Long userId, Long eventId, EventRequest request) {
        validateTimeRange(request);

        Event event = requireOwnership(userId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.FINISHED) {
            throw BusinessException.of(ErrorCode.EVENT_CANNOT_EDIT);
        }

        event.updateDetails(
                request.title().trim(),
                request.description(),
                request.coverUrl(),
                request.location().trim(),
                request.startAt(),
                request.endAt(),
                request.capacity()
        );
        return toResponse(event);
    }

    @Transactional
    public EventResponse publish(Long userId, Long eventId) {
        Event event = requireOwnership(userId, eventId);
        try {
            event.publish();
        } catch (IllegalStateException ex) {
            throw BusinessException.of(ErrorCode.EVENT_CANNOT_EDIT, ex.getMessage());
        }
        log.info("发布活动 eventId={}", eventId);
        return toResponse(event);
    }

    @Transactional
    public EventResponse cancel(Long userId, Long eventId) {
        Event event = requireOwnership(userId, eventId);
        try {
            event.cancel();
        } catch (IllegalStateException ex) {
            throw BusinessException.of(ErrorCode.EVENT_CANNOT_EDIT, ex.getMessage());
        }

        // 活动取消后，候补队列失去意义，整体清空。
        // 已确认的报名保留原状态：退款/通知属于阶段 3 的异步链路，此处不动。
        registrationRepository.findByEventIdAndStatus(eventId, RegistrationStatus.WAITLISTED)
                .forEach(Registration::cancel);

        log.info("取消活动 eventId={}", eventId);
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));

        // 草稿对外不可见，但不能返回 404 之外的信息泄露——所以就是 404
        if (event.getStatus() == EventStatus.DRAFT && !isOwnerOrAdmin(event)) {
            throw BusinessException.of(ErrorCode.EVENT_NOT_FOUND);
        }
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listPublished(Pageable pageable) {
        Page<Event> page = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);
        return withCounts(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listMine(Long organizerId, Pageable pageable) {
        Page<Event> page = eventRepository.findByOrganizerIdOrderByStartAtDesc(organizerId, pageable);
        return withCounts(page);
    }

    // ---------- 内部 ----------

    private PageResponse<EventResponse> withCounts(Page<Event> page) {
        List<Long> ids = page.getContent().stream().map(Event::getId).toList();
        Map<Long, Long> confirmed = countByEvent(ids, SEAT_STATUSES);
        Map<Long, Long> waitlisted = countByEvent(ids, WAITLIST_STATUSES);

        return PageResponse.of(page, event -> EventResponse.of(
                event,
                confirmed.getOrDefault(event.getId(), 0L),
                waitlisted.getOrDefault(event.getId(), 0L)
        ));
    }

    private EventResponse toResponse(Event event) {
        long confirmed = registrationRepository.countByEventIdAndStatusIn(event.getId(), SEAT_STATUSES);
        long waitlisted = registrationRepository.countByEventIdAndStatusIn(event.getId(), WAITLIST_STATUSES);
        return EventResponse.of(event, confirmed, waitlisted);
    }

    /** 一次查询取回整页活动的计数，避免逐条 count 造成 N+1。 */
    private Map<Long, Long> countByEvent(Collection<Long> eventIds, Collection<RegistrationStatus> statuses) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        return registrationRepository.countGroupedByEvent(eventIds, statuses).stream()
                .collect(Collectors.toMap(EventRegistrationCount::eventId,
                        EventRegistrationCount::total,
                        (a, b) -> a));
    }

    private Event requireOwnership(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));
        if (!event.isOwnedBy(userId) && !isAdmin()) {
            throw BusinessException.of(ErrorCode.NOT_EVENT_ORGANIZER);
        }
        return event;
    }

    private boolean isOwnerOrAdmin(Event event) {
        return SecurityUtils.currentPrincipal()
                .map(actor -> actor.hasRole(Role.ADMIN) || event.isOwnedBy(actor.userId()))
                .orElse(false);
    }

    private boolean isAdmin() {
        return SecurityUtils.currentPrincipal().map(actor -> actor.hasRole(Role.ADMIN)).orElse(false);
    }

    /**
     * 用户第一次创建活动时自动获得 ORGANIZER 角色。
     *
     * <p>这样「普通用户 → 组织者」是自己走出来的，不需要管理员预先授权；
     * 编辑权限仍然由活动归属决定（见 {@link #requireOwnership}），
     * 所以拿到这个角色并不能编辑别人的活动。
     */
    private void grantOrganizerRole(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (!user.getRoles().contains(Role.ORGANIZER)) {
                user.addRole(Role.ORGANIZER);
                log.info("用户 {} 首次创建活动，授予 ORGANIZER 角色", userId);
            }
        });
    }

    private static void validateTimeRange(EventRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw BusinessException.of(ErrorCode.EVENT_TIME_INVALID);
        }
    }

    /** 供其它 service 复用：按 id 取活动，不存在即 404。 */
    @Transactional(readOnly = true)
    public Event requireEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.EVENT_NOT_FOUND));
    }
}

package com.eventhub.api.registration.repository;

import com.eventhub.api.registration.domain.Registration;
import com.eventhub.api.registration.domain.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId);

    Optional<Registration> findByTicketCode(String ticketCode);

    /** 统计占位人数。只数 CONFIRMED / CHECKED_IN，候补与已取消不计。 */
    long countByEventIdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> findByIdForUpdate(@Param("id") Long id);

    /**
     * 取候补队列的队首。
     *
     * <p>排序键是 {@code waitlisted_at} 而非主键：取消后重新报名会复用同一行，
     * 用自增 id 排序会让老用户凭空插队。id 只作同秒入队的稳定 tie-breaker。
     */
    Optional<Registration> findFirstByEventIdAndStatusOrderByWaitlistedAtAscIdAsc(
            Long eventId, RegistrationStatus status);

    /** 按状态取某活动的全部报名，用于活动取消时批量清理候补。 */
    List<Registration> findByEventIdAndStatus(Long eventId, RegistrationStatus status);

    /** 按状态集合取某活动的全部报名，用于活动取消时收集通知名单。 */
    List<Registration> findByEventIdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);

    Page<Registration> findByUserIdOrderByRegisteredAtDesc(Long userId, Pageable pageable);

    Page<Registration> findByEventIdOrderByRegisteredAtAsc(Long eventId, Pageable pageable);

    /**
     * 一次取回多个活动的报名计数（按状态集合分组）。
     * 活动列表页若逐个活动查计数会退化成 N+1，这是为它准备的批量版本。
     */
    @Query("""
            select new com.eventhub.api.registration.repository.EventRegistrationCount(r.eventId, count(r))
            from Registration r
            where r.eventId in :eventIds and r.status in :statuses
            group by r.eventId
            """)
    List<EventRegistrationCount> countGroupedByEvent(
            @Param("eventIds") Collection<Long> eventIds,
            @Param("statuses") Collection<RegistrationStatus> statuses);
}

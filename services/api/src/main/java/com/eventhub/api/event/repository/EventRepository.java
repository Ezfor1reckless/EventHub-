package com.eventhub.api.event.repository;

import com.eventhub.api.event.domain.Event;
import com.eventhub.api.event.domain.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 带悲观写锁读取活动行。
     *
     * <p>报名、取消、递补三条路径都必须先走这里：它们都要做「数当前占位人数 → 决定
     * 确认还是候补」这个读-判断-写序列。不加锁的话，两个并发请求会各自读到同一个
     * 旧计数，双双判定「还有名额」，造成超卖。悲观锁把同一活动的这一序列串行化。
     *
     * <p>选悲观而非乐观锁的原因：抢最后一个名额时，乐观锁会让其中一个请求失败并需要
     * 重试，而重试逻辑要自己写、还容易漏；悲观锁让后来者排队等待，代码是直的。
     * 锁的粒度是单个活动行，不同活动之间不互相阻塞。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByOrganizerIdOrderByStartAtDesc(Long organizerId, Pageable pageable);
}

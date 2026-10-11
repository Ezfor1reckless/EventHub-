package com.eventhub.api.outbox.repository;

import com.eventhub.api.outbox.domain.OutboxEvent;
import com.eventhub.api.outbox.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 取一批待投递事件并加行锁。
     *
     * <p>{@code jakarta.persistence.lock.timeout = -2} 是 JPA 规范里表示
     * <b>SKIP LOCKED</b> 的约定值：遇到已被其他事务锁住的行直接跳过，而不是排队等待。
     * 这样多个应用实例可以并发投递而不会互相阻塞，也不会把同一条事件发两遍。
     *
     * <p>若将来换成不支持 SKIP LOCKED 的数据库，这里要退化成单实例投递。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEvent o where o.status = :status order by o.createdAt asc")
    List<OutboxEvent> findBatchForPublishing(@Param("status") OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    /**
     * 删除已投递且早于 cutoff 的记录。
     * 只删 PUBLISHED——FAILED 的必须留着等人排查。
     */
    @Modifying
    @Query("delete from OutboxEvent o where o.status = com.eventhub.api.outbox.domain.OutboxStatus.PUBLISHED "
            + "and o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}

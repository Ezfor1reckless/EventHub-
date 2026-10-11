package com.eventhub.api.audit.repository;

import com.eventhub.api.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    /** 消费幂等判断：这条 Outbox 事件是否已经落过审计。 */
    boolean existsBySourceEventId(Long sourceEventId);
}

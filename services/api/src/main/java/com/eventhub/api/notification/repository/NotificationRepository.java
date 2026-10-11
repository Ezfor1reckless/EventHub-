package com.eventhub.api.notification.repository;

import com.eventhub.api.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** 消费幂等判断：这条事件是否已经为这个用户生成过通知。 */
    boolean existsBySourceEventIdAndUserId(Long sourceEventId, Long userId);
}

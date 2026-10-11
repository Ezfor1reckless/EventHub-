package com.eventhub.api.notification.service;

import com.eventhub.api.common.api.PageResponse;
import com.eventhub.api.common.exception.BusinessException;
import com.eventhub.api.common.exception.ErrorCode;
import com.eventhub.api.notification.domain.Notification;
import com.eventhub.api.notification.dto.NotificationResponse;
import com.eventhub.api.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(Long userId, Pageable pageable) {
        return PageResponse.of(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 标记已读。只能标记自己的。 */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                // 他人的通知 id 一律返回 404，不区分「不存在」和「不是你的」，
                // 否则可以用状态码差异枚举出别人有哪些通知
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));

        if (!notification.getUserId().equals(userId)) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        notification.markRead();
    }
}

package com.eventhub.api.outbox.service;

import com.eventhub.api.outbox.domain.OutboxStatus;
import com.eventhub.api.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 清理已投递的 Outbox 记录，避免这张表无限增长。
 *
 * <p>只删 PUBLISHED。FAILED 的必须留着——那正是需要人去看的东西。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanupJob {

    private final OutboxEventRepository outboxEventRepository;

    @Value("${app.outbox.retention:7d}")
    private Duration retention;

    /** 每小时跑一次，不用很勤。 */
    @Scheduled(fixedDelayString = "${app.outbox.cleanup-interval:1h}")
    @Transactional
    public void purgePublished() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("清理已投递的 Outbox 记录 {} 条（早于 {}）", deleted, cutoff);
        }
    }
}

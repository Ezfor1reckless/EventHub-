package com.eventhub.api.audit.service;

import com.eventhub.api.common.web.ClientIpResolver;
import com.eventhub.api.outbox.OutboxEventTypes;
import com.eventhub.api.outbox.OutboxPayloads;
import com.eventhub.api.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 审计记录入口。
 *
 * <p>审计走异步：写一条 Outbox 事件，由 audit.queue 的消费者落库。
 * 好处是不拖慢主流程，代价是审计表落后于业务表若干毫秒，且投递器故障期间
 * 审计会积压（但不会丢——事件还在 Outbox 表里，恢复后照常投递）。
 *
 * <p>如果将来审计要求与业务同事务强一致，把这里改成直接写 audit_logs 即可，
 * 调用方无需改动。
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String NO_TARGET = "-";

    private final OutboxService outboxService;
    private final ClientIpResolver clientIpResolver;

    /** 记录一条审计。IP 从当前请求上下文自动获取。 */
    public void record(Long actorId, String action, String targetType, Object targetId, String detail) {
        recordWithIp(actorId, action, targetType, targetId, detail, clientIpResolver.currentIp());
    }

    /** 需要显式指定 IP 时使用（例如异步线程中没有请求上下文）。 */
    public void recordWithIp(Long actorId, String action, String targetType, Object targetId,
                             String detail, String ip) {
        String target = targetId == null ? NO_TARGET : String.valueOf(targetId);
        outboxService.enqueue(
                OutboxEventTypes.AGGREGATE_USER,
                target,
                OutboxEventTypes.AUDIT_RECORDED,
                OutboxEventTypes.ROUTING_AUDIT,
                new OutboxPayloads.AuditRecorded(actorId, action, targetType, target, detail, ip));
    }
}

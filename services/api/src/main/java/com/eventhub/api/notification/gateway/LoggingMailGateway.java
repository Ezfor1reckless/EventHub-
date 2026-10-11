package com.eventhub.api.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未配置 SMTP 时的兜底实现：把邮件内容打进日志。
 *
 * <p>它存在的意义是让开发环境不需要一个可用的 SMTP 服务器也能跑通整条通知链路，
 * 同时<b>不假装</b>邮件发成功了——日志里明确写着「未启用」。
 * 把 {@code app.mail.enabled} 设为 true 并配好 {@code spring.mail.*} 即切换到真实发送。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailGateway implements MailGateway {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[邮件未启用，仅记录] to={} subject={} body={}", to, subject, body);
    }
}

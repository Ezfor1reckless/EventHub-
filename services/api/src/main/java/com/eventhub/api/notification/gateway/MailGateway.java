package com.eventhub.api.notification.gateway;

/**
 * 邮件发送出口。
 *
 * <p>抽成接口是为了让「通知逻辑」与「怎么发邮件」解耦：消费端只负责决定
 * 该发什么，至于走 SMTP、走第三方 API、还是只在日志里打一行，由实现决定。
 */
public interface MailGateway {

    void send(String to, String subject, String body);
}

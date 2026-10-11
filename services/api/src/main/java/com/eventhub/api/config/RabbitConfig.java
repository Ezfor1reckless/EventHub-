package com.eventhub.api.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑。
 *
 * <p>阶段 1 只声明结构，生产者与消费者在阶段 3 接上。业务代码通过
 * 这里的常量引用队列名，避免字符串散落各处。
 *
 * <p>死信约定：三个业务队列都挂到同一个死信交换机 {@link #DLX}，
 * 用各自队列名做 routing key 路由到 {@link #DEAD_LETTER_QUEUE}。
 * 排查失败消息时能直接看出它原本属于哪个队列。
 */
@Configuration
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "eventhub.events";
    public static final String DLX_EXCHANGE = "eventhub.dlx";

    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String AUDIT_QUEUE = "audit.queue";
    public static final String ANALYTICS_QUEUE = "analytics.queue";
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";

    public static final String ROUTING_NOTIFICATION = "notification";
    public static final String ROUTING_AUDIT = "audit";
    public static final String ROUTING_ANALYTICS = "analytics";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return durableQueue(NOTIFICATION_QUEUE);
    }

    @Bean
    public Queue auditQueue() {
        return durableQueue(AUDIT_QUEUE);
    }

    @Bean
    public Queue analyticsQueue() {
        return durableQueue(ANALYTICS_QUEUE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventsExchange()).with(ROUTING_NOTIFICATION);
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(eventsExchange()).with(ROUTING_AUDIT);
    }

    @Bean
    public Binding analyticsBinding() {
        return BindingBuilder.bind(analyticsQueue()).to(eventsExchange()).with(ROUTING_ANALYTICS);
    }

    /**
     * 死信按「原队列名」路由到同一个死信队列，排查时一眼能看出消息原本属于哪个队列。
     * 必须逐个绑定：deadLetterExchange 是 DirectExchange，只做精确匹配，
     * "#" 是 TopicExchange 的通配符，用在这里永远不会命中。
     */
    @Bean
    public Binding deadLetterNotificationBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(NOTIFICATION_QUEUE);
    }

    @Bean
    public Binding deadLetterAuditBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(AUDIT_QUEUE);
    }

    @Bean
    public Binding deadLetterAnalyticsBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(ANALYTICS_QUEUE);
    }

    /**
     * JSON 序列化。默认的 JDK 序列化要求生产者和消费者的类路径完全一致，
     * 跨版本部署时会反序列化失败，且消息在管理台里不可读。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    private static Queue durableQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }
}

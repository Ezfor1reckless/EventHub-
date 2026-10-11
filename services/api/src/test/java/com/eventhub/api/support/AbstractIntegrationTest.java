package com.eventhub.api.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类：拉起真实的 MySQL / Redis / RabbitMQ。
 *
 * <p>用真实中间件而不是 H2 + 嵌入式替身，是因为本项目最关键的正确性
 * （唯一索引冲突、Flyway 迁移、Redis 黑名单语义）恰恰只在真实实现上才成立。
 *
 * <p>容器是 {@code static} 的，整个测试类共享同一套，只启动一次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    protected static final int REDIS_PORT = 6379;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("eventhub")
            .withUsername("eventhub")
            .withPassword("eventhub-test");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    /** Redis 没有官方 Testcontainers 模块，用通用容器 + 手工注入属性。 */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
    }
}

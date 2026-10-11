package com.eventhub.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类：拉起真实的 MySQL / Redis / RabbitMQ，并提供构造请求的公共辅助方法。
 *
 * <p><b>刻意不用 {@code @Testcontainers} / {@code @Container}。</b>
 * 那套注解的生命周期是<b>按测试类</b>的：第一个测试类跑完就把容器停掉，
 * 后面的测试类拿到的是已经死掉的容器，表现为连接被拒 + 30 秒连接池超时，
 * 而且报错信息（HikariPool Connection is not available）完全看不出真正原因。
 *
 * <p>这里改用 Testcontainers 官方推荐的 <b>singleton container</b> 模式：
 * 在静态初始化块里启动，容器活到 JVM 退出，由 Ryuk 负责回收。
 * 好处是所有测试类共用同一套容器，Spring 上下文缓存也能命中。
 *
 * <p>用真实中间件而不是 H2 + 嵌入式替身，是因为本项目最关键的正确性
 * （唯一索引冲突、Flyway 迁移、悲观锁防超卖、Redis 令牌 TTL）恰恰只在真实实现上成立。
 */
@SpringBootTest
@AutoConfigureMockMvc
// 激活 test profile，让 application-test.yml 作为**叠加层**生效。
// 主配置 application.yml 照常加载——这一点很重要，见 application-test.yml 顶部的说明：
// 如果测试资源里放的是同名 application.yml，它会整体替换掉主配置，
// 导致 ddl-auto=validate 这类设置静默失效。
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final int REDIS_PORT = 6379;
    protected static final String PASSWORD = "s3cretPass1";

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("eventhub")
            .withUsername("eventhub")
            .withPassword("eventhub-test");

    @ServiceConnection
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    /** Redis 没有官方 Testcontainers 模块，用通用容器 + 手工注入属性。 */
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(REDIS_PORT);

    static {
        // 启动一次，全程复用
        MYSQL.start();
        RABBITMQ.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /**
     * 注册一个新用户并返回其 access token。
     * 邮箱加随机后缀，保证各测试之间互不干扰、可任意顺序执行。
     */
    protected String signUp(String emailPrefix) throws Exception {
        String email = emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","nickname":"%s"}
                                """.formatted(email, PASSWORD, emailPrefix)))
                .andExpect(status().isCreated())
                .andReturn();
        return accessToken(result);
    }

    /** 创建一个活动并发布，返回活动 id。 */
    protected long createPublishedEvent(String organizerToken, int capacity) throws Exception {
        long eventId = createDraftEvent(organizerToken, capacity);
        publish(organizerToken, eventId);
        return eventId;
    }

    /** 只创建不发布，返回活动 id（DRAFT 状态）。 */
    protected long createDraftEvent(String organizerToken, int capacity) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("活动-" + UUID.randomUUID().toString().substring(0, 6), capacity)))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).path("id").asLong();
    }

    protected void publish(String organizerToken, long eventId) throws Exception {
        mvc.perform(post("/api/v1/events/" + eventId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organizerToken)))
                .andExpect(status().isOk());
    }

    /** 报名并返回报名 id。 */
    protected long registerForEvent(String userToken, long eventId) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/events/" + eventId + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).path("id").asLong();
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    protected String accessToken(MvcResult result) throws Exception {
        return data(result).path("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** 当前登录用户的 id。 */
    protected long currentUserId(String userToken) throws Exception {
        MvcResult result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("id").asLong();
    }

    /**
     * 轮询等待异步条件成立。
     *
     * <p>异步链路（Outbox → RabbitMQ → 消费者）无法用固定 sleep 可靠地测：
     * 睡短了会随机失败，睡长了拖慢整个测试套件。这里用「轮询 + 超时」，
     * 正常情况几十毫秒就返回。
     */
    protected void awaitUntil(String description, java.util.function.Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待被中断: " + description, ex);
            }
        }
        throw new AssertionError("等待超时（15s）: " + description);
    }

    /**
     * 活动表单。时间基于当前时刻推算，避免测试依赖写死日期。
     */
    protected static String eventBody(String title, int capacity) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        return """
                {"title":"%s","description":"集成测试活动","location":"测试场地",
                 "startAt":"%s","endAt":"%s","capacity":%d}
                """.formatted(title, start, end, capacity);
    }
}

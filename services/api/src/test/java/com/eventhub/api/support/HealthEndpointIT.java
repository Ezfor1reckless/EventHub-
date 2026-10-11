package com.eventhub.api.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口。
 *
 * <p>这个用例的存在有具体原因：整套部署编排都靠它判断实例是否可用——
 * 容器 healthcheck 打的就是这个地址，返回非 200 会被标记 unhealthy，
 * 依赖它的 Nginx 随之拒绝启动，整套栈起不来。
 *
 * <p>之前完全没有测试覆盖它，于是一个真实故障漏了过去：Spring Boot 只要看到
 * spring.mail.host 有值就会注册邮件健康指标去连 SMTP，连不上就把整个应用判成 DOWN。
 * 而邮件在本项目里是可选依赖（MAIL_ENABLED 默认为 false，走日志实现），
 * 它却一度拥有了否决整个应用启动的权力。
 *
 * <p>测试配置把 spring.mail.host 指向一个解析不了的主机，所以这个断言
 * 在修复缺失时确实会失败——它不是那种怎么写都能过的测试。
 */
class HealthEndpointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("健康检查为 UP，且不受可选依赖（SMTP）影响")
    void health_is_up_despite_unreachable_optional_dependencies() throws Exception {
        // 匿名访问只返回总体状态，这是部署探针实际使用的形式
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("核心依赖仍被报告，邮件指标整个不存在")
    void core_dependencies_are_reported_but_mail_is_not() throws Exception {
        String token = signUp("healthcheck");

        mvc.perform(get("/actuator/health")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // 数据库、Redis、RabbitMQ 是真正的依赖，必须继续反映在健康检查里，
                // 否则这个接口就失去意义了
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"))
                // 邮件指标应当整个不存在，而不是「存在但状态为 DOWN」——
                // 后者同样会把总体状态拉成 DOWN，正是要避免的
                .andExpect(jsonPath("$.components.mail").doesNotExist());
    }
}

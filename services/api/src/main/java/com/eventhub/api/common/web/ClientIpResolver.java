package com.eventhub.api.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 取客户端 IP，用于审计和限流。
 *
 * <p><b>默认不信任 {@code X-Forwarded-For}。</b> 这个请求头由客户端提供，
 * 任何人都能随便填。如果无条件采信它，基于 IP 的限流只要每次换一个伪造的
 * XFF 值就能绕过——限流会变成摆设，而且不会有任何报错提示你它失效了。
 *
 * <p>只有确实部署在可信反向代理（本项目的 Nginx）之后，才把
 * {@code app.security.trust-proxy} 设为 true，此时 XFF 由代理写入。
 * 注意反代必须<b>覆盖</b>而不是追加客户端的 XFF，否则同样可被伪造。
 */
@Component
public class ClientIpResolver {

    private final boolean trustProxy;

    public ClientIpResolver(@Value("${app.security.trust-proxy:false}") boolean trustProxy) {
        this.trustProxy = trustProxy;
    }

    /** 当前请求的客户端 IP；不在请求上下文中（如异步线程、定时任务）时返回 null。 */
    public String currentIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return from(servletAttributes.getRequest());
        }
        return null;
    }

    public String from(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // XFF 可能是逗号分隔的链（client, proxy1, proxy2），最左边是原始客户端
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}

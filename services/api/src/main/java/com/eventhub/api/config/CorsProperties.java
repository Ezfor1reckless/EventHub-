package com.eventhub.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param allowedOrigins 允许跨域的来源。生产环境必须显式配置为真实域名，
 *                       不要留 {@code *}——浏览器在携带凭证时会拒绝通配符。
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:3000", "http://localhost:8081");
        }
    }
}

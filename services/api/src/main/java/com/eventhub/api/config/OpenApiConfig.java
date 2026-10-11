package com.eventhub.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI eventHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventHub API")
                        .version("v1")
                        .description("社区活动报名与签到平台后端接口。所有响应统一为 ApiResponse 结构。")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("填入 /api/v1/auth/login 返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

package com.eventhub.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
// Outbox 投递器和清理任务依赖它
@EnableScheduling
public class EventHubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventHubApiApplication.class, args);
    }
}

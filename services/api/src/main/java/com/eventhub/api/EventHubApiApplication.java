package com.eventhub.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
public class EventHubApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventHubApiApplication.class, args);
    }
}

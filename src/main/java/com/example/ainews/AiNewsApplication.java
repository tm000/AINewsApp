package com.example.ainews;

import com.example.ainews.config.AiNewsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AiNewsProperties.class)
public class AiNewsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiNewsApplication.class, args);
    }
}

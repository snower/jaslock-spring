package io.github.snower.jaslock.spring.boot.test.config;

import io.github.snower.jaslock.spring.boot.test.service.AspectTestService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class SlockTestConfiguration {
    @Bean
    public String version() {
        return "1.0.0";
    }

    @Bean
    public AspectTestService aspectTestService() {
        return new AspectTestService();
    }
}

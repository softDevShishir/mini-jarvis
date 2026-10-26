package com.example.minijarvis.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Business timezone clock; injected everywhere "today" matters so tests can pin it. */
    @Bean
    public Clock clock(JarvisProperties properties) {
        return Clock.system(ZoneId.of(properties.timezone()));
    }
}

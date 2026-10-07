package com.interviewprep.common.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of "now" for both business logic and Bean Validation ({@code @FutureOrPresent}, etc.),
 * so date-dependent rules can be tested deterministically with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}

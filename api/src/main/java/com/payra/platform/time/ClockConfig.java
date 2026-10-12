package com.payra.platform.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the whole system.
 * 
 * <p>Injectable so tests can pin "now" - expiry rules get tested without
 * sleeping, and time is a dependency like any other.
 * 
 */
@Configuration 
public class ClockConfig {
    
    @Bean 
    public Clock clock() {
        return Clock.systemUTC();
    }
}

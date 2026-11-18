package dev.challenge.purchase.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every date-dependent rule (competence month, token expiry, {@code neededBy} in the future) reads time from this
 * clock, so tests can fix it.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}

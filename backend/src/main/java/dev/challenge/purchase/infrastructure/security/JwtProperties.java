package dev.challenge.purchase.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret HS256 signing key (at least 32 bytes); blank means a random key generated at startup
 * @param issuer value of the {@code iss} claim, checked on every request
 * @param ttl    token lifetime
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

	public JwtProperties {
		if (issuer == null || issuer.isBlank()) {
			issuer = "purchase-requests";
		}
		if (ttl == null) {
			ttl = Duration.ofMinutes(15);
		}
	}

}

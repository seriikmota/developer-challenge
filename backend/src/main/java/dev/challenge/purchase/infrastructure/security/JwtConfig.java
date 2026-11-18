package dev.challenge.purchase.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * HS256 JWT signed with a shared secret: the API issues and validates its own tokens.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

	private static final int MIN_KEY_BYTES = 32;

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		var secret = properties.secret();
		if (secret == null || secret.isBlank()) {
			log.warn("JWT_SECRET is not set: using a random signing key; tokens become invalid on every restart");
			var random = new byte[MIN_KEY_BYTES];
			new SecureRandom().nextBytes(random);
			return new SecretKeySpec(random, "HmacSHA256");
		}
		var bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("JWT_SECRET must have at least " + MIN_KEY_BYTES + " bytes for HS256, got " + bytes.length);
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
		var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(ALGORITHM).build();
		var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

}

package dev.challenge.purchase.infrastructure.security;

import java.time.Clock;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import dev.challenge.purchase.application.auth.AccessToken;
import dev.challenge.purchase.application.auth.AccessTokenIssuer;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;

/**
 * Short-lived JWT with the claims the API needs to authorize without a database lookup:
 * {@code sub} (user id), {@code role}, {@code costCenterId} and {@code approverLevel} (when present).
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	JwtAccessTokenIssuer(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public AccessToken issue(UserEntity user) {
		var now = clock.instant();
		var expiresAt = now.plus(properties.ttl());
		var claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(user.getId()))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim(JwtClaimNames.ROLE, user.getRole().name());
		if (user.getCostCenterId() != null) {
			claims.claim(JwtClaimNames.COST_CENTER_ID, user.getCostCenterId());
		}
		if (user.getApproverLevel() != null) {
			claims.claim(JwtClaimNames.APPROVER_LEVEL, user.getApproverLevel().name());
		}
		var header = JwsHeader.with(JwtConfig.ALGORITHM).build();
		var token = encoder.encode(JwtEncoderParameters.from(header, claims.build()));
		return new AccessToken(token.getTokenValue(), expiresAt);
	}

}

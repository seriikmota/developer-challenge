package dev.challenge.purchase.infrastructure.security;

/**
 * Custom claims of the access token (besides the standard {@code sub}, {@code iss}, {@code iat}, {@code exp}).
 */
public final class JwtClaimNames {

	public static final String ROLE = "role";

	public static final String COST_CENTER_ID = "costCenterId";

	public static final String APPROVER_LEVEL = "approverLevel";

	private JwtClaimNames() {
	}

}

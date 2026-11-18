package dev.challenge.purchase.application.error;

/**
 * Wrong credentials, or a token whose user no longer exists (401).
 */
public final class AuthenticationFailedException extends ApplicationException {

	public AuthenticationFailedException(String code, String message) {
		super(code, message);
	}

}

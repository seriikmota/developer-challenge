package dev.challenge.purchase.application.error;

/**
 * The resource is visible, but the user's role does not allow the action (403).
 */
public final class ForbiddenException extends ApplicationException {

	public ForbiddenException(String message) {
		super("FORBIDDEN", message);
	}

}

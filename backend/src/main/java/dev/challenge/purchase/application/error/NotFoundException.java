package dev.challenge.purchase.application.error;

/**
 * Resource that does not exist or is outside the user's visibility (404, see ADR 003).
 */
public final class NotFoundException extends ApplicationException {

	public NotFoundException(String message) {
		super("NOT_FOUND", message);
	}

}

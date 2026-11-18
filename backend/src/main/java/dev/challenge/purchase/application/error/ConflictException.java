package dev.challenge.purchase.application.error;

/**
 * The request conflicts with the current state (409): stale {@code lockVersion}, reused {@code Idempotency-Key}
 * with another payload, duplicated unique value.
 */
public final class ConflictException extends ApplicationException {

	public ConflictException(String code, String message) {
		super(code, message);
	}

}

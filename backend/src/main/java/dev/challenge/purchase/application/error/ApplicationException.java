package dev.challenge.purchase.application.error;

import java.util.Objects;

/**
 * Failure of a use case that is not a business rule violation (those are {@code DomainException}, 422).
 * Each subclass maps to one HTTP status; {@link #code()} is the machine-readable code sent to the client.
 */
public abstract sealed class ApplicationException extends RuntimeException permits NotFoundException, ForbiddenException, ConflictException, InvalidInputException, AuthenticationFailedException {

	private final String code;

	protected ApplicationException(String code, String message) {
		super(message);
		this.code = Objects.requireNonNull(code, "code");
	}

	public String code() {
		return code;
	}

}

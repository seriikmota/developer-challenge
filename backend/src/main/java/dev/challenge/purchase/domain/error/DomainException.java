package dev.challenge.purchase.domain.error;

import java.util.Objects;

/**
 * A business rule violation. The message is user-facing and must explain why the action was refused.
 */
public class DomainException extends RuntimeException {

	private final ErrorCode code;

	public DomainException(ErrorCode code, String message) {
		super(Objects.requireNonNull(message, "message"));
		this.code = Objects.requireNonNull(code, "code");
	}

	public ErrorCode code() {
		return code;
	}

}

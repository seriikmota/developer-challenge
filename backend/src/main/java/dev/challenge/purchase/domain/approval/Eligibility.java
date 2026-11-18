package dev.challenge.purchase.domain.approval;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;

/**
 * Outcome of an eligibility check. Used both to validate an action and to compute {@code allowedActions},
 * so the two never diverge.
 */
public record Eligibility(boolean allowed, ErrorCode code, String reason) {

	private static final Eligibility ALLOWED = new Eligibility(true, null, null);

	public static Eligibility granted() {
		return ALLOWED;
	}

	public static Eligibility denied(ErrorCode code, String reason) {
		return new Eligibility(false, code, reason);
	}

	public void orThrow() {
		if (!allowed) {
			throw new DomainException(code, reason);
		}
	}

}

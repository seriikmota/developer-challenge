package dev.challenge.purchase.domain.user;

public enum Role {
	SOLICITANTE,
	APROVADOR,
	ADMIN;

	/**
	 * Whether this role may decide (approve, reject, request changes) approval steps at all.
	 * Finer rules (level, cost center, segregation of duties) live in the eligibility policy.
	 */
	public boolean canDecideApprovals() {
		return this != SOLICITANTE;
	}
}

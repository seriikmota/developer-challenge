package dev.challenge.purchase.domain.request;

public enum RequestStatus {
	DRAFT,
	PENDING_APPROVAL,
	CHANGES_REQUESTED,
	APPROVED,
	REJECTED,
	CANCELLED;

	public boolean isFinal() {
		return this == APPROVED || this == REJECTED || this == CANCELLED;
	}
}

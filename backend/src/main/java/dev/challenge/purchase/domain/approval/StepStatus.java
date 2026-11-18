package dev.challenge.purchase.domain.approval;

/**
 * Outcome of one approval step. A step stays PENDING until it is decided; steps after a rejection or a
 * request for changes stay PENDING forever, because the version is closed and a resubmission builds a new chain.
 */
public enum StepStatus {

	PENDING, APPROVED, REJECTED, CHANGES_REQUESTED

}

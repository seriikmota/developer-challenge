package dev.challenge.purchase.domain.error;

/**
 * Machine-readable codes for business rule violations (HTTP 422).
 */
public enum ErrorCode {
	INVALID_TRANSITION,
	NOT_EDITABLE,
	NOT_OWNER,
	SELF_APPROVAL_FORBIDDEN,
	APPROVER_NOT_ELIGIBLE,
	DUPLICATE_APPROVER,
	COMMENT_REQUIRED,
	BUDGET_EXCEEDED,
	BUDGET_BELOW_CONSUMED
}

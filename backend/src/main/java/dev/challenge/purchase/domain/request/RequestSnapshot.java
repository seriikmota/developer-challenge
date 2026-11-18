package dev.challenge.purchase.domain.request;

import java.util.Objects;

import dev.challenge.purchase.domain.approval.ApprovalContext;

/**
 * What the action policy needs to know about a request.
 *
 * @param ownerId  user who created the request
 * @param status   current status
 * @param approval the step awaiting a decision; {@code null} unless the request is pending approval
 */
public record RequestSnapshot(Long ownerId, RequestStatus status, ApprovalContext approval) {

	public RequestSnapshot {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(status, "status");
	}

}

package dev.challenge.purchase.infrastructure.persistence.request;

import java.util.Optional;

import dev.challenge.purchase.domain.approval.ApprovalContext;
import dev.challenge.purchase.domain.request.RequestStatus;

/**
 * Builds the plain {@link ApprovalContext} the eligibility policy needs from the persisted request.
 */
public final class ApprovalContextMapper {

	private ApprovalContextMapper() {
	}

	/**
	 * @return the context of the step awaiting a decision, or empty if the request is not pending approval
	 */
	public static Optional<ApprovalContext> toContext(PurchaseRequestEntity request) {
		if (request.getStatus() != RequestStatus.PENDING_APPROVAL) {
			return Optional.empty();
		}
		var version = request.currentVersion();
		return version.currentStep()
				.map(step -> new ApprovalContext(request.getOwnerId(), request.getCostCenterId(), step.getLevel(), version.approvals()));
	}

}

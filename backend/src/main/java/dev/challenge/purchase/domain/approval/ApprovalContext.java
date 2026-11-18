package dev.challenge.purchase.domain.approval;

import java.util.List;
import java.util.Objects;

/**
 * What the eligibility policy needs to know about a request version awaiting a decision.
 *
 * @param ownerId           user who created the request
 * @param costCenterId      cost center the request is charged to
 * @param currentStep       level of the step awaiting a decision
 * @param approvedSteps     steps of the current version already approved, with their approvers
 */
public record ApprovalContext(Long ownerId, Long costCenterId, StepLevel currentStep, List<StepApproval> approvedSteps) {

	public ApprovalContext {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(costCenterId, "costCenterId");
		Objects.requireNonNull(currentStep, "currentStep");
		approvedSteps = List.copyOf(Objects.requireNonNull(approvedSteps, "approvedSteps"));
	}

}

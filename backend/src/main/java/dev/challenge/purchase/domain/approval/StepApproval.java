package dev.challenge.purchase.domain.approval;

import java.util.Objects;

/**
 * An approval already granted on a step of the current request version.
 */
public record StepApproval(StepLevel level, Long approverId) {

	public StepApproval {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(approverId, "approverId");
	}

}

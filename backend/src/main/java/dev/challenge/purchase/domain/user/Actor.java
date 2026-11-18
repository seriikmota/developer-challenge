package dev.challenge.purchase.domain.user;

import java.util.Objects;

/**
 * The authenticated user performing an action.
 *
 * @param userId         user identifier
 * @param role           user role
 * @param costCenterId   cost center of the user; may be {@code null} only for a global ADMIN
 * @param approverLevel  approver level; set for APROVADOR, {@code null} otherwise
 */
public record Actor(Long userId, Role role, Long costCenterId, ApproverLevel approverLevel) {

	public Actor {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(role, "role");
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

}

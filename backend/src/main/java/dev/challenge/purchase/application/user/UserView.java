package dev.challenge.purchase.application.user;

import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;

/**
 * Public view of a user: never includes the password or its hash.
 */
public record UserView(Long id, String name, String email, Role role, ApproverLevel approverLevel, Long costCenterId) {

	public static UserView of(UserEntity user) {
		return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getApproverLevel(), user.getCostCenterId());
	}

}

package dev.challenge.purchase.infrastructure.persistence.user;

import dev.challenge.purchase.domain.user.Actor;

/**
 * Turns a persisted user into the plain {@link Actor} the domain rules work with.
 */
public final class ActorMapper {

	private ActorMapper() {
	}

	public static Actor toActor(UserEntity user) {
		return new Actor(user.getId(), user.getRole(), user.getCostCenterId(), user.getApproverLevel());
	}

}

package dev.challenge.purchase.application.request;

import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;

/**
 * Who sees which request (README 2.2). A request outside the user's visibility is answered with 404, as if it did
 * not exist (ADR 003).
 * <ul>
 * <li>the owner always sees their own requests;</li>
 * <li>a DRAFT is visible only to its owner;</li>
 * <li>ADMIN sees every other request;</li>
 * <li>APROVADOR sees the requests of their cost center;</li>
 * <li>SOLICITANTE sees only their own.</li>
 * </ul>
 * The in-memory check and the database filter express the same rule and are tested together.
 */
public final class RequestVisibility {

	private RequestVisibility() {
	}

	public static boolean canSee(Actor actor, PurchaseRequestEntity request) {
		if (actor.userId().equals(request.getOwnerId())) {
			return true;
		}
		if (request.getStatus() == RequestStatus.DRAFT) {
			return false;
		}
		return actor.isAdmin() || actor.role() == Role.APROVADOR && Objects.equals(actor.costCenterId(), request.getCostCenterId());
	}

	public static Specification<PurchaseRequestEntity> visibleTo(Actor actor) {
		return (root, query, cb) -> {
			var own = cb.equal(root.get("ownerId"), actor.userId());
			var notDraft = cb.notEqual(root.get("status"), RequestStatus.DRAFT);
			if (actor.isAdmin()) {
				return cb.or(own, notDraft);
			}
			if (actor.role() == Role.APROVADOR && actor.costCenterId() != null) {
				return cb.or(own, cb.and(notDraft, cb.equal(root.get("costCenterId"), actor.costCenterId())));
			}
			return own;
		};
	}

}

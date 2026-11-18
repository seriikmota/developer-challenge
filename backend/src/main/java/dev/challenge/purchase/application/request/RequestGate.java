package dev.challenge.purchase.application.request;

import org.springframework.stereotype.Component;

import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.ForbiddenException;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * Loads a request applying the checks that come before the business rules, in the order of README 4.3:
 * 404 (missing or not visible) → 403 (role cannot do this kind of action) → 409 (stale {@code lockVersion}).
 * Business rules (422) are checked afterwards by the domain policy.
 */
@Component
public class RequestGate {

	private final PurchaseRequestRepository requests;

	private final EntityManager entityManager;

	public RequestGate(PurchaseRequestRepository requests, EntityManager entityManager) {
		this.requests = requests;
		this.entityManager = entityManager;
	}

	public PurchaseRequestEntity readable(Actor actor, Long id) {
		return requests.findById(id).filter(request -> RequestVisibility.canSee(actor, request)).orElseThrow(() -> notFound(id));
	}

	/**
	 * Loads and locks the request for a change. On success {@code lockVersion} is already incremented, so the
	 * response of the change carries the value the client must send next.
	 */
	public PurchaseRequestEntity writable(Actor actor, Long id, Action action, long expectedLockVersion) {
		var request = requests.findForUpdateById(id).filter(found -> RequestVisibility.canSee(actor, found)).orElseThrow(() -> notFound(id));
		if (action.isApprovalDecision() && !actor.role().canDecideApprovals()) {
			throw new ForbiddenException("O papel " + actor.role() + " não aprova, rejeita nem devolve solicitações para ajuste.");
		}
		if (request.getLockVersion() != expectedLockVersion) {
			throw new ConflictException("CONCURRENT_MODIFICATION", "A solicitação " + id + " foi alterada por outra requisição: lockVersion informado " + expectedLockVersion + ", atual " + request.getLockVersion() + ". Recarregue os dados e tente novamente.");
		}
		entityManager.lock(request, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
		return request;
	}

	private static NotFoundException notFound(Long id) {
		return new NotFoundException("Solicitação " + id + " não encontrada.");
	}

}

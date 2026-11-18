package dev.challenge.purchase.domain.request;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import dev.challenge.purchase.domain.approval.ApprovalEligibilityPolicy;
import dev.challenge.purchase.domain.approval.Eligibility;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.user.Actor;

/**
 * Single source of truth for "may this actor do this action on this request now?". The same answer validates the
 * action (422 with the reason) and builds {@code allowedActions} for the front end, so the two never diverge.
 * <p>
 * Business checks in a fixed order: status first, then ownership (edit, submit, cancel) or approval eligibility
 * (approve, reject, request changes: level, cost center, segregation of duties).
 */
public final class RequestActionPolicy {

	private final RequestStateMachine stateMachine;

	private final ApprovalEligibilityPolicy approvalPolicy;

	public RequestActionPolicy(RequestStateMachine stateMachine, ApprovalEligibilityPolicy approvalPolicy) {
		this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
		this.approvalPolicy = Objects.requireNonNull(approvalPolicy, "approvalPolicy");
	}

	public Eligibility evaluate(Actor actor, Action action, RequestSnapshot request) {
		var state = stateMachine.check(request.status(), action);
		if (!state.allowed()) {
			return state;
		}
		var isOwner = actor.userId().equals(request.ownerId());
		return switch (action) {
			case EDIT, SUBMIT -> isOwner ? Eligibility.granted() : Eligibility.denied(ErrorCode.NOT_OWNER, "Não é possível " + action.verb() + ": somente o dono da solicitação pode " + action.verb() + ".");
			case CANCEL -> isOwner || actor.isAdmin() ? Eligibility.granted() : Eligibility.denied(ErrorCode.NOT_OWNER, "Não é possível cancelar: somente o dono da solicitação ou um ADMIN pode cancelar.");
			case APPROVE, REJECT, REQUEST_CHANGES -> request.approval() == null
					? Eligibility.denied(ErrorCode.INVALID_TRANSITION, "Não é possível " + action.verb() + ": a solicitação não tem etapa aguardando decisão.")
					: approvalPolicy.evaluate(actor, action, request.approval());
		};
	}

	/**
	 * Actions the actor may perform on the request right now.
	 */
	public Set<Action> allowedActions(Actor actor, RequestSnapshot request) {
		var allowed = EnumSet.noneOf(Action.class);
		for (var action : stateMachine.availableActions(request.status())) {
			if (evaluate(actor, action, request).allowed()) {
				allowed.add(action);
			}
		}
		return Collections.unmodifiableSet(allowed);
	}

}

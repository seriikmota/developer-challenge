package dev.challenge.purchase.domain.request;

import static dev.challenge.purchase.domain.request.Action.APPROVE;
import static dev.challenge.purchase.domain.request.Action.CANCEL;
import static dev.challenge.purchase.domain.request.Action.EDIT;
import static dev.challenge.purchase.domain.request.Action.REJECT;
import static dev.challenge.purchase.domain.request.Action.REQUEST_CHANGES;
import static dev.challenge.purchase.domain.request.Action.SUBMIT;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;

/**
 * Status transitions of a purchase request (README table 4.2).
 *
 * <pre>
 * DRAFT              --submit-->           PENDING_APPROVAL
 * PENDING_APPROVAL   --approve-->          PENDING_APPROVAL (intermediate step) | APPROVED (last step)
 * PENDING_APPROVAL   --reject-->           REJECTED
 * PENDING_APPROVAL   --request-changes-->  CHANGES_REQUESTED
 * CHANGES_REQUESTED  --submit-->           PENDING_APPROVAL (new version)
 * DRAFT, PENDING_APPROVAL, CHANGES_REQUESTED --cancel--> CANCELLED
 * APPROVED, REJECTED, CANCELLED: final, no action allowed
 * </pre>
 *
 * EDIT keeps the status and is allowed only in DRAFT and CHANGES_REQUESTED.
 * This class only knows statuses: who may act (owner, eligible approver) is checked elsewhere.
 */
public final class RequestStateMachine {

	private static final Map<RequestStatus, Set<Action>> ACTIONS_BY_STATUS = new EnumMap<>(RequestStatus.class);

	static {
		ACTIONS_BY_STATUS.put(RequestStatus.DRAFT, EnumSet.of(EDIT, SUBMIT, CANCEL));
		ACTIONS_BY_STATUS.put(RequestStatus.PENDING_APPROVAL, EnumSet.of(APPROVE, REJECT, REQUEST_CHANGES, CANCEL));
		ACTIONS_BY_STATUS.put(RequestStatus.CHANGES_REQUESTED, EnumSet.of(EDIT, SUBMIT, CANCEL));
		ACTIONS_BY_STATUS.put(RequestStatus.APPROVED, EnumSet.noneOf(Action.class));
		ACTIONS_BY_STATUS.put(RequestStatus.REJECTED, EnumSet.noneOf(Action.class));
		ACTIONS_BY_STATUS.put(RequestStatus.CANCELLED, EnumSet.noneOf(Action.class));
	}

	/**
	 * Actions the status allows, regardless of who is acting.
	 */
	public Set<Action> availableActions(RequestStatus status) {
		return Collections.unmodifiableSet(ACTIONS_BY_STATUS.get(Objects.requireNonNull(status, "status")));
	}

	public boolean canApply(RequestStatus status, Action action) {
		return availableActions(status).contains(Objects.requireNonNull(action, "action"));
	}

	/**
	 * Returns the status after applying the action.
	 *
	 * @param isLastStep only relevant for APPROVE: whether the step being approved is the last of the chain
	 * @throws DomainException NOT_EDITABLE or INVALID_TRANSITION when the status does not allow the action
	 */
	public RequestStatus apply(RequestStatus from, Action action, boolean isLastStep) {
		if (!canApply(from, action)) {
			throw refusal(from, action);
		}
		return switch (action) {
			case EDIT -> from;
			case SUBMIT -> RequestStatus.PENDING_APPROVAL;
			case APPROVE -> isLastStep ? RequestStatus.APPROVED : RequestStatus.PENDING_APPROVAL;
			case REJECT -> RequestStatus.REJECTED;
			case REQUEST_CHANGES -> RequestStatus.CHANGES_REQUESTED;
			case CANCEL -> RequestStatus.CANCELLED;
		};
	}

	private DomainException refusal(RequestStatus from, Action action) {
		var prefix = "Não é possível " + action.verb() + ": a solicitação está em " + from;
		if (from.isFinal()) {
			var code = action == EDIT ? ErrorCode.NOT_EDITABLE : ErrorCode.INVALID_TRANSITION;
			return new DomainException(code, prefix + ", que é um estado final.");
		}
		if (action == EDIT) {
			return new DomainException(ErrorCode.NOT_EDITABLE, prefix + "; só é possível editar em DRAFT ou CHANGES_REQUESTED.");
		}
		var allowed = availableActions(from).stream().map(Action::verb).collect(Collectors.joining(", "));
		return new DomainException(ErrorCode.INVALID_TRANSITION, prefix + ". Ações possíveis nesse estado: " + allowed + ".");
	}

}

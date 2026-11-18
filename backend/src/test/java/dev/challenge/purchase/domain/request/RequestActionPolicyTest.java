package dev.challenge.purchase.domain.request;

import static dev.challenge.purchase.domain.request.Action.APPROVE;
import static dev.challenge.purchase.domain.request.Action.CANCEL;
import static dev.challenge.purchase.domain.request.Action.EDIT;
import static dev.challenge.purchase.domain.request.Action.REJECT;
import static dev.challenge.purchase.domain.request.Action.REQUEST_CHANGES;
import static dev.challenge.purchase.domain.request.Action.SUBMIT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.challenge.purchase.domain.approval.ApprovalContext;
import dev.challenge.purchase.domain.approval.ApprovalEligibilityPolicy;
import dev.challenge.purchase.domain.approval.StepApproval;
import dev.challenge.purchase.domain.approval.StepLevel;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;

class RequestActionPolicyTest {

	private static final long CC = 10L;

	private static final Actor OWNER = new Actor(1L, Role.SOLICITANTE, CC, null);

	private static final Actor OTHER_REQUESTER = new Actor(2L, Role.SOLICITANTE, CC, null);

	private static final Actor APPROVER_L1 = new Actor(3L, Role.APROVADOR, CC, ApproverLevel.NIVEL_1);

	private static final Actor APPROVER_L2 = new Actor(4L, Role.APROVADOR, CC, ApproverLevel.NIVEL_2);

	private static final Actor ADMIN = new Actor(5L, Role.ADMIN, null, null);

	private final RequestActionPolicy policy = new RequestActionPolicy(new RequestStateMachine(), new ApprovalEligibilityPolicy());

	private static RequestSnapshot inStatus(RequestStatus status) {
		return new RequestSnapshot(OWNER.userId(), status, null);
	}

	private static RequestSnapshot pendingAt(StepLevel step, StepApproval... approved) {
		return new RequestSnapshot(OWNER.userId(), RequestStatus.PENDING_APPROVAL, new ApprovalContext(OWNER.userId(), CC, step, List.of(approved)));
	}

	static Stream<Arguments> allowedActionsByActorAndState() {
		var pendingL1 = pendingAt(StepLevel.NIVEL_1);
		var pendingL2 = pendingAt(StepLevel.NIVEL_2, new StepApproval(StepLevel.NIVEL_1, APPROVER_L2.userId()));
		return Stream.of(
				Arguments.of("owner on DRAFT", OWNER, inStatus(RequestStatus.DRAFT), Set.of(EDIT, SUBMIT, CANCEL)),
				Arguments.of("owner on CHANGES_REQUESTED", OWNER, inStatus(RequestStatus.CHANGES_REQUESTED), Set.of(EDIT, SUBMIT, CANCEL)),
				Arguments.of("owner on PENDING", OWNER, pendingL1, Set.of(CANCEL)),
				Arguments.of("other requester on PENDING", OTHER_REQUESTER, pendingL1, Set.of()),
				Arguments.of("L1 approver on NIVEL_1", APPROVER_L1, pendingL1, Set.of(APPROVE, REJECT, REQUEST_CHANGES)),
				Arguments.of("L1 approver on NIVEL_2", APPROVER_L1, pendingL2, Set.of()),
				Arguments.of("L2 who approved NIVEL_1, on NIVEL_2", APPROVER_L2, pendingL2, Set.of()),
				Arguments.of("admin on NIVEL_2", ADMIN, pendingL2, Set.of(APPROVE, REJECT, REQUEST_CHANGES, CANCEL)),
				Arguments.of("admin on CHANGES_REQUESTED of another user", ADMIN, inStatus(RequestStatus.CHANGES_REQUESTED), Set.of(CANCEL)),
				Arguments.of("owner on APPROVED", OWNER, inStatus(RequestStatus.APPROVED), Set.of()),
				Arguments.of("admin on REJECTED", ADMIN, inStatus(RequestStatus.REJECTED), Set.of()));
	}

	@ParameterizedTest(name = "{0} -> {3}")
	@MethodSource("allowedActionsByActorAndState")
	void computesAllowedActions(String label, Actor actor, RequestSnapshot request, Set<Action> expected) {
		assertThat(policy.allowedActions(actor, request)).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void ownerApprovingOwnRequestIsSelfApproval() {
		var adminOwner = new Actor(OWNER.userId(), Role.ADMIN, null, null);

		var result = policy.evaluate(adminOwner, APPROVE, pendingAt(StepLevel.ADMIN));

		assertThat(result.code()).isEqualTo(ErrorCode.SELF_APPROVAL_FORBIDDEN);
	}

	@Test
	void statusIsCheckedBeforeOwnership() {
		var result = policy.evaluate(OTHER_REQUESTER, EDIT, inStatus(RequestStatus.APPROVED));

		assertThat(result.code()).isEqualTo(ErrorCode.NOT_EDITABLE);
	}

	@Test
	void nonOwnerCannotEdit() {
		var result = policy.evaluate(APPROVER_L1, EDIT, inStatus(RequestStatus.DRAFT));

		assertThat(result.code()).isEqualTo(ErrorCode.NOT_OWNER);
		assertThat(result.reason()).isEqualTo("Não é possível editar: somente o dono da solicitação pode editar.");
	}

	@Test
	void nonOwnerNonAdminCannotCancel() {
		var result = policy.evaluate(APPROVER_L2, CANCEL, pendingAt(StepLevel.NIVEL_1));

		assertThat(result.code()).isEqualTo(ErrorCode.NOT_OWNER);
		assertThat(result.reason()).isEqualTo("Não é possível cancelar: somente o dono da solicitação ou um ADMIN pode cancelar.");
	}

	@Test
	void decisionOnRequestNotPendingExplainsTheStatus() {
		var result = policy.evaluate(APPROVER_L1, APPROVE, inStatus(RequestStatus.DRAFT));

		assertThat(result.code()).isEqualTo(ErrorCode.INVALID_TRANSITION);
		assertThat(result.reason()).startsWith("Não é possível aprovar: a solicitação está em DRAFT.");
	}

	@Test
	void allowedActionsAndEvaluateAgreeForEveryAction() {
		var request = pendingAt(StepLevel.NIVEL_1);
		for (var actor : List.of(OWNER, OTHER_REQUESTER, APPROVER_L1, APPROVER_L2, ADMIN)) {
			var allowed = policy.allowedActions(actor, request);
			for (var action : Action.values()) {
				assertThat(policy.evaluate(actor, action, request).allowed()).as("%s %s", actor.role(), action).isEqualTo(allowed.contains(action));
			}
		}
	}

}

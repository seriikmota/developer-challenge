package dev.challenge.purchase.domain.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;

class ApprovalEligibilityPolicyTest {

	private static final long OWNER = 1L;

	private static final long CC_TI = 10L;

	private static final long CC_OPS = 20L;

	private static final Actor APPROVER_L1_TI = new Actor(2L, Role.APROVADOR, CC_TI, ApproverLevel.NIVEL_1);

	private static final Actor APPROVER_L2_TI = new Actor(3L, Role.APROVADOR, CC_TI, ApproverLevel.NIVEL_2);

	private static final Actor APPROVER_L1_OPS = new Actor(4L, Role.APROVADOR, CC_OPS, ApproverLevel.NIVEL_1);

	private static final Actor APPROVER_L2_OPS = new Actor(5L, Role.APROVADOR, CC_OPS, ApproverLevel.NIVEL_2);

	private static final Actor ADMIN_GLOBAL = new Actor(6L, Role.ADMIN, null, null);

	private static final Actor REQUESTER_TI = new Actor(7L, Role.SOLICITANTE, CC_TI, null);

	private final ApprovalEligibilityPolicy policy = new ApprovalEligibilityPolicy();

	private static ApprovalContext pendingAt(StepLevel step, StepApproval... approved) {
		return new ApprovalContext(OWNER, CC_TI, step, List.of(approved));
	}

	static Stream<Arguments> eligibilityMatrix() {
		return Stream.of(
				// NIVEL_1: APROVADOR NIVEL_1/NIVEL_2 of the same cost center, or ADMIN
				Arguments.of("L1 same CC", APPROVER_L1_TI, StepLevel.NIVEL_1, true),
				Arguments.of("L2 same CC", APPROVER_L2_TI, StepLevel.NIVEL_1, true),
				Arguments.of("L1 other CC", APPROVER_L1_OPS, StepLevel.NIVEL_1, false),
				Arguments.of("L2 other CC", APPROVER_L2_OPS, StepLevel.NIVEL_1, false),
				Arguments.of("ADMIN", ADMIN_GLOBAL, StepLevel.NIVEL_1, true),
				Arguments.of("SOLICITANTE", REQUESTER_TI, StepLevel.NIVEL_1, false),
				// NIVEL_2: APROVADOR NIVEL_2 of the same cost center, or ADMIN
				Arguments.of("L1 same CC", APPROVER_L1_TI, StepLevel.NIVEL_2, false),
				Arguments.of("L2 same CC", APPROVER_L2_TI, StepLevel.NIVEL_2, true),
				Arguments.of("L2 other CC", APPROVER_L2_OPS, StepLevel.NIVEL_2, false),
				Arguments.of("ADMIN", ADMIN_GLOBAL, StepLevel.NIVEL_2, true),
				Arguments.of("SOLICITANTE", REQUESTER_TI, StepLevel.NIVEL_2, false),
				// ADMIN: ADMIN only
				Arguments.of("L1 same CC", APPROVER_L1_TI, StepLevel.ADMIN, false),
				Arguments.of("L2 same CC", APPROVER_L2_TI, StepLevel.ADMIN, false),
				Arguments.of("ADMIN", ADMIN_GLOBAL, StepLevel.ADMIN, true),
				Arguments.of("SOLICITANTE", REQUESTER_TI, StepLevel.ADMIN, false));
	}

	@ParameterizedTest(name = "{0} on {2} -> allowed={3}")
	@MethodSource("eligibilityMatrix")
	void appliesLevelAndCostCenterRules(String label, Actor actor, StepLevel step, boolean expected) {
		var result = policy.evaluate(actor, Action.APPROVE, pendingAt(step));

		assertThat(result.allowed()).isEqualTo(expected);
		if (!expected) {
			assertThat(result.code()).isEqualTo(ErrorCode.APPROVER_NOT_ELIGIBLE);
			assertThat(result.reason()).startsWith("Não é possível aprovar: ");
		}
	}

	@Test
	void adminFromAnyCostCenterIsEligible() {
		var adminOfOtherCc = new Actor(8L, Role.ADMIN, CC_OPS, null);

		assertThat(policy.evaluate(adminOfOtherCc, Action.APPROVE, pendingAt(StepLevel.NIVEL_2)).allowed()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = Action.class, names = { "APPROVE", "REJECT", "REQUEST_CHANGES" })
	void nobodyDecidesOnOwnRequest(Action action) {
		var adminOwner = new Actor(OWNER, Role.ADMIN, null, null);

		var result = policy.evaluate(adminOwner, action, pendingAt(StepLevel.ADMIN));

		assertThat(result.allowed()).isFalse();
		assertThat(result.code()).isEqualTo(ErrorCode.SELF_APPROVAL_FORBIDDEN);
		assertThat(result.reason())
				.isEqualTo("Não é possível " + action.verb() + ": você é o solicitante desta solicitação (segregação de funções).");
	}

	@Test
	void sameApproverCannotApproveTwoStepsOfTheSameVersion() {
		var context = pendingAt(StepLevel.NIVEL_2, new StepApproval(StepLevel.NIVEL_1, APPROVER_L2_TI.userId()));

		var result = policy.evaluate(APPROVER_L2_TI, Action.APPROVE, context);

		assertThat(result.allowed()).isFalse();
		assertThat(result.code()).isEqualTo(ErrorCode.DUPLICATE_APPROVER);
		assertThat(result.reason()).isEqualTo("Não é possível aprovar: você já aprovou a etapa NIVEL_1 desta versão (segregação de funções).");
	}

	@Test
	void adminWhoApprovedEarlierStepCannotApproveAdminStep() {
		var context = pendingAt(StepLevel.ADMIN,
				new StepApproval(StepLevel.NIVEL_1, APPROVER_L1_TI.userId()),
				new StepApproval(StepLevel.NIVEL_2, ADMIN_GLOBAL.userId()));

		var result = policy.evaluate(ADMIN_GLOBAL, Action.APPROVE, context);

		assertThat(result.code()).isEqualTo(ErrorCode.DUPLICATE_APPROVER);
	}

	@Test
	void anotherApproverMayDecideNextStep() {
		var context = pendingAt(StepLevel.NIVEL_2, new StepApproval(StepLevel.NIVEL_1, APPROVER_L1_TI.userId()));

		assertThat(policy.evaluate(APPROVER_L2_TI, Action.APPROVE, context).allowed()).isTrue();
	}

	@Test
	void explainsLevelMismatch() {
		var result = policy.evaluate(APPROVER_L1_TI, Action.REJECT, pendingAt(StepLevel.NIVEL_2));

		assertThat(result.reason())
				.isEqualTo("Não é possível rejeitar: a etapa NIVEL_2 exige um aprovador NIVEL_2; seu nível é NIVEL_1.");
	}

	@Test
	void approverWithoutLevelIsNotEligible() {
		var noLevel = new Actor(9L, Role.APROVADOR, CC_TI, null);

		var result = policy.evaluate(noLevel, Action.APPROVE, pendingAt(StepLevel.NIVEL_2));

		assertThat(result.code()).isEqualTo(ErrorCode.APPROVER_NOT_ELIGIBLE);
		assertThat(result.reason()).contains("não tem nível de aprovador");
	}

	@Test
	void orThrowRaisesDomainExceptionWithCode() {
		var result = policy.evaluate(APPROVER_L1_OPS, Action.APPROVE, pendingAt(StepLevel.NIVEL_1));

		assertThatThrownBy(result::orThrow)
				.isInstanceOf(DomainException.class)
				.hasMessageContaining("outro centro de custo")
				.extracting(e -> ((DomainException) e).code())
				.isEqualTo(ErrorCode.APPROVER_NOT_ELIGIBLE);
	}

	@ParameterizedTest
	@EnumSource(value = Action.class, names = { "EDIT", "SUBMIT", "CANCEL" })
	void rejectsNonDecisionActions(Action action) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> policy.evaluate(ADMIN_GLOBAL, action, pendingAt(StepLevel.NIVEL_1)));
	}

}

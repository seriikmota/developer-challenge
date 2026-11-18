package dev.challenge.purchase.domain.request;

import static dev.challenge.purchase.domain.request.Action.APPROVE;
import static dev.challenge.purchase.domain.request.Action.CANCEL;
import static dev.challenge.purchase.domain.request.Action.EDIT;
import static dev.challenge.purchase.domain.request.Action.REJECT;
import static dev.challenge.purchase.domain.request.Action.REQUEST_CHANGES;
import static dev.challenge.purchase.domain.request.Action.SUBMIT;
import static dev.challenge.purchase.domain.request.RequestStatus.APPROVED;
import static dev.challenge.purchase.domain.request.RequestStatus.CANCELLED;
import static dev.challenge.purchase.domain.request.RequestStatus.CHANGES_REQUESTED;
import static dev.challenge.purchase.domain.request.RequestStatus.DRAFT;
import static dev.challenge.purchase.domain.request.RequestStatus.PENDING_APPROVAL;
import static dev.challenge.purchase.domain.request.RequestStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;

class RequestStateMachineTest {

	private final RequestStateMachine stateMachine = new RequestStateMachine();

	/**
	 * Valid transitions of README table 4.2 (plus EDIT, which keeps the status).
	 */
	static Stream<Arguments> validTransitions() {
		return Stream.of(
				Arguments.of(DRAFT, SUBMIT, false, PENDING_APPROVAL),
				Arguments.of(DRAFT, EDIT, false, DRAFT),
				Arguments.of(DRAFT, CANCEL, false, CANCELLED),
				Arguments.of(PENDING_APPROVAL, APPROVE, false, PENDING_APPROVAL),
				Arguments.of(PENDING_APPROVAL, APPROVE, true, APPROVED),
				Arguments.of(PENDING_APPROVAL, REJECT, false, REJECTED),
				Arguments.of(PENDING_APPROVAL, REQUEST_CHANGES, false, CHANGES_REQUESTED),
				Arguments.of(PENDING_APPROVAL, CANCEL, false, CANCELLED),
				Arguments.of(CHANGES_REQUESTED, SUBMIT, false, PENDING_APPROVAL),
				Arguments.of(CHANGES_REQUESTED, EDIT, false, CHANGES_REQUESTED),
				Arguments.of(CHANGES_REQUESTED, CANCEL, false, CANCELLED));
	}

	@ParameterizedTest(name = "{0} --{1} (last step: {2})--> {3}")
	@MethodSource("validTransitions")
	void appliesValidTransition(RequestStatus from, Action action, boolean isLastStep, RequestStatus expected) {
		assertThat(stateMachine.canApply(from, action)).isTrue();
		assertThat(stateMachine.apply(from, action, isLastStep)).isEqualTo(expected);
	}

	/**
	 * Every (status, action) pair not listed as valid must be refused.
	 */
	static Stream<Arguments> invalidTransitions() {
		Set<String> valid = new HashSet<>();
		validTransitions().forEach(args -> valid.add(args.get()[0] + ":" + args.get()[1]));
		return Arrays.stream(RequestStatus.values())
				.flatMap(status -> Arrays.stream(Action.values()).map(action -> Arguments.of(status, action)))
				.filter(args -> !valid.contains(args.get()[0] + ":" + args.get()[1]));
	}

	@ParameterizedTest(name = "{0} --{1}--> refused")
	@MethodSource("invalidTransitions")
	void refusesInvalidTransition(RequestStatus from, Action action) {
		var expectedCode = action == EDIT ? ErrorCode.NOT_EDITABLE : ErrorCode.INVALID_TRANSITION;

		assertThat(stateMachine.canApply(from, action)).isFalse();
		assertThatThrownBy(() -> stateMachine.apply(from, action, true))
				.isInstanceOf(DomainException.class)
				.hasMessageStartingWith("Não é possível " + action.verb() + ": a solicitação está em " + from)
				.extracting(e -> ((DomainException) e).code())
				.isEqualTo(expectedCode);
	}

	@Test
	void coversEveryStatusAndActionPair() {
		assertThat(validTransitions().map(args -> args.get()[0] + ":" + args.get()[1]).distinct().count()
				+ invalidTransitions().count())
				.isEqualTo((long) RequestStatus.values().length * Action.values().length);
	}

	@ParameterizedTest
	@EnumSource(value = RequestStatus.class, names = { "APPROVED", "REJECTED", "CANCELLED" })
	void finalStatusesAllowNothing(RequestStatus status) {
		assertThat(status.isFinal()).isTrue();
		assertThat(stateMachine.availableActions(status)).isEmpty();
		assertThatThrownBy(() -> stateMachine.apply(status, CANCEL, false))
				.hasMessage("Não é possível cancelar: a solicitação está em " + status + ", que é um estado final.");
	}

	@Test
	void explainsWhichActionsTheStatusAllows() {
		assertThatThrownBy(() -> stateMachine.apply(DRAFT, APPROVE, false))
				.hasMessage("Não é possível aprovar: a solicitação está em DRAFT. Ações possíveis nesse estado: editar, submeter, cancelar.");
	}

	@Test
	void explainsWhenEditingIsNotAllowed() {
		assertThatThrownBy(() -> stateMachine.apply(PENDING_APPROVAL, EDIT, false))
				.hasMessage("Não é possível editar: a solicitação está em PENDING_APPROVAL; só é possível editar em DRAFT ou CHANGES_REQUESTED.");
	}

	@Test
	void availableActionsCannotBeModified() {
		assertThatThrownBy(() -> stateMachine.availableActions(DRAFT).add(APPROVE))
				.isInstanceOf(UnsupportedOperationException.class);
	}

}

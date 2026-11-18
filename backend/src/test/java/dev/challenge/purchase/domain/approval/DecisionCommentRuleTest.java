package dev.challenge.purchase.domain.approval;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.request.Action;

class DecisionCommentRuleTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "curto", "123456789", "   123456789   " })
	void rejectAndRequestChangesNeedTenCharacters(String comment) {
		for (var action : new Action[] { Action.REJECT, Action.REQUEST_CHANGES }) {
			assertThatThrownBy(() -> DecisionCommentRule.validate(action, comment))
					.isInstanceOf(DomainException.class)
					.hasMessageStartingWith("Não é possível " + action.verb() + ": informe um comentário")
					.extracting(e -> ((DomainException) e).code())
					.isEqualTo(ErrorCode.COMMENT_REQUIRED);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "1234567890", "Fora do orçamento do trimestre" })
	void acceptsCommentWithTenOrMoreCharacters(String comment) {
		assertThatCode(() -> DecisionCommentRule.validate(Action.REJECT, comment)).doesNotThrowAnyException();
		assertThatCode(() -> DecisionCommentRule.validate(Action.REQUEST_CHANGES, comment))
				.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@EnumSource(value = Action.class, names = { "REJECT", "REQUEST_CHANGES" }, mode = EnumSource.Mode.EXCLUDE)
	void otherActionsDoNotRequireComment(Action action) {
		assertThatCode(() -> DecisionCommentRule.validate(action, null)).doesNotThrowAnyException();
	}

}

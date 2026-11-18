package dev.challenge.purchase.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RequestEventTypeTest {

	@ParameterizedTest
	@EnumSource(Action.class)
	void everyActionHasAnEventTypeWithTheSameName(Action action) {
		assertThat(RequestEventType.of(action).name()).isEqualTo(action.name());
	}

}

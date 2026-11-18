package dev.challenge.purchase.domain.approval;

import static dev.challenge.purchase.domain.approval.StepLevel.ADMIN;
import static dev.challenge.purchase.domain.approval.StepLevel.NIVEL_1;
import static dev.challenge.purchase.domain.approval.StepLevel.NIVEL_2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;

class ApprovalChainFactoryTest {

	private final ApprovalChainFactory factory = new ApprovalChainFactory();

	static Stream<Arguments> chains() {
		return Stream.of(
				Arguments.of("0.01", Category.EQUIPMENT, List.of(NIVEL_1)),
				Arguments.of("1000.00", Category.EQUIPMENT, List.of(NIVEL_1)),
				Arguments.of("1000.01", Category.EQUIPMENT, List.of(NIVEL_1, NIVEL_2)),
				Arguments.of("10000.00", Category.SERVICES, List.of(NIVEL_1, NIVEL_2)),
				Arguments.of("10000.01", Category.SERVICES, List.of(NIVEL_1, NIVEL_2, ADMIN)),
				Arguments.of("24499.50", Category.EQUIPMENT, List.of(NIVEL_1, NIVEL_2, ADMIN)),
				// SOFTWARE always ends with ADMIN, never duplicated
				Arguments.of("100.00", Category.SOFTWARE, List.of(NIVEL_1, ADMIN)),
				Arguments.of("1000.00", Category.SOFTWARE, List.of(NIVEL_1, ADMIN)),
				Arguments.of("1000.01", Category.SOFTWARE, List.of(NIVEL_1, NIVEL_2, ADMIN)),
				Arguments.of("10000.00", Category.SOFTWARE, List.of(NIVEL_1, NIVEL_2, ADMIN)),
				Arguments.of("50000.00", Category.SOFTWARE, List.of(NIVEL_1, NIVEL_2, ADMIN)));
	}

	@ParameterizedTest(name = "{1} R$ {0} -> {2}")
	@MethodSource("chains")
	void buildsChainByAmountAndCategory(String total, Category category, List<StepLevel> expected) {
		assertThat(factory.build(Money.of(total), category)).containsExactlyElementsOf(expected);
	}

	@ParameterizedTest
	@EnumSource(value = Category.class, names = "SOFTWARE", mode = EnumSource.Mode.EXCLUDE)
	void onlySoftwareForcesAdminBelowLimit(Category category) {
		assertThat(factory.build(Money.of("500.00"), category)).containsExactly(NIVEL_1);
	}

	@Test
	void rejectsNonPositiveTotal() {
		assertThatIllegalArgumentException().isThrownBy(() -> factory.build(Money.ZERO, Category.OTHER));
	}

}

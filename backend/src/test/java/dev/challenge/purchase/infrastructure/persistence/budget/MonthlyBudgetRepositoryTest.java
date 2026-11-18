package dev.challenge.purchase.infrastructure.persistence.budget;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.infrastructure.persistence.PersistenceIntegrationTest;

class MonthlyBudgetRepositoryTest extends PersistenceIntegrationTest {

	private static final CompetenceMonth NOVEMBER = CompetenceMonth.parse("2026-11");

	@Autowired
	private MonthlyBudgetRepository budgets;

	private Long costCenterId;

	@BeforeEach
	void setUp() {
		costCenterId = newCostCenter().getId();
	}

	private MonthlyBudgetEntity reload() {
		flushAndClear();
		return budgets.findByCostCenterIdAndMonth(costCenterId, NOVEMBER).orElseThrow();
	}

	@Test
	void createsBudgetOfTheMonth() {
		assertThat(budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("10000.00"), NOW)).isTrue();

		var budget = reload();
		assertThat(budget.getAmount()).isEqualTo(Money.of("10000.00"));
		assertThat(budget.getConsumed()).isEqualTo(Money.ZERO);
		assertThat(budget.getMonth()).isEqualTo(NOVEMBER);
	}

	@Test
	void consumesWhenBalanceCovers() {
		budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("10000.00"), NOW);

		assertThat(budgets.tryConsume(costCenterId, NOVEMBER, Money.of("4000.00"), NOW)).isTrue();
		assertThat(budgets.tryConsume(costCenterId, NOVEMBER, Money.of("6000.00"), NOW)).isTrue();

		var budget = reload();
		assertThat(budget.getConsumed()).isEqualTo(Money.of("10000.00"));
		assertThat(budget.getAvailable()).isEqualTo(Money.ZERO);
	}

	@Test
	void refusesConsumptionAboveBalanceWithoutChangingIt() {
		budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("8200.00"), NOW);

		assertThat(budgets.tryConsume(costCenterId, NOVEMBER, Money.of("8200.01"), NOW)).isFalse();

		assertThat(reload().getConsumed()).isEqualTo(Money.ZERO);
	}

	@Test
	void monthWithoutBudgetHasZeroBalance() {
		assertThat(budgets.tryConsume(costCenterId, NOVEMBER, Money.of("0.01"), NOW)).isFalse();
		assertThat(budgets.findByCostCenterIdAndMonth(costCenterId, NOVEMBER)).isEmpty();
	}

	@Test
	void updatesAmountNotBelowConsumed() {
		budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("10000.00"), NOW);
		budgets.tryConsume(costCenterId, NOVEMBER, Money.of("3000.00"), NOW);

		assertThat(budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("3000.00"), NOW)).isTrue();

		assertThat(reload().getAmount()).isEqualTo(Money.of("3000.00"));
	}

	@Test
	void refusesAmountBelowConsumedWithoutChangingIt() {
		budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("10000.00"), NOW);
		budgets.tryConsume(costCenterId, NOVEMBER, Money.of("3000.00"), NOW);

		assertThat(budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("2999.99"), NOW)).isFalse();

		assertThat(reload().getAmount()).isEqualTo(Money.of("10000.00"));
	}

	@Test
	void budgetsAreSeparatedByMonth() {
		budgets.trySetAmount(costCenterId, NOVEMBER, Money.of("1000.00"), NOW);

		assertThat(budgets.tryConsume(costCenterId, CompetenceMonth.parse("2026-12"), Money.of("1.00"), NOW)).isFalse();
		assertThat(budgets.findByMonth(NOVEMBER)).extracting(MonthlyBudgetEntity::getCostCenterId).contains(costCenterId);
	}

}

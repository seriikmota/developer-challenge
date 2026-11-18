package dev.challenge.purchase.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.challenge.purchase.domain.budget.Money;

class LineItemTest {

	@Test
	void lineTotalIsQuantityTimesUnitPrice() {
		assertThat(new LineItem("Notebook", 5, Money.of("4899.90")).lineTotal()).isEqualTo(Money.of("24499.50"));
	}

	@Test
	void totalIsTheSumOfTheLines() {
		var items = List.of(new LineItem("Notebook", 5, Money.of("4899.90")), new LineItem("Mouse", 3, Money.of("0.10")));

		assertThat(LineItem.total(items)).isEqualTo(Money.of("24499.80"));
	}

	@Test
	void totalOfNoItemsIsZero() {
		assertThat(LineItem.total(List.of())).isEqualTo(Money.ZERO);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1 })
	void quantityMustBePositive(int quantity) {
		assertThatIllegalArgumentException().isThrownBy(() -> new LineItem("Notebook", quantity, Money.of("10.00")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.00", "-0.01", "0.004" })
	void unitPriceMustBePositiveAfterRounding(String unitPrice) {
		assertThatIllegalArgumentException().isThrownBy(() -> new LineItem("Notebook", 1, Money.of(unitPrice)));
	}

}

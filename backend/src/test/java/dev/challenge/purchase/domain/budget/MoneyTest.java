package dev.challenge.purchase.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

	@Test
	void normalizesToTwoDecimalPlaces() {
		assertThat(Money.of("10").amount()).isEqualTo(new BigDecimal("10.00"));
		assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
	}

	@ParameterizedTest
	@CsvSource({
			"1.005, 1.00",
			"1.015, 1.02",
			"1.025, 1.02",
			"1.0051, 1.01",
			"-1.005, -1.00"
	})
	void roundsHalfEven(String input, String expected) {
		assertThat(Money.of(input)).isEqualTo(Money.of(expected));
	}

	@Test
	void multipliesByQuantityWithoutPrecisionLoss() {
		assertThat(Money.of("4899.90").times(5)).isEqualTo(Money.of("24499.50"));
		assertThat(Money.of("0.10").times(3)).isEqualTo(Money.of("0.30"));
	}

	@Test
	void addsAndSubtracts() {
		assertThat(Money.of("0.10").plus(Money.of("0.20"))).isEqualTo(Money.of("0.30"));
		assertThat(Money.of("100.00").minus(Money.of("100.01"))).isEqualTo(Money.of("-0.01"));
	}

	@Test
	void comparesByValue() {
		assertThat(Money.of("1000.01").isGreaterThan(Money.of("1000.00"))).isTrue();
		assertThat(Money.of("1000.00").isGreaterThan(Money.of("1000"))).isFalse();
		assertThat(Money.of("999.99").isLessThan(Money.of("1000.00"))).isTrue();
	}

	@Test
	void reportsSign() {
		assertThat(Money.of("0.01").isPositive()).isTrue();
		assertThat(Money.ZERO.isPositive()).isFalse();
		assertThat(Money.ZERO.isNegative()).isFalse();
		assertThat(Money.of("-0.01").isNegative()).isTrue();
	}

	@Test
	void formatsAsBrazilianReal() {
		assertThat(Money.of("24499.5").format()).isEqualTo("R$ 24.499,50");
		assertThat(Money.of("0").format()).isEqualTo("R$ 0,00");
	}

	@Test
	void serializesAsPlainString() {
		assertThat(Money.of("1E+3")).hasToString("1000.00");
	}

	@Test
	void rejectsNull() {
		assertThatNullPointerException().isThrownBy(() -> Money.of((BigDecimal) null));
		assertThatNullPointerException().isThrownBy(() -> Money.of((String) null));
	}

}

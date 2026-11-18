package dev.challenge.purchase.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompetenceMonthTest {

	@ParameterizedTest(name = "{0} UTC -> {1}")
	@CsvSource({
			// Sao Paulo is UTC-3: the month turns at 03:00 UTC
			"2026-12-01T02:59:59Z, 2026-11",
			"2026-12-01T03:00:00Z, 2026-12",
			"2027-01-01T02:59:59Z, 2026-12",
			"2027-01-01T03:00:00Z, 2027-01",
			"2026-11-15T12:00:00Z, 2026-11"
	})
	void usesSaoPauloTimeZone(String instant, String expected) {
		assertThat(CompetenceMonth.of(Instant.parse(instant))).hasToString(expected);
	}

	@Test
	void parsesApiFormat() {
		var month = CompetenceMonth.parse("2026-11");

		assertThat(month).isEqualTo(CompetenceMonth.of(Instant.parse("2026-11-20T10:00:00Z")));
		assertThat(month.firstDay()).isEqualTo(LocalDate.of(2026, 11, 1));
	}

	@Test
	void ordersChronologically() {
		assertThat(CompetenceMonth.parse("2026-12")).isGreaterThan(CompetenceMonth.parse("2026-11"));
	}

}

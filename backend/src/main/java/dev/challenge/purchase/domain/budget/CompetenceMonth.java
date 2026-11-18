package dev.challenge.purchase.domain.budget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Budget month a request is charged to: the month of the current version's submission,
 * evaluated in the {@code America/Sao_Paulo} time zone.
 */
public record CompetenceMonth(YearMonth value) implements Comparable<CompetenceMonth> {

	public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	public CompetenceMonth {
		Objects.requireNonNull(value, "value");
	}

	public static CompetenceMonth of(Instant instant) {
		return new CompetenceMonth(YearMonth.from(instant.atZone(ZONE)));
	}

	/**
	 * Parses the {@code yyyy-MM} format used in the API path.
	 */
	public static CompetenceMonth parse(String yearMonth) {
		return new CompetenceMonth(YearMonth.parse(yearMonth));
	}

	public LocalDate firstDay() {
		return value.atDay(1);
	}

	@Override
	public int compareTo(CompetenceMonth other) {
		return value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}

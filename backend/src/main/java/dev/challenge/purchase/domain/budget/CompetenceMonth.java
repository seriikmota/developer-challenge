package dev.challenge.purchase.domain.budget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Budget month a request is charged to: the month of the current version's submission,
 * evaluated in the {@code America/Sao_Paulo} time zone.
 */
public record CompetenceMonth(YearMonth value) implements Comparable<CompetenceMonth> {

	public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MM/yyyy");

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

	/**
	 * Month for messages shown to the user, e.g. {@code 11/2026}. The API keeps the ISO form ({@link #toString()}).
	 */
	public String format() {
		return value.format(DISPLAY);
	}

	@Override
	public String toString() {
		return value.toString();
	}

}

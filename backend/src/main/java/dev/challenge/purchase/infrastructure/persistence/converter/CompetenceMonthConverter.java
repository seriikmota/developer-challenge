package dev.challenge.purchase.infrastructure.persistence.converter;

import java.time.LocalDate;
import java.time.YearMonth;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link CompetenceMonth} as a {@code DATE} on the first day of the month.
 */
@Converter(autoApply = true)
public class CompetenceMonthConverter implements AttributeConverter<CompetenceMonth, LocalDate> {

	@Override
	public LocalDate convertToDatabaseColumn(CompetenceMonth month) {
		return month == null ? null : month.firstDay();
	}

	@Override
	public CompetenceMonth convertToEntityAttribute(LocalDate date) {
		return date == null ? null : new CompetenceMonth(YearMonth.from(date));
	}

}

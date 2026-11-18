package dev.challenge.purchase.infrastructure.persistence.converter;

import java.math.BigDecimal;

import dev.challenge.purchase.domain.budget.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link Money} as {@code NUMERIC(19,2)}.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

	@Override
	public BigDecimal convertToDatabaseColumn(Money money) {
		return money == null ? null : money.amount();
	}

	@Override
	public Money convertToEntityAttribute(BigDecimal amount) {
		return amount == null ? null : Money.of(amount);
	}

}

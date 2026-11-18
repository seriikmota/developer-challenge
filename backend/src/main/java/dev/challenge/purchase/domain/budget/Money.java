package dev.challenge.purchase.domain.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

/**
 * Monetary amount in BRL, always normalized to 2 decimal places with banker's rounding (HALF_EVEN).
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

	public static final int SCALE = 2;

	public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

	public static final Money ZERO = new Money(BigDecimal.ZERO);

	private static final Locale PT_BR = Locale.of("pt", "BR");

	public Money {
		Objects.requireNonNull(amount, "amount");
		amount = amount.setScale(SCALE, ROUNDING);
	}

	public static Money of(BigDecimal amount) {
		return new Money(amount);
	}

	public static Money of(String amount) {
		return new Money(new BigDecimal(Objects.requireNonNull(amount, "amount")));
	}

	public Money plus(Money other) {
		return new Money(amount.add(other.amount));
	}

	public Money minus(Money other) {
		return new Money(amount.subtract(other.amount));
	}

	public Money times(int quantity) {
		return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isGreaterThan(Money other) {
		return compareTo(other) > 0;
	}

	public boolean isLessThan(Money other) {
		return compareTo(other) < 0;
	}

	@Override
	public int compareTo(Money other) {
		return amount.compareTo(other.amount);
	}

	/**
	 * Human-readable BRL amount for messages, e.g. {@code R$ 24.499,50}.
	 */
	public String format() {
		var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
		return "R$ " + format.format(amount);
	}

	@Override
	public String toString() {
		return amount.toPlainString();
	}

}

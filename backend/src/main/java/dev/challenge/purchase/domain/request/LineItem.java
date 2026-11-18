package dev.challenge.purchase.domain.request;

import java.util.List;
import java.util.Objects;

import dev.challenge.purchase.domain.budget.Money;

/**
 * One item of a request version. The line total and the request total are always computed here, on the server;
 * any total sent by the client is ignored.
 */
public record LineItem(String description, int quantity, Money unitPrice) {

	public LineItem {
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(unitPrice, "unitPrice");
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be greater than zero, got " + quantity);
		}
		if (!unitPrice.isPositive()) {
			throw new IllegalArgumentException("unitPrice must be greater than zero, got " + unitPrice);
		}
	}

	public Money lineTotal() {
		return unitPrice.times(quantity);
	}

	public static Money total(List<LineItem> items) {
		return items.stream().map(LineItem::lineTotal).reduce(Money.ZERO, Money::plus);
	}

}

package dev.challenge.purchase.application.costcenter;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;

/**
 * Budget of a cost center in a month. Money values are strings with 2 decimals (e.g. {@code "8200.00"}).
 */
public record BudgetView(Long costCenterId, String costCenterCode, String month, String amount, String consumed, String available) {

	public static BudgetView of(Long costCenterId, String costCenterCode, CompetenceMonth month, Money amount, Money consumed) {
		return new BudgetView(costCenterId, costCenterCode, month.toString(), amount.toString(), consumed.toString(), amount.minus(consumed).toString());
	}

}

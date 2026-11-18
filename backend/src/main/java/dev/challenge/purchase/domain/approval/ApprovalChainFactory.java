package dev.challenge.purchase.domain.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;

/**
 * Builds the ordered approval chain for a request version on submission.
 *
 * <ul>
 * <li>up to R$ 1.000,00: NIVEL_1</li>
 * <li>R$ 1.000,01 to R$ 10.000,00: NIVEL_1, NIVEL_2</li>
 * <li>above R$ 10.000,00: NIVEL_1, NIVEL_2, ADMIN</li>
 * <li>SOFTWARE always ends with ADMIN (never duplicated)</li>
 * </ul>
 */
public final class ApprovalChainFactory {

	static final Money LEVEL_1_LIMIT = Money.of("1000.00");

	static final Money LEVEL_2_LIMIT = Money.of("10000.00");

	public List<StepLevel> build(Money total, Category category) {
		Objects.requireNonNull(total, "total");
		Objects.requireNonNull(category, "category");
		if (!total.isPositive()) {
			throw new IllegalArgumentException("Request total must be positive, got " + total);
		}

		var steps = new ArrayList<StepLevel>(3);
		steps.add(StepLevel.NIVEL_1);
		if (total.isGreaterThan(LEVEL_1_LIMIT)) {
			steps.add(StepLevel.NIVEL_2);
		}
		if (total.isGreaterThan(LEVEL_2_LIMIT) || category == Category.SOFTWARE) {
			steps.add(StepLevel.ADMIN);
		}
		return List.copyOf(steps);
	}

}

package dev.challenge.purchase.application.costcenter;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetEntity;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetRepository;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;

/**
 * Cost centers and their monthly budgets. A month without a budget row reads as zero.
 */
@Service
public class CostCenterService {

	private final CostCenterRepository costCenters;

	private final MonthlyBudgetRepository budgets;

	private final Clock clock;

	public CostCenterService(CostCenterRepository costCenters, MonthlyBudgetRepository budgets, Clock clock) {
		this.costCenters = costCenters;
		this.budgets = budgets;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CostCenterView> list() {
		return costCenters.findAll(Sort.by("code")).stream().map(CostCenterView::of).toList();
	}

	@Transactional
	public CostCenterView create(String code, String name) {
		var normalizedCode = code.strip().toUpperCase(Locale.ROOT);
		if (costCenters.existsByCode(normalizedCode)) {
			throw codeTaken(normalizedCode);
		}
		try {
			return CostCenterView.of(costCenters.saveAndFlush(new CostCenterEntity(normalizedCode, name.strip(), clock.instant())));
		} catch (DataIntegrityViolationException e) {
			throw codeTaken(normalizedCode);
		}
	}

	@Transactional(readOnly = true)
	public BudgetView budget(Long costCenterId, CompetenceMonth month) {
		var costCenter = find(costCenterId);
		return budgets.findByCostCenterIdAndMonth(costCenterId, month)
				.map(budget -> BudgetView.of(costCenterId, costCenter.getCode(), month, budget.getAmount(), budget.getConsumed()))
				.orElseGet(() -> BudgetView.of(costCenterId, costCenter.getCode(), month, Money.ZERO, Money.ZERO));
	}

	/**
	 * Creates or replaces the budget of the month. Refused when the amount is below what was already consumed.
	 */
	@Transactional
	public BudgetView defineBudget(Long costCenterId, CompetenceMonth month, Money amount) {
		var costCenter = find(costCenterId);
		if (!budgets.trySetAmount(costCenterId, month, amount, clock.instant())) {
			var consumed = budgets.findByCostCenterIdAndMonth(costCenterId, month).map(MonthlyBudgetEntity::getConsumed).orElse(Money.ZERO);
			throw new DomainException(ErrorCode.BUDGET_BELOW_CONSUMED, "Não é possível definir o orçamento de " + month.format() + " do centro de custo " + costCenter.getCode() + " em " + amount.format() + ": já foram consumidos " + consumed.format() + ".");
		}
		return budget(costCenterId, month);
	}

	CostCenterEntity find(Long costCenterId) {
		return costCenters.findById(costCenterId).orElseThrow(() -> new NotFoundException("Centro de custo " + costCenterId + " não encontrado."));
	}

	private static ConflictException codeTaken(String code) {
		return new ConflictException("COST_CENTER_CODE_TAKEN", "Já existe um centro de custo com o código " + code + ".");
	}

}

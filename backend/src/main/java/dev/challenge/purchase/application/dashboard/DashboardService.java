package dev.challenge.purchase.application.dashboard;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.costcenter.BudgetView;
import dev.challenge.purchase.application.request.RequestVisibility;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetEntity;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetRepository;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

/**
 * Totals by status in the user's scope (the same visibility as the request list) and the current month's budget of
 * each cost center in scope (every cost center for ADMIN, the user's own otherwise).
 */
@Service
public class DashboardService {

	private final EntityManager entityManager;

	private final CostCenterRepository costCenters;

	private final MonthlyBudgetRepository budgets;

	private final Clock clock;

	public DashboardService(EntityManager entityManager, CostCenterRepository costCenters, MonthlyBudgetRepository budgets, Clock clock) {
		this.entityManager = entityManager;
		this.costCenters = costCenters;
		this.budgets = budgets;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public DashboardView summary(Actor actor) {
		var month = CompetenceMonth.of(clock.instant());
		return new DashboardView(totalsByStatus(actor), month.toString(), currentBudgets(actor, month));
	}

	private Map<RequestStatus, Long> totalsByStatus(Actor actor) {
		var cb = entityManager.getCriteriaBuilder();
		var query = cb.createTupleQuery();
		var root = query.from(PurchaseRequestEntity.class);
		var status = root.<RequestStatus>get("status");
		query.multiselect(status, cb.count(root)).where(RequestVisibility.visibleTo(actor).toPredicate(root, query, cb)).groupBy(status);
		var totals = new EnumMap<RequestStatus, Long>(RequestStatus.class);
		for (var value : RequestStatus.values()) {
			totals.put(value, 0L);
		}
		for (Tuple row : entityManager.createQuery(query).getResultList()) {
			totals.put(row.get(0, RequestStatus.class), row.get(1, Long.class));
		}
		return totals;
	}

	private List<BudgetView> currentBudgets(Actor actor, CompetenceMonth month) {
		List<CostCenterEntity> scope;
		if (actor.isAdmin()) {
			scope = costCenters.findAll(Sort.by("code"));
		} else if (actor.costCenterId() != null) {
			scope = costCenters.findById(actor.costCenterId()).stream().toList();
		} else {
			scope = List.of();
		}
		var byCostCenter = budgets.findByMonth(month).stream().collect(Collectors.toMap(MonthlyBudgetEntity::getCostCenterId, Function.identity()));
		return scope.stream().map(costCenter -> {
			var budget = byCostCenter.get(costCenter.getId());
			var amount = budget == null ? Money.ZERO : budget.getAmount();
			var consumed = budget == null ? Money.ZERO : budget.getConsumed();
			return BudgetView.of(costCenter.getId(), costCenter.getCode(), month, amount, consumed);
		}).toList();
	}

	/**
	 * @param totalsByStatus every status, zero included
	 * @param month          current competence month ({@code yyyy-MM}, America/Sao_Paulo)
	 */
	public record DashboardView(Map<RequestStatus, Long> totalsByStatus, String month, List<BudgetView> budgets) {
	}

}

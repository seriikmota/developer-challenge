package dev.challenge.purchase.infrastructure.persistence.budget;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Budget of one cost center in one competence month. Read-only for JPA: rows are created and changed only by the
 * atomic conditional statements of {@link MonthlyBudgetRepository}, never by dirty checking.
 */
@Entity
@Immutable
@Table(name = "monthly_budget")
public class MonthlyBudgetEntity {

	@Id
	private Long id;

	@Column(nullable = false)
	private Long costCenterId;

	@Column(nullable = false)
	private CompetenceMonth month;

	@Column(nullable = false, precision = 19, scale = 2)
	private Money amount;

	@Column(nullable = false, precision = 19, scale = 2)
	private Money consumed;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected MonthlyBudgetEntity() {
	}

	public Long getId() {
		return id;
	}

	public Long getCostCenterId() {
		return costCenterId;
	}

	public CompetenceMonth getMonth() {
		return month;
	}

	public Money getAmount() {
		return amount;
	}

	public Money getConsumed() {
		return consumed;
	}

	public Money getAvailable() {
		return amount.minus(consumed);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

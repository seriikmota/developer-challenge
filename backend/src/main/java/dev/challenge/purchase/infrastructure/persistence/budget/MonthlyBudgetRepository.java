package dev.challenge.purchase.infrastructure.persistence.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;

/**
 * Budget writes are single conditional SQL statements: the database checks the balance and changes it atomically,
 * under the row lock taken by the write, so concurrent approvals never act on a stale balance.
 */
public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudgetEntity, Long> {

	Optional<MonthlyBudgetEntity> findByCostCenterIdAndMonth(Long costCenterId, CompetenceMonth month);

	List<MonthlyBudgetEntity> findByMonth(CompetenceMonth month);

	/**
	 * Consumes {@code amount} only if the balance covers it. A month without a budget row has zero balance.
	 *
	 * @return {@code true} if consumed; {@code false} if the balance is insufficient, and nothing changed
	 */
	default boolean tryConsume(Long costCenterId, CompetenceMonth month, Money amount, Instant now) {
		return consumeIfAvailable(costCenterId, month.firstDay(), amount.amount(), now) == 1;
	}

	/**
	 * Creates the budget of the month or replaces its amount, refusing an amount below what is already consumed.
	 *
	 * @return {@code true} if saved; {@code false} if {@code amount} is below the consumed value, and nothing changed
	 */
	default boolean trySetAmount(Long costCenterId, CompetenceMonth month, Money amount, Instant now) {
		return upsertAmountIfNotBelowConsumed(costCenterId, month.firstDay(), amount.amount(), now) == 1;
	}

	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE monthly_budget
			   SET consumed = consumed + :amount, updated_at = :now
			 WHERE cost_center_id = :costCenterId
			   AND month = :month
			   AND amount - consumed >= :amount
			""")
	int consumeIfAvailable(@Param("costCenterId") Long costCenterId, @Param("month") LocalDate month, @Param("amount") BigDecimal amount, @Param("now") Instant now);

	@Modifying(flushAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO monthly_budget AS b (cost_center_id, month, amount, consumed, created_at, updated_at)
			VALUES (:costCenterId, :month, :amount, 0, :now, :now)
			ON CONFLICT (cost_center_id, month)
			DO UPDATE SET amount = EXCLUDED.amount, updated_at = EXCLUDED.updated_at
			 WHERE b.consumed <= EXCLUDED.amount
			""")
	int upsertAmountIfNotBelowConsumed(@Param("costCenterId") Long costCenterId, @Param("month") LocalDate month, @Param("amount") BigDecimal amount, @Param("now") Instant now);

}

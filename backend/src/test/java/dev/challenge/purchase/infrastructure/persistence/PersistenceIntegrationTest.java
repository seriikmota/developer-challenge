package dev.challenge.purchase.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.IntegrationTest;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.LineItem;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;
import jakarta.persistence.EntityManager;

/**
 * Base of the persistence tests: real PostgreSQL (Testcontainers) with the Flyway schema, one shared Spring context,
 * and a transaction rolled back after each test.
 */
@IntegrationTest
@Transactional
public abstract class PersistenceIntegrationTest {

	protected static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

	@Autowired
	protected EntityManager entityManager;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected CostCenterRepository costCenters;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PurchaseRequestRepository requests;

	protected CostCenterEntity newCostCenter() {
		var code = "CC-" + UUID.randomUUID().toString().substring(0, 8);
		return costCenters.save(new CostCenterEntity(code, "Centro " + code, NOW));
	}

	protected UserEntity newUser(Role role, ApproverLevel level, CostCenterEntity costCenter) {
		var email = UUID.randomUUID() + "@example.test";
		return users.save(new UserEntity("Usuário " + role, email, "{bcrypt}hash", role, level, costCenter == null ? null : costCenter.getId(), NOW));
	}

	protected PurchaseRequestEntity newDraft(UserEntity owner, List<LineItem> items) {
		return requests.save(new PurchaseRequestEntity(owner.getId(), owner.getCostCenterId(), "Notebooks para o time de TI", "Substituição de equipamentos", Category.EQUIPMENT, null, items, NOW));
	}

	protected static LineItem item(String description, int quantity, String unitPrice) {
		return new LineItem(description, quantity, Money.of(unitPrice));
	}

	/**
	 * Writes pending changes and forgets loaded entities, so the next read comes from the database.
	 */
	protected void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Checks deferred constraints now, as the commit would.
	 */
	protected void checkDeferredConstraints() {
		entityManager.flush();
		jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
	}

}

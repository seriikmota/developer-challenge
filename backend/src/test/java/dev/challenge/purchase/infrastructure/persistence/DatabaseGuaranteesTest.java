package dev.challenge.purchase.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.challenge.purchase.domain.approval.ApprovalChainFactory;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventEntity;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventRepository;
import dev.challenge.purchase.infrastructure.persistence.idempotency.IdempotencyKeyEntity;
import dev.challenge.purchase.infrastructure.persistence.idempotency.IdempotencyKeyRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;

/**
 * Rules the database enforces by itself, as a safety net under the application: append-only history,
 * immutable submitted versions, segregation of duties per version and consistent budgets and users.
 */
class DatabaseGuaranteesTest extends PersistenceIntegrationTest {

	@Autowired
	private RequestEventRepository events;

	@Autowired
	private IdempotencyKeyRepository idempotencyKeys;

	private CostCenterEntity costCenter;

	private UserEntity owner;

	@BeforeEach
	void setUp() {
		costCenter = newCostCenter();
		owner = newUser(Role.SOLICITANTE, null, costCenter);
	}

	private PurchaseRequestEntity submittedRequest() {
		var request = newDraft(owner, List.of(item("Monitor", 2, "1500.00")));
		var version = request.currentVersion();
		version.submit(NOW, new ApprovalChainFactory().build(version.getTotal(), version.getCategory()));
		request.changeStatus(RequestStatus.PENDING_APPROVAL, NOW);
		entityManager.flush();
		return request;
	}

	private RequestEventEntity recordCreation(PurchaseRequestEntity request) {
		var event = events.saveAndFlush(new RequestEventEntity(request.getId(), 1, null, owner.getId(), owner.getRole(), RequestEventType.CREATE, null, RequestStatus.DRAFT, null, "req-1", NOW));
		entityManager.clear();
		return event;
	}

	@Test
	void historyIsReadInOrder() {
		var request = newDraft(owner, List.of(item("Monitor", 1, "100.00")));
		recordCreation(request);
		events.saveAndFlush(new RequestEventEntity(request.getId(), 1, null, owner.getId(), owner.getRole(), RequestEventType.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_APPROVAL, null, "req-2", NOW.plusSeconds(60)));

		assertThat(events.findByRequestIdOrderByOccurredAtAscIdAsc(request.getId()))
				.extracting(RequestEventEntity::getAction)
				.containsExactly(RequestEventType.CREATE, RequestEventType.SUBMIT);
	}

	@Test
	void historyRejectsUpdate() {
		var event = recordCreation(newDraft(owner, List.of(item("Monitor", 1, "100.00"))));

		assertThatThrownBy(() -> jdbc.update("UPDATE request_event SET comment = 'changed' WHERE id = ?", event.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void historyRejectsDelete() {
		var event = recordCreation(newDraft(owner, List.of(item("Monitor", 1, "100.00"))));

		assertThatThrownBy(() -> jdbc.update("DELETE FROM request_event WHERE id = ?", event.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void historyRejectsTruncate() {
		recordCreation(newDraft(owner, List.of(item("Monitor", 1, "100.00"))));

		assertThatThrownBy(() -> jdbc.execute("TRUNCATE request_event CASCADE"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void submittedVersionRejectsUpdate() {
		var version = submittedRequest().currentVersion();

		assertThatThrownBy(() -> jdbc.update("UPDATE request_version SET title = 'changed' WHERE id = ?", version.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("is submitted and cannot be changed");
	}

	@Test
	void submittedVersionRejectsItemChanges() {
		var version = submittedRequest().currentVersion();

		assertThatThrownBy(() -> jdbc.update("UPDATE request_item SET quantity = 99, line_total = 99 * unit_price WHERE version_id = ?", version.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("cannot be changed");
	}

	@Test
	void sameUserCannotDecideTwoStepsOfTheSameVersion() {
		var approver = newUser(Role.APROVADOR, ApproverLevel.NIVEL_2, costCenter);
		var request = submittedRequest();
		var steps = request.currentVersion().getSteps();
		steps.get(0).decide(StepStatus.APPROVED, approver.getId(), null, NOW);
		entityManager.flush();

		steps.get(1).decide(StepStatus.APPROVED, approver.getId(), null, NOW);

		assertThatThrownBy(() -> requests.saveAndFlush(request))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_approval_step_decided_by");
	}

	@Test
	void itemLineTotalMustMatchQuantityTimesUnitPrice() {
		var version = newDraft(owner, List.of(item("Monitor", 1, "100.00"))).currentVersion();
		entityManager.flush();

		assertThatThrownBy(() -> jdbc.update("UPDATE request_item SET line_total = 1 WHERE version_id = ?", version.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_request_item_line_total");
	}

	@Test
	void budgetConsumptionCannotExceedAmount() {
		jdbc.update("INSERT INTO monthly_budget (cost_center_id, month, amount, consumed) VALUES (?, DATE '2026-11-01', 100, 0)", costCenter.getId());

		assertThatThrownBy(() -> jdbc.update("UPDATE monthly_budget SET consumed = 100.01 WHERE cost_center_id = ?", costCenter.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_monthly_budget_consumed");
	}

	@Test
	void budgetMonthIsTheFirstDay() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO monthly_budget (cost_center_id, month, amount) VALUES (?, DATE '2026-11-15', 100)", costCenter.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_monthly_budget_first_day");
	}

	@Test
	void approverMustHaveLevel() {
		assertThatThrownBy(() -> users.saveAndFlush(new UserEntity("Sem nível", "sem-nivel@example.test", "hash", Role.APROVADOR, null, costCenter.getId(), NOW)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_app_user_level_by_role");
	}

	@Test
	void nonAdminMustHaveCostCenter() {
		assertThatThrownBy(() -> users.saveAndFlush(new UserEntity("Sem centro", "sem-centro@example.test", "hash", Role.SOLICITANTE, null, null, NOW)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_app_user_cost_center");
	}

	@Test
	void emailIsUniqueIgnoringCase() {
		users.saveAndFlush(new UserEntity("Ana", "ana.unique@example.test", "hash", Role.SOLICITANTE, null, costCenter.getId(), NOW));

		assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user (name, email, password_hash, role, cost_center_id) VALUES ('Ana 2', 'ANA.Unique@example.test', 'hash', 'SOLICITANTE', ?)", costCenter.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_app_user_email");
	}

	@Test
	void idempotencyKeyIsUniquePerUser() {
		idempotencyKeys.saveAndFlush(new IdempotencyKeyEntity(owner.getId(), "key-1", "a".repeat(64), 201, "{}", NOW));

		assertThatThrownBy(() -> idempotencyKeys.saveAndFlush(new IdempotencyKeyEntity(owner.getId(), "key-1", "b".repeat(64), 201, "{}", NOW)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_idempotency_key_user_key");
	}

}

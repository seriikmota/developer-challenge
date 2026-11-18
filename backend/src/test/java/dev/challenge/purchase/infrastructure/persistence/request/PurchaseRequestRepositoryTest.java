package dev.challenge.purchase.infrastructure.persistence.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import dev.challenge.purchase.domain.approval.ApprovalChainFactory;
import dev.challenge.purchase.domain.approval.StepApproval;
import dev.challenge.purchase.domain.approval.StepLevel;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.PersistenceIntegrationTest;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;

class PurchaseRequestRepositoryTest extends PersistenceIntegrationTest {

	private final ApprovalChainFactory chainFactory = new ApprovalChainFactory();

	private UserEntity owner;

	private UserEntity approverL1;

	@BeforeEach
	void setUp() {
		var costCenter = newCostCenter();
		owner = newUser(Role.SOLICITANTE, null, costCenter);
		approverL1 = newUser(Role.APROVADOR, ApproverLevel.NIVEL_1, costCenter);
	}

	private PurchaseRequestEntity reload(PurchaseRequestEntity request) {
		flushAndClear();
		return requests.findById(request.getId()).orElseThrow();
	}

	private void submit(PurchaseRequestEntity request, Instant at) {
		var version = request.currentVersion();
		version.submit(at, chainFactory.build(version.getTotal(), version.getCategory()));
		request.changeStatus(RequestStatus.PENDING_APPROVAL, at);
	}

	@Test
	void persistsDraftWithItemsAndServerComputedTotal() {
		var draft = newDraft(owner, List.of(item("Notebook 16GB/512GB", 5, "4899.90"), item("Mouse", 2, "10.25")));
		var loaded = reload(draft);

		var version = loaded.currentVersion();
		assertThat(loaded.getStatus()).isEqualTo(RequestStatus.DRAFT);
		assertThat(loaded.getCurrentVersion()).isEqualTo(1);
		assertThat(loaded.getLockVersion()).isZero();
		assertThat(version.getTotal()).isEqualTo(Money.of("24520.00"));
		assertThat(version.getItems()).extracting(RequestItemEntity::getPosition, RequestItemEntity::getLineTotal)
				.containsExactly(tuple(1, Money.of("24499.50")), tuple(2, Money.of("20.50")));
	}

	@Test
	void replacesItemsOfDraft() {
		var draft = newDraft(owner, List.of(item("A", 1, "10.00"), item("B", 2, "20.00")));
		flushAndClear();

		var loaded = requests.findById(draft.getId()).orElseThrow();
		loaded.currentVersion().edit("Novo título", null, Category.SUPPLIES, null, List.of(item("C", 3, "1.50"), item("D", 1, "2.00")));
		checkDeferredConstraints();

		var version = reload(loaded).currentVersion();
		assertThat(version.getTitle()).isEqualTo("Novo título");
		assertThat(version.getItems()).extracting(RequestItemEntity::getDescription).containsExactly("C", "D");
		assertThat(version.getTotal()).isEqualTo(Money.of("6.50"));
	}

	@Test
	void submitFreezesVersionWithChainAndCompetenceMonth() {
		var draft = newDraft(owner, List.of(item("Licença", 1, "500.00")));
		draft.currentVersion().edit("Licença", null, Category.SOFTWARE, null, List.of(item("Licença", 1, "500.00")));
		// edit and submit are separate requests; in one flush Hibernate would delete the old items after freezing the version
		entityManager.flush();
		// 02:59 UTC on Dec 1st is still November in America/Sao_Paulo
		submit(draft, Instant.parse("2026-12-01T02:59:00Z"));

		var version = reload(draft).currentVersion();
		assertThat(version.isSubmitted()).isTrue();
		assertThat(version.getCompetenceMonth()).isEqualTo(CompetenceMonth.parse("2026-11"));
		assertThat(version.getSteps()).extracting(ApprovalStepEntity::getStepOrder, ApprovalStepEntity::getLevel)
				.containsExactly(tuple(1, StepLevel.NIVEL_1), tuple(2, StepLevel.ADMIN));
		assertThat(version.currentStep()).get().extracting(ApprovalStepEntity::getLevel).isEqualTo(StepLevel.NIVEL_1);
	}

	@Test
	void mapsApprovalContextOfCurrentStep() {
		var draft = newDraft(owner, List.of(item("Servidor", 1, "5000.00")));
		submit(draft, NOW);
		draft.currentVersion().currentStep().orElseThrow().decide(StepStatus.APPROVED, approverL1.getId(), null, NOW);

		var context = ApprovalContextMapper.toContext(reload(draft)).orElseThrow();

		assertThat(context.ownerId()).isEqualTo(owner.getId());
		assertThat(context.costCenterId()).isEqualTo(owner.getCostCenterId());
		assertThat(context.currentStep()).isEqualTo(StepLevel.NIVEL_2);
		assertThat(context.approvedSteps()).containsExactly(new StepApproval(StepLevel.NIVEL_1, approverL1.getId()));
	}

	@Test
	void requestNotPendingHasNoApprovalContext() {
		var draft = newDraft(owner, List.of(item("Cadeira", 1, "300.00")));

		assertThat(ApprovalContextMapper.toContext(reload(draft))).isEmpty();
	}

	@Test
	void editAfterRequestForChangesOpensNewVersionAndKeepsPrevious() {
		var draft = newDraft(owner, List.of(item("Cadeira", 2, "300.00")));
		submit(draft, NOW);
		draft.currentVersion().currentStep().orElseThrow().decide(StepStatus.CHANGES_REQUESTED, approverL1.getId(), "Detalhe o modelo da cadeira.", NOW);
		draft.changeStatus(RequestStatus.CHANGES_REQUESTED, NOW);
		var loaded = reload(draft);

		var v2 = loaded.editableVersion(NOW);
		v2.edit("Cadeiras ergonômicas", null, Category.EQUIPMENT, null, List.of(item("Cadeira ergonômica", 2, "450.00")));
		var reloaded = reload(loaded);

		assertThat(reloaded.getCurrentVersion()).isEqualTo(2);
		assertThat(reloaded.getVersions()).extracting(RequestVersionEntity::getNumber).containsExactly(1, 2);
		assertThat(reloaded.version(1).getTotal()).isEqualTo(Money.of("600.00"));
		assertThat(reloaded.version(1).isSubmitted()).isTrue();
		assertThat(reloaded.version(2).getTotal()).isEqualTo(Money.of("900.00"));
		assertThat(reloaded.version(2).isSubmitted()).isFalse();
		assertThat(reloaded.version(2).getSteps()).isEmpty();
	}

	@Test
	void editingUnsubmittedVersionReusesIt() {
		var draft = newDraft(owner, List.of(item("Cadeira", 1, "300.00")));

		assertThat(draft.editableVersion(NOW)).isSameAs(draft.currentVersion());
	}

	@Test
	void submittedVersionCannotBeEditedInMemory() {
		var draft = newDraft(owner, List.of(item("Cadeira", 1, "300.00")));
		submit(draft, NOW);

		assertThatThrownBy(() -> draft.currentVersion().edit("x", null, Category.OTHER, null, List.of(item("x", 1, "1.00"))))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void lockVersionIncreasesOnEveryWrite() {
		var draft = newDraft(owner, List.of(item("Cadeira", 1, "300.00")));
		var loaded = reload(draft);

		loaded.changeStatus(RequestStatus.CANCELLED, NOW.plusSeconds(1));

		assertThat(reload(loaded).getLockVersion()).isEqualTo(1);
	}

	@Test
	void concurrentWriteFailsWithOptimisticLockConflict() {
		var draft = newDraft(owner, List.of(item("Cadeira", 1, "300.00")));
		var loaded = reload(draft);
		// another transaction changed the row after we read it
		jdbc.update("UPDATE purchase_request SET lock_version = lock_version + 1 WHERE id = ?", loaded.getId());

		loaded.changeStatus(RequestStatus.CANCELLED, NOW.plusSeconds(1));

		assertThatThrownBy(() -> requests.saveAndFlush(loaded)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

}

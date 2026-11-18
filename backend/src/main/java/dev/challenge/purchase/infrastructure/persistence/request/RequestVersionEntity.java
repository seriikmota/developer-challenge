package dev.challenge.purchase.infrastructure.persistence.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.challenge.purchase.domain.approval.StepApproval;
import dev.challenge.purchase.domain.approval.StepLevel;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.LineItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Content of a request at one version (v1, v2...), with its items and its approval chain.
 * Editable while not submitted; immutable after submission (also enforced by a database trigger).
 */
@Entity
@Table(name = "request_version")
public class RequestVersionEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false, updatable = false)
	private PurchaseRequestEntity request;

	@Column(nullable = false, updatable = false)
	private int number;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Category category;

	private LocalDate neededBy;

	@Column(nullable = false, precision = 19, scale = 2)
	private Money total;

	private Instant submittedAt;

	private CompetenceMonth competenceMonth;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<RequestItemEntity> items = new ArrayList<>();

	@OneToMany(mappedBy = "version", cascade = CascadeType.ALL)
	@OrderBy("stepOrder ASC")
	private List<ApprovalStepEntity> steps = new ArrayList<>();

	protected RequestVersionEntity() {
	}

	RequestVersionEntity(PurchaseRequestEntity request, int number, String title, String description, Category category, LocalDate neededBy, List<LineItem> items, Instant now) {
		this.request = request;
		this.number = number;
		this.createdAt = now;
		edit(title, description, category, neededBy, items);
	}

	/**
	 * Replaces the content of an unsubmitted version and recomputes the total from the items.
	 */
	public void edit(String title, String description, Category category, LocalDate neededBy, List<LineItem> items) {
		requireNotSubmitted();
		this.title = title;
		this.description = description;
		this.category = category;
		this.neededBy = neededBy;
		this.items.clear();
		for (int i = 0; i < items.size(); i++) {
			this.items.add(new RequestItemEntity(this, i + 1, items.get(i)));
		}
		this.total = LineItem.total(items);
	}

	/**
	 * Freezes the version: records the submission instant, its competence month and the approval chain.
	 */
	public void submit(Instant submittedAt, List<StepLevel> chain) {
		requireNotSubmitted();
		if (chain.isEmpty()) {
			throw new IllegalArgumentException("approval chain must have at least one step");
		}
		this.submittedAt = submittedAt;
		this.competenceMonth = CompetenceMonth.of(submittedAt);
		for (int i = 0; i < chain.size(); i++) {
			this.steps.add(new ApprovalStepEntity(this, i + 1, chain.get(i)));
		}
	}

	public boolean isSubmitted() {
		return submittedAt != null;
	}

	/**
	 * The step awaiting a decision: the first pending one, as long as no step closed the version
	 * (rejection or request for changes).
	 */
	public Optional<ApprovalStepEntity> currentStep() {
		var closed = steps.stream().anyMatch(step -> step.getStatus() == StepStatus.REJECTED || step.getStatus() == StepStatus.CHANGES_REQUESTED);
		if (closed) {
			return Optional.empty();
		}
		return steps.stream().filter(step -> step.getStatus() == StepStatus.PENDING).findFirst();
	}

	public boolean isLastStep(ApprovalStepEntity step) {
		return !steps.isEmpty() && steps.getLast() == step;
	}

	public List<StepApproval> approvals() {
		return steps.stream()
				.filter(step -> step.getStatus() == StepStatus.APPROVED)
				.map(step -> new StepApproval(step.getLevel(), step.getDecidedBy()))
				.toList();
	}

	public List<LineItem> lineItems() {
		return items.stream().map(RequestItemEntity::toLineItem).toList();
	}

	private void requireNotSubmitted() {
		if (isSubmitted()) {
			throw new IllegalStateException("version " + number + " of request " + request.getId() + " is submitted and immutable");
		}
	}

	public Long getId() {
		return id;
	}

	public PurchaseRequestEntity getRequest() {
		return request;
	}

	public int getNumber() {
		return number;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public LocalDate getNeededBy() {
		return neededBy;
	}

	public Money getTotal() {
		return total;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public CompetenceMonth getCompetenceMonth() {
		return competenceMonth;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<RequestItemEntity> getItems() {
		return Collections.unmodifiableList(items);
	}

	public List<ApprovalStepEntity> getSteps() {
		return Collections.unmodifiableList(steps);
	}

}

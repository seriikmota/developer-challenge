package dev.challenge.purchase.infrastructure.persistence.request;

import java.time.Instant;
import java.util.Objects;

import dev.challenge.purchase.domain.approval.StepLevel;
import dev.challenge.purchase.domain.approval.StepStatus;
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
import jakarta.persistence.Table;

/**
 * One step of the approval chain of a version. The database refuses a second decision by the same user on the
 * same version ({@code UNIQUE (version_id, decided_by)}).
 */
@Entity
@Table(name = "approval_step")
public class ApprovalStepEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "version_id", nullable = false, updatable = false)
	private RequestVersionEntity version;

	@Column(nullable = false, updatable = false)
	private int stepOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private StepLevel level;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StepStatus status;

	private Long decidedBy;

	private Instant decidedAt;

	@Column(length = 2000)
	private String comment;

	protected ApprovalStepEntity() {
	}

	ApprovalStepEntity(RequestVersionEntity version, int stepOrder, StepLevel level) {
		this.version = version;
		this.stepOrder = stepOrder;
		this.level = level;
		this.status = StepStatus.PENDING;
	}

	public void decide(StepStatus outcome, Long decidedBy, String comment, Instant decidedAt) {
		if (status != StepStatus.PENDING) {
			throw new IllegalStateException("step " + stepOrder + " is already " + status);
		}
		if (outcome == StepStatus.PENDING) {
			throw new IllegalArgumentException("a decision cannot be PENDING");
		}
		this.status = Objects.requireNonNull(outcome, "outcome");
		this.decidedBy = Objects.requireNonNull(decidedBy, "decidedBy");
		this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
		this.comment = comment;
	}

	public Long getId() {
		return id;
	}

	public RequestVersionEntity getVersion() {
		return version;
	}

	public int getStepOrder() {
		return stepOrder;
	}

	public StepLevel getLevel() {
		return level;
	}

	public StepStatus getStatus() {
		return status;
	}

	public Long getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getComment() {
		return comment;
	}

}

package dev.challenge.purchase.infrastructure.persistence.history;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One entry of the append-only history of a request. Insert-only: Hibernate never updates it ({@link Immutable})
 * and a database trigger rejects UPDATE, DELETE and TRUNCATE.
 */
@Entity
@Immutable
@Table(name = "request_event")
public class RequestEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long requestId;

	@Column(nullable = false)
	private int versionNumber;

	private Integer stepOrder;

	@Column(nullable = false)
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role actorRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestEventType action;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private RequestStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus toStatus;

	@Column(length = 2000)
	private String comment;

	@Column(length = 100)
	private String requestIdHeader;

	@Column(nullable = false)
	private Instant occurredAt;

	protected RequestEventEntity() {
	}

	public RequestEventEntity(Long requestId, int versionNumber, Integer stepOrder, Long actorId, Role actorRole, RequestEventType action, RequestStatus fromStatus, RequestStatus toStatus, String comment, String requestIdHeader, Instant occurredAt) {
		this.requestId = requestId;
		this.versionNumber = versionNumber;
		this.stepOrder = stepOrder;
		this.actorId = actorId;
		this.actorRole = actorRole;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.comment = comment;
		this.requestIdHeader = requestIdHeader;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public Long getRequestId() {
		return requestId;
	}

	public int getVersionNumber() {
		return versionNumber;
	}

	public Integer getStepOrder() {
		return stepOrder;
	}

	public Long getActorId() {
		return actorId;
	}

	public Role getActorRole() {
		return actorRole;
	}

	public RequestEventType getAction() {
		return action;
	}

	public RequestStatus getFromStatus() {
		return fromStatus;
	}

	public RequestStatus getToStatus() {
		return toStatus;
	}

	public String getComment() {
		return comment;
	}

	public String getRequestIdHeader() {
		return requestIdHeader;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}

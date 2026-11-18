package dev.challenge.purchase.infrastructure.persistence.idempotency;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Response stored for an {@code Idempotency-Key} of a user, replayed when the same key arrives with the same payload.
 */
@Entity
@Immutable
@Table(name = "idempotency_key")
public class IdempotencyKeyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 100)
	private String idemKey;

	/** SHA-256 of the canonical payload, in hex. */
	@Column(nullable = false, length = 64)
	private String requestHash;

	@Column(nullable = false)
	private int responseStatus;

	@Column(nullable = false, columnDefinition = "text")
	private String responseBody;

	@Column(nullable = false)
	private Instant createdAt;

	protected IdempotencyKeyEntity() {
	}

	public IdempotencyKeyEntity(Long userId, String idemKey, String requestHash, int responseStatus, String responseBody, Instant createdAt) {
		this.userId = userId;
		this.idemKey = idemKey;
		this.requestHash = requestHash;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.createdAt = createdAt;
	}

	public boolean matches(String requestHash) {
		return this.requestHash.equals(requestHash);
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getIdemKey() {
		return idemKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public int getResponseStatus() {
		return responseStatus;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

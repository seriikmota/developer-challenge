package dev.challenge.purchase.infrastructure.persistence.user;

import java.time.Instant;
import java.util.Locale;

import dev.challenge.purchase.domain.user.ApproverLevel;
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
 * Application user. Other aggregates reference it by id only.
 */
@Entity
@Table(name = "app_user")
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private ApproverLevel approverLevel;

	private Long costCenterId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserEntity() {
	}

	public UserEntity(String name, String email, String passwordHash, Role role, ApproverLevel approverLevel, Long costCenterId, Instant now) {
		this.name = name;
		this.email = email.toLowerCase(Locale.ROOT);
		this.passwordHash = passwordHash;
		this.role = role;
		this.approverLevel = approverLevel;
		this.costCenterId = costCenterId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String name, Role role, ApproverLevel approverLevel, Long costCenterId, Instant now) {
		this.name = name;
		this.role = role;
		this.approverLevel = approverLevel;
		this.costCenterId = costCenterId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public ApproverLevel getApproverLevel() {
		return approverLevel;
	}

	public Long getCostCenterId() {
		return costCenterId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

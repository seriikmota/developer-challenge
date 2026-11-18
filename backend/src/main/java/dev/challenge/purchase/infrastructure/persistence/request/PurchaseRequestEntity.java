package dev.challenge.purchase.infrastructure.persistence.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.LineItem;
import dev.challenge.purchase.domain.request.RequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate root of a purchase request: its status and its versions (each with items and approval steps).
 * <p>
 * {@code currentVersion} is the number of the version being edited or under approval. A new version is opened only
 * when the owner changes a request that was sent back for changes, so submitted versions stay immutable.
 * {@code lockVersion} changes on every write and backs optimistic locking.
 * <p>
 * This class keeps the aggregate consistent; the business rules (transitions, chain, eligibility) live in the domain.
 */
@Entity
@Table(name = "purchase_request")
public class PurchaseRequestEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false)
	private Long costCenterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus status;

	@Column(nullable = false)
	private int currentVersion;

	@Version
	@Column(nullable = false)
	private long lockVersion;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL)
	@OrderBy("number ASC")
	private List<RequestVersionEntity> versions = new ArrayList<>();

	protected PurchaseRequestEntity() {
	}

	/**
	 * Creates a request in DRAFT with its first version.
	 */
	public PurchaseRequestEntity(Long ownerId, Long costCenterId, String title, String description, Category category, LocalDate neededBy, List<LineItem> items, Instant now) {
		this.ownerId = ownerId;
		this.costCenterId = costCenterId;
		this.status = RequestStatus.DRAFT;
		this.currentVersion = 1;
		this.createdAt = now;
		this.updatedAt = now;
		this.versions.add(new RequestVersionEntity(this, 1, title, description, category, neededBy, items, now));
	}

	public RequestVersionEntity currentVersion() {
		return version(currentVersion);
	}

	public RequestVersionEntity version(int number) {
		return versions.stream()
				.filter(version -> version.getNumber() == number)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("request " + id + " has no version " + number));
	}

	/**
	 * Returns the version the owner may edit: the current one while it is not submitted, otherwise a new version
	 * copied from it (after a request for changes).
	 */
	public RequestVersionEntity editableVersion(Instant now) {
		var current = currentVersion();
		if (!current.isSubmitted()) {
			return current;
		}
		var next = new RequestVersionEntity(this, current.getNumber() + 1, current.getTitle(), current.getDescription(), current.getCategory(), current.getNeededBy(), current.lineItems(), now);
		versions.add(next);
		currentVersion = next.getNumber();
		return next;
	}

	public void changeCostCenter(Long costCenterId) {
		this.costCenterId = costCenterId;
	}

	public void changeStatus(RequestStatus status, Instant now) {
		this.status = status;
		touch(now);
	}

	/**
	 * Records the time of the last change. When only child rows change (e.g. an intermediate approval), the use case
	 * must also force the {@code lockVersion} increment ({@code LockModeType.OPTIMISTIC_FORCE_INCREMENT}).
	 */
	public void touch(Instant now) {
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public Long getCostCenterId() {
		return costCenterId;
	}

	public RequestStatus getStatus() {
		return status;
	}

	public int getCurrentVersion() {
		return currentVersion;
	}

	public long getLockVersion() {
		return lockVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<RequestVersionEntity> getVersions() {
		return Collections.unmodifiableList(versions);
	}

}

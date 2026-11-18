package dev.challenge.purchase.application.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import dev.challenge.purchase.domain.approval.StepLevel;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Role;

/**
 * Read models of a purchase request returned by the API. Money values are strings with 2 decimals
 * (e.g. {@code "24499.50"}), instants are UTC ISO-8601.
 */
public final class RequestViews {

	private RequestViews() {
	}

	/**
	 * Row of the request list.
	 */
	public record Summary(Long id, String title, Category category, RequestStatus status, String total, Long costCenterId, Long ownerId, String ownerName, int currentVersion, Instant createdAt, Instant updatedAt) {
	}

	/**
	 * A request with its current version, its approval chain, what the logged user may do now and the
	 * {@code lockVersion} to send back on the next change.
	 */
	public record Detail(Long id, RequestStatus status, Long ownerId, String ownerName, Long costCenterId, int currentVersion, long lockVersion, Instant createdAt, Instant updatedAt, Version version, List<Integer> versions, Set<Action> allowedActions) {
	}

	/**
	 * Content of one version (v1, v2...). Versions already submitted never change.
	 */
	public record Version(int number, String title, String description, Category category, LocalDate neededBy, String total, Instant submittedAt, String competenceMonth, List<Item> items, List<Step> steps) {
	}

	public record VersionSummary(int number, String title, String total, Instant submittedAt, String competenceMonth, String outcome) {
	}

	public record Item(int position, String description, int quantity, String unitPrice, String lineTotal) {
	}

	/**
	 * @param current whether this is the step awaiting a decision now
	 */
	public record Step(int order, StepLevel level, StepStatus status, boolean current, Long decidedBy, String decidedByName, Instant decidedAt, String comment) {
	}

	public record Event(Long id, int versionNumber, Integer stepOrder, Long actorId, String actorName, Role actorRole, RequestEventType action, RequestStatus fromStatus, RequestStatus toStatus, String comment, String requestId, Instant occurredAt) {
	}

}

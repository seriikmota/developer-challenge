package dev.challenge.purchase.application.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.challenge.purchase.application.common.UserDirectory;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.request.RequestActionPolicy;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventEntity;
import dev.challenge.purchase.infrastructure.persistence.request.ApprovalStepEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.RequestItemEntity;
import dev.challenge.purchase.infrastructure.persistence.request.RequestSnapshotMapper;
import dev.challenge.purchase.infrastructure.persistence.request.RequestVersionEntity;

/**
 * Turns persisted requests into {@link RequestViews}. {@code allowedActions} comes from the same policy that
 * validates the actions.
 */
@Component
public class RequestViewMapper {

	private final RequestActionPolicy actionPolicy;

	private final UserDirectory userDirectory;

	public RequestViewMapper(RequestActionPolicy actionPolicy, UserDirectory userDirectory) {
		this.actionPolicy = actionPolicy;
		this.userDirectory = userDirectory;
	}

	public RequestViews.Detail detail(PurchaseRequestEntity request, Actor actor) {
		var version = request.currentVersion();
		var userIds = new ArrayList<Long>();
		userIds.add(request.getOwnerId());
		version.getSteps().forEach(step -> userIds.add(step.getDecidedBy()));
		var names = userDirectory.namesOf(userIds);
		var allowed = actionPolicy.allowedActions(actor, RequestSnapshotMapper.toSnapshot(request));
		var versionNumbers = request.getVersions().stream().map(RequestVersionEntity::getNumber).toList();
		return new RequestViews.Detail(request.getId(), request.getStatus(), request.getOwnerId(), names.get(request.getOwnerId()), request.getCostCenterId(), request.getCurrentVersion(), request.getLockVersion(), request.getCreatedAt(), request.getUpdatedAt(), version(version, names), versionNumbers, allowed);
	}

	public RequestViews.Version version(RequestVersionEntity version) {
		return version(version, userDirectory.namesOf(version.getSteps().stream().map(ApprovalStepEntity::getDecidedBy).toList()));
	}

	public List<RequestViews.Summary> summaries(List<PurchaseRequestEntity> requests) {
		var names = userDirectory.namesOf(requests.stream().map(PurchaseRequestEntity::getOwnerId).toList());
		return requests.stream().map(request -> summary(request, names)).toList();
	}

	public RequestViews.VersionSummary versionSummary(RequestVersionEntity version) {
		return new RequestViews.VersionSummary(version.getNumber(), version.getTitle(), version.getTotal().toString(), version.getSubmittedAt(), monthOf(version), outcome(version));
	}

	public List<RequestViews.Event> events(List<RequestEventEntity> events) {
		var names = userDirectory.namesOf(events.stream().map(RequestEventEntity::getActorId).toList());
		return events.stream().map(event -> new RequestViews.Event(event.getId(), event.getVersionNumber(), event.getStepOrder(), event.getActorId(), names.get(event.getActorId()), event.getActorRole(), event.getAction(), event.getFromStatus(), event.getToStatus(), event.getComment(), event.getRequestIdHeader(), event.getOccurredAt())).toList();
	}

	private static RequestViews.Summary summary(PurchaseRequestEntity request, Map<Long, String> names) {
		var version = request.currentVersion();
		return new RequestViews.Summary(request.getId(), version.getTitle(), version.getCategory(), request.getStatus(), version.getTotal().toString(), request.getCostCenterId(), request.getOwnerId(), names.get(request.getOwnerId()), request.getCurrentVersion(), request.getCreatedAt(), request.getUpdatedAt());
	}

	private static RequestViews.Version version(RequestVersionEntity version, Map<Long, String> names) {
		var current = version.currentStep().orElse(null);
		var items = version.getItems().stream().map(RequestViewMapper::item).toList();
		var steps = version.getSteps().stream().map(step -> new RequestViews.Step(step.getStepOrder(), step.getLevel(), step.getStatus(), step == current, step.getDecidedBy(), names.get(step.getDecidedBy()), step.getDecidedAt(), step.getComment())).toList();
		return new RequestViews.Version(version.getNumber(), version.getTitle(), version.getDescription(), version.getCategory(), version.getNeededBy(), version.getTotal().toString(), version.getSubmittedAt(), monthOf(version), items, steps);
	}

	private static RequestViews.Item item(RequestItemEntity item) {
		return new RequestViews.Item(item.getPosition(), item.getDescription(), item.getQuantity(), item.getUnitPrice().toString(), item.getLineTotal().toString());
	}

	private static String monthOf(RequestVersionEntity version) {
		return version.getCompetenceMonth() == null ? null : version.getCompetenceMonth().toString();
	}

	/**
	 * How the approval of a version ended: DRAFT (not submitted), PENDING, APPROVED, REJECTED or CHANGES_REQUESTED.
	 * A cancelled request keeps the outcome its version had when it was cancelled.
	 */
	private static String outcome(RequestVersionEntity version) {
		if (!version.isSubmitted()) {
			return "DRAFT";
		}
		var statuses = version.getSteps().stream().map(ApprovalStepEntity::getStatus).toList();
		return Stream.of(StepStatus.REJECTED, StepStatus.CHANGES_REQUESTED)
				.filter(statuses::contains)
				.findFirst()
				.map(Enum::name)
				.orElse(statuses.stream().allMatch(status -> status == StepStatus.APPROVED) ? "APPROVED" : "PENDING");
	}

}

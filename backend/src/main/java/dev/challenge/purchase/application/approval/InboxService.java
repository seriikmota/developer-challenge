package dev.challenge.purchase.application.approval;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.request.RequestViewMapper;
import dev.challenge.purchase.application.request.RequestViews;
import dev.challenge.purchase.application.request.RequestVisibility;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.request.RequestActionPolicy;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import dev.challenge.purchase.infrastructure.persistence.request.RequestSnapshotMapper;

/**
 * Requests whose current step the logged user can approve right now. The database narrows the candidates (pending
 * and visible: same cost center, or all for ADMIN); the final filter is the same policy that validates the approval,
 * so the inbox never lists a request the user would be refused on.
 */
@Service
public class InboxService {

	private final PurchaseRequestRepository requests;

	private final RequestActionPolicy actionPolicy;

	private final RequestViewMapper views;

	public InboxService(PurchaseRequestRepository requests, RequestActionPolicy actionPolicy, RequestViewMapper views) {
		this.requests = requests;
		this.actionPolicy = actionPolicy;
		this.views = views;
	}

	@Transactional(readOnly = true)
	public PageView<RequestViews.Summary> inbox(Actor actor, int page, int size) {
		if (!actor.role().canDecideApprovals()) {
			return PageView.of(List.<RequestViews.Summary>of(), page, size);
		}
		Specification<PurchaseRequestEntity> pending = (root, query, cb) -> cb.equal(root.get("status"), RequestStatus.PENDING_APPROVAL);
		var actionable = requests.findAll(RequestVisibility.visibleTo(actor).and(pending), Sort.by("updatedAt", "id")).stream()
				.filter(request -> actionPolicy.evaluate(actor, Action.APPROVE, RequestSnapshotMapper.toSnapshot(request)).allowed())
				.toList();
		return PageView.of(views.summaries(actionable), page, size);
	}

}

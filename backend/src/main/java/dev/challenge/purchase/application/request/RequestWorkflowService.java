package dev.challenge.purchase.application.request;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.domain.approval.ApprovalChainFactory;
import dev.challenge.purchase.domain.approval.DecisionCommentRule;
import dev.challenge.purchase.domain.approval.StepStatus;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.request.RequestActionPolicy;
import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.request.RequestStateMachine;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetEntity;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetRepository;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import dev.challenge.purchase.infrastructure.persistence.request.RequestSnapshotMapper;
import dev.challenge.purchase.infrastructure.persistence.request.RequestVersionEntity;

/**
 * Status transitions of a request: submit, approve, reject, request changes and cancel. Each one runs in a single
 * transaction: the change, the budget consumption (last approval) and the history event commit or roll back together.
 */
@Service
public class RequestWorkflowService {

	private final PurchaseRequestRepository requests;

	private final MonthlyBudgetRepository budgets;

	private final CostCenterRepository costCenters;

	private final RequestGate gate;

	private final RequestActionPolicy actionPolicy;

	private final RequestStateMachine stateMachine;

	private final ApprovalChainFactory chainFactory;

	private final HistoryRecorder history;

	private final RequestViewMapper views;

	private final Clock clock;

	public RequestWorkflowService(PurchaseRequestRepository requests, MonthlyBudgetRepository budgets, CostCenterRepository costCenters, RequestGate gate, RequestActionPolicy actionPolicy, RequestStateMachine stateMachine, ApprovalChainFactory chainFactory, HistoryRecorder history, RequestViewMapper views, Clock clock) {
		this.requests = requests;
		this.budgets = budgets;
		this.costCenters = costCenters;
		this.gate = gate;
		this.actionPolicy = actionPolicy;
		this.stateMachine = stateMachine;
		this.chainFactory = chainFactory;
		this.history = history;
		this.views = views;
		this.clock = clock;
	}

	/**
	 * Applies a transition. Checks, in order: visibility (404), role (403), {@code lockVersion} (409), then the
	 * business rules (422): status, ownership or approver eligibility, comment and, on the last approval, budget.
	 *
	 * @param action SUBMIT, APPROVE, REJECT, REQUEST_CHANGES or CANCEL
	 */
	@Transactional
	public RequestViews.Detail transition(Actor actor, Long id, Action action, long lockVersion, String comment) {
		if (action == Action.EDIT) {
			throw new IllegalArgumentException("EDIT is not a status transition");
		}
		var request = gate.writable(actor, id, action, lockVersion);
		actionPolicy.evaluate(actor, action, RequestSnapshotMapper.toSnapshot(request)).orThrow();
		var normalizedComment = comment == null || comment.isBlank() ? null : comment.strip();
		DecisionCommentRule.validate(action, normalizedComment);
		var now = clock.instant();
		var from = request.getStatus();
		Integer stepOrder = switch (action) {
			case SUBMIT -> submit(request, now);
			case APPROVE -> approve(request, actor, normalizedComment, now);
			case REJECT -> decide(request, actor, StepStatus.REJECTED, normalizedComment, now);
			case REQUEST_CHANGES -> decide(request, actor, StepStatus.CHANGES_REQUESTED, normalizedComment, now);
			case CANCEL -> {
				request.changeStatus(stateMachine.apply(from, Action.CANCEL, false), now);
				yield null;
			}
			case EDIT -> throw new IllegalStateException();
		};
		history.record(request, actor, RequestEventType.of(action), from, stepOrder, normalizedComment, now);
		requests.flush();
		return views.detail(request, actor);
	}

	/**
	 * Freezes the version being edited (or a copy of the submitted one, when resubmitting without changes) and builds
	 * its approval chain from scratch.
	 */
	private Integer submit(PurchaseRequestEntity request, Instant now) {
		var version = request.editableVersion(now);
		// a new version and its items must exist before the version is frozen (the database refuses items of a submitted version)
		requests.flush();
		version.submit(now, chainFactory.build(version.getTotal(), version.getCategory()));
		request.changeStatus(stateMachine.apply(request.getStatus(), Action.SUBMIT, false), now);
		return null;
	}

	private Integer approve(PurchaseRequestEntity request, Actor actor, String comment, Instant now) {
		var version = request.currentVersion();
		var step = version.currentStep().orElseThrow();
		var isLast = version.isLastStep(step);
		step.decide(StepStatus.APPROVED, actor.userId(), comment, now);
		if (isLast) {
			consumeBudget(request, version, now);
		}
		request.changeStatus(stateMachine.apply(request.getStatus(), Action.APPROVE, isLast), now);
		return step.getStepOrder();
	}

	private Integer decide(PurchaseRequestEntity request, Actor actor, StepStatus outcome, String comment, Instant now) {
		var step = request.currentVersion().currentStep().orElseThrow();
		step.decide(outcome, actor.userId(), comment, now);
		var action = outcome == StepStatus.REJECTED ? Action.REJECT : Action.REQUEST_CHANGES;
		request.changeStatus(stateMachine.apply(request.getStatus(), action, false), now);
		return step.getStepOrder();
	}

	/**
	 * Consumes the total from the budget of the version's competence month with one conditional UPDATE. When the
	 * balance is not enough nothing changes and the whole approval rolls back (the request stays pending).
	 */
	private void consumeBudget(PurchaseRequestEntity request, RequestVersionEntity version, Instant now) {
		var month = version.getCompetenceMonth();
		if (budgets.tryConsume(request.getCostCenterId(), month, version.getTotal(), now)) {
			return;
		}
		var code = costCenters.findById(request.getCostCenterId()).map(CostCenterEntity::getCode).orElse(String.valueOf(request.getCostCenterId()));
		var available = budgets.findByCostCenterIdAndMonth(request.getCostCenterId(), month).map(MonthlyBudgetEntity::getAvailable).orElse(Money.ZERO);
		throw new DomainException(ErrorCode.BUDGET_EXCEEDED, "Não é possível aprovar: o centro de custo " + code + " tem " + available.format() + " disponíveis em " + month.format() + "; a solicitação exige " + version.getTotal().format() + ".");
	}

}

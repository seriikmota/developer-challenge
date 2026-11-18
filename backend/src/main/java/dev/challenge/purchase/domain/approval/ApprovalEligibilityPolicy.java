package dev.challenge.purchase.domain.approval;

import java.util.Objects;

import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;

/**
 * Decides whether an actor may decide (approve, reject or request changes on) the current approval step.
 *
 * <p>Rules, checked in this order:
 * <ol>
 * <li>Segregation of duties: nobody decides on their own request.</li>
 * <li>Segregation of duties: the same person does not approve two steps of the same version.</li>
 * <li>Eligibility by step level:
 * <ul>
 * <li>NIVEL_1: APROVADOR NIVEL_1 or NIVEL_2 of the request's cost center, or ADMIN</li>
 * <li>NIVEL_2: APROVADOR NIVEL_2 of the request's cost center, or ADMIN</li>
 * <li>ADMIN: ADMIN only</li>
 * </ul>
 * </li>
 * </ol>
 *
 * <p>Premise: reject and request changes require the same eligibility as approve
 * ("aprovador elegível da etapa atual", README 4.2).
 */
public final class ApprovalEligibilityPolicy {

	public Eligibility evaluate(Actor actor, Action action, ApprovalContext context) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(context, "context");
		if (!action.isApprovalDecision()) {
			throw new IllegalArgumentException("Not an approval decision: " + action);
		}

		var prefix = "Não é possível " + action.verb() + ": ";

		if (actor.userId().equals(context.ownerId())) {
			return Eligibility.denied(ErrorCode.SELF_APPROVAL_FORBIDDEN, prefix + "você é o solicitante desta solicitação (segregação de funções).");
		}

		var previous = context.approvedSteps().stream()
				.filter(step -> step.approverId().equals(actor.userId()))
				.findFirst();
		if (previous.isPresent()) {
			return Eligibility.denied(ErrorCode.DUPLICATE_APPROVER, prefix + "você já aprovou a etapa " + previous.get().level() + " desta versão (segregação de funções).");
		}

		return evaluateLevel(actor, context, prefix);
	}

	private Eligibility evaluateLevel(Actor actor, ApprovalContext context, String prefix) {
		var step = context.currentStep();
		if (actor.isAdmin()) {
			return Eligibility.granted();
		}
		if (actor.role() != Role.APROVADOR) {
			return Eligibility.denied(ErrorCode.APPROVER_NOT_ELIGIBLE, prefix + "o papel " + actor.role() + " não decide etapas de aprovação.");
		}
		if (step == StepLevel.ADMIN) {
			return Eligibility.denied(ErrorCode.APPROVER_NOT_ELIGIBLE, prefix + "a etapa ADMIN só pode ser decidida por um ADMIN.");
		}
		if (!context.costCenterId().equals(actor.costCenterId())) {
			return Eligibility.denied(ErrorCode.APPROVER_NOT_ELIGIBLE, prefix + "a solicitação pertence a outro centro de custo; você só decide etapas do seu centro de custo.");
		}
		if (actor.approverLevel() == null) {
			return Eligibility.denied(ErrorCode.APPROVER_NOT_ELIGIBLE, prefix + "seu usuário não tem nível de aprovador definido.");
		}
		if (step == StepLevel.NIVEL_2 && actor.approverLevel() != ApproverLevel.NIVEL_2) {
			return Eligibility.denied(ErrorCode.APPROVER_NOT_ELIGIBLE, prefix + "a etapa NIVEL_2 exige um aprovador NIVEL_2; seu nível é " + actor.approverLevel() + ".");
		}
		return Eligibility.granted();
	}

}

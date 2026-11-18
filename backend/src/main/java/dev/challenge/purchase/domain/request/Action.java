package dev.challenge.purchase.domain.request;

/**
 * Actions a user can take on a purchase request. Also the values exposed in {@code allowedActions}.
 */
public enum Action {
	EDIT("editar"),
	SUBMIT("submeter"),
	APPROVE("aprovar"),
	REJECT("rejeitar"),
	REQUEST_CHANGES("devolver para ajuste"),
	CANCEL("cancelar");

	private final String verb;

	Action(String verb) {
		this.verb = verb;
	}

	/**
	 * Portuguese infinitive used in user-facing messages, e.g. "Não é possível aprovar: ...".
	 */
	public String verb() {
		return verb;
	}

	/**
	 * Decisions taken by an approver on the current approval step.
	 */
	public boolean isApprovalDecision() {
		return this == APPROVE || this == REJECT || this == REQUEST_CHANGES;
	}
}

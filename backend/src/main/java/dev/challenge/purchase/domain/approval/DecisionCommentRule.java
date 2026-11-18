package dev.challenge.purchase.domain.approval;

import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;
import dev.challenge.purchase.domain.request.Action;

/**
 * Comment is optional when approving and mandatory (at least 10 characters, ignoring surrounding blanks)
 * when rejecting or requesting changes.
 */
public final class DecisionCommentRule {

	public static final int MIN_LENGTH = 10;

	private DecisionCommentRule() {
	}

	public static void validate(Action action, String comment) {
		if (action != Action.REJECT && action != Action.REQUEST_CHANGES) {
			return;
		}
		var length = comment == null ? 0 : comment.strip().length();
		if (length < MIN_LENGTH) {
			throw new DomainException(ErrorCode.COMMENT_REQUIRED, "Não é possível " + action.verb() + ": informe um comentário com pelo menos " + MIN_LENGTH + " caracteres explicando o motivo (recebido: " + length + ").");
		}
	}

}

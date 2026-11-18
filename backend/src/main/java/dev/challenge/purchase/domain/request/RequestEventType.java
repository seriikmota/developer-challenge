package dev.challenge.purchase.domain.request;

import java.util.Objects;

/**
 * What happened to a request, as recorded in its append-only history: every {@link Action} plus the creation.
 */
public enum RequestEventType {

	CREATE, EDIT, SUBMIT, APPROVE, REJECT, REQUEST_CHANGES, CANCEL;

	public static RequestEventType of(Action action) {
		return switch (Objects.requireNonNull(action, "action")) {
			case EDIT -> EDIT;
			case SUBMIT -> SUBMIT;
			case APPROVE -> APPROVE;
			case REJECT -> REJECT;
			case REQUEST_CHANGES -> REQUEST_CHANGES;
			case CANCEL -> CANCEL;
		};
	}

}

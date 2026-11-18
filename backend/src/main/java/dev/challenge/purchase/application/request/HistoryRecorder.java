package dev.challenge.purchase.application.request;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.challenge.purchase.application.common.Correlation;
import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventEntity;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;

/**
 * Appends one event to the history of a request, in the same transaction as the change it describes.
 */
@Component
public class HistoryRecorder {

	private final RequestEventRepository events;

	public HistoryRecorder(RequestEventRepository events) {
		this.events = events;
	}

	public void record(PurchaseRequestEntity request, Actor actor, RequestEventType action, RequestStatus from, Integer stepOrder, String comment, Instant at) {
		events.save(new RequestEventEntity(request.getId(), request.getCurrentVersion(), stepOrder, actor.userId(), actor.role(), action, from, request.getStatus(), comment, Correlation.currentId(), at));
	}

}

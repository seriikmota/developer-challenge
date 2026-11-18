package dev.challenge.purchase.infrastructure.persistence.request;

import dev.challenge.purchase.domain.request.RequestSnapshot;

/**
 * Builds the plain {@link RequestSnapshot} the action policy needs from the persisted request.
 */
public final class RequestSnapshotMapper {

	private RequestSnapshotMapper() {
	}

	public static RequestSnapshot toSnapshot(PurchaseRequestEntity request) {
		return new RequestSnapshot(request.getOwnerId(), request.getStatus(), ApprovalContextMapper.toContext(request).orElse(null));
	}

}

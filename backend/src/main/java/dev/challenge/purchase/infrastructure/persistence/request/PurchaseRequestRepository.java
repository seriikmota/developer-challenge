package dev.challenge.purchase.infrastructure.persistence.request;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequestEntity, Long>, JpaSpecificationExecutor<PurchaseRequestEntity> {

	/**
	 * Loads a request to change it, locking its row until the end of the transaction ({@code SELECT ... FOR UPDATE}).
	 * Concurrent writers of the same request run one after the other, so the second one sees the {@code lockVersion}
	 * written by the first and its stale value is refused with 409 (ADR 001).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<PurchaseRequestEntity> findForUpdateById(Long id);

}

package dev.challenge.purchase.infrastructure.persistence.idempotency;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

	Optional<IdempotencyKeyEntity> findByUserIdAndIdemKey(Long userId, String idemKey);

}

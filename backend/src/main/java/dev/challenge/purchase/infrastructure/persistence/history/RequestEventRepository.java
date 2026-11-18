package dev.challenge.purchase.infrastructure.persistence.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestEventRepository extends JpaRepository<RequestEventEntity, Long> {

	List<RequestEventEntity> findByRequestIdOrderByOccurredAtAscIdAsc(Long requestId);

}

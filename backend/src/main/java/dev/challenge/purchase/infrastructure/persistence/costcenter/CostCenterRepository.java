package dev.challenge.purchase.infrastructure.persistence.costcenter;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCenterRepository extends JpaRepository<CostCenterEntity, Long> {

	Optional<CostCenterEntity> findByCode(String code);

	boolean existsByCode(String code);

}

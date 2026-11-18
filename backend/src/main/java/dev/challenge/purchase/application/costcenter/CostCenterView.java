package dev.challenge.purchase.application.costcenter;

import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;

public record CostCenterView(Long id, String code, String name) {

	public static CostCenterView of(CostCenterEntity costCenter) {
		return new CostCenterView(costCenter.getId(), costCenter.getCode(), costCenter.getName());
	}

}

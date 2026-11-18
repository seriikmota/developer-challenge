package dev.challenge.purchase.application.request;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.RequestVersionEntity;
import jakarta.persistence.criteria.Predicate;

/**
 * Filters of the request list. Every field is optional. Dates are calendar days in America/Sao_Paulo, inclusive;
 * category, amount and text apply to the current version.
 *
 * @param q text searched in the title and description (case-insensitive)
 */
public record RequestFilter(RequestStatus status, Category category, Long costCenterId, LocalDate createdFrom, LocalDate createdTo, Money minAmount, Money maxAmount, String q) {

	public static RequestFilter none() {
		return new RequestFilter(null, null, null, null, null, null, null, null);
	}

	public Specification<PurchaseRequestEntity> toSpecification() {
		return (root, query, cb) -> {
			var predicates = new ArrayList<Predicate>();
			if (status != null) {
				predicates.add(cb.equal(root.get("status"), status));
			}
			if (costCenterId != null) {
				predicates.add(cb.equal(root.get("costCenterId"), costCenterId));
			}
			if (createdFrom != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom.atStartOfDay(CompetenceMonth.ZONE).toInstant()));
			}
			if (createdTo != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), createdTo.plusDays(1).atStartOfDay(CompetenceMonth.ZONE).toInstant()));
			}
			if (category != null || minAmount != null || maxAmount != null || q != null && !q.isBlank()) {
				var version = root.<PurchaseRequestEntity, RequestVersionEntity>join("versions");
				predicates.add(cb.equal(version.get("number"), root.get("currentVersion")));
				if (category != null) {
					predicates.add(cb.equal(version.get("category"), category));
				}
				if (minAmount != null) {
					predicates.add(cb.greaterThanOrEqualTo(version.get("total"), minAmount));
				}
				if (maxAmount != null) {
					predicates.add(cb.lessThanOrEqualTo(version.get("total"), maxAmount));
				}
				if (q != null && !q.isBlank()) {
					var pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
					predicates.add(cb.or(cb.like(cb.lower(version.get("title")), pattern, '\\'), cb.like(cb.lower(cb.coalesce(version.get("description"), "")), pattern, '\\')));
				}
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

}

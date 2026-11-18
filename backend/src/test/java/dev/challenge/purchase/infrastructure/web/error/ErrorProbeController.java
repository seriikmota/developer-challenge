package dev.challenge.purchase.infrastructure.web.error;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.application.error.ForbiddenException;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.error.DomainException;
import dev.challenge.purchase.domain.error.ErrorCode;

/**
 * Test-only endpoints that throw each kind of error, to check how {@link ProblemDetailAdvice} renders them.
 */
@RestController
class ErrorProbeController {

	@GetMapping("/test/errors/{kind}")
	String fail(@PathVariable String kind) {
		throw switch (kind) {
			case "domain" -> new DomainException(ErrorCode.BUDGET_EXCEEDED, "O centro de custo CC-TI-01 tem R$ 8.200,00 disponíveis em 11/2026; a solicitação exige R$ 24.499,50.");
			case "not-found" -> new NotFoundException("Solicitação 42 não encontrada.");
			case "forbidden" -> new ForbiddenException("Somente ADMIN gerencia centros de custo.");
			case "conflict" -> new ConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key já usada com outro payload.");
			case "optimistic-lock" -> new ObjectOptimisticLockingFailureException("PurchaseRequestEntity", 42L);
			case "access-denied" -> new AccessDeniedException("denied");
			default -> new IllegalStateException("boom: secret internal detail");
		};
	}

}

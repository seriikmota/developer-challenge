package dev.challenge.purchase.infrastructure.web.admin;

import java.math.BigDecimal;
import java.net.URI;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.costcenter.BudgetView;
import dev.challenge.purchase.application.costcenter.CostCenterService;
import dev.challenge.purchase.application.costcenter.CostCenterView;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/cost-centers")
@Tag(name = "Centros de custo e orçamentos")
public class CostCenterController {

	private final CostCenterService costCenters;

	public CostCenterController(CostCenterService costCenters) {
		this.costCenters = costCenters;
	}

	@GetMapping
	@Operation(summary = "Lista centros de custo")
	public List<CostCenterView> list() {
		return costCenters.list();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Cria centro de custo (ADMIN)")
	public ResponseEntity<CostCenterView> create(@Valid @RequestBody CreateCostCenterRequest body) {
		var costCenter = costCenters.create(body.code(), body.name());
		return ResponseEntity.created(URI.create("/cost-centers/" + costCenter.id())).body(costCenter);
	}

	@GetMapping("/{id}/budgets/{month}")
	@Operation(summary = "Orçamento do mês (yyyy-MM)", description = "Mês sem orçamento cadastrado devolve valores zerados.")
	public BudgetView budget(@PathVariable Long id, @PathVariable String month) {
		return costCenters.budget(id, parseMonth(month));
	}

	@PutMapping("/{id}/budgets/{month}")
	@PreAuthorize("hasRole('ADMIN')")
	@Operation(summary = "Define o orçamento do mês (ADMIN)", description = "Valor abaixo do já consumido: 422 BUDGET_BELOW_CONSUMED.")
	public BudgetView defineBudget(@PathVariable Long id, @PathVariable String month, @Valid @RequestBody BudgetRequest body) {
		return costCenters.defineBudget(id, parseMonth(month), Money.of(body.amount()));
	}

	private static CompetenceMonth parseMonth(String month) {
		try {
			return CompetenceMonth.parse(month);
		} catch (DateTimeParseException e) {
			throw InvalidInputException.of("month", "mês inválido: use o formato yyyy-MM, por exemplo 2026-11");
		}
	}

	public record CreateCostCenterRequest(
			@NotBlank(message = "informe o código") @Size(max = 30, message = "o código tem no máximo 30 caracteres") @Pattern(regexp = "[A-Za-z0-9-]+", message = "o código aceita letras, números e hífen") String code,
			@NotBlank(message = "informe o nome") @Size(max = 120, message = "o nome tem no máximo 120 caracteres") String name) {
	}

	/**
	 * @param amount accepts a JSON string or number, e.g. {@code "8200.00"}
	 */
	public record BudgetRequest(
			@NotNull(message = "informe o valor") @DecimalMin(value = "0.00", message = "o orçamento não pode ser negativo") @Digits(integer = 15, fraction = 2, message = "valor com no máximo 15 dígitos inteiros e 2 decimais") BigDecimal amount) {
	}

}

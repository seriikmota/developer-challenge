package dev.challenge.purchase.infrastructure.web.request;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.application.request.IdempotentRequestCreator;
import dev.challenge.purchase.application.request.RequestCommand;
import dev.challenge.purchase.application.request.RequestFilter;
import dev.challenge.purchase.application.request.RequestService;
import dev.challenge.purchase.application.request.RequestViews;
import dev.challenge.purchase.application.request.RequestWorkflowService;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.request.Category;
import dev.challenge.purchase.domain.request.LineItem;
import dev.challenge.purchase.domain.request.RequestStatus;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/requests")
@Tag(name = "Solicitações de compra")
public class RequestController {

	public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	public static final String REPLAYED = "Idempotent-Replayed";

	private final RequestService requests;

	private final RequestWorkflowService workflow;

	private final IdempotentRequestCreator creator;

	public RequestController(RequestService requests, RequestWorkflowService workflow, IdempotentRequestCreator creator) {
		this.requests = requests;
		this.workflow = workflow;
		this.creator = creator;
	}

	@GetMapping
	@Operation(summary = "Lista as solicitações visíveis ao usuário", description = "Paginação: page (desde 0), size (1 a 100), sort=campo,direção (createdAt, updatedAt, status, id; padrão createdAt,desc). Datas em America/Sao_Paulo, inclusivas.")
	public PageView<RequestViews.Summary> list(Actor actor,
			@RequestParam(required = false) RequestStatus status,
			@RequestParam(required = false) Category category,
			@RequestParam(required = false) Long costCenterId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
			@RequestParam(required = false) BigDecimal minAmount,
			@RequestParam(required = false) BigDecimal maxAmount,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@Parameter(description = "Ex.: createdAt,desc. Pode repetir.") @RequestParam(required = false) List<String> sort) {
		var paging = Paging.of(page, size);
		var filter = new RequestFilter(status, category, costCenterId, createdFrom, createdTo, minAmount == null ? null : Money.of(minAmount), maxAmount == null ? null : Money.of(maxAmount), q);
		return requests.list(actor, filter, paging.page(), paging.size(), parseSort(sort));
	}

	@PostMapping
	@Operation(summary = "Cria uma solicitação em DRAFT", description = "O total é calculado no servidor. Com Idempotency-Key, repetir o mesmo payload devolve a mesma resposta sem criar outra solicitação; outro payload com a mesma chave recebe 409.")
	public ResponseEntity<RequestViews.Detail> create(Actor actor, @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey, @Valid @RequestBody RequestPayload body) {
		if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
			throw InvalidInputException.of(IDEMPOTENCY_KEY, "a chave deve ter entre 1 e 100 caracteres");
		}
		var result = creator.create(actor, body.toCommand(), idempotencyKey);
		var response = ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/requests/" + result.detail().id()));
		if (idempotencyKey != null) {
			response.header(REPLAYED, String.valueOf(result.replayed()));
		}
		return response.body(result.detail());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Detalhe da versão atual, cadeia de aprovação, allowedActions e lockVersion")
	public RequestViews.Detail get(Actor actor, @PathVariable Long id) {
		return requests.get(actor, id);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Edita (DRAFT ou CHANGES_REQUESTED, só o dono)", description = "Exige o lockVersion atual. Após uma devolução, a primeira edição abre uma nova versão.")
	public RequestViews.Detail edit(Actor actor, @PathVariable Long id, @Valid @RequestBody RequestPayload body) {
		if (body.lockVersion() == null) {
			throw InvalidInputException.of("lockVersion", "informe o lockVersion atual da solicitação");
		}
		return requests.edit(actor, id, body.lockVersion(), body.toCommand());
	}

	@GetMapping("/{id}/versions")
	@Operation(summary = "Versões da solicitação")
	public List<RequestViews.VersionSummary> versions(Actor actor, @PathVariable Long id) {
		return requests.versions(actor, id);
	}

	@GetMapping("/{id}/versions/{number}")
	@Operation(summary = "Uma versão específica, com itens e etapas")
	public RequestViews.Version version(Actor actor, @PathVariable Long id, @PathVariable int number) {
		return requests.version(actor, id, number);
	}

	@PostMapping("/{id}/submit")
	@Operation(summary = "Submete ou reenvia (só o dono)", description = "O reenvio após devolução cria nova versão e recalcula a cadeia do zero.")
	public RequestViews.Detail submit(Actor actor, @PathVariable Long id, @Valid @RequestBody TransitionRequest body) {
		return workflow.transition(actor, id, Action.SUBMIT, body.lockVersion(), body.comment());
	}

	@PostMapping("/{id}/approve")
	@Operation(summary = "Aprova a etapa atual", description = "Na última etapa consome o orçamento do mês de competência; saldo insuficiente: 422 BUDGET_EXCEEDED.")
	public RequestViews.Detail approve(Actor actor, @PathVariable Long id, @Valid @RequestBody TransitionRequest body) {
		return workflow.transition(actor, id, Action.APPROVE, body.lockVersion(), body.comment());
	}

	@PostMapping("/{id}/reject")
	@Operation(summary = "Rejeita (comentário com pelo menos 10 caracteres)")
	public RequestViews.Detail reject(Actor actor, @PathVariable Long id, @Valid @RequestBody TransitionRequest body) {
		return workflow.transition(actor, id, Action.REJECT, body.lockVersion(), body.comment());
	}

	@PostMapping("/{id}/request-changes")
	@Operation(summary = "Devolve para ajuste (comentário com pelo menos 10 caracteres)")
	public RequestViews.Detail requestChanges(Actor actor, @PathVariable Long id, @Valid @RequestBody TransitionRequest body) {
		return workflow.transition(actor, id, Action.REQUEST_CHANGES, body.lockVersion(), body.comment());
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancela (dono ou ADMIN)")
	public RequestViews.Detail cancel(Actor actor, @PathVariable Long id, @Valid @RequestBody TransitionRequest body) {
		return workflow.transition(actor, id, Action.CANCEL, body.lockVersion(), body.comment());
	}

	@GetMapping("/{id}/history")
	@Operation(summary = "Histórico de eventos (append-only)")
	public List<RequestViews.Event> history(Actor actor, @PathVariable Long id) {
		return requests.history(actor, id);
	}

	private static Sort parseSort(List<String> values) {
		if (values == null || values.isEmpty()) {
			return Sort.unsorted();
		}
		// "sort=createdAt,desc" arrives split by comma: [createdAt, desc]
		var orders = new ArrayList<Sort.Order>();
		for (int i = 0; i < values.size(); i++) {
			var property = values.get(i).strip();
			var direction = Sort.Direction.ASC;
			if (i + 1 < values.size() && Sort.Direction.fromOptionalString(values.get(i + 1).strip()).isPresent()) {
				direction = Sort.Direction.fromString(values.get(i + 1).strip().toUpperCase(Locale.ROOT));
				i++;
			}
			if (!property.isEmpty()) {
				orders.add(new Sort.Order(direction, property));
			}
		}
		return Sort.by(orders);
	}

	/**
	 * Body of create and edit. Any total sent by the client is ignored.
	 *
	 * @param lockVersion required on edit, ignored on create
	 */
	public record RequestPayload(
			@NotBlank(message = "informe o título") @Size(max = 150, message = "o título tem no máximo 150 caracteres") String title,
			@Size(max = 2000, message = "a descrição tem no máximo 2000 caracteres") String description,
			@NotNull(message = "informe a categoria") Category category,
			@Positive(message = "centro de custo inválido") Long costCenterId,
			LocalDate neededBy,
			@NotEmpty(message = "informe pelo menos um item") @Size(max = 100, message = "no máximo 100 itens") List<@Valid @NotNull(message = "item vazio") ItemPayload> items,
			Long lockVersion) {

		RequestCommand toCommand() {
			var lineItems = items.stream().map(item -> new LineItem(item.description().strip(), item.quantity(), Money.of(item.unitPrice()))).toList();
			return new RequestCommand(title, description, category, costCenterId, neededBy, lineItems);
		}

	}

	/**
	 * @param unitPrice accepts a JSON string or number, e.g. {@code "4899.90"}
	 */
	public record ItemPayload(
			@NotBlank(message = "informe a descrição do item") @Size(max = 300, message = "a descrição do item tem no máximo 300 caracteres") String description,
			@NotNull(message = "informe a quantidade") @Positive(message = "a quantidade deve ser maior que zero") @Max(value = 100_000, message = "a quantidade máxima é 100000") Integer quantity,
			@NotNull(message = "informe o preço unitário") @DecimalMin(value = "0.01", message = "o preço unitário deve ser maior que zero") @Digits(integer = 10, fraction = 2, message = "preço com no máximo 10 dígitos inteiros e 2 decimais") BigDecimal unitPrice) {
	}

	/**
	 * @param lockVersion the value received in the last read of the request; a stale value gets 409
	 * @param comment     optional on approve; at least 10 characters on reject and request-changes
	 */
	public record TransitionRequest(
			@NotNull(message = "informe o lockVersion atual da solicitação") Long lockVersion,
			@Size(max = 2000, message = "o comentário tem no máximo 2000 caracteres") String comment) {
	}

}

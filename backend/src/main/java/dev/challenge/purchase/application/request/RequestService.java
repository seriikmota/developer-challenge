package dev.challenge.purchase.application.request;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.error.InvalidInputException;
import dev.challenge.purchase.application.error.InvalidInputException.FieldError;
import dev.challenge.purchase.application.error.NotFoundException;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.request.Action;
import dev.challenge.purchase.domain.request.RequestActionPolicy;
import dev.challenge.purchase.domain.request.RequestEventType;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.history.RequestEventRepository;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestEntity;
import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import dev.challenge.purchase.infrastructure.persistence.request.RequestSnapshotMapper;

/**
 * Creation, edition and queries of purchase requests. Status transitions are in {@link RequestWorkflowService}.
 */
@Service
public class RequestService {

	/**
	 * Dates in messages shown to the user. The API itself always uses ISO dates.
	 */
	private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Request fields the list can be sorted by. */
	public static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "status", "id");

	private final PurchaseRequestRepository requests;

	private final CostCenterRepository costCenters;

	private final RequestEventRepository events;

	private final RequestGate gate;

	private final RequestActionPolicy actionPolicy;

	private final HistoryRecorder history;

	private final RequestViewMapper views;

	private final Clock clock;

	public RequestService(PurchaseRequestRepository requests, CostCenterRepository costCenters, RequestEventRepository events, RequestGate gate, RequestActionPolicy actionPolicy, HistoryRecorder history, RequestViewMapper views, Clock clock) {
		this.requests = requests;
		this.costCenters = costCenters;
		this.events = events;
		this.gate = gate;
		this.actionPolicy = actionPolicy;
		this.history = history;
		this.views = views;
		this.clock = clock;
	}

	/**
	 * Creates a request in DRAFT, charged to the given cost center or, by default, to the owner's.
	 */
	@Transactional
	public RequestViews.Detail create(Actor actor, RequestCommand command) {
		var costCenterId = command.costCenterId() != null ? command.costCenterId() : actor.costCenterId();
		validate(command, costCenterId);
		var now = clock.instant();
		var request = requests.saveAndFlush(new PurchaseRequestEntity(actor.userId(), costCenterId, command.title().strip(), blankToNull(command.description()), command.category(), command.neededBy(), command.items(), now));
		history.record(request, actor, RequestEventType.CREATE, null, null, null, now);
		return views.detail(request, actor);
	}

	/**
	 * Replaces the content of a request in DRAFT or CHANGES_REQUESTED. After a request for changes, the first edition
	 * opens a new version and the submitted one stays untouched.
	 */
	@Transactional
	public RequestViews.Detail edit(Actor actor, Long id, long lockVersion, RequestCommand command) {
		var request = gate.writable(actor, id, Action.EDIT, lockVersion);
		actionPolicy.evaluate(actor, Action.EDIT, RequestSnapshotMapper.toSnapshot(request)).orThrow();
		var costCenterId = command.costCenterId() != null ? command.costCenterId() : request.getCostCenterId();
		validate(command, costCenterId);
		var now = clock.instant();
		var version = request.editableVersion(now);
		version.edit(command.title().strip(), blankToNull(command.description()), command.category(), command.neededBy(), command.items());
		request.changeCostCenter(costCenterId);
		request.touch(now);
		history.record(request, actor, RequestEventType.EDIT, request.getStatus(), null, null, now);
		requests.flush();
		return views.detail(request, actor);
	}

	@Transactional(readOnly = true)
	public RequestViews.Detail get(Actor actor, Long id) {
		return views.detail(gate.readable(actor, id), actor);
	}

	@Transactional(readOnly = true)
	public PageView<RequestViews.Summary> list(Actor actor, RequestFilter filter, int page, int size, Sort sort) {
		var invalid = sort.stream().map(Sort.Order::getProperty).filter(property -> !SORTABLE.contains(property)).toList();
		if (!invalid.isEmpty()) {
			throw InvalidInputException.of("sort", "não é possível ordenar por " + String.join(", ", invalid) + "; campos aceitos: " + String.join(", ", SORTABLE.stream().sorted().toList()));
		}
		var effectiveSort = sort.isUnsorted() ? Sort.by(Sort.Direction.DESC, "createdAt") : sort;
		var result = requests.findAll(RequestVisibility.visibleTo(actor).and(filter.toSpecification()), PageRequest.of(page, size, effectiveSort.and(Sort.by("id"))));
		var summaries = views.summaries(result.getContent());
		return new PageView<>(summaries, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public List<RequestViews.VersionSummary> versions(Actor actor, Long id) {
		return gate.readable(actor, id).getVersions().stream().map(views::versionSummary).toList();
	}

	@Transactional(readOnly = true)
	public RequestViews.Version version(Actor actor, Long id, int number) {
		var request = gate.readable(actor, id);
		return request.getVersions().stream()
				.filter(version -> version.getNumber() == number)
				.findFirst()
				.map(views::version)
				.orElseThrow(() -> new NotFoundException("A solicitação " + id + " não tem a versão " + number + "."));
	}

	@Transactional(readOnly = true)
	public List<RequestViews.Event> history(Actor actor, Long id) {
		gate.readable(actor, id);
		return views.events(events.findByRequestIdOrderByOccurredAtAscIdAsc(id));
	}

	private void validate(RequestCommand command, Long costCenterId) {
		List<FieldError> errors = new ArrayList<>();
		if (costCenterId == null) {
			errors.add(new FieldError("costCenterId", "informe o centro de custo: seu usuário não tem um centro de custo padrão"));
		} else if (!costCenters.existsById(costCenterId)) {
			errors.add(new FieldError("costCenterId", "centro de custo " + costCenterId + " não existe"));
		}
		var today = LocalDate.now(clock.withZone(CompetenceMonth.ZONE));
		if (command.neededBy() != null && !command.neededBy().isAfter(today)) {
			errors.add(new FieldError("neededBy", "a data de necessidade precisa ser futura (depois de " + today.format(DISPLAY_DATE) + ")"));
		}
		if (command.items().isEmpty()) {
			errors.add(new FieldError("items", "informe pelo menos um item"));
		}
		if (!errors.isEmpty()) {
			throw new InvalidInputException(errors);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}

package dev.challenge.purchase.infrastructure.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

/**
 * Full lifecycle of a request through the API, including the status code of every refusal (README 4.3).
 */
class RequestLifecycleApiTest extends ApiTestSupport {

	private CostCenterEntity costCenter;

	private Login owner;

	private Login approverL1;

	private Login approverL2;

	private Login admin;

	@BeforeEach
	void setUp() {
		costCenter = newCostCenter();
		owner = requester(costCenter);
		approverL1 = approver(ApproverLevel.NIVEL_1, costCenter);
		approverL2 = approver(ApproverLevel.NIVEL_2, costCenter);
		admin = admin();
		defineBudget(costCenter, "50000.00");
	}

	private Money consumed() {
		return budgets.findByCostCenterIdAndMonth(costCenter.getId(), currentMonth()).orElseThrow().getConsumed();
	}

	@Test
	void createsDraftWithServerComputedTotalAndOwnerActions() throws Exception {
		createRequest(owner, body("Notebooks para o time de TI", "EQUIPMENT", 5, "4899.90").replace("\"items\"", "\"total\": \"1.00\", \"items\""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", notNullValue()))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.costCenterId").value(costCenter.getId()))
				.andExpect(jsonPath("$.currentVersion").value(1))
				.andExpect(jsonPath("$.lockVersion").value(0))
				.andExpect(jsonPath("$.version.total").value("24499.50"))
				.andExpect(jsonPath("$.version.items[0].lineTotal").value("24499.50"))
				.andExpect(jsonPath("$.version.steps", empty()))
				.andExpect(jsonPath("$.allowedActions", containsInAnyOrder("EDIT", "SUBMIT", "CANCEL")));
	}

	@Test
	void approvesTwoStepChainAndConsumesBudgetOnLastStep() throws Exception {
		var id = draft(owner, "SERVICES", 2, "2500.00");
		act(owner, id, "submit", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.version.competenceMonth").value(currentMonth().toString()))
				.andExpect(jsonPath("$.version.steps[*].level", contains("NIVEL_1", "NIVEL_2")))
				.andExpect(jsonPath("$.version.steps[0].current").value(true))
				.andExpect(jsonPath("$.allowedActions", contains("CANCEL")));

		getRequest(approverL1, id).andExpect(jsonPath("$.allowedActions", containsInAnyOrder("APPROVE", "REJECT", "REQUEST_CHANGES")));
		act(approverL1, id, "approve", "De acordo.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.version.steps[0].status").value("APPROVED"))
				.andExpect(jsonPath("$.version.steps[0].decidedByName").value(approverL1.user().getName()))
				.andExpect(jsonPath("$.version.steps[1].current").value(true));
		assertThat(consumed()).isEqualTo(Money.ZERO);

		act(approverL2, id, "approve", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.allowedActions", empty()));
		assertThat(consumed()).isEqualTo(Money.of("5000.00"));
	}

	@Test
	void lastApprovalWithoutBudgetIsRefusedAndRequestStaysPending() throws Exception {
		defineBudget(costCenter, "999.99");
		var id = draft(owner, "SUPPLIES", 1, "1000.00");
		act(owner, id, "submit", null);

		act(approverL1, id, "approve", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"))
				.andExpect(jsonPath("$.detail").value("Não é possível aprovar: o centro de custo " + costCenter.getCode() + " tem R$ 999,99 disponíveis em " + currentMonth() + "; a solicitação exige R$ 1.000,00."));

		getRequest(owner, id)
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.version.steps[0].status").value("PENDING"));
		assertThat(consumed()).isEqualTo(Money.ZERO);
	}

	@Test
	void requestChangesThenResubmitCreatesNewVersionWithFreshChain() throws Exception {
		var id = draft(owner, "SERVICES", 1, "3000.00");
		act(owner, id, "submit", null);
		act(approverL1, id, "approve", null);
		act(approverL2, id, "request-changes", "Detalhe o escopo do serviço.").andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));

		edit(owner, id, lockVersion(owner, id), "Serviço com escopo detalhado", "SERVICES", 1, "2800.00")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentVersion").value(2))
				.andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));
		act(owner, id, "submit", null)
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.versions", contains(1, 2)))
				.andExpect(jsonPath("$.version.steps[*].status", everyItem(is("PENDING"))));

		// the approval of v1 does not count: NIVEL_1 must be approved again, and the same approver may do it
		getRequest(approverL1, id).andExpect(jsonPath("$.allowedActions", containsInAnyOrder("APPROVE", "REJECT", "REQUEST_CHANGES")));
		mvc.perform(get("/requests/" + id + "/versions/1").header("Authorization", owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value("3000.00"))
				.andExpect(jsonPath("$.steps[0].status").value("APPROVED"))
				.andExpect(jsonPath("$.steps[1].status").value("CHANGES_REQUESTED"));
		mvc.perform(get("/requests/" + id + "/versions").header("Authorization", owner.bearer()))
				.andExpect(jsonPath("$[*].outcome", contains("CHANGES_REQUESTED", "PENDING")));
	}

	@Test
	void resubmitWithoutEditingAlsoCreatesNewVersion() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);
		act(approverL1, id, "request-changes", "Informe o fornecedor.");

		act(owner, id, "submit", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentVersion").value(2))
				.andExpect(jsonPath("$.version.total").value("100.00"));
	}

	@Test
	void rejectRequiresCommentAndEndsTheRequest() throws Exception {
		var id = draft(owner, "TRAVEL", 1, "800.00");
		act(owner, id, "submit", null);

		act(approverL1, id, "reject", "curto")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"))
				.andExpect(jsonPath("$.detail").value("Não é possível rejeitar: informe um comentário com pelo menos 10 caracteres explicando o motivo (recebido: 5)."));
		act(approverL1, id, "reject", "Viagem fora da política.").andExpect(jsonPath("$.status").value("REJECTED"));

		act(owner, id, "cancel", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
				.andExpect(jsonPath("$.detail").value("Não é possível cancelar: a solicitação está em REJECTED, que é um estado final."));
	}

	@Test
	void sameApproverCannotApproveTwoStepsOfTheSameVersion() throws Exception {
		var id = draft(owner, "SERVICES", 1, "5000.00");
		act(owner, id, "submit", null);
		act(approverL2, id, "approve", null);

		act(approverL2, id, "approve", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("DUPLICATE_APPROVER"))
				.andExpect(jsonPath("$.detail").value("Não é possível aprovar: você já aprovou a etapa NIVEL_1 desta versão (segregação de funções)."));
	}

	@Test
	void globalAdminMustChooseCostCenter() throws Exception {
		createRequest(admin, body("Sem centro", "OTHER", 1, "10.00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("costCenterId"));
		createRequest(admin, body("Com centro", "OTHER", 1, "10.00").replace("\"items\"", "\"costCenterId\": " + costCenter.getId() + ", \"items\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.costCenterId").value(costCenter.getId()));
	}

	@Test
	void nobodyApprovesOwnRequestEvenAdmin() throws Exception {
		var adminOwner = login(Role.ADMIN, null, costCenter);
		var id = draft(adminOwner, "SOFTWARE", 1, "100.00");
		act(adminOwner, id, "submit", null);
		act(approverL1, id, "approve", null);

		act(adminOwner, id, "approve", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
		act(admin, id, "approve", null).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void levelMismatchIsNotEligible() throws Exception {
		var id = draft(owner, "SERVICES", 1, "5000.00");
		act(owner, id, "submit", null);
		act(approverL2, id, "approve", null);

		act(approverL1, id, "approve", null)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("APPROVER_NOT_ELIGIBLE"))
				.andExpect(jsonPath("$.detail").value("Não é possível aprovar: a etapa NIVEL_2 exige um aprovador NIVEL_2; seu nível é NIVEL_1."));
	}

	@Test
	void requesterTryingToApproveIsForbidden() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);

		act(owner, id, "approve", null)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void requestsOutsideVisibilityAreNotFound() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		var otherCostCenterApprover = approver(ApproverLevel.NIVEL_2, newCostCenter());

		// a DRAFT is visible only to its owner, even for ADMIN
		getRequest(admin, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		getRequest(approverL1, id).andExpect(status().isNotFound());
		act(owner, id, "submit", null);
		getRequest(approverL1, id).andExpect(status().isOk());
		getRequest(admin, id).andExpect(status().isOk());
		getRequest(otherCostCenterApprover, id).andExpect(status().isNotFound());
		transition(otherCostCenterApprover, id, "approve", 0, null).andExpect(status().isNotFound());
		getRequest(requester(costCenter), id).andExpect(status().isNotFound());
		getRequest(owner, 999_999_999L).andExpect(status().isNotFound());
	}

	@Test
	void staleLockVersionIsConflict() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		var stale = lockVersion(owner, id);
		edit(owner, id, stale, "Título novo", "SUPPLIES", 2, "100.00").andExpect(status().isOk()).andExpect(jsonPath("$.lockVersion").value(greaterThan((int) stale)));

		transition(owner, id, "submit", stale, null)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
		edit(owner, id, stale, "Outro título", "SUPPLIES", 1, "100.00").andExpect(status().isConflict());
	}

	@Test
	void lockVersionReturnedByAChangeIsTheOneToSendNext() throws Exception {
		var id = draft(owner, "SERVICES", 1, "5000.00");
		var afterSubmit = act(owner, id, "submit", null).andReturn().getResponse().getContentAsString();
		long next = ((Number) JsonPath.read(afterSubmit, "$.lockVersion")).longValue();

		assertThat(next).isEqualTo(lockVersion(owner, id));
		transition(approverL1, id, "approve", next, null).andExpect(status().isOk());
	}

	@Test
	void onlyOwnerEditsAndSubmits() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);
		act(approverL1, id, "request-changes", "Ajuste a quantidade.");

		edit(approverL1, id, lockVersion(approverL1, id), "Mudança indevida", "SUPPLIES", 1, "100.00")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("NOT_OWNER"));
		act(approverL1, id, "submit", null).andExpect(jsonPath("$.code").value("NOT_OWNER"));
	}

	@Test
	void editingPendingRequestIsNotEditable() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);

		edit(owner, id, lockVersion(owner, id), "Mudança", "SUPPLIES", 1, "100.00")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("NOT_EDITABLE"));
	}

	@Test
	void adminCancelsAnotherUsersPendingRequest() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);

		getRequest(admin, id).andExpect(jsonPath("$.allowedActions", containsInAnyOrder("APPROVE", "REJECT", "REQUEST_CHANGES", "CANCEL")));
		act(admin, id, "cancel", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		act(approverL1, id, "cancel", null).andExpect(status().isUnprocessableContent());
	}

	@Test
	void historyRecordsEveryActionWithActorStatusAndRequestId() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "100.00");
		act(owner, id, "submit", null);
		mvc.perform(post("/requests/" + id + "/approve").header("Authorization", approverL1.bearer()).header("X-Request-Id", "req-approve-1").contentType("application/json").content("{ \"lockVersion\": " + lockVersion(approverL1, id) + ", \"comment\": \"Ok\" }"))
				.andExpect(status().isOk());

		mvc.perform(get("/requests/" + id + "/history").header("Authorization", owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].action", contains("CREATE", "SUBMIT", "APPROVE")))
				.andExpect(jsonPath("$[0].fromStatus").doesNotExist())
				.andExpect(jsonPath("$[1].fromStatus").value("DRAFT"))
				.andExpect(jsonPath("$[1].toStatus").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$[2].actorId").value(approverL1.id()))
				.andExpect(jsonPath("$[2].actorRole").value("APROVADOR"))
				.andExpect(jsonPath("$[2].stepOrder").value(1))
				.andExpect(jsonPath("$[2].comment").value("Ok"))
				.andExpect(jsonPath("$[2].requestId").value("req-approve-1"))
				.andExpect(jsonPath("$[2].toStatus").value("APPROVED"));
	}

	@Test
	void invalidPayloadListsFieldErrors() throws Exception {
		createRequest(owner, """
				{ "title": "", "category": "FOO", "items": [] }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("category"));
		createRequest(owner, """
				{ "title": "", "category": "OTHER", "neededBy": "2000-01-01", "items": [ { "description": "x", "quantity": 0, "unitPrice": "10.001" } ] }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "items[0].quantity", "items[0].unitPrice")));
		createRequest(owner, body("Data passada", "OTHER", 1, "10.00").replace(LocalDate.now().plusDays(30).toString(), LocalDate.now().toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("neededBy"));
	}

}

package dev.challenge.purchase.infrastructure.web.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

class AdminApiTest extends ApiTestSupport {

	private CostCenterEntity costCenter;

	private Login admin;

	private Login requester;

	@BeforeEach
	void setUp() {
		costCenter = newCostCenter();
		admin = admin();
		requester = requester(costCenter);
	}

	private ResultActions send(MockHttpServletRequestBuilder request, Login login, String json) throws Exception {
		return mvc.perform(request.header("Authorization", login.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String userJson(String role, String level, Long costCenterId) {
		return """
				{ "name": "Novo Usuário", "email": "%s@example.test", "password": "senha-forte-1", "role": "%s", "approverLevel": %s, "costCenterId": %s }
				""".formatted(UUID.randomUUID(), role, level == null ? "null" : "\"" + level + "\"", costCenterId);
	}

	@Test
	void onlyAdminManagesUsersAndCostCenters() throws Exception {
		mvc.perform(get("/users").header("Authorization", requester.bearer())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		send(post("/users"), requester, userJson("ADMIN", null, null)).andExpect(status().isForbidden());
		send(post("/cost-centers"), requester, "{ \"code\": \"X-1\", \"name\": \"X\" }").andExpect(status().isForbidden());
		send(put("/cost-centers/" + costCenter.getId() + "/budgets/2026-11"), requester, "{ \"amount\": \"1.00\" }").andExpect(status().isForbidden());
	}

	@Test
	void adminCreatesApproverAndListsUsers() throws Exception {
		send(post("/users"), admin, userJson("APROVADOR", "NIVEL_2", costCenter.getId()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("APROVADOR"))
				.andExpect(jsonPath("$.approverLevel").value("NIVEL_2"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		mvc.perform(get("/users?size=5").header("Authorization", admin.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
	}

	@Test
	void userProfileMustBeConsistent() throws Exception {
		send(post("/users"), admin, userJson("APROVADOR", null, null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("approverLevel", "costCenterId")));
		send(post("/users"), admin, userJson("SOLICITANTE", "NIVEL_1", costCenter.getId()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("approverLevel"));
		send(post("/users"), admin, userJson("ADMIN", null, null)).andExpect(status().isCreated());
	}

	@Test
	void patchChangesRoleAndClearsLevel() throws Exception {
		var approver = approver(ApproverLevel.NIVEL_1, costCenter);

		send(patch("/users/" + approver.id()), admin, "{ \"approverLevel\": \"NIVEL_2\" }").andExpect(status().isOk()).andExpect(jsonPath("$.approverLevel").value("NIVEL_2"));
		send(patch("/users/" + approver.id()), admin, "{ \"role\": \"SOLICITANTE\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("SOLICITANTE"))
				.andExpect(jsonPath("$.approverLevel").doesNotExist());
		send(patch("/users/" + requester.id()), admin, "{ \"role\": \"APROVADOR\" }").andExpect(status().isBadRequest());
		send(patch("/users/999999999"), admin, "{ \"name\": \"x\" }").andExpect(status().isNotFound());
	}

	@Test
	void costCenterCodeIsUnique() throws Exception {
		var code = "cc-" + UUID.randomUUID().toString().substring(0, 6);

		send(post("/cost-centers"), admin, "{ \"code\": \"%s\", \"name\": \"Operações\" }".formatted(code))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value(code.toUpperCase()));
		send(post("/cost-centers"), admin, "{ \"code\": \"%s\", \"name\": \"Outro\" }".formatted(code.toUpperCase()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("COST_CENTER_CODE_TAKEN"));
		mvc.perform(get("/cost-centers").header("Authorization", requester.bearer())).andExpect(status().isOk());
	}

	@Test
	void definesAndReadsMonthlyBudget() throws Exception {
		var url = "/cost-centers/" + costCenter.getId() + "/budgets/2026-10";
		mvc.perform(get(url).header("Authorization", requester.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value("0.00"))
				.andExpect(jsonPath("$.available").value("0.00"));

		send(put(url), admin, "{ \"amount\": \"8200.00\" }")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value("2026-10"))
				.andExpect(jsonPath("$.amount").value("8200.00"))
				.andExpect(jsonPath("$.consumed").value("0.00"))
				.andExpect(jsonPath("$.available").value("8200.00"));
	}

	@Test
	void budgetCannotGoBelowConsumed() throws Exception {
		var month = CompetenceMonth.parse("2026-09");
		budgets.trySetAmount(costCenter.getId(), month, Money.of("5000.00"), Instant.now());
		budgets.tryConsume(costCenter.getId(), month, Money.of("3000.00"), Instant.now());

		send(put("/cost-centers/" + costCenter.getId() + "/budgets/2026-09"), admin, "{ \"amount\": 2999.99 }")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("BUDGET_BELOW_CONSUMED"))
				.andExpect(jsonPath("$.detail").value("Não é possível definir o orçamento de 09/2026 do centro de custo " + costCenter.getCode() + " em R$ 2.999,99: já foram consumidos R$ 3.000,00."));
		send(put("/cost-centers/" + costCenter.getId() + "/budgets/2026-09"), admin, "{ \"amount\": 3000 }").andExpect(status().isOk()).andExpect(jsonPath("$.available").value("0.00"));
	}

	@Test
	void rejectsInvalidBudgetInput() throws Exception {
		send(put("/cost-centers/" + costCenter.getId() + "/budgets/2026-13"), admin, "{ \"amount\": 1 }").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("month"));
		send(put("/cost-centers/" + costCenter.getId() + "/budgets/2026-11"), admin, "{ \"amount\": -1 }").andExpect(status().isBadRequest());
		send(put("/cost-centers/999999999/budgets/2026-11"), admin, "{ \"amount\": 1 }").andExpect(status().isNotFound());
	}

}

package dev.challenge.purchase.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.IntegrationTest;
import dev.challenge.purchase.application.auth.AccessTokenIssuer;
import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.budget.MonthlyBudgetRepository;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;

/**
 * Base of the API tests: the whole application over HTTP (MockMvc) against PostgreSQL. Tests commit for real, so
 * every test creates its own cost centers and users and never depends on data from other tests.
 */
@IntegrationTest
public abstract class ApiTestSupport {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected CostCenterRepository costCenters;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected MonthlyBudgetRepository budgets;

	@Autowired
	private AccessTokenIssuer tokenIssuer;

	/**
	 * A logged user: the persisted entity and a valid bearer token.
	 */
	protected record Login(UserEntity user, String token) {

		public Long id() {
			return user.getId();
		}

		public String bearer() {
			return "Bearer " + token;
		}

	}

	protected CostCenterEntity newCostCenter() {
		var code = "CC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		return costCenters.save(new CostCenterEntity(code, "Centro " + code, Instant.now()));
	}

	protected Login login(Role role, ApproverLevel level, CostCenterEntity costCenter) {
		var user = users.save(new UserEntity(role + " " + UUID.randomUUID().toString().substring(0, 4), UUID.randomUUID() + "@example.test", "{noop}unused", role, level, costCenter == null ? null : costCenter.getId(), Instant.now()));
		return new Login(user, tokenIssuer.issue(user).value());
	}

	protected Login requester(CostCenterEntity costCenter) {
		return login(Role.SOLICITANTE, null, costCenter);
	}

	protected Login approver(ApproverLevel level, CostCenterEntity costCenter) {
		return login(Role.APROVADOR, level, costCenter);
	}

	protected Login admin() {
		return login(Role.ADMIN, null, null);
	}

	protected static CompetenceMonth currentMonth() {
		return CompetenceMonth.of(Instant.now());
	}

	protected void defineBudget(CostCenterEntity costCenter, String amount) {
		budgets.trySetAmount(costCenter.getId(), currentMonth(), Money.of(amount), Instant.now());
	}

	protected static String body(String title, String category, int quantity, String unitPrice) {
		return """
				{ "title": "%s", "description": "Descrição de teste", "category": "%s", "neededBy": "%s",
				  "items": [ { "description": "Item de teste", "quantity": %d, "unitPrice": "%s" } ] }
				""".formatted(title, category, LocalDate.now().plusDays(30), quantity, unitPrice);
	}

	protected ResultActions createRequest(Login login, String json) throws Exception {
		return mvc.perform(post("/requests").header("Authorization", login.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/**
	 * Creates a request and returns its id.
	 */
	protected long draft(Login owner, String category, int quantity, String unitPrice) throws Exception {
		var json = createRequest(owner, body("Compra de teste", category, quantity, unitPrice)).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	protected ResultActions getRequest(Login login, long id) throws Exception {
		return mvc.perform(get("/requests/" + id).header("Authorization", login.bearer()));
	}

	protected long lockVersion(Login login, long id) throws Exception {
		var json = getRequest(login, id).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(json, "$.lockVersion")).longValue();
	}

	protected ResultActions transition(Login login, long id, String action, long lockVersion, String comment) throws Exception {
		var json = comment == null ? "{ \"lockVersion\": %d }".formatted(lockVersion) : "{ \"lockVersion\": %d, \"comment\": \"%s\" }".formatted(lockVersion, comment);
		return mvc.perform(post("/requests/" + id + "/" + action).header("Authorization", login.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/**
	 * Transition with the current lockVersion, read by the same user.
	 */
	protected ResultActions act(Login login, long id, String action, String comment) throws Exception {
		return transition(login, id, action, lockVersion(login, id), comment);
	}

	protected ResultActions edit(Login login, long id, long lockVersion, String title, String category, int quantity, String unitPrice) throws Exception {
		var json = body(title, category, quantity, unitPrice).replaceFirst("\\{", "{ \"lockVersion\": " + lockVersion + ",");
		return mvc.perform(put("/requests/" + id).header("Authorization", login.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

}

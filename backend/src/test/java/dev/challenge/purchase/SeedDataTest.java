package dev.challenge.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.domain.budget.CompetenceMonth;

/**
 * The demo seed loaded on its own container, as docker compose does: the data must be consistent with the rules of the
 * application, not only with the schema. Tests here only read or act on requests no other test of this class uses.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/seed")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SeedDataTest {

	private static final String PASSWORD = "Senha@123";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Flyway flyway;

	private String bearer(String email) throws Exception {
		var json = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(json, "$.accessToken");
	}

	private long requestId(String title) {
		return jdbc.queryForObject("SELECT request_id FROM request_version WHERE title = ? AND number = 1", Long.class, title);
	}

	private ResultActions budget(String bearer, String code) throws Exception {
		var costCenterId = jdbc.queryForObject("SELECT id FROM cost_center WHERE code = ?", Long.class, code);
		return mvc.perform(get("/cost-centers/" + costCenterId + "/budgets/" + CompetenceMonth.of(Instant.now())).header("Authorization", bearer));
	}

	private ResultActions approve(String bearer, long id) throws Exception {
		var json = mvc.perform(get("/requests/" + id).header("Authorization", bearer)).andReturn().getResponse().getContentAsString();
		var lockVersion = ((Number) JsonPath.read(json, "$.lockVersion")).longValue();
		return mvc.perform(post("/requests/" + id + "/approve").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{ \"lockVersion\": " + lockVersion + " }"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "solicitante.ti@example.com", "aprovador1.ti@example.com", "aprovador2.ti@example.com", "solicitante.mkt@example.com", "aprovador1.mkt@example.com", "aprovador2.mkt@example.com", "admin@example.com", "admin2@example.com" })
	void everySeedUserLogsInWithTheDocumentedPassword(String email) throws Exception {
		mvc.perform(get("/auth/me").header("Authorization", bearer(email))).andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void currentMonthBudgetsMatchTheApprovedRequests() throws Exception {
		var admin = bearer("admin@example.com");
		budget(admin, "MKT").andExpect(jsonPath("$.amount").value("5000.00")).andExpect(jsonPath("$.consumed").value("4800.00")).andExpect(jsonPath("$.available").value("200.00"));
		budget(admin, "TI").andExpect(jsonPath("$.amount").value("50000.00"));
	}

	@Test
	void approvingAboveTheAlmostExhaustedBudgetIsRefused() throws Exception {
		approve(bearer("aprovador1.mkt@example.com"), requestId("Brindes para evento"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("BUDGET_EXCEEDED"));
	}

	@Test
	void seededPendingRequestCanBeApprovedThroughTheApi() throws Exception {
		var approver = bearer("aprovador2.ti@example.com");
		var id = requestId("Consultoria de segurança");
		mvc.perform(get("/requests/" + id + "/history").header("Authorization", approver)).andExpect(jsonPath("$[*].action", contains("CREATE", "SUBMIT", "APPROVE")));

		approve(approver, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
	}

	@Test
	void requestOwnedByAnAdminWaitsForTheOtherAdmin() throws Exception {
		var id = (int) requestId("Ferramenta de observabilidade");
		mvc.perform(get("/approvals/inbox?size=100").header("Authorization", bearer("admin2@example.com"))).andExpect(jsonPath("$.data[*].id", hasItem(id)));
		mvc.perform(get("/approvals/inbox?size=100").header("Authorization", bearer("admin@example.com"))).andExpect(jsonPath("$.data[*].id", not(hasItem(id))));
	}

	@Test
	void resubmittedRequestKeepsTheReturnedVersion() throws Exception {
		var owner = bearer("solicitante.ti@example.com");
		var id = requestId("Notebook para estagiário");
		mvc.perform(get("/requests/" + id).header("Authorization", owner)).andExpect(jsonPath("$.versions", contains(1, 2))).andExpect(jsonPath("$.version.total").value("4500.00"));
		mvc.perform(get("/requests/" + id + "/versions").header("Authorization", owner)).andExpect(jsonPath("$[*].outcome", contains("CHANGES_REQUESTED", "PENDING")));
	}

	@Test
	void draftsAreVisibleOnlyToTheirOwner() throws Exception {
		var id = requestId("Monitores para o time de dados");
		mvc.perform(get("/requests/" + id).header("Authorization", bearer("solicitante.ti@example.com"))).andExpect(status().isOk());
		mvc.perform(get("/requests/" + id).header("Authorization", bearer("admin@example.com"))).andExpect(status().isNotFound());
	}

	@Test
	void runningTheSeedAgainChangesNothing() throws InterruptedException {
		// the run marker has second precision: wait so the checksum changes and Flyway really runs the seed again
		Thread.sleep(1100);
		var before = jdbc.queryForObject("SELECT (SELECT count(*) FROM app_user) || '/' || (SELECT count(*) FROM purchase_request) || '/' || (SELECT count(*) FROM monthly_budget)", String.class);

		var result = flyway.migrate();

		assertThat(result.migrationsExecuted).isEqualTo(1);
		var after = jdbc.queryForObject("SELECT (SELECT count(*) FROM app_user) || '/' || (SELECT count(*) FROM purchase_request) || '/' || (SELECT count(*) FROM monthly_budget)", String.class);
		assertThat(after).isEqualTo(before);
	}

}

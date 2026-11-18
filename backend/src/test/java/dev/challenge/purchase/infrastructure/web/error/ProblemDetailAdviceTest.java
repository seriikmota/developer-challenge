package dev.challenge.purchase.infrastructure.web.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import dev.challenge.purchase.IntegrationTest;

@IntegrationTest
class ProblemDetailAdviceTest {

	@Autowired
	private MockMvc mvc;

	@ParameterizedTest(name = "{0} -> {1} {2}")
	@CsvSource({
			"domain,          422, BUDGET_EXCEEDED,         Orçamento insuficiente",
			"not-found,       404, NOT_FOUND,               Recurso não encontrado",
			"forbidden,       403, FORBIDDEN,               Ação não permitida",
			"conflict,        409, IDEMPOTENCY_KEY_REUSED,  Idempotency-Key reutilizada",
			"optimistic-lock, 409, CONCURRENT_MODIFICATION, Conflito de concorrência",
			"access-denied,   403, FORBIDDEN,               Ação não permitida",
			"unexpected,      500, INTERNAL_ERROR,          Erro interno" })
	void rendersProblemDetail(String kind, int status, String code, String title) throws Exception {
		mvc.perform(get("/test/errors/" + kind).with(jwt()).header("X-Request-Id", "req-" + kind))
				.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.type").value("https://example.com/problems/" + code.toLowerCase().replace('_', '-')))
				.andExpect(jsonPath("$.detail").exists())
				.andExpect(jsonPath("$.requestId").value("req-" + kind));
	}

	@Test
	void domainDetailExplainsTheReason() throws Exception {
		mvc.perform(get("/test/errors/domain").with(jwt()))
				.andExpect(jsonPath("$.detail").value("O centro de custo CC-TI-01 tem R$ 8.200,00 disponíveis em 11/2026; a solicitação exige R$ 24.499,50."));
	}

	@Test
	void unexpectedErrorDoesNotLeakInternals() throws Exception {
		mvc.perform(get("/test/errors/unexpected").with(jwt()))
				.andExpect(jsonPath("$.detail").value(not(containsString("secret internal detail"))));
	}

}

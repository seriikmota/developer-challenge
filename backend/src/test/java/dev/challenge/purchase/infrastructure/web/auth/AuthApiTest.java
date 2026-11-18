package dev.challenge.purchase.infrastructure.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.IntegrationTest;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.domain.user.Role;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterRepository;
import dev.challenge.purchase.infrastructure.persistence.user.UserEntity;
import dev.challenge.purchase.infrastructure.persistence.user.UserRepository;

@IntegrationTest
@Transactional
class AuthApiTest {

	private static final String PASSWORD = "senha-forte-123";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CostCenterRepository costCenters;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	private CostCenterEntity costCenter;

	@BeforeEach
	void setUp() {
		costCenter = costCenters.save(new CostCenterEntity("CC-" + UUID.randomUUID().toString().substring(0, 8), "Tecnologia", Instant.now()));
	}

	private UserEntity existingUser(Role role, ApproverLevel level) {
		var email = UUID.randomUUID() + "@example.test";
		return users.save(new UserEntity("Ana Aprovadora", email, passwordEncoder.encode(PASSWORD), role, level, costCenter.getId(), Instant.now()));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{ "email": "%s", "password": "%s" }
				""".formatted(email, password)));
	}

	private String tokenOf(UserEntity user) throws Exception {
		var body = login(user.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	@Test
	void registersSolicitanteWithoutExposingPassword() throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Bruno Solicitante", "email": "Bruno.%s@Example.test", "password": "%s", "costCenterId": %d }
				""".formatted(UUID.randomUUID(), PASSWORD, costCenter.getId())))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", notNullValue()))
				.andExpect(jsonPath("$.role").value("SOLICITANTE"))
				.andExpect(jsonPath("$.email").value(endsWith("@example.test")))
				.andExpect(jsonPath("$.costCenterId").value(costCenter.getId()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registrationCannotChooseRole() throws Exception {
		var body = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Esperto", "email": "esperto.%s@example.test", "password": "%s", "costCenterId": %d, "role": "ADMIN" }
				""".formatted(UUID.randomUUID(), PASSWORD, costCenter.getId())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat((String) JsonPath.read(body, "$.role")).isEqualTo("SOLICITANTE");
	}

	@Test
	void duplicatedEmailIsConflict() throws Exception {
		var user = existingUser(Role.SOLICITANTE, null);

		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Outro", "email": "%s", "password": "%s", "costCenterId": %d }
				""".formatted(user.getEmail().toUpperCase(), PASSWORD, costCenter.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void invalidRegistrationListsFieldErrors() throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "", "email": "não-é-email", "password": "curta" }
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("name")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
				.andExpect(jsonPath("$.errors[*].field").value(hasItem("costCenterId")));
	}

	@Test
	void unknownCostCenterIsFieldError() throws Exception {
		mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "name": "Carla", "email": "carla.%s@example.test", "password": "%s", "costCenterId": 999999 }
				""".formatted(UUID.randomUUID(), PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("costCenterId"));
	}

	@Test
	void malformedJsonIsBadRequest() throws Exception {
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void loginReturnsTokenWithClaimsAndMeReturnsUser() throws Exception {
		var approver = existingUser(Role.APROVADOR, ApproverLevel.NIVEL_2);

		var body = login(approver.getEmail(), PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresAt").exists())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");
		var claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

		assertThat(claims).contains("\"sub\":\"" + approver.getId() + "\"", "\"role\":\"APROVADOR\"", "\"approverLevel\":\"NIVEL_2\"", "\"costCenterId\":" + costCenter.getId());
		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(approver.getId()))
				.andExpect(jsonPath("$.role").value("APROVADOR"))
				.andExpect(jsonPath("$.approverLevel").value("NIVEL_2"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
		var user = existingUser(Role.SOLICITANTE, null);

		login(user.getEmail(), "senha-errada-1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
		login("ninguem@example.test", "senha-errada-1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS")).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
	}

	@Test
	void meWithoutTokenIsUnauthorizedProblem() throws Exception {
		mvc.perform(get("/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.type").value("https://example.com/problems/unauthorized"))
				.andExpect(jsonPath("$.title").value("Autenticação necessária"))
				.andExpect(jsonPath("$.requestId").exists());
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		var user = existingUser(Role.SOLICITANTE, null);
		var issuedAt = Instant.now().minusSeconds(3600);
		var claims = JwtClaimsSet.builder().issuer("purchase-requests").subject(user.getId().toString()).issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(900)).claim("role", "SOLICITANTE").build();
		var expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void tamperedTokenIsRejected() throws Exception {
		var token = tokenOf(existingUser(Role.SOLICITANTE, null));
		var parts = token.split("\\.");
		var forgedClaims = Base64.getUrlEncoder().withoutPadding().encodeToString(new String(Base64.getUrlDecoder().decode(parts[1])).replace("SOLICITANTE", "ADMIN").getBytes());

		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + parts[0] + "." + forgedClaims + "." + parts[2]))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
	}

	@Test
	void tokenOfDeletedUserIsUnauthorized() throws Exception {
		var user = existingUser(Role.SOLICITANTE, null);
		var token = tokenOf(user);
		users.delete(user);
		users.flush();

		mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void requestIdIsEchoedWhenSafeAndGeneratedOtherwise() throws Exception {
		mvc.perform(get("/auth/me").header("X-Request-Id", "abc-123"))
				.andExpect(header().string("X-Request-Id", "abc-123"))
				.andExpect(jsonPath("$.requestId").value("abc-123"));

		mvc.perform(get("/auth/me").header("X-Request-Id", "bad id with spaces"))
				.andExpect(header().string("X-Request-Id", not("bad id with spaces")))
				.andExpect(header().string("X-Request-Id", notNullValue()));
	}

	@Test
	void healthAndApiDocsArePublic() throws Exception {
		mvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.db.status").value("UP"))
				.andExpect(jsonPath("$.components.db.details").doesNotExist());
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/auth/login']").exists())
				.andExpect(jsonPath("$.components.securitySchemes['bearer-jwt']").exists());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void unknownPathIsNotFoundProblemForAuthenticatedUser() throws Exception {
		var token = tokenOf(existingUser(Role.SOLICITANTE, null));

		mvc.perform(get("/nao-existe").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.requestId").exists());
	}

}

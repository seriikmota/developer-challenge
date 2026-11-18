package dev.challenge.purchase.infrastructure.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.infrastructure.persistence.request.PurchaseRequestRepository;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

class IdempotencyApiTest extends ApiTestSupport {

	@Autowired
	private PurchaseRequestRepository requests;

	private Login owner;

	private String key;

	@BeforeEach
	void setUp() {
		owner = requester(newCostCenter());
		key = UUID.randomUUID().toString();
	}

	private ResultActions create(Login login, String idempotencyKey, String json) throws Exception {
		return mvc.perform(post("/requests").header("Authorization", login.bearer()).header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private long ownedRequests() {
		return requests.findAll().stream().filter(request -> request.getOwnerId().equals(owner.id())).count();
	}

	@Test
	void samePayloadReturnsTheSameResponseWithoutCreatingAnother() throws Exception {
		var first = create(owner, key, body("Monitores", "EQUIPMENT", 2, "899.90"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andReturn().getResponse().getContentAsString();
		// same content with other formatting and an equivalent amount
		var replay = create(owner, key, body("Monitores", "EQUIPMENT", 2, "899.9").replace("  ", " "))
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andReturn().getResponse().getContentAsString();

		assertThat((Object) JsonPath.read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
		assertThat(replay).isEqualTo(first);
		assertThat(ownedRequests()).isEqualTo(1);
	}

	@Test
	void sameKeyWithAnotherPayloadIsConflict() throws Exception {
		create(owner, key, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isCreated());

		create(owner, key, body("Monitores", "EQUIPMENT", 3, "899.90"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
		assertThat(ownedRequests()).isEqualTo(1);
	}

	@Test
	void keysAreScopedByUser() throws Exception {
		var otherUser = requester(newCostCenter());

		create(owner, key, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(header().string("Idempotent-Replayed", "false"));
		create(otherUser, key, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "false"));
	}

	@Test
	void withoutKeyEveryCallCreates() throws Exception {
		createRequest(owner, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotent-Replayed"));
		createRequest(owner, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isCreated());

		assertThat(ownedRequests()).isEqualTo(2);
	}

	@Test
	void invalidRequestDoesNotConsumeTheKey() throws Exception {
		create(owner, key, body("", "EQUIPMENT", 2, "899.90")).andExpect(status().isBadRequest());

		create(owner, key, body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isCreated()).andExpect(header().string("Idempotent-Replayed", "false"));
	}

	@Test
	void tooLongKeyIsBadRequest() throws Exception {
		create(owner, "x".repeat(101), body("Monitores", "EQUIPMENT", 2, "899.90")).andExpect(status().isBadRequest());
	}

}

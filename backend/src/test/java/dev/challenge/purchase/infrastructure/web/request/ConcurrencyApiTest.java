package dev.challenge.purchase.infrastructure.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jayway.jsonpath.JsonPath;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

/**
 * Real races against PostgreSQL: every call waits on a latch and all of them are released at once, each on its own
 * thread and therefore on its own transaction and connection. Repeated to make an accidental pass unlikely.
 */
class ConcurrencyApiTest extends ApiTestSupport {

	private static final int REPETITIONS = 3;

	private CostCenterEntity costCenter;

	private Login owner;

	@BeforeEach
	void setUp() {
		costCenter = newCostCenter();
		owner = requester(costCenter);
	}

	/**
	 * Runs every call at the same moment and returns the responses in the order of the calls.
	 */
	private static List<MockHttpServletResponse> race(List<Callable<MockHttpServletResponse>> calls) throws Exception {
		var start = new CountDownLatch(1);
		var ready = new CountDownLatch(calls.size());
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			var futures = new ArrayList<Future<MockHttpServletResponse>>();
			for (var call : calls) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return call.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var responses = new ArrayList<MockHttpServletResponse>();
			for (var future : futures) {
				responses.add(future.get(30, TimeUnit.SECONDS));
			}
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private Callable<MockHttpServletResponse> approveCall(Login approver, long id, long lockVersion) {
		return () -> transition(approver, id, "approve", lockVersion, null).andReturn().getResponse();
	}

	private static long count(List<MockHttpServletResponse> responses, int status) {
		return responses.stream().filter(response -> response.getStatus() == status).count();
	}

	private static String code(MockHttpServletResponse response) throws Exception {
		return JsonPath.read(response.getContentAsString(), "$.code");
	}

	@RepeatedTest(REPETITIONS)
	void twoApprovalsOfTheSameStepEndWithOneSuccessAndOneConflict() throws Exception {
		defineBudget(costCenter, "10000.00");
		var firstApprover = approver(ApproverLevel.NIVEL_1, costCenter);
		var secondApprover = approver(ApproverLevel.NIVEL_1, costCenter);
		var id = draft(owner, "SERVICES", 1, "5000.00");
		act(owner, id, "submit", null);
		var lockVersion = lockVersion(owner, id);

		var responses = race(List.of(approveCall(firstApprover, id, lockVersion), approveCall(secondApprover, id, lockVersion)));

		assertThat(count(responses, 200)).isEqualTo(1);
		assertThat(count(responses, 409)).isEqualTo(1);
		var conflict = responses.stream().filter(response -> response.getStatus() == 409).findFirst().orElseThrow();
		assertThat(code(conflict)).isEqualTo("CONCURRENT_MODIFICATION");

		// exactly one decision was recorded: step 1 approved, step 2 still pending, one APPROVE event
		getRequest(owner, id)
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.version.steps[0].status").value("APPROVED"))
				.andExpect(jsonPath("$.version.steps[1].status").value("PENDING"));
		var history = mvc.perform(get("/requests/" + id + "/history").header("Authorization", owner.bearer())).andReturn().getResponse().getContentAsString();
		List<String> approvals = JsonPath.read(history, "$[?(@.action == 'APPROVE')].action");
		assertThat(approvals).hasSize(1);
	}

	@RepeatedTest(REPETITIONS)
	void finalApprovalsOfDifferentRequestsNeverOverdrawTheBudget() throws Exception {
		defineBudget(costCenter, "3000.00");
		var requests = 6;
		var calls = new ArrayList<Callable<MockHttpServletResponse>>();
		for (var i = 0; i < requests; i++) {
			// a single-step chain (NIVEL_1) makes every approval the final one
			var id = draft(owner, "SUPPLIES", 1, "1000.00");
			act(owner, id, "submit", null);
			calls.add(approveCall(approver(ApproverLevel.NIVEL_1, costCenter), id, lockVersion(owner, id)));
		}

		var responses = race(calls);

		assertThat(count(responses, 200)).isEqualTo(3);
		assertThat(count(responses, 422)).isEqualTo(3);
		for (var response : responses) {
			if (response.getStatus() == 422) {
				assertThat(code(response)).isEqualTo("BUDGET_EXCEEDED");
			}
		}
		var budget = budgets.findByCostCenterIdAndMonth(costCenter.getId(), currentMonth()).orElseThrow();
		assertThat(budget.getConsumed()).isEqualTo(Money.of("3000.00"));
		assertThat(budget.getAvailable()).isEqualTo(Money.ZERO);
	}

	@RepeatedTest(REPETITIONS)
	void finalApprovalsThatDoNotFitAllAreDecidedByTheRemainingBalance() throws Exception {
		// 2500 available: 1500 + 1000 fit together, 1500 + 1500 do not, so one 1500 is always left out
		defineBudget(costCenter, "2500.00");
		var amounts = List.of("1500.00", "1500.00", "1000.00");
		var calls = new ArrayList<Callable<MockHttpServletResponse>>();
		for (var amount : amounts) {
			var id = draft(owner, "SUPPLIES", 1, amount);
			act(owner, id, "submit", null);
			// above 1000.00 the chain has two steps: approve NIVEL_1 first so the race is on the final step
			if (Money.of(amount).isGreaterThan(Money.of("1000.00"))) {
				act(approver(ApproverLevel.NIVEL_1, costCenter), id, "approve", null);
				calls.add(approveCall(approver(ApproverLevel.NIVEL_2, costCenter), id, lockVersion(owner, id)));
			}
			else {
				calls.add(approveCall(approver(ApproverLevel.NIVEL_1, costCenter), id, lockVersion(owner, id)));
			}
		}

		var responses = race(calls);

		var consumed = Money.ZERO;
		for (var i = 0; i < responses.size(); i++) {
			var response = responses.get(i);
			assertThat(response.getStatus()).isIn(200, 422);
			if (response.getStatus() == 200) {
				consumed = consumed.plus(Money.of(amounts.get(i)));
			}
			else {
				assertThat(code(response)).isEqualTo("BUDGET_EXCEEDED");
			}
		}
		var budget = budgets.findByCostCenterIdAndMonth(costCenter.getId(), currentMonth()).orElseThrow();
		assertThat(budget.getConsumed()).isEqualTo(consumed);
		assertThat(budget.getAvailable().isNegative()).isFalse();
		// whatever the order, exactly one 1500.00 is refused and 2500.00 is consumed
		assertThat(count(responses, 200)).isEqualTo(2);
		assertThat(consumed).isEqualTo(Money.of("2500.00"));
	}

	@RepeatedTest(REPETITIONS)
	void concurrentCreationsWithTheSameIdempotencyKeyCreateOnlyOneRequest() throws Exception {
		var key = "race-" + System.nanoTime();
		var json = body("Compra concorrente", "SUPPLIES", 1, "10.00");
		Callable<MockHttpServletResponse> create = () -> mvc.perform(post("/requests").header("Authorization", owner.bearer()).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();

		var responses = race(List.of(create, create, create));

		assertThat(count(responses, 201)).isEqualTo(3);
		var ids = new ArrayList<Integer>();
		for (var response : responses) {
			ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
		}
		assertThat(ids).containsOnly(ids.get(0));
		assertThat(responses.stream().filter(response -> "true".equals(response.getHeader("Idempotent-Replayed"))).count()).isEqualTo(2);
		mvc.perform(get("/requests?costCenterId=" + costCenter.getId()).header("Authorization", owner.bearer())).andExpect(jsonPath("$.totalElements").value(1));
	}

}

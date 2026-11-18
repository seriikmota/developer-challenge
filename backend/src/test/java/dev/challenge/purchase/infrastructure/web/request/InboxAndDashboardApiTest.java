package dev.challenge.purchase.infrastructure.web.request;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

class InboxAndDashboardApiTest extends ApiTestSupport {

	private CostCenterEntity costCenter;

	private Login owner;

	private Login approverL1;

	private Login approverL2;

	@BeforeEach
	void setUp() {
		costCenter = newCostCenter();
		owner = requester(costCenter);
		approverL1 = approver(ApproverLevel.NIVEL_1, costCenter);
		approverL2 = approver(ApproverLevel.NIVEL_2, costCenter);
	}

	private ResultActions inbox(Login login) throws Exception {
		return mvc.perform(get("/approvals/inbox?size=100").header("Authorization", login.bearer()));
	}

	@Test
	void inboxListsOnlyWhatTheUserCanApproveNow() throws Exception {
		var small = draft(owner, "SUPPLIES", 1, "100.00");
		var large = draft(owner, "SERVICES", 1, "5000.00");
		var draftOnly = draft(owner, "SUPPLIES", 1, "50.00");
		act(owner, small, "submit", null);
		act(owner, large, "submit", null);

		inbox(approverL1).andExpect(status().isOk()).andExpect(jsonPath("$.data[*].id", contains((int) small, (int) large)));
		inbox(approverL2).andExpect(jsonPath("$.data[*].id", contains((int) small, (int) large)));

		// after L2 approves NIVEL_1 of the large one, only another NIVEL_2 (or ADMIN) may decide NIVEL_2
		act(approverL2, large, "approve", null);
		inbox(approverL1).andExpect(jsonPath("$.data[*].id", contains((int) small)));
		inbox(approverL2).andExpect(jsonPath("$.data[*].id", contains((int) small)));
		inbox(approver(ApproverLevel.NIVEL_2, costCenter)).andExpect(jsonPath("$.data[*].id", contains((int) small, (int) large)));
		inbox(approver(ApproverLevel.NIVEL_2, newCostCenter())).andExpect(jsonPath("$.data[*].id", not(hasItem((int) small))));
		inbox(owner).andExpect(jsonPath("$.totalElements").value(0));
		inbox(admin()).andExpect(jsonPath("$.data[*].id", hasItem((int) large))).andExpect(jsonPath("$.data[*].id", not(hasItem((int) draftOnly))));
	}

	@Test
	void dashboardCountsByStatusInScopeAndShowsCurrentBudget() throws Exception {
		defineBudget(costCenter, "1000.00");
		var approved = draft(owner, "SUPPLIES", 2, "150.00");
		act(owner, approved, "submit", null);
		act(approverL1, approved, "approve", null);
		draft(owner, "SUPPLIES", 1, "10.00");

		mvc.perform(get("/dashboard/summary").header("Authorization", owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalsByStatus.APPROVED").value(1))
				.andExpect(jsonPath("$.totalsByStatus.DRAFT").value(1))
				.andExpect(jsonPath("$.totalsByStatus.REJECTED").value(0))
				.andExpect(jsonPath("$.month").value(currentMonth().toString()))
				.andExpect(jsonPath("$.budgets.length()").value(1))
				.andExpect(jsonPath("$.budgets[0].costCenterCode").value(costCenter.getCode()))
				.andExpect(jsonPath("$.budgets[0].amount").value("1000.00"))
				.andExpect(jsonPath("$.budgets[0].consumed").value("300.00"))
				.andExpect(jsonPath("$.budgets[0].available").value("700.00"));

		// another requester of the same cost center does not see the owner's requests
		mvc.perform(get("/dashboard/summary").header("Authorization", requester(costCenter).bearer()))
				.andExpect(jsonPath("$.totalsByStatus.APPROVED").value(0))
				.andExpect(jsonPath("$.budgets[0].available").value("700.00"));
	}

}

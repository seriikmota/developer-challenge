package dev.challenge.purchase.infrastructure.web.request;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import dev.challenge.purchase.domain.budget.CompetenceMonth;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

/**
 * Listing with visibility, filters, sorting and pagination. Every query filters by the test's own cost center, so
 * data created by other tests never interferes.
 */
class RequestQueryApiTest extends ApiTestSupport {

	private CostCenterEntity costCenter;

	private Login owner;

	private Login otherOwner;

	private Login approverL1;

	private long draftId;

	private long pendingSoftwareId;

	private long pendingOtherOwnerId;

	@BeforeEach
	void setUp() throws Exception {
		costCenter = newCostCenter();
		owner = requester(costCenter);
		otherOwner = requester(costCenter);
		approverL1 = approver(ApproverLevel.NIVEL_1, costCenter);
		draftId = draft(owner, "EQUIPMENT", 1, "100.00");
		pendingSoftwareId = draft(owner, "SOFTWARE", 2, "750.00");
		act(owner, pendingSoftwareId, "submit", null);
		pendingOtherOwnerId = draft(otherOwner, "SERVICES", 1, "5000.00");
		act(otherOwner, pendingOtherOwnerId, "submit", null);
	}

	private ResultActions list(Login login, String query) throws Exception {
		return mvc.perform(get("/requests?costCenterId=" + costCenter.getId() + (query.isEmpty() ? "" : "&" + query)).header("Authorization", login.bearer()));
	}

	@Test
	void requesterSeesOnlyOwnRequests() throws Exception {
		list(owner, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].id", containsInAnyOrder((int) draftId, (int) pendingSoftwareId)))
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void approverSeesCostCenterRequestsExceptOthersDrafts() throws Exception {
		list(approverL1, "")
				.andExpect(jsonPath("$.data[*].id", containsInAnyOrder((int) pendingSoftwareId, (int) pendingOtherOwnerId)));
	}

	@Test
	void adminSeesEverythingButOthersDrafts() throws Exception {
		list(admin(), "").andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void filtersByStatusCategoryAndAmount() throws Exception {
		list(approverL1, "status=PENDING_APPROVAL&category=SOFTWARE").andExpect(jsonPath("$.data[*].id", contains((int) pendingSoftwareId)));
		list(approverL1, "minAmount=1500.00&maxAmount=1500").andExpect(jsonPath("$.data[*].id", contains((int) pendingSoftwareId)));
		list(approverL1, "minAmount=1500.01").andExpect(jsonPath("$.data[*].id", contains((int) pendingOtherOwnerId)));
	}

	@Test
	void filtersByCreationDateInSaoPauloDays() throws Exception {
		var today = LocalDate.now(CompetenceMonth.ZONE);
		list(owner, "createdFrom=" + today + "&createdTo=" + today).andExpect(jsonPath("$.totalElements").value(2));
		list(owner, "createdTo=" + today.minusDays(1)).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void searchesTitleAndDescription() throws Exception {
		list(owner, "q=COMPRA DE").andExpect(jsonPath("$.totalElements").value(2));
		list(owner, "q=inexistente%25").andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void paginatesAndSorts() throws Exception {
		list(owner, "size=1&page=0&sort=createdAt,asc")
				.andExpect(jsonPath("$.data[0].id").value((int) draftId))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2));
		list(owner, "size=1&page=1&sort=createdAt,asc").andExpect(jsonPath("$.data[0].id").value((int) pendingSoftwareId));
		list(owner, "sort=createdAt,desc").andExpect(jsonPath("$.data[0].id").value((int) pendingSoftwareId));
	}

	@Test
	void listRowsCarryCurrentVersionData() throws Exception {
		list(owner, "category=SOFTWARE")
				.andExpect(jsonPath("$.data[0].title").value("Compra de teste"))
				.andExpect(jsonPath("$.data[0].total").value("1500.00"))
				.andExpect(jsonPath("$.data[0].status").value("PENDING_APPROVAL"))
				.andExpect(jsonPath("$.data[0].ownerName").value(owner.user().getName()));
	}

	@Test
	void rejectsInvalidQueryParameters() throws Exception {
		list(owner, "sort=title,asc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sort"));
		list(owner, "size=101").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("size"));
		list(owner, "status=WHATEVER").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
		list(owner, "createdFrom=18/11/2026").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("createdFrom"));
	}

}

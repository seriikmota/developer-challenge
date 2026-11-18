package dev.challenge.purchase.infrastructure.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.challenge.purchase.domain.budget.Money;
import dev.challenge.purchase.domain.user.ApproverLevel;
import dev.challenge.purchase.infrastructure.persistence.costcenter.CostCenterEntity;
import dev.challenge.purchase.infrastructure.web.ApiTestSupport;

/**
 * The longest chain (NIVEL_1, NIVEL_2, ADMIN) from draft to APPROVED, with budget consumption on the last step and the
 * resulting history protected by the append-only trigger.
 */
class FullApprovalFlowApiTest extends ApiTestSupport {

	@Autowired
	private JdbcTemplate jdbc;

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
		defineBudget(costCenter, "20000.00");
	}

	private Money consumed() {
		return budgets.findByCostCenterIdAndMonth(costCenter.getId(), currentMonth()).orElseThrow().getConsumed();
	}

	@Test
	void threeStepChainEndsApprovedAndConsumesBudgetOnlyOnTheLastStep() throws Exception {
		var id = draft(owner, "EQUIPMENT", 3, "4000.00");
		act(owner, id, "submit", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version.total").value("12000.00"))
				.andExpect(jsonPath("$.version.steps[*].level", contains("NIVEL_1", "NIVEL_2", "ADMIN")));

		act(approverL1, id, "approve", null).andExpect(jsonPath("$.version.steps[*].status", contains("APPROVED", "PENDING", "PENDING")));
		act(approverL2, id, "approve", null).andExpect(jsonPath("$.version.steps[*].status", contains("APPROVED", "APPROVED", "PENDING")));
		assertThat(consumed()).isEqualTo(Money.ZERO);

		act(admin, id, "approve", "Aprovado pela diretoria.")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.version.steps[*].status", contains("APPROVED", "APPROVED", "APPROVED")))
				.andExpect(jsonPath("$.allowedActions", empty()));
		assertThat(consumed()).isEqualTo(Money.of("12000.00"));

		mvc.perform(get("/requests/" + id + "/history").header("Authorization", owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].action", contains("CREATE", "SUBMIT", "APPROVE", "APPROVE", "APPROVE")))
				.andExpect(jsonPath("$[*].actorId", contains(owner.id().intValue(), owner.id().intValue(), approverL1.id().intValue(), approverL2.id().intValue(), admin.id().intValue())));
		mvc.perform(get("/cost-centers/" + costCenter.getId() + "/budgets/" + currentMonth()).header("Authorization", owner.bearer()))
				.andExpect(jsonPath("$.consumed").value("12000.00"))
				.andExpect(jsonPath("$.available").value("8000.00"));
	}

	@Test
	void historyOfARealFlowCannotBeRewritten() throws Exception {
		var id = draft(owner, "SUPPLIES", 1, "50.00");
		act(owner, id, "submit", null);

		assertThatThrownBy(() -> jdbc.update("UPDATE request_event SET comment = 'adulterado' WHERE request_id = ?", id))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("request_event is append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM request_event WHERE request_id = ?", id))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("request_event is append-only");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM request_event WHERE request_id = ?", Long.class, id)).isEqualTo(2L);
	}

}

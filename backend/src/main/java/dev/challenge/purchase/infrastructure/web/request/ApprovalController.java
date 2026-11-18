package dev.challenge.purchase.infrastructure.web.request;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.challenge.purchase.application.approval.InboxService;
import dev.challenge.purchase.application.common.PageView;
import dev.challenge.purchase.application.dashboard.DashboardService;
import dev.challenge.purchase.application.request.RequestViews;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.web.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Caixa de aprovação e dashboard")
public class ApprovalController {

	private final InboxService inbox;

	private final DashboardService dashboard;

	public ApprovalController(InboxService inbox, DashboardService dashboard) {
		this.inbox = inbox;
		this.dashboard = dashboard;
	}

	@GetMapping("/approvals/inbox")
	@Operation(summary = "Solicitações cuja etapa atual o usuário pode aprovar agora", description = "Já aplica centro de custo, nível e segregação de funções. Mais antigas primeiro.")
	public PageView<RequestViews.Summary> inbox(Actor actor, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		var paging = Paging.of(page, size);
		return inbox.inbox(actor, paging.page(), paging.size());
	}

	@GetMapping("/dashboard/summary")
	@Operation(summary = "Totais por status e orçamento do mês corrente, no escopo do papel")
	public DashboardService.DashboardView summary(Actor actor) {
		return dashboard.summary(actor);
	}

}

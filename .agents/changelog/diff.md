# AI Agent Changelog

> This file is written **automatically** by `.agents/hooks/ai-log.sh` (repository hooks for
> Claude Code and Cursor; explicit commands for every other agent — see `AGENTS.md`).
>
> Purpose: let the evaluators distinguish the candidate's own work from work delegated to AI.
>
> - **Append-only.** Never edit, reorder or delete existing entries. This is audited from the git history.
> - **All entries must be written in English.**
> - Never write secrets or personal data here.

---

## Entry format

One block per agent turn, one row per changed file:

```
---
### Turn YYYY-MM-DD HH:MM:SS ±HHMM · <Agent>
- **Source**: hook | command | manual
- **Session**: <agent session id or n/a>
- **Prompt**: <user prompt, truncated, secrets masked>
- **Summary**: <one-line summary written by the agent>

| File | Change | Additions | Removals |
|---|---|---|---|
| `relative/path/to/file` | created / modified / deleted | +N | -N |
```

`Source: manual` is only for entries appended by hand when the logger cannot run.

---

## Change log

<!-- AI agent entries will be inserted below this line -->

---
### Turn 2026-11-17 18:39:58 -0300 · ClaudeCode
- **Source**: command
- **Session**: 92da3de5-81c0-495f-b50f-a93d5b8b13f4
- **Prompt**: pode começar a fase 1
- **Summary**: Phase 1 scaffold: Spring Boot 4.1.2 backend (Java 21, Maven wrapper, springdoc, ArchUnit), application.yml with env vars, Postgres compose, .env.example, backend CI workflow, ADR index

| File | Change | Additions | Removals |
|---|---|---|---|
| `.env.example` | created | +16 | -0 |
| `.github/workflows/ci.yml` | created | +27 | -0 |
| `backend/.gitattributes` | created | +2 | -0 |
| `backend/.gitignore` | created | +33 | -0 |
| `backend/.mvn/wrapper/maven-wrapper.properties` | created | +3 | -0 |
| `backend/mvnw` | created | +295 | -0 |
| `backend/mvnw.cmd` | created | +189 | -0 |
| `backend/pom.xml` | created | +132 | -0 |
| `backend/src/main/java/dev/challenge/purchase/PurchaseRequestsApplication.java` | created | +13 | -0 |
| `backend/src/main/resources/application.yml` | created | +47 | -0 |
| `backend/src/test/java/dev/challenge/purchase/PurchaseRequestsApplicationTests.java` | created | +15 | -0 |
| `backend/src/test/java/dev/challenge/purchase/TestPurchaseRequestsApplication.java` | created | +11 | -0 |
| `backend/src/test/java/dev/challenge/purchase/TestcontainersConfiguration.java` | created | +18 | -0 |
| `docker-compose.yml` | created | +20 | -0 |
| `docs/adr/README.md` | created | +12 | -0 |

---
### Turn 2026-11-17 18:44:59 -0300 · ClaudeCode
- **Source**: hook
- **Session**: 92da3de5-81c0-495f-b50f-a93d5b8b13f4
- **Prompt**: adicione os arquivos que você modificou no git add
- **Summary**: n/a

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/mvnw` | modified | +0 | -0 |

---
### Turn 2026-11-17 19:29:33 -0300 · ClaudeCode
- **Source**: command
- **Session**: 92da3de5-81c0-495f-b50f-a93d5b8b13f4
- **Prompt**: Pode começar
- **Summary**: Phase 2 pure domain: Money, CompetenceMonth, enums, DomainException, ApprovalChainFactory, ApprovalEligibilityPolicy, DecisionCommentRule, RequestStateMachine with parameterized tests, ArchUnit rule, ADRs 004 and 005

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/dev/challenge/purchase/domain/approval/ApprovalChainFactory.java` | created | +44 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/ApprovalContext.java` | created | +23 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/ApprovalEligibilityPolicy.java` | created | +89 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/DecisionCommentRule.java` | created | +30 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/Eligibility.java` | created | +28 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/StepApproval.java` | created | +15 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/StepLevel.java` | created | +10 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/budget/CompetenceMonth.java` | created | +46 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/budget/Money.java` | created | +82 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/error/DomainException.java` | created | +21 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/error/ErrorCode.java` | created | +16 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/Action.java` | created | +33 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/Category.java` | created | +10 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestStateMachine.java` | created | +96 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestStatus.java` | created | +14 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/user/Actor.java` | created | +24 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/user/ApproverLevel.java` | created | +6 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/user/Role.java` | created | +15 | -0 |
| `backend/src/test/java/dev/challenge/purchase/architecture/DomainArchitectureTest.java` | created | +34 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/approval/ApprovalChainFactoryTest.java` | created | +58 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/approval/ApprovalEligibilityPolicyTest.java` | created | +170 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/approval/DecisionCommentRuleTest.java` | created | +44 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/budget/CompetenceMonthTest.java` | created | +40 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/budget/MoneyTest.java` | created | +76 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/request/RequestStateMachineTest.java` | created | +121 | -0 |
| `docs/adr/004-monetary-representation.md` | created | +32 | -0 |
| `docs/adr/005-layered-architecture.md` | created | +46 | -0 |
| `docs/adr/README.md` | modified | +2 | -2 |

---
### Turn 2026-11-17 19:37:41 -0300 · ClaudeCode
- **Source**: command
- **Session**: 92da3de5-81c0-495f-b50f-a93d5b8b13f4
- **Prompt**: Faça um ajuste, em casos de mensagem muito grande, tente colocar apenas em uma linha o codigo, exemplo ruim: <pasted_content id="4762"> return Eligibility.denied(ErrorCode.DUPLICATE_APPROVER, prefix + "você já aprovou a etapa " + previous.get().level() + " desta versão (segregação de funções)."); </pasted_content id="4762">
- **Summary**: Put long user-facing message statements on a single line in domain code and tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/dev/challenge/purchase/domain/approval/ApprovalEligibilityPolicy.java` | modified | +7 | -17 |
| `backend/src/main/java/dev/challenge/purchase/domain/approval/DecisionCommentRule.java` | modified | +1 | -3 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestStateMachine.java` | modified | +2 | -4 |
| `backend/src/test/java/dev/challenge/purchase/domain/approval/ApprovalEligibilityPolicyTest.java` | modified | +2 | -4 |
| `backend/src/test/java/dev/challenge/purchase/domain/request/RequestStateMachineTest.java` | modified | +2 | -4 |

---
### Turn 2026-11-18 09:04:57 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Phase 3: Flyway schema with append-only history and immutable-version triggers, JPA entities, repositories with atomic budget updates, mappers, Testcontainers persistence tests and ADR 002

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/dev/challenge/purchase/domain/approval/StepStatus.java` | created | +11 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/LineItem.java` | created | +33 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestEventType.java` | created | +23 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/budget/MonthlyBudgetEntity.java` | created | +79 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/budget/MonthlyBudgetRepository.java` | created | +65 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/converter/CompetenceMonthConverter.java` | created | +26 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/converter/MoneyConverter.java` | created | +25 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/costcenter/CostCenterEntity.java` | created | +54 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/costcenter/CostCenterRepository.java` | created | +13 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/history/RequestEventEntity.java` | created | +133 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/history/RequestEventRepository.java` | created | +11 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/idempotency/IdempotencyKeyEntity.java` | created | +89 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/idempotency/IdempotencyKeyRepository.java` | created | +11 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/ApprovalContextMapper.java` | created | +28 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/ApprovalStepEntity.java` | created | +109 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/PurchaseRequestEntity.java` | created | +164 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/PurchaseRequestRepository.java` | created | +8 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/RequestItemEntity.java` | created | +82 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/RequestVersionEntity.java` | created | +211 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/user/ActorMapper.java` | created | +17 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/user/UserEntity.java` | created | +111 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/user/UserRepository.java` | created | +13 | -0 |
| `backend/src/main/resources/db/migration/V1__create_users_cost_centers_and_budgets.sql` | created | +49 | -0 |
| `backend/src/main/resources/db/migration/V2__create_purchase_requests.sql` | created | +120 | -0 |
| `backend/src/main/resources/db/migration/V3__create_request_event.sql` | created | +39 | -0 |
| `backend/src/main/resources/db/migration/V4__create_idempotency_key.sql` | created | +16 | -0 |
| `backend/src/test/java/dev/challenge/purchase/TestcontainersConfiguration.java` | modified | +1 | -1 |
| `backend/src/test/java/dev/challenge/purchase/domain/request/LineItemTest.java` | created | +45 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/request/RequestEventTypeTest.java` | created | +16 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/persistence/DatabaseGuaranteesTest.java` | created | +193 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/persistence/PersistenceIntegrationTest.java` | created | +87 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/persistence/budget/MonthlyBudgetRepositoryTest.java` | created | +97 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/persistence/request/PurchaseRequestRepositoryTest.java` | created | +179 | -0 |
| `docs/adr/002-versions-and-history.md` | created | +59 | -0 |
| `docs/adr/005-layered-architecture.md` | modified | +1 | -1 |
| `docs/adr/README.md` | modified | +1 | -1 |

---
### Turn 2026-11-18 10:04:08 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Phase 4: JWT auth (register, login, me) with BCrypt, RequestIdFilter, ProblemDetail advice for all errors, public health and Swagger, ArchUnit rule for application layer, integration tests

| File | Change | Additions | Removals |
|---|---|---|---|
| `.env.example` | modified | +6 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/auth/AccessToken.java` | created | +7 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/auth/AccessTokenIssuer.java` | created | +12 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/auth/AuthService.java` | created | +92 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/ApplicationException.java` | created | +22 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/AuthenticationFailedException.java` | created | +12 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/ConflictException.java` | created | +13 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/ForbiddenException.java` | created | +12 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/InvalidInputException.java` | created | +28 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/error/NotFoundException.java` | created | +12 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/config/ClockConfig.java` | created | +20 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/CurrentActorResolver.java` | created | +41 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/JwtAccessTokenIssuer.java` | created | +55 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/JwtClaimNames.java` | created | +17 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/JwtConfig.java` | created | +70 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/JwtProperties.java` | created | +24 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/ProblemSecurityHandler.java` | created | +34 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/SecurityConfig.java` | created | +66 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/OpenApiConfig.java` | created | +36 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/RequestIdFilter.java` | created | +61 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/WebConfig.java` | created | +19 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/auth/AuthController.java` | created | +87 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/error/ProblemDetailAdvice.java` | created | +164 | -0 |
| `backend/src/main/resources/application.yml` | modified | +15 | -1 |
| `backend/src/test/java/dev/challenge/purchase/IntegrationTest.java` | created | +25 | -0 |
| `backend/src/test/java/dev/challenge/purchase/PurchaseRequestsApplicationTests.java` | modified | +1 | -4 |
| `backend/src/test/java/dev/challenge/purchase/architecture/DomainArchitectureTest.java` | modified | +6 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/persistence/PersistenceIntegrationTest.java` | modified | +2 | -5 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/auth/AuthApiTest.java` | created | +265 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/error/ErrorProbeController.java` | created | +34 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/error/ProblemDetailAdviceTest.java` | created | +59 | -0 |

---
### Turn 2026-11-18 10:25:00 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Phase 5: use cases (admin users, cost centers, budgets; requests create with idempotency, edit, list, detail, versions, transitions, history; inbox; dashboard), RequestActionPolicy as single source of allowedActions, API tests and ADRs 001, 003, 006

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/main/java/dev/challenge/purchase/application/approval/InboxService.java` | created | +54 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/common/Correlation.java` | created | +19 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/common/PageView.java` | created | +27 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/common/UserDirectory.java` | created | +38 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/costcenter/BudgetView.java` | created | +15 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/costcenter/CostCenterService.java` | created | +88 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/costcenter/CostCenterView.java` | created | +11 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/dashboard/DashboardService.java` | created | +97 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/HistoryRecorder.java` | created | +31 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/IdempotentRequestCreator.java` | created | +104 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestCommand.java` | created | +20 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestFilter.java` | created | +65 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestGate.java` | created | +56 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestService.java` | created | +159 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestViewMapper.java` | created | +102 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestViews.java` | created | +59 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestVisibility.java` | created | +53 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/request/RequestWorkflowService.java` | created | +149 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/user/UserAdminService.java` | created | +106 | -0 |
| `backend/src/main/java/dev/challenge/purchase/application/user/UserView.java` | created | +16 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestActionPolicy.java` | created | +59 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestSnapshot.java` | created | +21 | -0 |
| `backend/src/main/java/dev/challenge/purchase/domain/request/RequestStateMachine.java` | modified | +12 | -7 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/config/DomainConfig.java` | created | +37 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/budget/MonthlyBudgetRepository.java` | modified | +3 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/PurchaseRequestRepository.java` | modified | +13 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/persistence/request/RequestSnapshotMapper.java` | created | +17 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/security/SecurityConfig.java` | modified | +2 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/Paging.java` | created | +29 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/RequestIdFilter.java` | modified | +2 | -1 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/admin/CostCenterController.java` | created | +92 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/admin/UserController.java` | created | +79 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/auth/AuthController.java` | modified | +7 | -20 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/error/ProblemDetailAdvice.java` | modified | +18 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/request/ApprovalController.java` | created | +42 | -0 |
| `backend/src/main/java/dev/challenge/purchase/infrastructure/web/request/RequestController.java` | created | +228 | -0 |
| `backend/src/main/resources/application.yml` | modified | +2 | -0 |
| `backend/src/test/java/dev/challenge/purchase/domain/request/RequestActionPolicyTest.java` | created | +127 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/ApiTestSupport.java` | created | +142 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/admin/AdminApiTest.java` | created | +145 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/IdempotencyApiTest.java` | created | +100 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/InboxAndDashboardApiTest.java` | created | +87 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/RequestLifecycleApiTest.java` | created | +341 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/RequestQueryApiTest.java` | created | +125 | -0 |
| `docs/adr/001-concurrency-strategy.md` | created | +62 | -0 |
| `docs/adr/003-forbidden-vs-not-found.md` | created | +49 | -0 |
| `docs/adr/006-idempotency.md` | created | +44 | -0 |
| `docs/adr/README.md` | modified | +3 | -3 |

---
### Turn 2026-11-18 11:42:56 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Phase 6: full three-step approval flow, append-only history check and real concurrency tests (same-step approvals, shared budget, idempotency key race)

| File | Change | Additions | Removals |
|---|---|---|---|
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/ConcurrencyApiTest.java` | created | +198 | -0 |
| `backend/src/test/java/dev/challenge/purchase/infrastructure/web/request/FullApprovalFlowApiTest.java` | created | +97 | -0 |

---
### Turn 2026-11-18 12:01:19 -0300 · ClaudeCode
- **Source**: command
- **Session**: n/a
- **Prompt**: n/a
- **Summary**: Phase 7: idempotent repeatable demo seed (current-month budgets), seed consistency test, multi-stage backend Dockerfile and backend README

| File | Change | Additions | Removals |
|---|---|---|---|
| `.env.example` | modified | +2 | -0 |
| `backend/.dockerignore` | created | +7 | -0 |
| `backend/Dockerfile` | created | +33 | -0 |
| `backend/README.md` | created | +175 | -0 |
| `backend/src/main/resources/application.yml` | modified | +2 | -1 |
| `backend/src/main/resources/db/seed/R__seed_demo_data.sql` | created | +291 | -0 |
| `backend/src/test/java/dev/challenge/purchase/IntegrationTest.java` | modified | +2 | -2 |
| `backend/src/test/java/dev/challenge/purchase/SeedDataTest.java` | created | +137 | -0 |

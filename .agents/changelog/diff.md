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

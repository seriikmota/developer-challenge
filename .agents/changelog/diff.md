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

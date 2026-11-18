# Back-end: API de solicitações de compra

API REST em **Java 21 + Spring Boot 4**, com **PostgreSQL 17** e migrations **Flyway**.

As decisões de arquitetura estão em [`docs/adr/`](../docs/adr/README.md).

## Pré-requisitos

| Para | Precisa de |
|---|---|
| Rodar com Docker | Docker 24+ com Compose v2 |
| Rodar localmente | JDK 21 e um PostgreSQL (o do `docker compose` serve). O Maven vem pelo wrapper `./mvnw`. |
| Rodar os testes | JDK 21 e Docker em execução (os testes de integração usam Testcontainers) |

## Variáveis de ambiente

Todas têm valor padrão para desenvolvimento local. A referência completa está em [`.env.example`](../.env.example).

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/purchase` | URL JDBC do PostgreSQL |
| `DB_USERNAME` / `DB_PASSWORD` | `purchase` / `purchase` | Credenciais do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `JWT_SECRET` | vazio | Chave HS256 com no mínimo 32 bytes. Vazia: uma chave aleatória é gerada a cada inicialização, e os tokens deixam de valer quando a aplicação reinicia. |
| `JWT_TTL` | `PT15M` | Validade do token (duração ISO-8601) |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/seed` | Migrations aplicadas. Use só `classpath:db/migration` para subir sem os dados de demonstração. |
| `LOG_FORMAT` | `ecs` | `ecs` para logs JSON estruturados, ou vazio para texto |

## Comandos

```bash
# subir só o banco (na raiz do repositório)
docker compose up -d postgres

# rodar a API em http://localhost:8080
./mvnw spring-boot:run

# testes unitários, de integração (Testcontainers), de concorrência e de arquitetura
./mvnw verify

# empacotar sem testes
./mvnw package -DskipTests

# imagem Docker (multi-stage, JRE, usuário sem privilégios)
docker build -t purchase-backend .
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

A imagem não roda os testes no build, porque eles precisam de Docker para o Testcontainers. Os testes rodam no `./mvnw verify` e no CI.

## Endereços úteis

| Endereço | O que é |
|---|---|
| `GET /health` | Health check, com o status do banco (`db: UP`) |
| `/swagger-ui` | Documentação interativa (OpenAPI) |
| `/v3/api-docs` | Especificação OpenAPI em JSON |

## Dados de demonstração (seed)

O seed é a migration repetível `src/main/resources/db/seed/R__seed_demo_data.sql`. Ela roda a cada inicialização e é idempotente:

- usuários, centros de custo e solicitações são criados só na primeira vez;
- o orçamento do **mês corrente** (fuso `America/Sao_Paulo`) é criado sempre que estiver faltando, então a demonstração funciona em qualquer mês.

Todos os usuários usam a senha **`Senha@123`**.

| E-mail | Papel | Centro de custo |
|---|---|---|
| `solicitante.ti@example.com` | `SOLICITANTE` | TI |
| `aprovador1.ti@example.com` | `APROVADOR` `NIVEL_1` | TI |
| `aprovador2.ti@example.com` | `APROVADOR` `NIVEL_2` | TI |
| `solicitante.mkt@example.com` | `SOLICITANTE` | MKT |
| `aprovador1.mkt@example.com` | `APROVADOR` `NIVEL_1` | MKT |
| `aprovador2.mkt@example.com` | `APROVADOR` `NIVEL_2` | MKT |
| `admin@example.com` | `ADMIN` | — |
| `admin2@example.com` | `ADMIN` | — |

Orçamentos do mês corrente:

- **TI:** R$ 50.000,00, com R$ 800,00 consumidos.
- **MKT:** R$ 5.000,00, com R$ 4.800,00 consumidos. Restam só R$ 200,00, então aprovar a solicitação "Brindes para evento" (R$ 900,00) com `aprovador1.mkt` resulta em `422 BUDGET_EXCEEDED`.

Solicitações de exemplo, em todos os estados:

- rascunhos;
- pendentes em cada nível (`NIVEL_1`, `NIVEL_2` e `ADMIN`);
- aprovadas, rejeitadas, devolvidas para ajuste e canceladas;
- uma com duas versões ("Notebook para estagiário");
- uma de `SOFTWARE` criada pelo `admin@example.com`, cuja etapa `ADMIN` só o `admin2@example.com` pode aprovar.

## Exemplos de requisição

Os exemplos usam `bash` com `curl` e `jq`.

```bash
API=http://localhost:8080

# login: guarda o token
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"solicitante.ti@example.com","password":"Senha@123"}' | jq -r .accessToken)

# usuário logado
curl -s $API/auth/me -H "Authorization: Bearer $TOKEN"

# criar uma solicitação (o total é calculado pelo servidor; o header de idempotência é opcional)
curl -s -X POST $API/requests -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7c1e2a8e-0001' \
  -d '{"title":"Teclados mecânicos","description":"Reposição","category":"EQUIPMENT","neededBy":"2030-01-31",
       "items":[{"description":"Teclado","quantity":3,"unitPrice":"450.00"}]}'

# listar com filtros, paginação e ordenação
curl -s "$API/requests?status=PENDING_APPROVAL&minAmount=1000&sort=createdAt,desc&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

# submeter: toda escrita envia o lockVersion recebido por último
curl -s -X POST $API/requests/42/submit -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"lockVersion":0}'

# como aprovador: caixa de aprovação e aprovação
TOKEN_APR=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"aprovador1.ti@example.com","password":"Senha@123"}' | jq -r .accessToken)
curl -s $API/approvals/inbox -H "Authorization: Bearer $TOKEN_APR"
curl -s -X POST $API/requests/42/approve -H "Authorization: Bearer $TOKEN_APR" -H 'Content-Type: application/json' \
  -d '{"lockVersion":1,"comment":"De acordo."}'

# rejeitar e devolver exigem comentário
curl -s -X POST $API/requests/42/request-changes -H "Authorization: Bearer $TOKEN_APR" -H 'Content-Type: application/json' \
  -d '{"lockVersion":2,"comment":"Informe o fornecedor."}'

# versões e histórico
curl -s $API/requests/42/versions -H "Authorization: Bearer $TOKEN"
curl -s $API/requests/42/history -H "Authorization: Bearer $TOKEN"

# como ADMIN: definir o orçamento de um mês
TOKEN_ADM=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Senha@123"}' | jq -r .accessToken)
curl -s -X PUT $API/cost-centers/1/budgets/2026-12 -H "Authorization: Bearer $TOKEN_ADM" \
  -H 'Content-Type: application/json' -d '{"amount":"60000.00"}'
curl -s $API/dashboard/summary -H "Authorization: Bearer $TOKEN_ADM"
```

Substitua `42` pelo id retornado na criação. O `lockVersion` de cada chamada é o que veio na resposta anterior. Ele é opaco e pode subir mais de 1 por escrita.

## Erros

Todo erro segue o formato `application/problem+json` (RFC 9457) e traz `code`, `title`, `detail` e `requestId`. Erros de validação trazem também a lista `errors[]`, com o campo e a mensagem de cada erro.

A ordem das verificações é: `401` → `404` → `403` → `409` → `422`.

| Status | Quando |
|---|---|
| `400` | Payload ou parâmetro inválido |
| `401` | Sem token ou com token inválido |
| `403` | O papel não permite a ação |
| `404` | A solicitação não existe ou está fora da visibilidade do usuário ([ADR 003](../docs/adr/003-forbidden-vs-not-found.md)) |
| `409` | `CONCURRENT_MODIFICATION` (`lockVersion` desatualizado) ou `IDEMPOTENCY_KEY_REUSED` |
| `422` | Regra de negócio, com `code` específico: `BUDGET_EXCEEDED`, `NOT_OWNER`, `APPROVER_NOT_ELIGIBLE`, `INVALID_TRANSITION` etc. |

O header `X-Request-Id` é aceito na requisição, gerado quando falta e devolvido na resposta. O mesmo id aparece nos logs e no histórico da solicitação.

## Estrutura

```
src/main/java/dev/challenge/purchase/
├── domain/           regras puras (Java, sem Spring/JPA): máquina de estados, cadeia, elegibilidade, dinheiro
├── application/      casos de uso e transações
└── infrastructure/   web (controllers, erros), segurança (JWT), persistência (JPA), configuração
src/main/resources/db/
├── migration/        schema (V1..V4), com triggers de imutabilidade e append-only
└── seed/             dados de demonstração (migration repetível)
```

O ArchUnit garante que o domínio não depende de Spring nem de JPA, e que a camada de aplicação não depende da web ([ADR 005](../docs/adr/005-layered-architecture.md)).

# Solução: solicitações de compra com aprovação por alçada

Sistema de solicitações de compra com orçamento mensal por centro de custo, cadeia de aprovação sequencial, segregação de funções, versionamento das solicitações, controle de concorrência e idempotência.

| Camada | Tecnologia |
|---|---|
| Back-end | Java 21, Spring Boot 4, Spring Security (JWT HS256), JPA/Hibernate, Flyway |
| Banco | PostgreSQL 17 |
| Front-end | Angular 21 (standalone, signals, zoneless), Angular Material |
| Testes | JUnit 5, Testcontainers, ArchUnit, MockMvc; Vitest e Angular Testing Library |
| Entrega | Docker multi-stage, Docker Compose e GitHub Actions |

Leituras complementares:

- [`backend/README.md`](backend/README.md): variáveis, comandos e exemplos com `curl`.
- [`frontend/README.md`](frontend/README.md): telas, decisões e testes do front.
- [`docs/adr/`](docs/adr/README.md): as decisões de arquitetura, com alternativas consideradas.
- [`docs/deploy.md`](docs/deploy.md): o deploy contínuo no servidor.

**Demonstração pública:** https://compras.erikmota.dev (usuários do seed abaixo).

---

## Como rodar

### Tudo com Docker (recomendado)

```bash
docker compose up --build
```

Não há passo manual: as migrations e o seed rodam na subida, e o `.env` é opcional (todas as variáveis têm valor padrão).

| Endereço | O que é |
|---|---|
| http://localhost:4200 | Aplicação web (nginx). O caminho `/api` é repassado ao back-end. |
| http://localhost:8080/swagger-ui | Documentação OpenAPI interativa |
| http://localhost:8080/health | Health check, com o status do banco |

As portas podem ser trocadas com `FRONTEND_PORT`, `BACKEND_PORT` e `POSTGRES_PORT`. Veja [`.env.example`](.env.example).

### Localmente, sem Docker para a aplicação

```bash
docker compose up -d postgres                  # só o banco

cd backend && ./mvnw spring-boot:run           # API em http://localhost:8080
cd frontend && npm ci && npm start             # web em http://localhost:4200 (proxy de /api para :8080)
```

### Testes

```bash
cd backend && ./mvnw verify   # 284 testes: unitários, integração (Testcontainers), concorrência e ArchUnit (exige Docker)
cd frontend && npm test       # 20 testes (Vitest + Testing Library); também: npm run lint, npm run format:check
```

O CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) roda três jobs:

1. **back-end:** `verify`;
2. **front-end:** lint, formatação, testes e build;
3. **compose:** sobe a pilha com `docker compose up --build --wait` e testa a aplicação web, o proxy `/api`, o health check, o login e o seed.

## Usuários do seed

Todos usam a senha **`Senha@123`**. Os dados são fictícios.

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

### Orçamentos

O orçamento é do **mês corrente** (fuso `America/Sao_Paulo`), criado na subida sempre que estiver faltando. A demonstração funciona em qualquer mês.

| Centro de custo | Orçado | Consumido | Disponível |
|---|---|---|---|
| TI | R$ 50.000,00 | R$ 800,00 | R$ 49.200,00 |
| MKT | R$ 5.000,00 | R$ 4.800,00 | R$ 200,00 (quase esgotado) |

### Solicitações de exemplo

São 13 solicitações, com todos os estados. Algumas servem para demonstrar regras específicas:

| Solicitação | Para demonstrar |
|---|---|
| **#12 "Brindes para evento"** (MKT, R$ 900,00) | Ao aprovar com `aprovador1.mkt`, a resposta é `422 BUDGET_EXCEEDED`, com uma mensagem que explica o saldo. |
| **#9 "Notebook para estagiário"** | Tem duas versões: a v1 foi devolvida e continua consultável; a v2 foi reenviada com a cadeia recalculada. |
| **#10 "Ferramenta de observabilidade"** (`SOFTWARE`) | Foi criada pelo `admin@example.com`, então a etapa `ADMIN` só pode ser aprovada pelo `admin2@example.com`. |
| **#4 "Servidor de homologação"** (R$ 15.000,00) | Está na última etapa de uma cadeia de três (`NIVEL_1` → `NIVEL_2` → `ADMIN`). |

---

## Visão geral da solução

### Back-end: hexagonal leve

```
infrastructure  ->  application  ->  domain
(web, JPA,          (casos de uso,    (Java puro: máquina de estados, cadeia,
 segurança, JWT)     transações)       elegibilidade, Money, CompetenceMonth)
```

- **`domain`** não conhece Spring, JPA nem Jackson. O ArchUnit (`DomainArchitectureTest`) quebra o build se isso mudar. As regras de negócio são testadas sem subir servidor nem banco.
- **`RequestActionPolicy`** é a **fonte única** das permissões.
  - Ela combina o estado (`RequestStateMachine`), a propriedade da solicitação e a elegibilidade do aprovador (`ApprovalEligibilityPolicy`: nível, centro de custo e segregação).
  - O mesmo código **valida a ação** e **calcula `allowedActions`**, então os botões do front e a validação da API nunca divergem.
  - Um teste confere, para cada papel e cada ação, que as duas respostas coincidem.
- **`application`** tem um caso de uso por operação. Cada escrita é uma única transação: a mudança na solicitação, o consumo de orçamento e o evento de histórico são gravados juntos ou desfeitos juntos.
- **Erros** seguem o formato `application/problem+json` (RFC 9457), com `code`, `title`, `detail`, `requestId` e `errors[]`. Cada `422` explica o motivo, por exemplo: *"Não é possível aprovar: o centro de custo MKT tem R$ 200,00 disponíveis em 11/2026; a solicitação exige R$ 900,00."*
- **Ordem das verificações:** `401` → `404` (visibilidade) → `403` (papel) → `409` (`lockVersion`) → `422` (regra).
- **Logs** são JSON estruturados (ECS). O `X-Request-Id` é aceito na requisição, gerado quando falta, devolvido na resposta, registrado no log e gravado no evento de histórico.

### Modelo de dados

| Tabela | Papel |
|---|---|
| `purchase_request` | Agregado: status, dono, centro de custo, versão atual e `lock_version` |
| `request_version` | Conteúdo da versão. Fica **imutável depois de enviada**, garantido por trigger. |
| `request_item` | Itens da versão |
| `approval_step` | Etapas da cadeia, uma cadeia por versão |
| `request_event` | Histórico **append-only**, garantido por trigger: `UPDATE`, `DELETE` e `TRUNCATE` são recusados |
| `monthly_budget` | Orçamento por centro de custo e mês, com `CHECK (consumed <= amount)` |
| `idempotency_key` | Respostas gravadas do `POST /requests` |

Detalhes no [ADR 002](docs/adr/002-versions-and-history.md).

### Front-end

Telas: login, dashboard, listagem (filtros, ordenação e paginação no servidor, refletidos na URL), formulário com itens dinâmicos, detalhe e caixa de aprovação.

- **Ações:** os botões vêm só de `allowedActions`, sem nenhuma regra de permissão no front.
- **Erros tratados num interceptor central:**
  - `401` leva ao login e volta à rota de origem;
  - `409` mostra "outra pessoa agiu antes de você" e recarrega;
  - `422` mostra o `detail` da API;
  - `400` coloca cada erro no campo correspondente, inclusive dentro dos itens.

---

## Principais decisões e trade-offs

| Decisão | Por quê | Custo | ADR |
|---|---|---|---|
| **Linha da solicitação travada** (`SELECT ... FOR UPDATE`) + `lockVersion` incrementado na hora | O `409` fica determinístico (dois aprovadores na mesma etapa: um `200`, um `409`), e a resposta de cada escrita já traz o `lockVersion` certo | Escritas na **mesma** solicitação são serializadas (milissegundos). O `lockVersion` é opaco e pode subir mais de 1. | [001](docs/adr/001-concurrency-strategy.md) |
| **Orçamento consumido com `UPDATE` condicional atômico** (`... WHERE amount - consumed >= :total`) | Sem janela entre ler e gravar; o saldo nunca fica negativo, sem `SERIALIZABLE` nem lock de tabela | Aprovações finais do mesmo centro de custo e mês esperam umas pelas outras | [001](docs/adr/001-concurrency-strategy.md) |
| **Versão imutável com itens e etapas próprios**; reenviar cria v2 e recalcula a cadeia | A versão anterior fica consultável exatamente como foi decidida; a imutabilidade é garantida pelo banco, não só pela aplicação | Mais tabelas e cópia de itens por versão | [002](docs/adr/002-versions-and-history.md) |
| **`404` fora da visibilidade**; `403` só para erro de papel | Não revela a existência de solicitações de outros centros de custo nem de rascunhos alheios | Quem tem um link de outro centro de custo vê "não encontrada" | [003](docs/adr/003-forbidden-vs-not-found.md) |
| **`BigDecimal`** com escala 2 e `HALF_EVEN`, `NUMERIC(19,2)` no banco e **string no JSON** | Sem erro de ponto flutuante em nenhuma ponta; o total é sempre calculado no servidor | O front precisa converter para exibir | [004](docs/adr/004-monetary-representation.md) |
| **Hexagonal leve**: domínio puro, sem interfaces de porta para a persistência | Regras testáveis isoladamente, sem a cerimônia de portas e adaptadores para um único banco | A camada `application` depende das entidades JPA | [005](docs/adr/005-layered-architecture.md) |
| **Idempotência em tabela**, gravada na mesma transação da criação, com hash do payload já interpretado | Não há janela entre criar e registrar a chave; `10.5` e `"10.50"` são tratados como o mesmo payload; corridas com a mesma chave criam uma só solicitação | A tabela cresce, porque a limpeza não foi implementada | [006](docs/adr/006-idempotency.md) |
| **Seed como migration repetível** do Flyway | Roda em toda subida sem duplicar dados e recria o orçamento do mês corrente | Fica fora dos testes de integração, que criam os próprios dados | — |

## Teste de carga

Rodei a pilha do `docker compose` (nginx → API → PostgreSQL) por 150 segundos com, ao mesmo tempo:

- 16 leitores (listagem com filtros, detalhe, histórico, dashboard e caixa de aprovação);
- 12 solicitantes criando e enviando solicitações;
- 8 aprovadores decidindo o que chegava na caixa;
- rajadas de corrida: mesma etapa, mesma chave de idempotência e o mesmo saldo disputado.

| Resultado | Valor |
|---|---|
| Vazão | ~750 req/s, 113 mil requisições, 0 erros 5xx |
| Latência p95 | 35 a 100 ms nas escritas; ~100 ms na listagem; ~460 ms na caixa de aprovação (com 23 mil pendentes no mesmo centro de custo) |
| Corridas | Mesma etapa: sempre um `200` e o resto `409`. Mesma chave: sempre um único id. 20 aprovações de R$ 1.000 contra R$ 5.000: exatamente 5 aprovadas e 15 `BUDGET_EXCEEDED`. |
| Invariantes no banco | Todas verdadeiras: consumo do orçamento igual à soma das aprovadas, nenhum saldo negativo, ninguém decidindo duas etapas da mesma versão nem a própria solicitação, status de cada solicitação igual ao do último evento do histórico |

O teste encontrou um problema, já corrigido.

- **Sintoma:** o nginx abria uma conexão nova com a API a cada requisição. Por volta de 1.000 req/s, as portas locais se esgotavam (`connect() failed (99: Address not available)`) e o nginx respondia `502`.
- **Correção:** o proxy passou a usar um pool de conexões persistentes (`keepalive`).

## O que ficou de fora

| Item | Situação |
|---|---|
| **Telas de administração** no front (usuários, centros de custo e orçamentos) | Opcional no enunciado. Tudo existe na API (`/users`, `/cost-centers`, `PUT .../budgets/{mês}`) e está documentado no Swagger. |
| **Refresh token** | O token dura 15 minutos (`JWT_TTL`). Ao expirar, o front leva ao login e volta para a mesma tela. |
| **Revogação imediata de papel** | O papel e o nível vêm do JWT. Se um ADMIN muda o papel de alguém, a mudança vale quando o token atual expirar. |
| **Limpeza das chaves de idempotência** | As chaves nunca são apagadas, o que cumpre o mínimo de 24 horas. O índice em `created_at` deixa a limpeza periódica pronta para ser criada. |
| **Idempotência nas transições** | Não é necessária para a segurança, porque o `lockVersion` já faz uma ação repetida receber `409`. |
| **Diferenciais não feitos** | Tempo médio de aprovação, outbox, job de SLA, rate limit no login, E2E, métricas e tracing. |

Diferenciais entregues:

- histórico append-only **garantido pelo banco** (triggers);
- filtros `minAmount`, `maxAmount` e `q`;
- imutabilidade das versões enviadas também garantida por trigger;
- **deploy público** em https://compras.erikmota.dev. Cada push em `teste-pleno` com o CI verde publica imagens arm64 no GHCR e atualiza o servidor por SSH, com backup do banco e rollback automático. O acesso passa pelo Cloudflare Tunnel, sem porta exposta. Detalhes em [`docs/deploy.md`](docs/deploy.md).

## O que eu faria com mais tempo

1. **Tempo médio de aprovação** no dashboard, calculado a partir de `request_event` (do `SUBMIT` à decisão final, últimos 30 dias).
2. **Rate limit no login** (Bucket4j) e refresh token com rotação.
3. **Testes E2E** com Playwright cobrindo o roteiro completo, desde a criação até o `BUDGET_EXCEEDED`.
4. **Transactional outbox** publicando as mudanças de estado, base para notificações e para o job de SLA.
5. **Telas de administração** e um job agendado para limpar as chaves de idempotência.
6. **Caixa de aprovação filtrada no banco:**
   - Hoje o SQL traz as candidatas (centro de custo e status) e a policy de elegibilidade filtra em memória. É a mesma regra que valida a ação.
   - Com dezenas de milhares de pendentes no mesmo centro de custo, a latência sobe (p95 ~460 ms no teste de carga).
   - Levaria o filtro de nível e de segregação para a consulta, mantendo a policy como verificação final.
7. **Observabilidade:** métricas do Actuator no Prometheus e tracing com OpenTelemetry, usando o `X-Request-Id` que já é propagado.

# Front-end: solicitações de compra

SPA em **Angular 21** com **Angular Material**. Usa componentes standalone, signals, controle de fluxo `@if`/`@for` e detecção de mudanças zoneless. Os testes rodam em **Vitest** com **Angular Testing Library**.

## Pré-requisitos

- Node.js 20.19+, 22.12+ ou 24+, com npm.
- O back-end rodando em `http://localhost:8080`. Veja [`backend/README.md`](../backend/README.md).

## Comandos

```bash
npm ci                 # instala exatamente as versões do package-lock.json
npm start              # http://localhost:4200, com proxy de /api para http://localhost:8080
npm test               # testes (Vitest, execução única)
npm run lint           # ESLint (angular-eslint)
npm run format:check   # Prettier (npm run format corrige)
npm run build          # build de produção em dist/frontend/browser

docker build -t purchase-frontend .   # imagem nginx com o build e proxy de /api
```

## Configuração

O front chama sempre o caminho relativo `/api`, nunca uma URL absoluta. Por isso a mesma build funciona em qualquer ambiente, e o back-end não precisa de CORS.

| Onde | Quem atende `/api` | Configuração |
|---|---|---|
| `npm start` | dev-server do Angular | `proxy.conf.json` (alvo `http://localhost:8080`) |
| Docker | nginx | variável `BACKEND_URL` (padrão `http://backend:8080`) em `nginx/default.conf.template` |

O nginx também devolve o `index.html` para as rotas do cliente. Assim, um link como `/requests/42` funciona quando é aberto direto ou depois de recarregar a página.

## Usuários para testar

Use os usuários do seed, todos com a senha `Senha@123`:

- `solicitante.ti@example.com`
- `aprovador1.ti@example.com`
- `aprovador2.ti@example.com`
- `admin@example.com`
- `admin2@example.com`

A tabela completa está em [`backend/README.md`](../backend/README.md#dados-de-demonstração-seed).

## Telas

| Rota | Tela |
|---|---|
| `/login` | Login. Com `?returnUrl=`, volta à tela de origem depois de entrar. |
| `/dashboard` | Totais por status e orçamento do mês por centro de custo (orçado, consumido e disponível), no escopo do papel |
| `/requests` | Listagem com busca, filtros, ordenação e paginação no servidor (detalhes abaixo) |
| `/requests/new` e `/requests/:id/edit` | Formulário com itens dinâmicos e total estimado em tempo real |
| `/requests/:id` | Detalhe com dados, itens, cadeia de aprovação, versões, histórico e as ações permitidas |
| `/approvals` | Caixa de aprovação, para `APROVADOR` e `ADMIN` |

Na listagem, todos os parâmetros (filtros, página e ordenação) ficam na URL. Um link copiado abre a mesma visão.

## Decisões

- **As ações vêm da API.** Os botões do detalhe saem só de `allowedActions` (`shared/request-actions.ts`), e o front não reimplementa nenhuma regra de permissão. O guard de papel só esconde a caixa de aprovação de quem nunca aprova; é navegação, não segurança.
- **Concorrência.** Toda ação envia o `lockVersion` lido junto com a página.
  - Um `409` mostra a mensagem "outra pessoa agiu antes de você" e recarrega o detalhe, com as ações atualizadas.
  - Na edição, o formulário mantém o que foi digitado e oferece recarregar os dados atuais.
- **Erros.** Um interceptor central (`core/http/interceptors.ts`) trata os erros:

  | Status | Comportamento |
  |---|---|
  | `401` | Encerra a sessão e vai para o login com `returnUrl` |
  | `422` | Mostra o `detail` da API, que explica a regra (por exemplo, `BUDGET_EXCEEDED` com os valores) |
  | `400` | No formulário, cada item de `errors[]` aparece no campo correspondente, inclusive `items[1].unitPrice` |
  | `5xx` | Mostra o `requestId` como código para o suporte |

- **Comentário obrigatório.** Rejeitar e devolver abrem um modal que exige pelo menos 10 caracteres, a mesma regra da API. Aprovar e cancelar pedem confirmação, com comentário opcional.
- **Idempotência.** Cada formulário de criação gera um `Idempotency-Key`. Um duplo clique ou uma nova tentativa devolvem a mesma solicitação, em vez de criar outra.
- **Dinheiro.** Os valores trafegam como texto (`"1234.50"`) e só são formatados para exibição.
  - O total do formulário é uma prévia calculada em centavos, para evitar erro de ponto flutuante.
  - Quem calcula o valor gravado é o servidor.
- **Sessão.** O token fica no `sessionStorage`: sobrevive a recarregar a página, mas não a fechar a aba. A sessão é descartada quando o token expira.

## Testes

| Arquivo | O que cobre |
|---|---|
| `features/requests/request-form.page.spec.ts` | Adicionar e remover itens com o total em tempo real; erros `400` do servidor exibidos no campo certo, incluindo itens; envio da chave de idempotência |
| `shared/request-actions.spec.ts` | Botões renderizados somente a partir de `allowedActions` |
| `core/http/interceptors.spec.ts` | Token e `X-Request-Id`; mensagens de `409` e `422`; `401` levando ao login com `returnUrl` |
| `features/requests/request-detail.page.spec.ts` | Envio do `lockVersion`; recarga após `409` e `422`, com as ações atualizadas; estado "não encontrada" para `404` |

## Estrutura

```
src/app/
├── core/       api (contratos e serviço HTTP), auth (sessão e guards), http (interceptors e erros)
├── shared/     status, dinheiro, modal de comentário, botões de ação, estados de carregando, vazio e erro
├── layout/     barra de navegação
└── features/   auth, dashboard, requests (lista, formulário, detalhe), approvals
```

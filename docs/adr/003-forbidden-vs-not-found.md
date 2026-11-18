# ADR 003 — `403` × `404` para recursos fora da visibilidade

- **Status:** accepted
- **Data:** 2026-11-18

## Contexto

As regras de visibilidade das solicitações são estas:

- o `SOLICITANTE` vê as próprias solicitações;
- o `APROVADOR` vê as do seu centro de custo;
- o `ADMIN` vê todas;
- um `DRAFT` só é visível para o dono.

O enunciado pede para decidir entre `403` e `404` quando alguém acessa uma solicitação que existe, mas está fora da sua visibilidade. A ordem de verificação é fixa: `401`, depois `404`, `403`, `409` e por fim `422`.

## Decisão

**Fora da visibilidade, a resposta é `404`**, com a mesma resposta de um id inexistente (`"Solicitação 42 não encontrada."`).

O `403` fica reservado para quando **o recurso é visível, mas o papel não permite aquele tipo de ação**:

| Situação | Resposta |
|---|---|
| Solicitação inexistente | `404 NOT_FOUND` |
| Rascunho de outro usuário (mesmo para `ADMIN`) | `404 NOT_FOUND` |
| Solicitação de outro centro de custo (para `APROVADOR` e `SOLICITANTE`) | `404 NOT_FOUND` |
| `SOLICITANTE` tentando aprovar, rejeitar ou devolver uma solicitação que vê (a própria) | `403 FORBIDDEN` |
| Não-`ADMIN` em `/users`, `POST /cost-centers` e `PUT` de orçamento | `403 FORBIDDEN` |
| Visível, papel permite, mas a regra de negócio recusa (não é o dono, nível errado, segregação, estado) | `422` com `code` específico |

Na prática:

- Editar, submeter ou cancelar sem ser o dono recebe `422 NOT_OWNER`: o papel permite essas ações, mas a regra de propriedade não.
- Um `APROVADOR` do mesmo centro de custo com o nível errado recebe `422 APPROVER_NOT_ELIGIBLE`.

A regra de visibilidade fica em um só lugar (`RequestVisibility`), com duas formas do mesmo predicado: uma verificação em memória para `GET /requests/:id` e as transições, e uma `Specification` para a listagem e o dashboard. Os testes de API verificam as duas.

## Consequências

- **Não vaza existência:** quem está fora do escopo não consegue descobrir, variando ids, quais solicitações existem em outros centros de custo ou quais rascunhos outros usuários têm.
- **O comportamento é coerente com a listagem:** se a solicitação não aparece em `GET /requests`, o acesso direto também devolve "não encontrada".
- **O `403` continua útil para o front:** ele indica um erro de papel (por exemplo, um botão que não deveria ser exibido), e não um id inválido.
- **Custo:** um usuário com link para uma solicitação de outro centro de custo vê "não encontrada" em vez de "sem permissão", e o suporte precisa saber disso.

## Alternativas consideradas

- **`403` sempre que o recurso existe:** é mais explícito para o usuário legítimo, mas confirma a existência de solicitações de outros centros de custo e de rascunhos alheios.
- **`404` também para erro de papel:** esconderia demais e confundiria o front, que receberia "não encontrada" para uma solicitação que acabou de exibir.

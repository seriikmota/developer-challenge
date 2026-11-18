# ADR 002 — Modelagem de versões e histórico

- **Status:** accepted
- **Data:** 2026-11-18

## Contexto

O enunciado exige que:

- a devolução para ajuste gere uma **nova versão** no reenvio (v2, v3…), com a versão anterior **imutável e consultável**;
- a cadeia de aprovação seja **recalculada do zero** a cada versão, sem contar aprovações anteriores;
- a mesma pessoa não aprove duas etapas **da mesma versão**;
- toda ação gere um evento de histórico **append-only** (nunca alterado nem apagado), com ator, papel, ação, status de origem e destino, etapa, versão, comentário, data/hora UTC e `X-Request-Id`.

## Decisão

### Agregado da solicitação

```
purchase_request   (status, current_version, lock_version, owner, cost center)
 └── request_version   v1, v2…  (título, descrição, categoria, neededBy, total, submitted_at, competence_month)
      ├── request_item      itens da versão (quantity × unit_price = line_total)
      └── approval_step     cadeia da versão (step_order, level, status, decided_by, decided_at, comment)
request_event      histórico append-only, por solicitação
```

- **Itens e etapas pertencem à versão**, não à solicitação. Cada versão tem sua própria cadeia, então "recalcular do zero" é só criar as etapas da nova versão, e as aprovações antigas ficam guardadas na versão antiga.
- **Uma versão é editável enquanto `submitted_at` é nulo.** No submit ela é congelada: grava `submitted_at`, o mês de competência (`America/Sao_Paulo`) e a cadeia.
- **Nova versão nasce na primeira edição após uma devolução** (ou no reenvio sem edição): copia o conteúdo da versão congelada para `n + 1`, e `current_version` passa a apontar para ela. Assim a versão anterior nunca é tocada.
- **`current_version` × `lock_version`:** `current_version` é o número da versão em edição ou em aprovação (muda só quando nasce uma versão nova); `lock_version` é o `@Version` do JPA e muda a cada escrita, para o lock otimista.
- **Agregados se referenciam por id** (`owner_id`, `cost_center_id`, `decided_by`, `actor_id`): só versões, itens e etapas são relações JPA, porque formam um único agregado.

### Garantias no banco (rede de segurança sob a aplicação)

| Regra | Como o banco garante |
|---|---|
| Histórico append-only | Trigger `BEFORE UPDATE OR DELETE` e `BEFORE TRUNCATE` em `request_event` lança erro |
| Versão submetida imutável | Trigger em `request_version` (update/delete) e em `request_item` (insert/update/delete) quando `submitted_at` não é nulo |
| Mesma pessoa não decide duas etapas da versão | `UNIQUE (version_id, decided_by)` em `approval_step` |
| Etapa decidida tem autor e data | `CHECK` ligando `status`, `decided_by` e `decided_at` |
| Total coerente | `CHECK (line_total = quantity * unit_price)` |
| Orçamento nunca negativo | `CHECK (consumed >= 0 AND consumed <= amount)` |

As regras continuam implementadas no domínio, com mensagens explicativas (`422`). O banco só impede que um bug, um script manual ou uma corrida as violem.

## Consequências

- Consultar uma versão antiga (`GET /requests/:id/versions/:n`) é ler a linha da versão com seus itens e etapas, sem reconstruir nada a partir de eventos.
- O histórico é uma tabela simples, ordenada por `occurred_at`, e não depende das tabelas de versão para ser lido.
- Copiar a versão na nova edição duplica itens (poucas linhas por solicitação). É um custo aceitável pela simplicidade.
- Os triggers exigem atenção na ordem de escrita: o Hibernate executa os `UPDATE` antes dos `DELETE` no flush, então editar e submeter a mesma versão no mesmo flush é recusado pelo banco. Na API são requisições distintas, então não acontece.
- A constraint `UNIQUE (version_id, position)` dos itens é `DEFERRABLE INITIALLY DEFERRED`, porque substituir os itens de um rascunho insere os novos antes de o Hibernate apagar os antigos.

## Alternativas consideradas

- **Event sourcing (estado derivado dos eventos):** histórico e versões viriam de graça, mas listagem, filtros e lock ficariam bem mais complexos. É desproporcional para o escopo.
- **Snapshot em JSON da versão anterior:** menos tabelas, mas itens e etapas antigos deixariam de ter constraints e de ser consultáveis por SQL.
- **Itens e etapas ligados à solicitação, com coluna `version`:** funciona, mas mistura dados de versões diferentes na mesma coleção e torna fácil contar uma aprovação antiga por engano.
- **Append-only só na aplicação (sem trigger):** mais simples, mas qualquer `UPDATE` manual ou bug quebraria a auditoria sem ninguém perceber.

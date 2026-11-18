# ADR 001 — Estratégia de concorrência

- **Status:** accepted
- **Data:** 2026-11-18

## Contexto

O enunciado exige que:

- toda transição (`submit`, `approve`, `reject`, `request-changes`, `cancel`) e a edição recebam o `lockVersion` atual, e que um valor divergente resulte em `409`;
- duas aprovações **da mesma etapa** ao mesmo tempo terminem com uma aprovada e a outra em `409`;
- aprovações finais de **solicitações diferentes** disputando o mesmo saldo aprovem as que cabem e recusem a que não cabe com `422 BUDGET_EXCEEDED`, **sem o saldo ficar negativo**.

São dois conflitos diferentes: o primeiro é sobre a **mesma linha** (a solicitação), o segundo é sobre um **recurso compartilhado** (o orçamento do mês) por linhas diferentes.

## Decisão

### Mesma solicitação: `lockVersion` + lock da linha durante a transição

1. O cliente lê a solicitação e recebe `lockVersion` (coluna `@Version` do JPA).
2. Na escrita, a solicitação é carregada com `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`). Escritas concorrentes na mesma solicitação passam a acontecer uma depois da outra.
3. Com a linha travada, o `lockVersion` recebido é comparado com o atual. Se for diferente, a resposta é `409 CONCURRENT_MODIFICATION`.
4. Se for igual, o `lockVersion` é incrementado na hora (`PESSIMISTIC_FORCE_INCREMENT`), mesmo quando só linhas filhas mudam (por exemplo, uma aprovação intermediária só altera `approval_step`).
5. A resposta da escrita traz o novo `lockVersion`, que o cliente deve enviar na próxima ação. O valor é opaco: só cresce, e pode subir mais de 1 por escrita.

Na corrida de dois aprovadores na mesma etapa, os dois enviam o mesmo `lockVersion`. O primeiro trava a linha e aprova; o segundo espera, lê o `lockVersion` já incrementado e recebe `409`. O resultado é determinístico.

O `@Version` do JPA continua ativo como rede de segurança: qualquer escrita fora desse caminho que perca a corrida gera `ObjectOptimisticLockingFailureException`, também traduzida para `409`.

### Orçamento: `UPDATE` condicional atômico

```sql
UPDATE monthly_budget
   SET consumed = consumed + :amount, updated_at = :now
 WHERE cost_center_id = :costCenterId
   AND month = :competenceMonth
   AND amount - consumed >= :amount;
```

- Uma linha afetada significa que o orçamento foi consumido.
- Zero linhas afetadas significa saldo insuficiente, ou mês sem orçamento cadastrado (saldo zero). Nesse caso a resposta é `422 BUDGET_EXCEEDED`, e a transação inteira é desfeita: a etapa volta para pendente e nada é gravado no histórico.
- O `UPDATE` trava a linha do orçamento. Aprovações finais concorrentes do mesmo centro de custo e mês esperam umas pelas outras, e cada uma reavalia a condição com o saldo já atualizado. Não existe janela de "leu o saldo, depois gravou".
- O `CHECK (consumed >= 0 AND consumed <= amount)` da tabela é a rede de segurança caso algum caminho esqueça a condição.
- O `PUT` de orçamento usa a mesma técnica (`INSERT ... ON CONFLICT DO UPDATE ... WHERE consumed <= novo valor`), e um valor abaixo do consumido resulta em `422 BUDGET_BELOW_CONSUMED`.

### Transações

Cada caso de uso de escrita é um único `@Transactional`: a mudança na solicitação, o consumo de orçamento e o evento de histórico são gravados juntos ou desfeitos juntos. A ordem dos locks é sempre a mesma (solicitação, depois orçamento), o que evita deadlock entre aprovações finais.

## Consequências

- O cliente tem um protocolo simples: enviar o `lockVersion` recebido por último. O `409` sempre significa "recarregue e tente de novo".
- A trava da linha da solicitação dura só a transação (milissegundos) e afeta apenas escritas na **mesma** solicitação. Leituras não esperam.
- O saldo nunca fica negativo, sem lock de tabela nem nível de isolamento `SERIALIZABLE`.
- Os testes de concorrência (fase 6) disparam requisições simultâneas reais contra o PostgreSQL para provar os dois cenários.

## Alternativas consideradas

- **Só lock otimista (`@Version` detectando no flush):** funciona, mas o incremento forçado do JPA só acontece no commit. Com isso, a resposta da escrita não teria o `lockVersion` final, e o cliente receberia `409` na ação seguinte.
- **Ler o saldo e depois gravar (`SELECT` + `UPDATE`):** tem uma janela entre a leitura e a escrita. Exigiria `SELECT FOR UPDATE` no orçamento ou isolamento `SERIALIZABLE` com retentativas.
- **Isolamento `SERIALIZABLE`:** correto, mas transfere a complexidade para retentativas na aplicação e gera falsos conflitos sob carga.
- **Fila ou processamento assíncrono das aprovações:** desproporcional para o escopo e mudaria a semântica síncrona da API (`422` imediato).

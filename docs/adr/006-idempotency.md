# ADR 006 — Idempotência do `POST /requests`

- **Status:** accepted
- **Data:** 2026-11-18

## Contexto

Um `POST /requests` com o mesmo `Idempotency-Key`, do mesmo usuário e com o mesmo payload deve devolver **a mesma resposta** sem criar outra solicitação, com validade mínima de 24 h. Reutilizar a chave com outro payload resulta em `409`. Os casos típicos são retentativas de rede, duplo clique e um proxy que reenvia a requisição. Retentativas podem chegar **ao mesmo tempo**.

## Decisão

Uma tabela dedicada, gravada **na mesma transação** que cria a solicitação:

```
idempotency_key (user_id, idem_key, request_hash, response_status, response_body, created_at)
UNIQUE (user_id, idem_key)
```

Fluxo (`IdempotentRequestCreator`):

1. Sem o header, a solicitação é criada normalmente.
2. Com o header, a aplicação calcula o **SHA-256 do payload já interpretado** (o `RequestCommand` serializado). Formatação, ordem das chaves e valores equivalentes (`10.5` e `"10.50"`) geram o mesmo hash. Campos ignorados pela API, como um `total` enviado pelo cliente, não entram no hash.
3. A aplicação busca `(user_id, key)`:
   - se a chave existe com o mesmo hash, devolve a resposta gravada (`201`, mesmo corpo, header `Idempotent-Replayed: true`);
   - se a chave existe com outro hash, a resposta é `409 IDEMPOTENCY_KEY_REUSED`;
   - se a chave não existe, a aplicação cria a solicitação e grava a chave com a resposta, na mesma transação.
4. **Corrida com a mesma chave nova:** as duas transações tentam inserir a chave. O índice único deixa uma delas commitar; a outra recebe violação de unicidade e é **desfeita por inteiro**, inclusive a solicitação que ela criou. Em seguida, uma nova transação lê a resposta gravada pela primeira e a devolve.
5. Uma requisição inválida (`400` ou `422`) não grava a chave, porque a transação é desfeita. A mesma chave pode ser usada depois com o payload corrigido.

A chave é por usuário: dois usuários podem usar o mesmo valor sem conflito.

**Validade:** as chaves não são apagadas, o que atende ao mínimo de 24 h. O índice em `created_at` deixa pronta uma limpeza periódica (por exemplo, apagar chaves com mais de 7 dias), que ficou fora do escopo.

## Consequências

- Não há janela entre "criei a solicitação" e "gravei a chave": as duas coisas acontecem juntas ou nenhuma acontece.
- Repetir a requisição devolve exatamente o corpo original, inclusive o `lockVersion` e as `allowedActions` do momento da criação. Para ver o estado atual, o cliente faz `GET /requests/:id`.
- A tabela cresce com o uso. Sem a limpeza, o custo é só armazenamento.

## Alternativas consideradas

- **Guardar só o id criado e reconstruir a resposta:** gasta menos espaço, mas a resposta repetida refletiria o estado atual (por exemplo, já editada), e não "a mesma resposta".
- **Chave guardada em cache (Redis) com TTL:** traz expiração automática, mas sai da transação do banco: um crash entre criar a solicitação e gravar a chave quebraria a garantia. Também adicionaria infraestrutura.
- **Hash do corpo bruto:** é mais simples, mas diferenças irrelevantes (espaços, ordem das chaves, `10.5` contra `10.50`) seriam tratadas como outro payload e gerariam `409` indevidos.

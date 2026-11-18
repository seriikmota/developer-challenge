# ADR 005 — Arquitetura em camadas (hexagonal leve)

- **Status:** accepted
- **Data:** 2026-11-17

## Contexto

O enunciado exige que a máquina de estados e a montagem da cadeia de aprovação sejam testáveis **sem subir servidor ou banco**, e avalia a facilidade de estender regras. A conversa técnica inclui uma mudança de regra ao vivo, então as regras precisam estar em lugares previsíveis e isoladas de detalhes de HTTP e ORM.

## Decisão

Três camadas, com dependências apontando sempre para dentro:

```
infrastructure  ->  application  ->  domain
(web, JPA,          (casos de uso,    (Java puro: regras,
 segurança)          @Transactional)   value objects)
```

- **`domain`**: Java puro, sem Spring, JPA ou Jackson. Contém:
  - `request/RequestStateMachine`: transições de status (tabela 4.2 do enunciado);
  - `request/RequestActionPolicy`: combina estado, dono e elegibilidade; é a fonte única de `allowedActions`;
  - `approval/ApprovalChainFactory`: etapas por valor e categoria;
  - `approval/ApprovalEligibilityPolicy`: nível, centro de custo e segregação de funções;
  - `approval/DecisionCommentRule`: comentário obrigatório;
  - `budget/Money` e `budget/CompetenceMonth`: value objects;
  - `error/DomainException` + `ErrorCode`: violações de regra (`422`) com mensagem que explica o motivo.
- **`application`**: casos de uso transacionais. Orquestram as regras do domínio, a persistência e o histórico.
- **`infrastructure`**: controllers, DTOs, entidades JPA, repositórios e segurança. Exceção deliberada da versão leve: os casos de uso usam diretamente as entidades e os repositórios de `infrastructure.persistence`, sem interfaces de porta; `web` e `security` continuam fora do alcance da `application`.

Regras de projeto:

1. **Uma fonte de verdade para permissões.** A mesma policy valida a ação e calcula `allowedActions` (`Eligibility` devolve o resultado sem lançar exceção; `orThrow()` lança). Validação e botões do front nunca divergem.
2. **Entidades JPA separadas do domínio.** As regras recebem dados simples (`Actor`, `ApprovalContext`, `RequestStatus`), não entidades.
3. **O isolamento é verificado por teste.** `DomainArchitectureTest` (ArchUnit) falha o build se o domínio importar Spring, JPA, Hibernate, Jackson ou as camadas externas.

## Consequências

- As regras são testadas com testes unitários rápidos (milissegundos) e parametrizados, sem Spring.
- Mudar uma regra (ex.: nova faixa de valor, nova categoria com etapa extra) altera uma única classe do domínio e o teste dela.
- Custo: mapeamento entre entidades JPA e objetos do domínio, e um pouco mais de classes do que um CRUD em camadas tradicional.

## Alternativas consideradas

- **Camadas tradicionais (controller → service → repository) com regras nos services:** menos classes, mas as regras ficam acopladas ao Spring e ao JPA, e testá-las exige contexto ou mocks.
- **Hexagonal completa (ports/adapters explícitos para tudo):** isolamento máximo, mas cerimônia demais para o tamanho do projeto. Fica a versão leve: domínio puro e casos de uso que usam os repositórios do Spring Data diretamente.
- **Entidades JPA como modelo de domínio:** menos mapeamento, mas anotações de persistência e lazy loading entram nas regras e quebram o isolamento verificado pelo ArchUnit.

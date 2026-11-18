# ADR 004 — Representação de valores monetários

- **Status:** accepted
- **Data:** 2026-11-17

## Contexto

O enunciado deixa a representação, o armazenamento e o arredondamento de valores monetários a critério do candidato. O total da solicitação é calculado no back-end (`quantity × unitPrice` por item, somado), a moeda é sempre BRL, e o orçamento do centro de custo é comparado com esse total na aprovação final. Qualquer erro de centavo quebra a regra "o saldo nunca fica negativo" e as faixas da cadeia de aprovação (R$ 1.000,00 e R$ 10.000,00 são limites exatos).

## Decisão

- **Domínio:** value object `Money` (`domain/budget/Money.java`) sobre `BigDecimal`, sempre normalizado para **escala 2** com `RoundingMode.HALF_EVEN`. Operações: `plus`, `minus`, `times(int)`, comparações. Imutável.
- **Banco:** `NUMERIC(19,2)`.
- **API (JSON):** string decimal com ponto e duas casas, por exemplo `"4899.90"`.
- **Moeda:** implícita (BRL). Não existe campo de moeda.
- **Mensagens ao usuário:** formatadas como `R$ 24.499,50` (`Money.format()`).

## Consequências

- Sem erro de ponto flutuante: `0.10 + 0.20 = 0.30` e `4899.90 × 5 = 24499.50` exatos.
- `quantity` é inteiro, então `unitPrice × quantity` não gera casas extras: o arredondamento só atua na entrada, quando o cliente envia mais de 2 casas. Essa entrada deve ser recusada com `400` na validação da API, e a normalização do `Money` fica como rede de segurança.
- `equals` funciona por valor (`10` e `10.00` são iguais), porque a escala é sempre 2.
- String no JSON evita que clientes JavaScript convertam o valor para `number` (double) e percam precisão.
- Custo: o front precisa converter a string para exibir e para calcular o total em tempo real (que é só exibição).

## Alternativas consideradas

- **`double`/`float`:** descartado. Erro binário de arredondamento em valores monetários.
- **Centavos em `long`:** exato e rápido, mas exige conversão em todas as bordas (API, mensagens, seed) e torna o JSON menos legível (`489990`). O ganho de desempenho não importa neste volume.
- **`HALF_UP`:** é o arredondamento "escolar". `HALF_EVEN` (arredondamento bancário) evita viés acumulado em somas. Como o arredondamento quase nunca é acionado (ver acima), a escolha tem pouco impacto prático.
- **Biblioteca de moeda (JSR 354 / Moneta):** dependência extra para um único cenário de moeda fixa.
- **Número no JSON:** mais natural, mas sujeito à perda de precisão em clientes JavaScript.

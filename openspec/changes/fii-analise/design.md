## Context

A análise de FII é composta por três fontes externas (Yahoo Finance, Status Invest, Google News RSS) agregadas em tempo real, mais uma camada de IA (Claude) que sintetiza os dados em análise textual. O cache Caffeine evita sobrecarga nas fontes externas.

## Goals / Non-Goals

**Goals:**
- Análise completa em tempo real por FII
- Análise textual estruturada via Claude
- Ranking de FIIs em alta por segmento
- Cache de dados de mercado para reduzir latência e chamadas externas

**Non-Goals:**
- Persistência de histórico de análises de mercado
- Alertas automáticos de variação de preço
- Comparação entre FIIs

## Decisions

**Agregação síncrona das três fontes**
`FiiAnaliseCompletaService` chama `FiiDadosMercadoService`, `FiiNoticiasService` e `FiiRecomendacoesService` em sequência. Falhas parciais resultam em campos nulos/vazios, não em erro da requisição. Trade-off: latência total pode ser alta (3 chamadas HTTP); melhoria futura seria paralelizar com `CompletableFuture`.

**Caffeine para cache de dados de mercado**
Dados de mercado são cacheados via `@Cacheable` com Caffeine. TTL configurável. Evita múltiplas chamadas ao Yahoo Finance para o mesmo FII em curto intervalo. Notícias e recomendações não são cacheadas (menor volume de chamadas e maior necessidade de frescor).

**Claude API para análise-ia**
`FiiAnaliseIAService` monta prompt com todos os dados coletados e chama Claude. Sem streaming — resposta é aguardada completa. O resultado não é cacheado pois depende de dados de mercado em tempo real.

**Separação de controllers**
`FundoImobiliarioController` cuida do CRUD. `FiiAnaliseController` cuida de todas as análises. Ambos mapeiam para `/api/v1/fundos-imobiliarios` — Spring faz o roteamento pela URL completa.

## Risks / Trade-offs

- **[Risco] Latência alta** → Três chamadas HTTP síncronas por análise; resolver com paralelização futura
- **[Risco] Quebra de scraping** → Mudanças no Status Invest podem quebrar recomendações; campos voltam vazios silenciosamente
- **[Trade-off] Cache pode retornar dados desatualizados** → Aceitável para análise — dados de segundos atrás são suficientes para o caso de uso

## Open Questions

- Qual o TTL ideal do cache de dados de mercado? (sugestão: 5 minutos para dados de pregão)

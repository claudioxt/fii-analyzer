## Why

Além do catálogo estático, o sistema deve fornecer análises dinâmicas de cada FII com dados de mercado em tempo real, notícias recentes e recomendações de analistas. Uma camada de IA (Claude) deve sintetizar esses dados em análise textual estruturada.

## What Changes

- Endpoint público `GET /api/v1/fundos-imobiliarios/{codigo}/analise` retorna análise completa com dados de mercado, notícias e recomendações
- Endpoint público `GET /api/v1/fundos-imobiliarios/{codigo}/analise-ia` retorna análise textual gerada pelo Claude com base nos dados de mercado
- Endpoint público `GET /api/v1/fundos-imobiliarios/em-alta` retorna FIIs em alta agrupados por segmento

## Capabilities

### New Capabilities

- `analise-mercado`: Agregação de dados de mercado, notícias e recomendações de analistas para um FII
- `analise-ia-fii`: Análise textual estruturada de FII individual gerada pelo Claude
- `fiis-em-alta`: Ranking de FIIs com melhor desempenho agrupados por segmento

### Modified Capabilities

## Impact

- `com.fii.service`: FiiAnaliseCompletaService, FiiDadosMercadoService, FiiNoticiasService, FiiRecomendacoesService, FiiAnaliseIAService, FiisEmAltaService
- `com.fii.controller.FiiAnaliseController`: endpoints de análise
- Integrações: Yahoo Finance (preços), Status Invest (P/VP, recomendações), Google News RSS (notícias), Claude API (análise IA)
- Cache: Caffeine para dados de mercado (evita chamadas repetidas)
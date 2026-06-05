## Why

O sistema deve permitir registrar carteiras de FIIs recomendadas por casas de análise, possibilitando comparação histórica e análise crítica por IA. A IA também deve ser capaz de gerar uma carteira recomendada automaticamente com base nos dados disponíveis.

## What Changes

- CRUD completo de carteiras recomendadas (`/api/v1/carteiras-recomendadas`) com filtros por casa de análise e mês de referência
- Endpoint `GET /{id}/analise-ia` analisa criticamente uma carteira com Claude
- Endpoint `POST /gerar-ia` gera uma nova carteira recomendada com Claude

## Capabilities

### New Capabilities

- `carteiras-recomendadas`: CRUD de carteiras recomendadas por casas de análise, com itens (código + percentual)
- `analise-ia-carteira`: Análise crítica de carteira existente via Claude
- `geracao-ia-carteira`: Geração automática de carteira recomendada via Claude com base nos FIIs cadastrados

### Modified Capabilities

## Impact

- `com.fii.entity`: CarteiraRecomendada, CarteiraRecomendadaItem
- `com.fii.service`: CarteiraRecomendadaService, CarteiraRecomendadaBuscaService, CarteiraAnaliseIAService, CarteiraGerarIAService
- `com.fii.controller.CarteiraRecomendadaController`
- `com.fii.repository`: CarteiraRecomendadaRepository
- Integração: Claude API para análise e geração
- Banco: tabelas `carteiras_recomendadas` e `carteira_recomendada_itens`

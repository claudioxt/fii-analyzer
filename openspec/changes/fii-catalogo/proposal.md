## Why

O sistema precisa manter um catálogo de Fundos de Investimento Imobiliário (FIIs) que sirva de base para análises. O cadastro deve ser simples: o administrador informa apenas o código do fundo e o sistema busca automaticamente as informações complementares (nome, tipo, segmento) em fontes externas.

## What Changes

- Endpoint público `GET /api/v1/fundos-imobiliarios` para listar todos os FIIs cadastrados
- Endpoint público `GET /api/v1/fundos-imobiliarios/{codigo}` para buscar um FII por código
- Endpoint protegido `POST /api/v1/fundos-imobiliarios` (ADMIN) para cadastrar novo FII com enriquecimento automático via Yahoo Finance e Status Invest
- Endpoint protegido `PUT /api/v1/fundos-imobiliarios/{codigo}` (ADMIN) para atualizar dados de um FII
- Endpoint protegido `DELETE /api/v1/fundos-imobiliarios/{codigo}` (ADMIN) para remover um FII

## Capabilities

### New Capabilities

- `catalogo-fiis`: CRUD de fundos imobiliários com enriquecimento automático de dados

### Modified Capabilities

## Impact

- `com.fii.entity.FundoImobiliario`: entidade JPA principal
- `com.fii.service.FundoImobiliarioService`: lógica de negócio e enriquecimento
- `com.fii.controller.FundoImobiliarioController`: endpoints REST
- `com.fii.repository.FundoImobiliarioRepository`: Spring Data JPA
- Integrações: Yahoo Finance, Status Invest (Jsoup scraping)
- Banco: tabela `fundos_imobiliarios`
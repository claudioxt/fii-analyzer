## ADDED Requirements

### Requirement: Listagem de FIIs em alta por segmento
O sistema SHALL identificar e retornar os FIIs com melhor desempenho recente, agrupados por segmento (ex: Logística, Shoppings, Lajes Corporativas, etc.).

#### Scenario: FIIs em alta disponíveis
- **WHEN** qualquer cliente envia `GET /api/v1/fundos-imobiliarios/em-alta`
- **THEN** o sistema retorna HTTP 200 com FiisEmAltaDTO contendo lista de FiiEmAltaPorSegmentoDTO, cada um com o segmento e lista de FiiEmAltaItemDTO

#### Scenario: Estrutura de cada item em alta
- **WHEN** o sistema retorna FIIs em alta
- **THEN** cada FiiEmAltaItemDTO contém: codigo, nome, variacaoPercentual (no período), precoAtual

#### Scenario: Sem dados disponíveis
- **WHEN** as fontes externas não retornam dados de desempenho
- **THEN** o sistema retorna lista vazia agrupada por segmento

---

### Requirement: Critério de "em alta"
O sistema SHALL considerar como "em alta" os FIIs com variação positiva no período (dia ou semana), ordenados do maior para o menor ganho dentro de cada segmento.

#### Scenario: Ordenação dentro do segmento
- **WHEN** vários FIIs do mesmo segmento estão em alta
- **THEN** os FIIs são retornados ordenados por variação percentual decrescente

#### Scenario: FII sem segmento definido
- **WHEN** um FII em alta não possui segmento cadastrado
- **THEN** o FII é agrupado em categoria "Outros"

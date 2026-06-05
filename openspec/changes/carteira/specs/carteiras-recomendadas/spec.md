## ADDED Requirements

### Requirement: Listagem de carteiras recomendadas
O sistema SHALL retornar todas as carteiras recomendadas cadastradas, com suporte a filtros opcionais por casa de análise e mês de referência.

#### Scenario: Listagem sem filtros
- **WHEN** qualquer cliente envia `GET /api/v1/carteiras-recomendadas`
- **THEN** o sistema retorna HTTP 200 com lista de CarteiraRecomendadaResponseDTO

#### Scenario: Filtro por casa de análise
- **WHEN** o cliente envia `GET /api/v1/carteiras-recomendadas?casaDeAnalise=XP`
- **THEN** o sistema retorna apenas as carteiras da casa de análise XP

#### Scenario: Filtro por mês de referência
- **WHEN** o cliente envia `GET /api/v1/carteiras-recomendadas?mesReferencia=2025-01-01`
- **THEN** o sistema retorna apenas as carteiras do mês de referência especificado

#### Scenario: Filtros combinados
- **WHEN** o cliente envia ambos os filtros simultaneamente
- **THEN** o sistema aplica ambos (AND lógico)

---

### Requirement: Busca de carteira por ID
O sistema SHALL permitir buscar uma carteira específica pelo seu identificador único.

#### Scenario: Carteira existente
- **WHEN** o cliente envia `GET /api/v1/carteiras-recomendadas/{id}`
- **THEN** o sistema retorna HTTP 200 com a carteira e seus itens completos

#### Scenario: Carteira inexistente
- **WHEN** o cliente envia `GET /api/v1/carteiras-recomendadas/{id}` com ID que não existe
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Criação de carteira recomendada
O sistema SHALL permitir criar carteiras recomendadas com lista de FIIs e percentuais de alocação. A soma dos percentuais dos itens DEVE ser 100%.

#### Scenario: Criação com dados válidos
- **WHEN** qualquer cliente autenticado envia `POST /api/v1/carteiras-recomendadas` com casaDeAnalise, mesReferencia e itens totalizando 100%
- **THEN** o sistema persiste a carteira e retorna HTTP 201

#### Scenario: Percentual total diferente de 100%
- **WHEN** os percentuais dos itens somam valor diferente de 100%
- **THEN** o sistema retorna HTTP 400

#### Scenario: Carteira com item para FII não cadastrado
- **WHEN** um item referencia código de FII que não existe no catálogo
- **THEN** o sistema retorna HTTP 422 indicando o FII inválido

---

### Requirement: Atualização de carteira
O sistema SHALL permitir atualizar todos os dados de uma carteira existente, incluindo substituição completa dos itens.

#### Scenario: Atualização com dados válidos
- **WHEN** um cliente envia `PUT /api/v1/carteiras-recomendadas/{id}` com dados válidos
- **THEN** o sistema substitui a carteira completa (inclusive itens) e retorna HTTP 200

#### Scenario: Carteira inexistente
- **WHEN** o cliente envia `PUT /api/v1/carteiras-recomendadas/{id}` com ID que não existe
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Remoção de carteira
O sistema SHALL permitir remover uma carteira recomendada e todos os seus itens.

#### Scenario: Remoção de carteira existente
- **WHEN** um cliente envia `DELETE /api/v1/carteiras-recomendadas/{id}`
- **THEN** o sistema remove a carteira e seus itens em cascata, retornando HTTP 204

#### Scenario: Remoção de carteira inexistente
- **WHEN** o cliente envia `DELETE /api/v1/carteiras-recomendadas/{id}` com ID que não existe
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Estrutura de itens da carteira
Cada item de carteira SHALL conter: código do FII (referência ao catálogo) e percentual de alocação (número decimal, ex: 15.5 para 15,5%).

#### Scenario: Item com percentual válido
- **WHEN** um item é criado com percentual entre 0.01 e 100.00
- **THEN** o sistema aceita e persiste o item

#### Scenario: Item com percentual inválido
- **WHEN** um item contém percentual zero, negativo ou acima de 100
- **THEN** o sistema retorna HTTP 400

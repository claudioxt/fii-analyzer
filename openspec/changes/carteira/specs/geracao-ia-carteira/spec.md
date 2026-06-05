## ADDED Requirements

### Requirement: Geração automática de carteira por IA
O sistema SHALL permitir que o Claude gere uma carteira recomendada automaticamente, baseada nos FIIs cadastrados no catálogo e seus dados de mercado atuais.

#### Scenario: Geração bem-sucedida
- **WHEN** qualquer cliente envia `POST /api/v1/carteiras-recomendadas/gerar-ia`
- **THEN** o sistema instrui o Claude a selecionar FIIs e definir percentuais, persiste a carteira gerada e retorna HTTP 201 com a carteira completa

#### Scenario: Carteira gerada é válida
- **WHEN** o Claude gera a carteira
- **THEN** a soma dos percentuais dos itens DEVE ser 100% e todos os FIIs referenciados DEVEM existir no catálogo

#### Scenario: Catálogo vazio
- **WHEN** não há FIIs cadastrados no momento da geração
- **THEN** o sistema retorna HTTP 422 indicando que não há FIIs disponíveis para compor a carteira

---

### Requirement: Metadados da carteira gerada por IA
O sistema SHALL identificar as carteiras geradas por IA com casa de análise "IA" e o mês de referência correspondente ao mês atual da geração.

#### Scenario: Carteira gerada tem identificação correta
- **WHEN** uma carteira é gerada por IA
- **THEN** casaDeAnalise é "IA" e mesReferencia é o primeiro dia do mês corrente

#### Scenario: Justificativa de cada FII
- **WHEN** o Claude seleciona os FIIs
- **THEN** cada item da carteira contém uma justificativa textual da escolha do FII e do percentual atribuído

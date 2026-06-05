## ADDED Requirements

### Requirement: Análise crítica de carteira por IA
O sistema SHALL gerar uma análise crítica de uma carteira recomendada usando o Claude, avaliando diversificação, concentração de risco, qualidade dos FIIs e coerência da alocação.

#### Scenario: Análise com carteira válida
- **WHEN** qualquer cliente envia `GET /api/v1/carteiras-recomendadas/{id}/analise-ia`
- **THEN** o sistema retorna HTTP 200 com CarteiraAnaliseIADTO contendo: resumo, pontos fortes, pontos fracos, sugestões de melhoria e avaliação geral

#### Scenario: Carteira inexistente
- **WHEN** o cliente solicita análise-ia de ID que não existe
- **THEN** o sistema retorna HTTP 404

#### Scenario: Análise inclui dados de mercado dos FIIs
- **WHEN** o sistema prepara o contexto para o Claude
- **THEN** cada FII da carteira tem seus dados atuais de mercado (preço, P/VP, dividend yield) injetados no prompt

---

### Requirement: Análise considera o perfil da carteira
O sistema SHALL contextualizar a análise do Claude com o nome da casa de análise e o mês de referência da carteira para gerar avaliações mais precisas.

#### Scenario: Contexto da carteira no prompt
- **WHEN** o sistema monta o prompt de análise
- **THEN** o prompt contém casaDeAnalise, mesReferencia, lista de FIIs com percentuais e dados de mercado de cada um

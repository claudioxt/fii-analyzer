## ADDED Requirements

### Requirement: Análise textual de FII via IA
O sistema SHALL gerar uma análise textual estruturada de um FII individual usando o Claude, baseada nos dados de mercado, notícias e recomendações coletados.

#### Scenario: Análise gerada com sucesso
- **WHEN** qualquer cliente envia `GET /api/v1/fundos-imobiliarios/{codigo}/analise-ia`
- **THEN** o sistema retorna HTTP 200 com FiiAnaliseIADTO contendo: resumo, pontos positivos, pontos negativos, recomendação e nível de risco

#### Scenario: FII não cadastrado
- **WHEN** o cliente solicita análise-ia de código não cadastrado
- **THEN** o sistema retorna HTTP 404

#### Scenario: Falha na Claude API
- **WHEN** a Claude API está indisponível ou retorna erro
- **THEN** o sistema retorna HTTP 502 indicando falha no serviço externo de IA

---

### Requirement: Análise baseada em dados coletados em tempo real
O sistema SHALL coletar dados atualizados do FII (mercado, notícias, recomendações) antes de enviar ao Claude para análise.

#### Scenario: Dados coletados antes da análise
- **WHEN** o endpoint analise-ia é chamado
- **THEN** o sistema primeiro obtém os dados completos (como em `/analise`) e os injeta como contexto no prompt enviado ao Claude

#### Scenario: Fontes externas indisponíveis
- **WHEN** as fontes de dados de mercado estão inacessíveis
- **THEN** o Claude analisa com os dados parciais disponíveis, indicando na resposta os dados ausentes

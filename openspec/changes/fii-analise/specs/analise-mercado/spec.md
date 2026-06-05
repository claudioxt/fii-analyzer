## ADDED Requirements

### Requirement: Análise completa de FII
O sistema SHALL agregar dados de mercado, notícias e recomendações de analistas para um FII e retornar em resposta única.

#### Scenario: FII cadastrado com dados disponíveis
- **WHEN** qualquer cliente envia `GET /api/v1/fundos-imobiliarios/{codigo}/analise`
- **THEN** o sistema retorna HTTP 200 com FiiAnaliseCompletaDTO contendo: dados de mercado, lista de notícias e recomendações de analistas

#### Scenario: FII não cadastrado
- **WHEN** o cliente solicita análise de código não cadastrado no catálogo
- **THEN** o sistema retorna HTTP 404

#### Scenario: Fonte externa indisponível
- **WHEN** Yahoo Finance ou Status Invest estão inacessíveis durante a análise
- **THEN** o sistema retorna os dados disponíveis, preenchendo com `null` ou lista vazia os campos não obtidos, sem retornar erro 5xx

---

### Requirement: Dados de mercado em tempo real
O sistema SHALL buscar dados de mercado atualizados para o FII incluindo: preço atual, P/VP, variações percentuais no dia, semana, mês e ano, e dividend yield.

#### Scenario: Dados obtidos com sucesso
- **WHEN** Yahoo Finance e Status Invest estão acessíveis
- **THEN** DadosMercadoDTO contém: precoAtual, pvp, variacaoDia, variacaoSemana, variacaoMes, variacaoAno, dividendYield

#### Scenario: Cache ativo
- **WHEN** a mesma requisição de dados de mercado é feita em curto intervalo
- **THEN** o sistema retorna os dados do cache Caffeine sem chamar as fontes externas novamente

---

### Requirement: Notícias recentes via Google News RSS
O sistema SHALL buscar as notícias mais recentes relacionadas ao FII via feed RSS do Google News.

#### Scenario: Notícias disponíveis
- **WHEN** o sistema obtém notícias do Google News RSS para o código do FII
- **THEN** NoticiaDTO contém: titulo, fonte, url, dataPublicacao

#### Scenario: Sem notícias encontradas
- **WHEN** o Google News RSS não retorna resultados para o FII
- **THEN** o campo `noticias` na resposta é uma lista vazia

---

### Requirement: Recomendações de analistas
O sistema SHALL buscar recomendações de analistas do Status Invest para o FII.

#### Scenario: Recomendações disponíveis
- **WHEN** o Status Invest contém recomendações para o FII
- **THEN** RecomendacaoDTO contém: analista, recomendacao (comprar/manter/vender), precoAlvo

#### Scenario: Sem recomendações
- **WHEN** o Status Invest não possui recomendações para o FII
- **THEN** o campo `recomendacoes` na resposta é uma lista vazia

# FII Analyzer — Backend

API REST para análise de Fundos de Investimento Imobiliário (FIIs) do mercado brasileiro. Consolida dados de mercado em tempo real, notícias, recomendações de analistas e análises geradas por Inteligência Artificial (Claude) em um único serviço.

## Stack

| Camada        | Tecnologia                          |
|---------------|-------------------------------------|
| Runtime       | Java 21                             |
| Framework     | Spring Boot 4.0.5                   |
| Banco         | PostgreSQL 16                       |
| IA            | Anthropic Claude Haiku (SDK 2.17.0) |
| Cache         | Caffeine                            |
| Web Scraping  | Jsoup 1.18.3                        |
| Documentação  | SpringDoc OpenAPI / Swagger UI      |
| Build         | Maven 3.9+                          |
| Infraestrutura| Docker Compose                      |

## Fontes de dados externas

| Fonte           | Dados obtidos                                      |
|-----------------|----------------------------------------------------|
| Yahoo Finance   | Preço atual, variações dia/semana/mês/ano, nome    |
| Status Invest   | P/VP, Dividend Yield, segmento, recomendações      |
| Google News RSS | Notícias recentes, recomendações por detecção      |
| Funds Explorer  | Ranking de FIIs mais recomendados                  |
| Claude Haiku    | Análise de sentimento, pontos positivos/negativos  |

## Pré-requisitos

- Docker e Docker Compose instalados
- Chave de API da Anthropic ([console.anthropic.com](https://console.anthropic.com))

## Configuração

### 1. Clone o repositório

```bash
git clone <url-do-repositório>
cd fii-analyzer
```

### 2. Crie o arquivo `.env`

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=fii_analyzer
DB_USERNAME=fii_user
DB_PASSWORD=fii_pass
ANTHROPIC_API_KEY=sk-ant-...
```

> O arquivo `.env` está no `.gitignore`. Nunca o commite em repositórios públicos.

### 3. Suba os containers

```bash
docker compose up
```

O Docker Compose sobe automaticamente:
- **PostgreSQL 16** na porta `5432`
- **Backend Spring Boot** na porta `8080`
- **Frontend Next.js** na porta `3000` (se disponível)

Na primeira inicialização, o banco é criado e populado com **12 FIIs** e **5 carteiras recomendadas** de exemplo.

## Documentação da API

Acesse a interface Swagger UI após subir o projeto:

```
http://localhost:8080/swagger-ui.html
```

## Endpoints

### Fundos Imobiliários

| Método   | Rota                                          | Descrição                                                   |
|----------|-----------------------------------------------|-------------------------------------------------------------|
| `GET`    | `/api/v1/fundos-imobiliarios`                 | Lista todos os fundos com dados de mercado enriquecidos     |
| `GET`    | `/api/v1/fundos-imobiliarios/{codigo}`        | Busca fundo por ticker (fallback internet se não cadastrado)|
| `POST`   | `/api/v1/fundos-imobiliarios`                 | Cadastra novo fundo                                         |
| `PUT`    | `/api/v1/fundos-imobiliarios/{codigo}`        | Atualiza dados do fundo                                     |
| `DELETE` | `/api/v1/fundos-imobiliarios/{codigo}`        | Remove fundo                                                |
| `GET`    | `/api/v1/fundos-imobiliarios/{codigo}/analise`| Análise completa: mercado + notícias + recomendações        |
| `GET`    | `/api/v1/fundos-imobiliarios/{codigo}/analise-ia` | Análise de sentimento por Claude com base em notícias   |
| `GET`    | `/api/v1/fundos-imobiliarios/em-alta`         | FIIs em alta para analistas (busca internet + IA)           |

### Carteiras Recomendadas

| Método   | Rota                                                | Descrição                                           |
|----------|-----------------------------------------------------|-----------------------------------------------------|
| `GET`    | `/api/v1/carteiras-recomendadas`                    | Lista carteiras (filtros: `casaDeAnalise`, `mesReferencia`) |
| `GET`    | `/api/v1/carteiras-recomendadas/{id}`               | Busca carteira por ID                               |
| `POST`   | `/api/v1/carteiras-recomendadas`                    | Cria nova carteira recomendada                      |
| `PUT`    | `/api/v1/carteiras-recomendadas/{id}`               | Atualiza carteira                                   |
| `DELETE` | `/api/v1/carteiras-recomendadas/{id}`               | Remove carteira                                     |
| `GET`    | `/api/v1/carteiras-recomendadas/{id}/analise-ia`    | Análise consolidada da carteira por Claude           |

## Funcionamento dos principais recursos

### Listagem de fundos (`GET /api/v1/fundos-imobiliarios`)

Cada fundo retorna campos enriquecidos em paralelo:

- `precoAtual`, `pvp`, `dividendYield`, `variacaoMes` — Yahoo Finance + Status Invest
- `consenso` — calculado a partir das recomendações de analistas ou do P/VP
- `mencoesPositivas` — buzz extraído do endpoint `/em-alta`

### Busca de fundo não cadastrado (`GET /api/v1/fundos-imobiliarios/{codigo}`)

Se o ticker não estiver no banco de dados, o sistema tenta buscá-lo automaticamente na internet:

1. **Yahoo Finance** — confirma existência do ticker e extrai o nome
2. **Status Invest** — extrai segmento e nome mais descritivo
3. Retorna o fundo enriquecido normalmente (sem persistir no banco)
4. Retorna `404` apenas se o ticker não existir em nenhuma fonte

### FIIs em alta para analistas (`GET /api/v1/fundos-imobiliarios/em-alta`)

1. Busca em 4 queries no Google News RSS (recomendações, top picks, análises)
2. Faz scraping do ranking do Funds Explorer
3. Envia todos os textos para Claude processar
4. Claude identifica os tickers, classifica por segmento e extrai motivos e fontes

### Análise por IA de carteira (`GET /api/v1/carteiras-recomendadas/{id}/analise-ia`)

1. Coleta notícias de todos os FIIs da carteira
2. Envia composição + notícias em um único prompt para Claude
3. Retorna: sentimento geral, diversificação, pontos fortes/riscos, perspectiva e análise individual de cada fundo

## Cache

| Cache            | TTL         | Recurso                                   |
|------------------|-------------|-------------------------------------------|
| `dadosMercado`   | 15 minutos  | Preço, P/VP, DY, variações                |
| `noticias`       | 30 minutos  | Notícias por ticker (Google News)         |
| `recomendacoes`  | 120 minutos | Recomendações de analistas (Status Invest)|
| `fiisEmAlta`     | 60 minutos  | FIIs em alta para analistas               |

> A primeira chamada a `/api/v1/fundos-imobiliarios` ou `/em-alta` pode ser lenta (múltiplas requisições externas + IA). As chamadas seguintes dentro do TTL são servidas do cache.

> O prompt de sistema das análises por IA usa **prompt caching da Anthropic** (TTL 5 min), reduzindo custo e latência em chamadas consecutivas.

## Dados de exemplo (seed)

### Fundos cadastrados

| Código   | Nome                              | Tipo              | Segmento           |
|----------|-----------------------------------|-------------------|--------------------|
| HGLG11   | CSHG Logística FII                | Fundo de Tijolo   | Logística          |
| HGBS11   | CSHG Brasil Shopping FII          | Fundo de Tijolo   | Shoppings          |
| TRXF11   | TRX Real Estate Logística FII     | Fundo de Tijolo   | Renda Urbana       |
| GARE11   | Guardian Real Estate FII          | Fundo de Tijolo   | Renda Urbana       |
| VILG11   | Vinci Logística FII               | Fundo de Tijolo   | Logística          |
| XPML11   | XP Malls FII                      | Fundo de Tijolo   | Shoppings          |
| RBRX11   | RBR Alpha Multiestratégia FII     | Fundo Híbrido     | Híbrido            |
| BTLG11   | BTG Pactual Logística FII         | Fundo de Tijolo   | Logística          |
| KNCR11   | Kinea Rendimentos Imobiliários FII| Fundo de Papel    | Recebíveis         |
| HGRE11   | CSHG Real Estate FII              | Fundo de Tijolo   | Lajes Corporativas |
| KNRI11   | Kinea Renda Imobiliária FII       | Fundo de Tijolo   | Híbrido            |
| MXRF11   | Maxi Renda FII                    | Fundo de Papel    | Recebíveis         |

### Carteiras recomendadas

5 carteiras pré-cadastradas para abril/2026: **XP Investimentos**, **BTG Pactual**, **Itaú BBA**, **Genial Investimentos** e **Suno Research**.

## Estrutura do projeto

```
src/main/java/com/fii/
├── config/
│   └── AppConfig.java               # Beans: AnthropicClient, RestClient, CacheManager
├── controller/
│   ├── FundoImobiliarioController.java
│   ├── FiiAnaliseController.java
│   └── CarteiraRecomendadaController.java
├── service/
│   ├── FundoImobiliarioService.java  # CRUD + enriquecimento paralelo
│   ├── FiiDadosMercadoService.java   # Yahoo Finance + Status Invest
│   ├── FiiNoticiasService.java       # Google News RSS
│   ├── FiiRecomendacoesService.java  # Recomendações de analistas
│   ├── FiiAnaliseCompletaService.java
│   ├── FiiAnaliseIAService.java      # Análise individual por Claude
│   ├── FiisEmAltaService.java        # FIIs em alta (internet + IA)
│   ├── CarteiraRecomendadaService.java
│   └── CarteiraAnaliseIAService.java # Análise de carteira por Claude
├── dto/                              # Records Java (request/response)
├── entity/                           # Entidades JPA
├── repository/                       # Spring Data JPA
└── exception/
    └── GlobalExceptionHandler.java
```

## Variáveis de ambiente

| Variável           | Padrão       | Descrição                         |
|--------------------|--------------|-----------------------------------|
| `DB_HOST`          | `localhost`  | Host do PostgreSQL                |
| `DB_PORT`          | `5432`       | Porta do PostgreSQL               |
| `DB_NAME`          | `fii_analyzer` | Nome do banco de dados          |
| `DB_USERNAME`      | `fii_user`   | Usuário do banco                  |
| `DB_PASSWORD`      | `fii_pass`   | Senha do banco                    |
| `ANTHROPIC_API_KEY`| —            | Chave da API Anthropic (obrigatória para análises por IA) |

## Exemplos de uso

```bash
# Listar todos os fundos com dados de mercado
curl http://localhost:8080/api/v1/fundos-imobiliarios

# Buscar fundo cadastrado
curl http://localhost:8080/api/v1/fundos-imobiliarios/HGLG11

# Buscar fundo NÃO cadastrado (fallback internet)
curl http://localhost:8080/api/v1/fundos-imobiliarios/BCFF11

# Análise completa
curl http://localhost:8080/api/v1/fundos-imobiliarios/HGLG11/analise

# Análise por IA
curl http://localhost:8080/api/v1/fundos-imobiliarios/HGLG11/analise-ia

# FIIs em alta para analistas
curl http://localhost:8080/api/v1/fundos-imobiliarios/em-alta

# Listar carteiras recomendadas
curl http://localhost:8080/api/v1/carteiras-recomendadas

# Carteiras da XP do mês de abril
curl "http://localhost:8080/api/v1/carteiras-recomendadas?casaDeAnalise=XP%20Investimentos&mesReferencia=2026-04-01"

# Análise de IA para a carteira 1
curl http://localhost:8080/api/v1/carteiras-recomendadas/1/analise-ia
```

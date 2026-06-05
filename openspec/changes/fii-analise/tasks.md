## 1. Serviços de dados de mercado

- [x] 1.1 Implementar FiiDadosMercadoService com Yahoo Finance (preço, P/VP, variações dia/semana/mês/ano)
- [x] 1.2 Implementar FiiNoticiasService com Google News RSS (titulo, fonte, url, dataPublicacao)
- [x] 1.3 Implementar FiiRecomendacoesService com scraping Status Invest (analista, recomendacao, precoAlvo)
- [x] 1.4 Configurar cache Caffeine via AppConfig/CacheManager para dados de mercado

## 2. Análise completa e FIIs em alta

- [x] 2.1 Implementar FiiAnaliseCompletaService agregando os três serviços de dados
- [x] 2.2 Criar FiiAnaliseCompletaDTO com campos: dadosMercado, noticias, recomendacoes
- [x] 2.3 Implementar FiisEmAltaService com agrupamento por segmento e ordenação por variação
- [x] 2.4 Criar DTOs: FiisEmAltaDTO, FiiEmAltaPorSegmentoDTO, FiiEmAltaItemDTO

## 3. Análise com IA

- [x] 3.1 Implementar FiiAnaliseIAService com chamada Claude API baseada em dados de mercado coletados
- [x] 3.2 Criar FiiAnaliseIADTO com campos: resumo, pontosPositivos, pontosNegativos, recomendacao, nivelRisco
- [x] 3.3 Implementar FiiAnaliseController com endpoints: analise, analise-ia, em-alta

## 4. Melhorias identificadas (pendentes)

- [ ] 4.1 Paralelizar as três chamadas de análise (mercado, notícias, recomendações) com CompletableFuture
- [ ] 4.2 Definir e documentar TTL do cache Caffeine para dados de mercado (sugestão: 5 minutos)
- [ ] 4.3 Adicionar tratamento explícito para quando fontes externas retornam HTTP 429 (rate limit)

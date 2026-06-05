## 1. Entidade e persistência

- [x] 1.1 Criar entidade CarteiraImagemAnalise com campos: conversaId, usuario, sentimentoGeral, resumo, listas, analisadoEm
- [x] 1.2 Criar converters JPA: ListStringConverter e ListSugestaoFiiConverter
- [x] 1.3 Criar CarteiraImagemAnaliseRepository com findByUsuarioOrderByAnalisadoEmDesc
- [x] 1.4 Criar migration Flyway para tabela carteira_imagem_analises

## 2. Processamento assíncrono

- [x] 2.1 Implementar AnaliseJobStore (ConcurrentHashMap em memória) com estados: PROCESSANDO, CONCLUIDO, ERRO
- [x] 2.2 Implementar CarteiraImagemProcessadorService com processamento em thread separada
- [x] 2.3 Implementar validação de formato (JPEG/PNG/GIF/WEBP) e tamanho (máx 5 MB) das imagens
- [x] 2.4 Criar AnaliseIniciadaDTO (jobId) e AnaliseStatusDTO (status, conversaId, resultado)

## 3. Integração com Claude Vision

- [x] 3.1 Implementar CarteiraImagemAnaliseService com chamada Claude Vision (claude-sonnet-4-6)
- [x] 3.2 Configurar prompt caching no system prompt (cache_control: ephemeral)
- [x] 3.3 Injetar últimas 3 análises do usuário no user message antes de enviar ao Claude
- [x] 3.4 Criar CarteiraImagemAnaliseDTO e SugestaoFiiDTO com todos os campos de resultado

## 4. Chat contextualizado

- [x] 4.1 Implementar ConversaContexto para manter histórico de mensagens em memória por conversaId
- [x] 4.2 Implementar CarteiraChatService com system prompt baseado na análise original
- [x] 4.3 Criar CarteiraChatRequestDTO (pergunta) e CarteiraChatResponseDTO (resposta)

## 5. Controller

- [x] 5.1 Implementar CarteiraImagemController com todos os endpoints (analise-imagem, status, historico, busca, chat)

## 6. Melhorias identificadas (pendentes)

- [ ] 6.1 Definir limite máximo de imagens por requisição e documentar (sugestão: 5)
- [ ] 6.2 Implementar TTL e limpeza de jobs antigos no AnaliseJobStore
- [ ] 6.3 Garantir retorno 404 (não 403) ao acessar análise de outro usuário para evitar enumeração
- [ ] 6.4 Adicionar limite de mensagens no histórico do chat para evitar contexto excessivo

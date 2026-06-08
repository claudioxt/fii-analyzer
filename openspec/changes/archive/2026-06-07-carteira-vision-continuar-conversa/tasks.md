## 1. Modelo de dados e migrations

- [x] 1.1 Adicionar tabela `carteira_imagem_dados` ao `schema.sql` (`conversa_id` FK 1:1 para `carteira_imagem_analises`, `imagem_bytes bytea`, `media_type`, `criado_em`) — projeto usa `spring.sql.init` com `schema.sql`, não Flyway
- [x] 1.2 Adicionar tabela `carteira_chat_mensagens` ao `schema.sql` (`id`, `conversa_id` FK, `role` [USER|ASSISTANT], `conteudo TEXT`, `criado_em`, índice por `conversa_id` + `criado_em`)
- [x] 1.3 Criar entidades JPA `CarteiraImagemDados` e `CarteiraChatMensagem` + repositórios Spring Data correspondentes
- [x] 1.4 Atualizar `application-test.yml`/configuração de testes para que as novas tabelas existam no H2 (se necessário) — `ddl-auto: create-drop` já gera as tabelas a partir das novas entidades; nenhuma alteração necessária

## 2. Persistência da imagem de referência

- [x] 2.1 Alterar `CarteiraImagemProcessadorService` para persistir os bytes e o `mediaType` da primeira imagem (referência) junto com `persistirAnalise`, vinculados ao `conversaId`
- [x] 2.2 Garantir que falhas ao persistir a imagem sejam logadas sem interromper a resposta da análise (mesmo padrão defensivo de `persistirAnalise`)

## 3. Persistência do histórico de mensagens

- [x] 3.1 Alterar `CarteiraChatService.perguntar` para persistir a pergunta do usuário e a resposta do Claude como linhas em `carteira_chat_mensagens`, em ordem cronológica, após gerar a resposta
- [x] 3.2 Garantir que falhas ao persistir mensagens sejam logadas sem interromper a resposta ao usuário

## 4. Reconstrução de contexto a partir do banco (cache miss)

- [x] 4.1 Alterar `recuperarContexto` em `CarteiraChatService` para, em caso de cache miss, buscar `CarteiraImagemAnalise` + `CarteiraImagemDados` pelo `conversaId` (validando que pertence ao usuário autenticado) e reconstruir um `ConversaContexto`
- [x] 4.2 Reaproveitar/extrair a lógica de `construirContextoTextual` (hoje em `CarteiraImagemProcessadorService`) para montar o texto da análise a partir da entidade persistida durante a reconstrução
- [x] 4.3 Repopular o cache `conversaCarteira` com o contexto reconstruído, para acelerar interações subsequentes na mesma janela
- [x] 4.4 Ajustar a mensagem/condição de erro 404 para ocorrer apenas quando não há análise/imagem persistida recuperável para o `conversaId` (e não simplesmente por cache miss)

## 5. Uso do histórico persistido na conversa

- [x] 5.1 Alterar `CarteiraChatService.perguntar` para carregar as últimas N mensagens persistidas de `carteira_chat_mensagens` e usá-las como histórico de contexto enviado ao Claude (mantendo compatibilidade com o campo opcional `historico` do request)
- [x] 5.2 Definir e aplicar o limite N de mensagens (alinhado ao requisito existente de "histórico limitado") tanto na consulta ao banco quanto no envio ao Claude

## 6. Exposição do histórico de mensagens via API

- [x] 6.1 Criar DTO de mensagem persistida (ex. `CarteiraChatMensagemDTO` com `role`, `conteudo`, `criadoEm`)
- [x] 6.2 Expor as mensagens da conversa em `GET /api/v1/carteira/analise-imagem/{conversaId}` (ou endpoint dedicado, conforme decisão da questão aberta em design.md), retornando lista vazia quando não houver mensagens
- [x] 6.3 Garantir que a consulta retorne HTTP 404 quando a conversa pertencer a outro usuário ou não existir

## 7. Testes

- [x] 7.1 Testes de integração (H2) para `perguntar` com cache miss e análise persistida, validando reconstrução do contexto sem reenvio de imagem — `CarteiraImagemChatE2ETest.perguntar_quandoCacheMissEAnaliseEImagemPersistidas_reconstroiContextoSemImagem`
- [x] 7.2 Testes de integração para o fluxo de persistência de mensagens (pergunta + resposta) e leitura do histórico em chamadas subsequentes — `perguntar_quandoCacheMissEAnaliseEImagemPersistidas_reconstroiContextoSemImagem` e `perguntar_emConversaJaIniciada_usaHistoricoPersistidoNaProximaPergunta`
- [x] 7.3 Testes para o endpoint de consulta de histórico de mensagens (conversa com mensagens, sem mensagens, e de outro usuário) — `buscarPorConversa_retornaMensagensJaTrocadas`, `buscarPorConversa_semMensagens_retornaListaVazia`, `buscarPorConversa_deOutroUsuario_retorna404`
- [x] 7.4 Teste garantindo que o erro 404 ocorre apenas quando não há dados recuperáveis (e não por simples expiração de cache) — `perguntar_quandoConversaPertenceAOutroUsuario_retorna404`, `perguntar_quandoConversaNaoExiste_retorna404`

## 8. Documentação e configuração

- [x] 8.1 Atualizar a memória/documentação do projeto descrevendo o novo fluxo de persistência e recuperação de contexto de conversa — seção "Chat de continuação de conversa" adicionada em `project_fii_analyzer.md`
- [x] 8.2 Revisar comentários/configuração do cache `conversaCarteira` em `AppConfig` para refletir seu novo papel de camada de otimização (não mais fonte única de verdade) — comentário adicionado em `AppConfig.cacheManager()`

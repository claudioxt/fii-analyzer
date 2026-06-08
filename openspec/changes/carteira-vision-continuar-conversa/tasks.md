## 1. Modelo de dados e migrations

- [ ] 1.1 Criar migration Flyway para a tabela `carteira_imagem_dados` (`conversa_id` FK 1:1 para `carteira_imagem_analises`, `imagem_bytes bytea`, `media_type`, `criado_em`)
- [ ] 1.2 Criar migration Flyway para a tabela `carteira_chat_mensagens` (`id`, `conversa_id` FK, `role` [USER|ASSISTANT], `conteudo TEXT`, `criado_em`, índice por `conversa_id` + `criado_em`)
- [ ] 1.3 Criar entidades JPA `CarteiraImagemDados` e `CarteiraChatMensagem` + repositórios Spring Data correspondentes
- [ ] 1.4 Atualizar `application-test.yml`/configuração de testes para que as novas tabelas existam no H2 (se necessário)

## 2. Persistência da imagem de referência

- [ ] 2.1 Alterar `CarteiraImagemProcessadorService` para persistir os bytes e o `mediaType` da primeira imagem (referência) junto com `persistirAnalise`, vinculados ao `conversaId`
- [ ] 2.2 Garantir que falhas ao persistir a imagem sejam logadas sem interromper a resposta da análise (mesmo padrão defensivo de `persistirAnalise`)

## 3. Persistência do histórico de mensagens

- [ ] 3.1 Alterar `CarteiraChatService.perguntar` para persistir a pergunta do usuário e a resposta do Claude como linhas em `carteira_chat_mensagens`, em ordem cronológica, após gerar a resposta
- [ ] 3.2 Garantir que falhas ao persistir mensagens sejam logadas sem interromper a resposta ao usuário

## 4. Reconstrução de contexto a partir do banco (cache miss)

- [ ] 4.1 Alterar `recuperarContexto` em `CarteiraChatService` para, em caso de cache miss, buscar `CarteiraImagemAnalise` + `CarteiraImagemDados` pelo `conversaId` (validando que pertence ao usuário autenticado) e reconstruir um `ConversaContexto`
- [ ] 4.2 Reaproveitar/extrair a lógica de `construirContextoTextual` (hoje em `CarteiraImagemProcessadorService`) para montar o texto da análise a partir da entidade persistida durante a reconstrução
- [ ] 4.3 Repopular o cache `conversaCarteira` com o contexto reconstruído, para acelerar interações subsequentes na mesma janela
- [ ] 4.4 Ajustar a mensagem/condição de erro 404 para ocorrer apenas quando não há análise/imagem persistida recuperável para o `conversaId` (e não simplesmente por cache miss)

## 5. Uso do histórico persistido na conversa

- [ ] 5.1 Alterar `CarteiraChatService.perguntar` para carregar as últimas N mensagens persistidas de `carteira_chat_mensagens` e usá-las como histórico de contexto enviado ao Claude (mantendo compatibilidade com o campo opcional `historico` do request)
- [ ] 5.2 Definir e aplicar o limite N de mensagens (alinhado ao requisito existente de "histórico limitado") tanto na consulta ao banco quanto no envio ao Claude

## 6. Exposição do histórico de mensagens via API

- [ ] 6.1 Criar DTO de mensagem persistida (ex. `CarteiraChatMensagemDTO` com `role`, `conteudo`, `criadoEm`)
- [ ] 6.2 Expor as mensagens da conversa em `GET /api/v1/carteira/analise-imagem/{conversaId}` (ou endpoint dedicado, conforme decisão da questão aberta em design.md), retornando lista vazia quando não houver mensagens
- [ ] 6.3 Garantir que a consulta retorne HTTP 404 quando a conversa pertencer a outro usuário ou não existir

## 7. Testes

- [ ] 7.1 Testes de integração (H2) para `perguntar` com cache miss e análise persistida, validando reconstrução do contexto sem reenvio de imagem
- [ ] 7.2 Testes de integração para o fluxo de persistência de mensagens (pergunta + resposta) e leitura do histórico em chamadas subsequentes
- [ ] 7.3 Testes para o endpoint de consulta de histórico de mensagens (conversa com mensagens, sem mensagens, e de outro usuário)
- [ ] 7.4 Teste garantindo que o erro 404 ocorre apenas quando não há dados recuperáveis (e não por simples expiração de cache)

## 8. Documentação e configuração

- [ ] 8.1 Atualizar a memória/documentação do projeto descrevendo o novo fluxo de persistência e recuperação de contexto de conversa
- [ ] 8.2 Revisar comentários/configuração do cache `conversaCarteira` em `AppConfig` para refletir seu novo papel de camada de otimização (não mais fonte única de verdade)

## Context

O fluxo atual de chat sobre carteira (`CarteiraImagemProcessadorService` → `CarteiraChatService`) depende inteiramente do cache Caffeine `conversaCarteira` (TTL 60 min, máx. 20 entradas, configurado em `AppConfig`) para guardar um `ConversaContexto` (imagem em base64 + texto da análise). A entidade `CarteiraImagemAnalise` já persiste o resultado estruturado da análise (resumo, pontos positivos/negativos, sugestões, `nomeArquivo`) vinculado ao `Usuario`, mas **não** persiste os bytes da imagem nem o histórico de mensagens trocadas — essas informações só existem em memória, no cache, durante a janela de TTL.

O projeto é backend-only, Spring Boot 4 + PostgreSQL 16 (Docker) + Flyway, sem integração com storage externo (S3, etc.) configurada hoje.

## Goals / Non-Goals

**Goals:**
- Permitir que o usuário continue uma conversa sobre uma análise antiga mesmo após o cache expirar, ser despejado por LRU, ou a aplicação reiniciar.
- Persistir o histórico de mensagens da conversa no banco, para que o cliente não precise reenviar todo o `historico` a cada requisição nem perder o histórico ao trocar de dispositivo/sessão.
- Manter o caminho "quente" (cache) como otimização de performance e custo de tokens — sem regressão na experiência atual quando o cache está presente.

**Non-Goals:**
- Não vamos introduzir um serviço de storage de objetos externo (S3/MinIO) nesta mudança — ficará registrado como possível evolução futura.
- Não vamos implementar edição/exclusão de mensagens do histórico, nem branching de conversas (múltiplos caminhos a partir do mesmo ponto).
- Não vamos mudar o modelo de IA, prompts de análise inicial ou o fluxo assíncrono de processamento de imagem (`AnaliseJobStore`/`@Async`).

## Decisions

### 1. Onde guardar os bytes da imagem: coluna BLOB no PostgreSQL (`bytea`)
Optamos por persistir a imagem de referência como `bytea` em uma nova tabela `carteira_imagem_dados` (1:1 com `carteira_imagem_analises`, FK por `conversa_id`), em vez de:
- **Object storage externo (S3/MinIO)**: adicionaria uma nova dependência de infraestrutura e configuração (credenciais, bucket, lifecycle) que o projeto não possui hoje — desproporcional ao volume esperado (uma imagem por análise, já limitada a 5 MB).
- **Filesystem local**: funciona em desenvolvimento, mas é frágil em produção (containers efêmeros, múltiplas réplicas não compartilham disco) e exigiria gerenciar paths e limpeza manualmente.

Manter em uma tabela separada (e não como coluna na própria `CarteiraImagemAnalise`) evita carregar os bytes da imagem em consultas que só precisam dos metadados (ex.: listagem de histórico).

### 2. Histórico de mensagens: nova tabela `carteira_chat_mensagens`
Cada pergunta e resposta passa a ser persistida como uma linha (`conversa_id`, `role` [USER|ASSISTANT], `conteudo`, `criado_em`), em ordem cronológica. Isso substitui a dependência do campo `historico` enviado pelo cliente a cada requisição (`CarteiraChatRequestDTO.historico`) como única fonte de verdade — o campo continua aceito por compatibilidade, mas o sistema persiste e usa o histórico armazenado como base.

Alternativa descartada: serializar o histórico como JSON/TEXT em uma coluna da própria análise (como já é feito com `ListStringConverter` para outras listas). Rejeitada porque o histórico cresce de forma não limitada ao longo da conversa — uma tabela permite paginação/limitação por consulta sem reescrever um blob inteiro a cada nova mensagem.

### 3. Estratégia de recuperação de contexto: cache-first com fallback para o banco
`CarteiraChatService.recuperarContexto` passa a:
1. Tentar o cache `conversaCarteira` (caminho atual, sem mudanças de performance no caso comum).
2. Em caso de **cache miss**, buscar `CarteiraImagemAnalise` + `carteira_imagem_dados` pelo `conversaId`, reconstruir o `ConversaContexto` (imagem em base64 + texto da análise reaproveitando `construirContextoTextual`/equivalente) e **repopular o cache** para acelerar interações subsequentes.
3. Carregar as últimas N mensagens persistidas de `carteira_chat_mensagens` para montar o histórico enviado ao Claude (substituindo/complementando `request.historico()`).

Isso elimina o `EntityNotFoundException("Conversa não encontrada ou expirada...")` para qualquer `conversaId` que tenha uma análise persistida — o erro 404 passa a ocorrer apenas quando o `conversaId` realmente não existe (ou não pertence ao usuário autenticado).

### 4. Persistência assíncrona das mensagens
Cada interação (pergunta do usuário + resposta do Claude) é persistida em `carteira_chat_mensagens` logo após a resposta ser gerada, dentro do mesmo fluxo síncrono de `perguntar` (sem necessidade de `@Async`, já que o volume por requisição é de duas linhas). Falhas de persistência do histórico são logadas mas não devem quebrar a resposta ao usuário (mesmo padrão defensivo já usado em `persistirAnalise`).

### 5. Exposição do histórico via API
`GET /api/v1/carteira/analise-imagem/{conversaId}` passa a incluir as mensagens já trocadas (ou um novo endpoint dedicado, ex. `GET /api/v1/carteira/{conversaId}/mensagens`, caso o payload fique grande). A decisão final de payload único vs. endpoint dedicado fica registrada como questão aberta (ver abaixo) e deve ser resolvida na fase de specs/tasks.

## Risks / Trade-offs

- **[Risco] Crescimento do banco com imagens em `bytea`** → Mitigação: manter o limite de 5 MB por imagem já existente; considerar política de retenção (ex.: manter apenas a imagem das últimas K conversas por usuário, ou TTL de expurgo) como follow-up — registrado como questão aberta.
- **[Risco] Migração de dados existentes**: conversas já persistidas em `CarteiraImagemAnalise` antes desta mudança não terão imagem nem histórico salvos (cache provavelmente já expirou) → Mitigação: o fallback para o banco simplesmente não encontrará dados de imagem para essas conversas antigas; o sistema deve retornar 404/mensagem amigável indicando que aquela conversa específica não pode ser retomada (em vez de erro genérico), preservando o comportamento atual só para esses casos legados.
- **[Trade-off] Tabela separada para a imagem (`bytea`) vs. simplicidade de uma única tabela** → Optamos por separar para não penalizar consultas de listagem/histórico que não precisam dos bytes da imagem.
- **[Risco] Tamanho do histórico em conversas longas** → Mitigação: já existe o requisito "Histórico limitado para evitar contexto excessivo" na spec `chat-carteira`; a consulta ao banco deve aplicar o mesmo limite (top-N mensagens mais recentes) usado hoje no envio ao Claude.

## Migration Plan

1. Criar migration Flyway para as novas tabelas (`carteira_imagem_dados` e `carteira_chat_mensagens`), com FKs para `carteira_imagem_analises`/`conversa_id`.
2. Implementar a persistência da imagem no fluxo de `CarteiraImagemProcessadorService` (gravar junto com `persistirAnalise`).
3. Implementar a persistência de mensagens em `CarteiraChatService.perguntar`.
4. Implementar o fallback de reconstrução de contexto a partir do banco (cache miss).
5. Expor o histórico de mensagens na API.
6. Rollback: as novas tabelas podem ser removidas via migration reversa sem afetar `carteira_imagem_analises`; o comportamento de chat volta a depender só do cache se as tabelas forem removidas (compatível com o estado atual).

## Open Questions

- O histórico de mensagens deve ser retornado junto de `GET /analise-imagem/{conversaId}` (payload único) ou em um endpoint dedicado e paginável (ex. `GET /{conversaId}/mensagens`)?
- Deve haver alguma política de retenção/expurgo das imagens persistidas (por usuário, por tempo, por quantidade de conversas), ou isso fica fora do escopo inicial?
- Conversas antigas (anteriores a esta mudança, sem imagem/histórico persistidos) devem ser sinalizadas de forma diferenciada na listagem de histórico (ex. flag `retomavel: false`) para que o cliente saiba que precisa iniciar uma nova análise?

# chat-carteira

## Purpose

TBD — Capability responsável pelo chat contextualizado sobre análises de imagens de carteira de FIIs, permitindo que o usuário continue a conversa com o Claude com base no contexto da análise original, mantendo histórico persistido de mensagens e reconstruindo o contexto (imagem de referência e resumo da análise) a partir dos dados persistidos quando necessário.

## Requirements

### Requirement: Chat contextualizado sobre análise de imagem
O sistema SHALL permitir que o usuário faça perguntas em linguagem natural sobre uma análise de carteira já realizada, com o Claude respondendo com base no contexto da análise original — independentemente de o contexto da conversa ainda estar presente no cache em memória, desde que a análise correspondente tenha sido persistida.

#### Scenario: Pergunta sobre análise existente
- **WHEN** qualquer cliente envia `POST /api/v1/carteira/{conversaId}/perguntar` com `{ pergunta: "Por que você recomendou vender XPML11?" }`
- **THEN** o sistema responde HTTP 200 com CarteiraChatResponseDTO contendo a resposta contextualizada do Claude

#### Scenario: Continuar conversa após expiração do cache
- **WHEN** o cliente envia uma pergunta para um `conversaId` cuja análise foi persistida, mas cujo contexto não está mais presente no cache `conversaCarteira` (expirado, despejado por LRU, ou após reinício da aplicação)
- **THEN** o sistema reconstrói o contexto da conversa (imagem de referência e resumo da análise) a partir dos dados persistidos no banco e responde HTTP 200, sem exigir que o cliente reenvie a imagem

#### Scenario: ConversaId inexistente ou sem dados recuperáveis
- **WHEN** o cliente envia pergunta para um `conversaId` que não existe, não pertence ao usuário autenticado, ou não possui análise/imagem persistida que permita reconstruir o contexto
- **THEN** o sistema retorna HTTP 404 com mensagem indicando que a conversa não pode ser retomada

#### Scenario: Pergunta vazia
- **WHEN** o cliente envia requisição com campo pergunta vazio ou ausente
- **THEN** o sistema retorna HTTP 400

---

### Requirement: Manutenção do contexto histórico da conversa
O sistema SHALL persistir o histórico de mensagens (perguntas e respostas) trocadas em cada conversa, de modo que o Claude tenha contexto das interações anteriores e o usuário possa retomar a conversa de onde parou em qualquer sessão, sem depender do cliente reenviar o histórico completo a cada requisição.

#### Scenario: Persistência de cada interação
- **WHEN** o sistema gera uma resposta para uma pergunta do usuário em uma conversa
- **THEN** tanto a pergunta quanto a resposta são persistidas, associadas ao `conversaId`, em ordem cronológica

#### Scenario: Continuação usando histórico persistido
- **WHEN** o cliente faz uma nova pergunta em uma conversa que já possui mensagens persistidas (de uma sessão anterior ou de outro dispositivo)
- **THEN** o sistema carrega o histórico persistido da conversa e o utiliza como contexto enviado ao Claude, complementando ou substituindo o campo `historico` opcionalmente enviado pelo cliente

#### Scenario: Histórico limitado para evitar contexto excessivo
- **WHEN** o histórico persistido da conversa cresce muito
- **THEN** o sistema utiliza apenas as N mensagens mais recentes ao montar o contexto enviado ao Claude, descartando as mais antigas

---

### Requirement: Análise original como contexto do sistema
O sistema SHALL incluir o resultado da análise de imagem original como parte do system prompt do Claude, garantindo que todas as respostas do chat sejam fundamentadas nos dados analisados — recuperando esse contexto do banco de dados quando não estiver disponível em cache.

#### Scenario: System prompt com análise original
- **WHEN** o sistema prepara a chamada ao Claude para o chat
- **THEN** o system prompt contém o resumo completo da análise original (sentimento, pontos positivos/negativos, sugestões), seja a partir do cache ou reconstruído a partir dos dados persistidos

#### Scenario: Resposta fora do escopo da análise
- **WHEN** o usuário faz pergunta não relacionada à análise de carteira
- **THEN** o Claude responde de forma educada redirecionando para o contexto de análise de investimentos

---

### Requirement: Persistência da imagem de referência da conversa
O sistema SHALL persistir a imagem de referência utilizada na análise original de cada conversa, de modo que ela possa ser reutilizada em perguntas futuras do chat sem que o usuário precise reenviá-la.

#### Scenario: Imagem persistida ao concluir a análise
- **WHEN** uma análise de imagem de carteira é concluída e associada a um `conversaId`
- **THEN** o sistema persiste os bytes da imagem de referência (a primeira imagem enviada, no caso de múltiplas imagens) vinculados a esse `conversaId`

#### Scenario: Reuso da imagem persistida no chat
- **WHEN** o sistema precisa montar o contexto de uma conversa para responder uma pergunta e o cache não contém a imagem
- **THEN** o sistema utiliza a imagem persistida vinculada ao `conversaId` para montar o bloco de imagem enviado ao Claude

---

### Requirement: Consulta do histórico de mensagens de uma conversa
O sistema SHALL permitir que o usuário autenticado consulte as mensagens (perguntas e respostas) já trocadas em uma conversa de análise de carteira, para retomar o diálogo a partir do ponto em que parou.

#### Scenario: Consulta de conversa com mensagens existentes
- **WHEN** o usuário autenticado consulta uma conversa pelo seu `conversaId` e existem mensagens persistidas para essa conversa
- **THEN** o sistema retorna as mensagens em ordem cronológica, identificando o autor de cada uma (usuário ou assistente) e o momento em que foi enviada

#### Scenario: Consulta de conversa sem mensagens
- **WHEN** o usuário consulta uma conversa que ainda não teve nenhuma pergunta respondida pelo chat
- **THEN** o sistema retorna a lista de mensagens vazia, sem erro

#### Scenario: Consulta de conversa de outro usuário
- **WHEN** um usuário autenticado tenta consultar as mensagens de uma conversa que pertence a outro usuário
- **THEN** o sistema retorna HTTP 404

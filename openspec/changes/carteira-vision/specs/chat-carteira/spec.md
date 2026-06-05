## ADDED Requirements

### Requirement: Chat contextualizado sobre análise de imagem
O sistema SHALL permitir que o usuário faça perguntas em linguagem natural sobre uma análise de carteira já realizada, com o Claude respondendo com base no contexto da análise original.

#### Scenario: Pergunta sobre análise existente
- **WHEN** qualquer cliente envia `POST /api/v1/carteira/{conversaId}/perguntar` com `{ pergunta: "Por que você recomendou vender XPML11?" }`
- **THEN** o sistema responde HTTP 200 com CarteiraChatResponseDTO contendo a resposta contextualizada do Claude

#### Scenario: ConversaId inexistente
- **WHEN** o cliente envia pergunta para conversaId que não existe
- **THEN** o sistema retorna HTTP 404

#### Scenario: Pergunta vazia
- **WHEN** o cliente envia requisição com campo pergunta vazio ou ausente
- **THEN** o sistema retorna HTTP 400

---

### Requirement: Manutenção do contexto histórico da conversa
O sistema SHALL manter o histórico de mensagens trocadas no chat para que o Claude tenha contexto das perguntas e respostas anteriores da mesma sessão.

#### Scenario: Segunda pergunta na mesma conversa
- **WHEN** o cliente faz uma segunda pergunta no mesmo conversaId
- **THEN** o sistema inclui as mensagens anteriores (pergunta + resposta) no histórico enviado ao Claude

#### Scenario: Histórico limitado para evitar contexto excessivo
- **WHEN** o histórico da conversa cresce muito
- **THEN** o sistema mantém as N mensagens mais recentes, descartando as mais antigas para controlar o tamanho do contexto

---

### Requirement: Análise original como contexto do sistema
O sistema SHALL incluir o resultado da análise de imagem original como parte do system prompt do Claude, garantindo que todas as respostas do chat sejam fundamentadas nos dados analisados.

#### Scenario: System prompt com análise original
- **WHEN** o sistema prepara a chamada ao Claude para o chat
- **THEN** o system prompt contém o resumo completo da análise original (sentimento, pontos positivos/negativos, sugestões)

#### Scenario: Resposta fora do escopo da análise
- **WHEN** o usuário faz pergunta não relacionada à análise de carteira
- **THEN** o Claude responde de forma educada redirecionando para o contexto de análise de investimentos

## ADDED Requirements

### Requirement: Análise de carteira por imagens
O sistema SHALL analisar imagens de extratos ou carteiras de investimento enviadas pelo usuário autenticado, gerando um relatório estruturado com sentimento, pontos positivos/negativos e sugestões.

#### Scenario: Upload de imagens válidas
- **WHEN** um usuário autenticado envia `POST /api/v1/carteira/analise-imagem` com uma ou mais imagens JPEG/PNG/GIF/WEBP até 5 MB cada
- **THEN** o sistema aceita as imagens, inicia processamento assíncrono e retorna HTTP 202 com `{ jobId: "<uuid>" }`

#### Scenario: Imagem acima do limite de tamanho
- **WHEN** o usuário envia imagem maior que 5 MB
- **THEN** o sistema retorna HTTP 400 indicando o limite excedido

#### Scenario: Formato de imagem não suportado
- **WHEN** o usuário envia arquivo que não é JPEG, PNG, GIF ou WEBP
- **THEN** o sistema retorna HTTP 400 indicando formato não suportado

#### Scenario: Requisição sem autenticação
- **WHEN** uma requisição não autenticada tenta enviar imagem para análise
- **THEN** o sistema retorna HTTP 401

---

### Requirement: Processamento assíncrono com status de job
O sistema SHALL processar a análise de imagem de forma assíncrona e disponibilizar endpoint de consulta de status.

#### Scenario: Consulta de job em processamento
- **WHEN** o cliente envia `GET /api/v1/carteira/analise-imagem/status/{jobId}` e o job ainda está sendo processado
- **THEN** o sistema retorna HTTP 200 com `{ status: "PROCESSANDO" }`

#### Scenario: Consulta de job concluído
- **WHEN** o processamento terminou com sucesso
- **THEN** o sistema retorna HTTP 200 com `{ status: "CONCLUIDO", conversaId: "<id>", resultado: CarteiraImagemAnaliseDTO }`

#### Scenario: Consulta de job com erro
- **WHEN** o processamento falhou
- **THEN** o sistema retorna HTTP 200 com `{ status: "ERRO", mensagem: "<descrição do erro>" }`

#### Scenario: Job inexistente
- **WHEN** o cliente consulta jobId que não existe
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Resultado da análise de imagem
O sistema SHALL retornar análise estruturada com os seguintes campos obrigatórios: sentimentoGeral, resumo, pontosPositivos, pontosNegativos, pontosAtencao, ativosParaComprar, ativosParaVender, proximosAportes, recomendacaoGeral.

#### Scenario: Campos obrigatórios presentes
- **WHEN** o Claude conclui a análise das imagens
- **THEN** CarteiraImagemAnaliseDTO contém todos os campos acima, sendo listas vazias para os campos sem ocorrências

#### Scenario: Sugestão de ativo com justificativa
- **WHEN** o Claude identifica ativos para comprar ou vender
- **THEN** cada sugestão contém `codigo` (ticker) e `justificativa` textual

---

### Requirement: Histórico de análises por usuário
O sistema SHALL persistir cada análise vinculada ao usuário autenticado e disponibilizar o histórico.

#### Scenario: Listar histórico
- **WHEN** um usuário autenticado envia `GET /api/v1/carteira/analise-imagem/historico`
- **THEN** o sistema retorna lista de análises do usuário, ordenadas da mais recente para a mais antiga

#### Scenario: Buscar análise específica por conversaId
- **WHEN** o usuário envia `GET /api/v1/carteira/analise-imagem/{conversaId}`
- **THEN** o sistema retorna a análise correspondente, se pertencer ao usuário autenticado

#### Scenario: Isolamento entre usuários
- **WHEN** usuário A tenta acessar conversaId pertencente ao usuário B
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Contexto histórico injetado na análise
O sistema SHALL injetar as últimas 3 análises anteriores do usuário como contexto para o Claude, permitindo análise comparativa evolutiva.

#### Scenario: Usuário com histórico anterior
- **WHEN** o usuário tem análises anteriores e envia nova imagem
- **THEN** o sistema carrega as últimas 3 análises e as inclui no user message enviado ao Claude

#### Scenario: Usuário sem histórico
- **WHEN** é a primeira análise do usuário
- **THEN** o sistema envia o prompt sem contexto histórico, sem erro

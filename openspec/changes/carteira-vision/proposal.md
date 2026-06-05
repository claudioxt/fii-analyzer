## Why

Investidores frequentemente têm prints de extrato ou carteira em imagem. O sistema deve analisar essas imagens com visão computacional (Claude Vision) e permitir um chat contextualizado para tirar dúvidas sobre a análise recebida.

## What Changes

- Endpoint autenticado `POST /api/v1/carteira/analise-imagem` aceita múltiplas imagens (multipart) e retorna jobId (processamento assíncrono)
- Endpoint `GET /api/v1/carteira/analise-imagem/status/{jobId}` consulta status do job
- Endpoint `GET /api/v1/carteira/analise-imagem/historico` retorna histórico de análises do usuário autenticado
- Endpoint `GET /api/v1/carteira/analise-imagem/{conversaId}` retorna análise específica
- Endpoint `POST /api/v1/carteira/{conversaId}/perguntar` permite chat contextualizado sobre uma análise

## Capabilities

### New Capabilities

- `analise-imagem-carteira`: Análise de carteira por imagens via Claude Vision com histórico por usuário
- `chat-carteira`: Chat conversacional contextualizado sobre análise de imagem já realizada

### Modified Capabilities

## Impact

- `com.fii.entity.CarteiraImagemAnalise`: entidade com histórico de análises vinculadas ao usuário
- `com.fii.service`: CarteiraImagemAnaliseService, CarteiraImagemProcessadorService, CarteiraChatService, AnaliseJobStore, ConversaContexto
- `com.fii.controller.CarteiraImagemController`
- `com.fii.entity.converter`: ListStringConverter, ListSugestaoFiiConverter
- Integração: Claude API (claude-sonnet-4-6) com vision + prompt caching no system prompt
- Banco: tabela `carteira_imagem_analises`
- Limite: arquivos JPEG/PNG/GIF/WEBP até 5 MB por imagem

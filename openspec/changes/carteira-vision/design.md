## Context

Análise de carteira por imagens usa Claude Vision para interpretar prints de extratos. O processamento é assíncrono pois chamadas multimodais ao Claude podem levar vários segundos. O sistema mantém histórico de análises por usuário e permite chat contextualizado sobre cada análise.

## Goals / Non-Goals

**Goals:**
- Upload de múltiplas imagens com processamento assíncrono via job
- Análise por Claude Vision com resultado estruturado
- Histórico de análises vinculado ao usuário autenticado
- Chat sobre análise já realizada com histórico de conversa

**Non-Goals:**
- OCR independente (delegado ao Claude)
- Análise de PDFs (apenas imagens)
- Compartilhamento de análises entre usuários

## Decisions

**Processamento assíncrono com AnaliseJobStore em memória**
O `POST /analise-imagem` retorna imediatamente um `jobId`. O `CarteiraImagemProcessadorService` processa em thread separada. O `AnaliseJobStore` mantém estado dos jobs em memória (ConcurrentHashMap). Trade-off: jobs são perdidos em restart — aceitável pois o resultado persiste em `CarteiraImagemAnalise`.

**Persistência em CarteiraImagemAnalise com `@AuthenticationPrincipal`**
Cada análise concluída é salva no banco vinculada ao `Usuario`. Isso garante que o histórico sobrevive a restarts. O `conversaId` é UUID gerado no início do job.

**Contexto histórico injetado no user message**
As últimas 3 análises do usuário são carregadas do banco e injetadas no user message (não no system prompt) antes de chamar o Claude. Mantém o system prompt em cache (prompt caching do Claude) enquanto o contexto variável fica no user message.

**Prompt caching no system prompt**
O system prompt de análise de carteira é estático e volumoso — marcado com `cache_control: ephemeral` para ativar o prompt caching da Claude API. Reduz custo e latência em chamadas repetidas.

**ConversaContexto para histórico de chat em memória**
O histórico de mensagens do chat (`CarteiraChatService`) é mantido em `ConversaContexto` (em memória, por conversaId). Trade-off: histórico de chat perdido em restart — diferente do resultado da análise, que é persistido.

**claude-sonnet-4-6 para análise e chat**
Modelo com suporte a vision e bom equilíbrio custo/qualidade para análise de imagens de investimento.

## Risks / Trade-offs

- **[Risco] Jobs perdidos em restart** → AnaliseJobStore em memória; se a aplicação reiniciar com jobs em andamento, o usuário nunca receberá o resultado; mitigação: histórico de análises completas está no banco
- **[Risco] Consumo de contexto no Claude** → Múltiplas imagens grandes + histórico de 3 análises pode aproximar o limite de tokens; validar tamanho das imagens antes de enviar
- **[Trade-off] Histórico de chat em memória** → Perda de contexto de chat em restart é aceitável; o resultado da análise permanece no banco
- **[Risco] Isolamento de dados do usuário** → Acesso ao historico/conversaId de outro usuário deve retornar 404, não 403 (evitar enumeração)

## Open Questions

- Limite máximo de imagens por requisição (atualmente não documentado — recomenda-se definir como 5)?
- TTL do AnaliseJobStore para limpeza de jobs antigos?

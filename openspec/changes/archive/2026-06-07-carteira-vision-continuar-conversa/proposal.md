## Why

Hoje o chat sobre uma análise de carteira (`POST /api/v1/carteira/{conversaId}/perguntar`) só funciona enquanto o contexto da conversa (imagem em base64 + resumo da análise) permanecer no cache Caffeine `conversaCarteira` (TTL de 60 minutos, máximo de 20 entradas). Quando o cache expira, é despejado por LRU ou a aplicação reinicia, o usuário recebe `404 — Conversa não encontrada ou expirada` e a única saída é reenviar a imagem e gerar uma análise nova, perdendo a conversa anterior. Os endpoints de histórico (`GET /analise-imagem/historico` e `GET /analise-imagem/{conversaId}`) já permitem ao usuário visualizar análises antigas, mas não permitem retomar a conversa a partir delas — criando uma experiência inconsistente: o histórico "existe" mas não é realmente reutilizável.

## What Changes

- Persistir, junto da análise (`CarteiraImagemAnalise`), os dados necessários para reconstruir o contexto de chat sem depender do cache em memória: a imagem de referência (em armazenamento de arquivo/blob) e o histórico de mensagens (perguntas e respostas) trocadas na conversa.
- Alterar `CarteiraChatService` para recuperar o contexto da conversa primeiro do cache (caminho rápido) e, em caso de cache miss, reconstruí-lo a partir dos dados persistidos no banco — eliminando o erro "conversa expirada" para conversas que já foram analisadas e salvas.
- Persistir cada nova pergunta/resposta do chat associada ao `conversaId`, para que o histórico completo da conversa fique disponível em consultas futuras (não apenas o que o cliente reenviar no campo `historico`).
- Expor o histórico de mensagens já trocadas ao consultar uma conversa (`GET /analise-imagem/{conversaId}`), permitindo que o cliente carregue a conversa completa e continue de onde parou sem reenviar a imagem nem reconstruir o histórico manualmente.
- Aplicar política de retenção/limpeza para os arquivos de imagem persistidos (evitar crescimento ilimitado de armazenamento), alinhada ao ciclo de vida das análises do usuário.

## Capabilities

### New Capabilities
(nenhuma — o chat contextualizado já existe; esta mudança torna a conversa retomável)

### Modified Capabilities
- `chat-carteira`: o contexto da conversa (imagem + histórico de mensagens) passa a ser persistido e recuperável a partir do banco de dados, não apenas do cache em memória; o sistema deixa de exigir reenvio da imagem para continuar uma conversa cuja análise já foi salva, e passa a manter/expor o histórico completo de mensagens da conversa.

## Impact

- **Banco de dados**: nova tabela (ou extensão de `carteira_imagem_analises`) para armazenar referência/metadados da imagem persistida e o histórico de mensagens do chat (nova migration Flyway).
- **Armazenamento de arquivos**: necessidade de um local para guardar os bytes da imagem de referência (ex.: disco local com path versionado por usuário/conversa, ou storage externo) — define o `nomeArquivo`/referência hoje só registrado como metadado.
- **Código afetado**: `CarteiraImagemProcessadorService` (gravação da imagem e do contexto inicial), `CarteiraChatService` (reconstrução de contexto a partir do banco em caso de cache miss, persistência de cada interação), `CarteiraImagemController` (exposição do histórico de mensagens), DTOs (`CarteiraImagemAnaliseDTO`, `CarteiraChatResponseDTO`, `MensagemHistoricoDTO`), `CarteiraImagemAnaliseRepository`.
- **Configuração**: cache `conversaCarteira` (`AppConfig`) passa a ser apenas uma otimização de performance (camada quente), não mais a única fonte do contexto de conversa.

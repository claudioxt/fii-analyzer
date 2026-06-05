package com.fii.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.*;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fii.dto.CarteiraImagemAnaliseDTO;
import com.fii.dto.SugestaoAtivoDTO;
import com.fii.entity.CarteiraImagemAnalise;
import com.fii.entity.Usuario;
import com.fii.repository.CarteiraImagemAnaliseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraImagemProcessadorService {

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final CarteiraImagemAnaliseRepository analiseRepository;
    private final AnaliseJobStore jobStore;

    private static final String MODEL = "claude-sonnet-4-6";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String SYSTEM_PROMPT = """
            Você é um analista de investimentos especializado no mercado brasileiro, com expertise em todos os tipos de ativos: Fundos de Investimento Imobiliário (FIIs), ações brasileiras, BDRs, ações estrangeiras, Tesouro Direto, ETFs, criptoativos e outros investimentos.

            Sua tarefa é analisar uma imagem de carteira de investimentos e retornar uma análise estruturada e detalhada.

            INSTRUÇÕES:
            - Identifique todos os ativos presentes na imagem (FIIs, ações, BDRs, Tesouro Direto, ETFs, criptos, etc.), com seus códigos, quantidades, pesos percentuais, preços e valores quando visíveis
            - Classifique cada ativo identificado pelo tipo: FII, ACAO, BDR, ACAO_ESTRANGEIRA, TESOURO_DIRETO, ETF, CRIPTO ou OUTRO
            - Avalie o sentimento geral da carteira: POSITIVO, NEGATIVO ou NEUTRO
            - Elabore um resumo conciso da situação geral da carteira (2-3 frases), mencionando as classes de ativos presentes
            - Identifique até 5 pontos positivos da carteira (diversificação entre classes, qualidade dos ativos, proteção inflacionária, etc.)
            - Identifique até 5 pontos negativos ou riscos da carteira
            - Identifique até 5 pontos de atenção (concentração excessiva em uma classe, exposição cambial, liquidez, etc.)
            - Sugira até 3 ativos para comprar (de qualquer classe), indicando o tipo de cada um, caso haja necessidade de rebalanceamento ou diversificação
            - Sugira ativos da carteira para vender, caso necessário, com justificativa
            - Indique onde devem ser os próximos aportes (quais ativos ou classes priorizar e por quê)
            - Emita uma recomendação geral sobre a carteira
            - Se houver histórico de análises anteriores no contexto, considere a evolução da carteira ao longo do tempo

            FORMATO DE RESPOSTA:
            Retorne SOMENTE um objeto JSON válido, sem markdown, sem explicações adicionais:
            {
              "sentimentoGeral": "POSITIVO|NEGATIVO|NEUTRO",
              "resumo": "...",
              "pontosPositivos": ["...", "..."],
              "pontosNegativos": ["...", "..."],
              "pontosAtencao": ["...", "..."],
              "ativosParaComprar": [
                {"codigo": "TICKER", "tipoAtivo": "FII|ACAO|BDR|ACAO_ESTRANGEIRA|TESOURO_DIRETO|ETF|CRIPTO|OUTRO", "justificativa": "..."}
              ],
              "ativosParaVender": [
                {"codigo": "TICKER", "tipoAtivo": "FII|ACAO|BDR|ACAO_ESTRANGEIRA|TESOURO_DIRETO|ETF|CRIPTO|OUTRO", "justificativa": "..."}
              ],
              "proximosAportes": ["...", "..."],
              "recomendacaoGeral": "..."
            }

            Se não conseguir identificar claramente os ativos na imagem, informe isso no resumo e forneça orientações gerais baseadas no que for visível.
            Responda em português brasileiro. Baseie-se exclusivamente no que estiver visível na imagem.
            """;

    @Async("analiseExecutor")
    @Transactional
    public void processar(String jobId, List<ImagemDados> imagens, Usuario usuario) {
        log.info("Processando análise async. jobId={}, imagens={}", jobId, imagens.size());
        try {
            CarteiraImagemAnaliseDTO resultado = executar(jobId, imagens, usuario);
            jobStore.concluir(jobId, resultado);
            log.info("Análise async concluída. jobId={}", jobId);
        } catch (Exception e) {
            log.error("Erro na análise async. jobId={}: {}", jobId, e.getMessage(), e);
            jobStore.falhar(jobId, mensagemAmigavel(e));
        }
    }

    private CarteiraImagemAnaliseDTO executar(String jobId, List<ImagemDados> imagens,
                                               Usuario usuario) throws Exception {
        List<CarteiraImagemAnalise> historico = carregarHistorico(usuario);

        // Usa a primeira imagem como referência para armazenar contexto de conversa
        String base64Primeira = Base64.getEncoder().encodeToString(imagens.get(0).bytes());
        Base64ImageSource.MediaType mediaPrimeira = imagens.get(0).mediaType();

        List<ContentBlockParam> userBlocks = new ArrayList<>();
        if (!historico.isEmpty()) {
            userBlocks.add(ContentBlockParam.ofText(
                    TextBlockParam.builder()
                            .text(construirContextoHistorico(historico))
                            .build()
            ));
        }

        // Adiciona cada imagem como bloco separado
        for (int i = 0; i < imagens.size(); i++) {
            ImagemDados img = imagens.get(i);
            if (imagens.size() > 1) {
                userBlocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder()
                                .text("Imagem " + (i + 1) + " de " + imagens.size() + ":")
                                .build()
                ));
            }
            userBlocks.add(ContentBlockParam.ofImage(
                    ImageBlockParam.builder()
                            .source(ImageBlockParam.Source.ofBase64(
                                    Base64ImageSource.builder()
                                            .mediaType(img.mediaType())
                                            .data(Base64.getEncoder().encodeToString(img.bytes()))
                                            .build()))
                            .build()
            ));
        }

        String instrucao = imagens.size() > 1
                ? "Analise a carteira de investimentos visível nas " + imagens.size() + " imagens acima (podem ser partes da mesma carteira ou telas diferentes do mesmo aplicativo) e retorne a análise completa no formato JSON especificado, considerando todos os ativos visíveis no conjunto de imagens."
                : "Analise a carteira de investimentos visível nesta imagem e retorne a análise completa no formato JSON especificado.";

        userBlocks.add(ContentBlockParam.ofText(
                TextBlockParam.builder().text(instrucao).build()
        ));

        MessageCreateParams params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(8192L)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(SYSTEM_PROMPT)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()
                ))
                .addUserMessageOfBlockParams(userBlocks)
                .build();

        Message response = anthropicClient.messages().create(params);

        String jsonResposta = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Resposta vazia da API"));

        log.info("Resposta IA: stopReason={}, outputTokens={}, chars={}",
                response.stopReason(),
                response.usage().outputTokens(),
                jsonResposta.length());

        if (response.stopReason().map(StopReason.MAX_TOKENS::equals).orElse(false)) {
            log.warn("Resposta truncada por max_tokens! Aumente maxTokens ou reduza o contexto enviado.");
        }

        String jsonLimpo = extrairJson(jsonResposta);
        AnaliseImagemResponse analise = objectMapper.readValue(jsonLimpo, AnaliseImagemResponse.class);

        log.info("IA concluiu análise. jobId={}, sentimento={}, cache hit={}",
                jobId,
                analise.sentimentoGeral(),
                response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

        // Contexto de chat usa apenas a primeira imagem como referência visual
        armazenarContexto(jobId, base64Primeira, mediaPrimeira, analise);

        String nomesArquivos = imagens.stream()
                .map(ImagemDados::nome)
                .filter(n -> n != null && !n.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("desconhecido");
        persistirAnalise(jobId, nomesArquivos, analise, usuario);

        return new CarteiraImagemAnaliseDTO(
                jobId,
                analise.sentimentoGeral(),
                analise.resumo(),
                nvl(analise.pontosPositivos()),
                nvl(analise.pontosNegativos()),
                nvl(analise.pontosAtencao()),
                nvl(analise.ativosParaComprar()),
                nvl(analise.ativosParaVender()),
                nvl(analise.proximosAportes()),
                analise.recomendacaoGeral(),
                LocalDateTime.now()
        );
    }

    // ── Histórico ────────────────────────────────────────────────────────────

    private List<CarteiraImagemAnalise> carregarHistorico(Usuario usuario) {
        if (usuario == null) return List.of();
        try {
            return analiseRepository.findTop3ByUsuarioOrderByAnalisadoEmDesc(usuario);
        } catch (Exception e) {
            log.warn("Não foi possível carregar histórico: {}", e.getMessage());
            return List.of();
        }
    }

    private String construirContextoHistorico(List<CarteiraImagemAnalise> historico) {
        var sb = new StringBuilder();
        sb.append("HISTÓRICO DE ANÁLISES ANTERIORES DA CARTEIRA:\n");
        sb.append("Abaixo estão as últimas ").append(historico.size())
          .append(" análise(s) realizadas. Considere a evolução ao fazer a análise atual.\n\n");

        for (int i = 0; i < historico.size(); i++) {
            CarteiraImagemAnalise h = historico.get(i);
            sb.append("--- Análise ").append(i + 1).append(" (")
              .append(h.getAnalisadoEm().format(FORMATTER)).append(") ---\n");
            sb.append("Sentimento: ").append(h.getSentimentoGeral()).append("\n");
            sb.append("Resumo: ").append(h.getResumo()).append("\n");

            if (!nvl(h.getPontosPositivos()).isEmpty())
                sb.append("Pontos positivos: ").append(String.join("; ", h.getPontosPositivos())).append("\n");
            if (!nvl(h.getPontosNegativos()).isEmpty())
                sb.append("Pontos negativos: ").append(String.join("; ", h.getPontosNegativos())).append("\n");
            if (!nvl(h.getPontosAtencao()).isEmpty())
                sb.append("Pontos de atenção: ").append(String.join("; ", h.getPontosAtencao())).append("\n");

            if (!nvl(h.getAtivosParaComprar()).isEmpty()) {
                sb.append("Ativos sugeridos para comprar: ");
                h.getAtivosParaComprar().forEach(s ->
                        sb.append(s.codigo()).append(" [").append(nvlTipo(s.tipoAtivo())).append("]")
                          .append(" (").append(s.justificativa()).append(") "));
                sb.append("\n");
            }
            if (!nvl(h.getAtivosParaVender()).isEmpty()) {
                sb.append("Ativos sugeridos para vender: ");
                h.getAtivosParaVender().forEach(s ->
                        sb.append(s.codigo()).append(" [").append(nvlTipo(s.tipoAtivo())).append("]")
                          .append(" (").append(s.justificativa()).append(") "));
                sb.append("\n");
            }
            sb.append("Recomendação: ").append(h.getRecomendacaoGeral()).append("\n\n");
        }
        return sb.toString();
    }

    // ── Persistência ─────────────────────────────────────────────────────────

    private void persistirAnalise(String conversaId, String nomeArquivo,
                                  AnaliseImagemResponse analise, Usuario usuario) {
        try {
            CarteiraImagemAnalise entidade = CarteiraImagemAnalise.builder()
                    .conversaId(conversaId)
                    .usuario(usuario)
                    .sentimentoGeral(analise.sentimentoGeral())
                    .resumo(analise.resumo())
                    .pontosPositivos(nvl(analise.pontosPositivos()))
                    .pontosNegativos(nvl(analise.pontosNegativos()))
                    .pontosAtencao(nvl(analise.pontosAtencao()))
                    .ativosParaComprar(nvl(analise.ativosParaComprar()))
                    .ativosParaVender(nvl(analise.ativosParaVender()))
                    .proximosAportes(nvl(analise.proximosAportes()))
                    .recomendacaoGeral(analise.recomendacaoGeral())
                    .nomeArquivo(nomeArquivo)
                    .build();
            analiseRepository.save(entidade);
            log.debug("Análise persistida. conversaId={}", conversaId);
        } catch (Exception e) {
            log.warn("Falha ao persistir análise (conversaId={}): {}", conversaId, e.getMessage());
        }
    }

    // ── Cache de conversa ─────────────────────────────────────────────────────

    private void armazenarContexto(String conversaId, String base64,
                                   Base64ImageSource.MediaType mediaType,
                                   AnaliseImagemResponse analise) {
        try {
            Cache cache = cacheManager.getCache("conversaCarteira");
            if (cache != null) {
                cache.put(conversaId, new ConversaContexto(base64, mediaType, construirContextoTextual(analise)));
                log.debug("Contexto armazenado. conversaId={}", conversaId);
            }
        } catch (Exception e) {
            log.warn("Falha ao armazenar contexto {}: {}", conversaId, e.getMessage());
        }
    }

    private String construirContextoTextual(AnaliseImagemResponse analise) {
        var sb = new StringBuilder();
        sb.append("Sentimento geral: ").append(analise.sentimentoGeral()).append("\n");
        sb.append("Resumo: ").append(analise.resumo()).append("\n\n");
        appendLista(sb, "Pontos positivos", analise.pontosPositivos());
        appendLista(sb, "Pontos negativos", analise.pontosNegativos());
        appendLista(sb, "Pontos de atenção", analise.pontosAtencao());
        if (analise.ativosParaComprar() != null && !analise.ativosParaComprar().isEmpty()) {
            sb.append("Ativos sugeridos para comprar:\n");
            analise.ativosParaComprar().forEach(s ->
                    sb.append("  - ").append(s.codigo())
                      .append(" [").append(nvlTipo(s.tipoAtivo())).append("]")
                      .append(": ").append(s.justificativa()).append("\n"));
            sb.append("\n");
        }
        if (analise.ativosParaVender() != null && !analise.ativosParaVender().isEmpty()) {
            sb.append("Ativos sugeridos para vender:\n");
            analise.ativosParaVender().forEach(s ->
                    sb.append("  - ").append(s.codigo())
                      .append(" [").append(nvlTipo(s.tipoAtivo())).append("]")
                      .append(": ").append(s.justificativa()).append("\n"));
            sb.append("\n");
        }
        appendLista(sb, "Próximos aportes", analise.proximosAportes());
        sb.append("Recomendação geral: ").append(analise.recomendacaoGeral());
        return sb.toString();
    }

    private void appendLista(StringBuilder sb, String titulo, List<String> itens) {
        if (itens == null || itens.isEmpty()) return;
        sb.append(titulo).append(":\n");
        itens.forEach(i -> sb.append("  - ").append(i).append("\n"));
        sb.append("\n");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extrairJson(String texto) {
        int primeiro = texto.indexOf('{');
        int ultimo = texto.lastIndexOf('}');
        if (primeiro == -1 || ultimo <= primeiro) {
            throw new RuntimeException("Nenhum JSON válido encontrado na resposta da IA.");
        }
        return texto.substring(primeiro, ultimo + 1);
    }

    private <T> List<T> nvl(List<T> lista) {
        return lista != null ? lista : List.of();
    }

    private String nvlTipo(String tipoAtivo) {
        return tipoAtivo != null ? tipoAtivo : "OUTRO";
    }

    private String mensagemAmigavel(Exception e) {
        if (e instanceof UnauthorizedException) return "API key da Anthropic não configurada.";
        if (e instanceof AnthropicException) return "Erro na API de IA: " + e.getMessage();
        return e.getMessage();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnaliseImagemResponse(
            String sentimentoGeral,
            String resumo,
            List<String> pontosPositivos,
            List<String> pontosNegativos,
            List<String> pontosAtencao,
            List<SugestaoAtivoDTO> ativosParaComprar,
            List<SugestaoAtivoDTO> ativosParaVender,
            List<String> proximosAportes,
            String recomendacaoGeral
    ) {}
}

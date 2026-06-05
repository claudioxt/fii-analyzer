package com.fii.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fii.dto.CarteiraAnaliseIADTO;
import com.fii.dto.CarteiraItemAnaliseDTO;
import com.fii.dto.NoticiaDTO;
import com.fii.entity.CarteiraRecomendada;
import com.fii.entity.CarteiraRecomendadaItem;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.CarteiraRecomendadaRepository;
import com.fii.repository.FundoImobiliarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraAnaliseIAService {

    private final AnthropicClient anthropicClient;
    private final CarteiraRecomendadaRepository carteiraRepository;
    private final FundoImobiliarioRepository fundoRepository;
    private final FiiNoticiasService noticiasService;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-haiku-4-5";

    private static final String SYSTEM_PROMPT = """
            Você é um analista especializado em carteiras de Fundos de Investimento Imobiliário (FIIs) do mercado brasileiro.

            Sua tarefa é analisar as notícias recentes dos FIIs de uma carteira recomendada e retornar uma análise consolidada.

            INSTRUÇÕES:
            - Avalie o sentimento geral da carteira com base nas notícias: POSITIVO, NEGATIVO ou NEUTRO
            - Elabore um resumo conciso da situação geral da carteira (2-3 frases)
            - Avalie a diversificação por segmento dos FIIs presentes na carteira (1-2 frases)
            - Identifique até 5 pontos fortes da carteira com base nas notícias
            - Identifique até 5 pontos de risco da carteira com base nas notícias
            - Forneça uma perspectiva de curto prazo para a carteira como um todo (1-2 frases)
            - Emita uma recomendação geral sobre a carteira baseada exclusivamente nas notícias
            - Para cada FII, classifique o sentimento individual e forneça um resumo de 1-2 frases

            FORMATO DE RESPOSTA:
            Retorne SOMENTE um objeto JSON válido, sem markdown, sem explicações adicionais:
            {
              "sentimentoGeral": "POSITIVO|NEGATIVO|NEUTRO",
              "resumo": "...",
              "diversificacao": "...",
              "pontosFortes": ["...", "..."],
              "pontosRisco": ["...", "..."],
              "perspectiva": "...",
              "recomendacaoIA": "...",
              "analisesPorFundo": [
                {
                  "codigo": "TICKER11",
                  "sentimento": "POSITIVO|NEGATIVO|NEUTRO",
                  "resumo": "..."
                }
              ]
            }

            Baseie sua análise SOMENTE nas notícias fornecidas. Responda em português brasileiro.
            """;

    @Transactional(readOnly = true)
    public CarteiraAnaliseIADTO analisar(Long carteiraId) {
        log.info("Iniciando análise IA para carteira id={}", carteiraId);

        CarteiraRecomendada carteira = carteiraRepository.findById(carteiraId)
                .orElseThrow(() -> new EntityNotFoundException("Carteira recomendada não encontrada: " + carteiraId));

        List<CarteiraRecomendadaItem> itens = carteira.getItens();
        Set<String> codigos = itens.stream().map(CarteiraRecomendadaItem::getCodigoFundo).collect(Collectors.toSet());
        Map<String, FundoImobiliario> fundosPorCodigo = fundoRepository.findAllById(codigos).stream()
                .collect(Collectors.toMap(FundoImobiliario::getCodigo, f -> f));

        Map<String, List<NoticiaDTO>> noticiasPorFundo = itens.stream()
                .collect(Collectors.toMap(
                        CarteiraRecomendadaItem::getCodigoFundo,
                        item -> noticiasService.buscarNoticias(item.getCodigoFundo())
                ));

        int totalNoticias = noticiasPorFundo.values().stream().mapToInt(List::size).sum();

        if (totalNoticias == 0) {
            log.warn("Nenhuma notícia encontrada para a carteira {}. Análise IA indisponível.", carteiraId);
            return semDados(carteira, itens, fundosPorCodigo);
        }

        String userPrompt = montarPrompt(carteira, itens, fundosPorCodigo, noticiasPorFundo);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(2048L)
                    .systemOfTextBlockParams(List.of(
                            TextBlockParam.builder()
                                    .text(SYSTEM_PROMPT)
                                    .cacheControl(CacheControlEphemeral.builder().build())
                                    .build()
                    ))
                    .addUserMessage(userPrompt)
                    .build();

            Message response = anthropicClient.messages().create(params);

            String jsonResposta = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(textBlock -> textBlock.text())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Resposta vazia da API"));

            log.debug("Resposta IA para carteira {}: {}", carteiraId, jsonResposta);

            String jsonLimpo = extrairJson(jsonResposta);
            AnaliseCarteiraResponse analise = objectMapper.readValue(jsonLimpo, AnaliseCarteiraResponse.class);

            log.info("Análise IA concluída para carteira {}. Sentimento: {}, Cache hit: {}",
                    carteiraId, analise.sentimentoGeral(),
                    response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

            List<CarteiraItemAnaliseDTO> analisesPorFundo = construirAnalisesPorFundo(
                    itens, fundosPorCodigo, noticiasPorFundo, analise.analisesPorFundo()
            );

            return new CarteiraAnaliseIADTO(
                    carteira.getId(),
                    carteira.getCasaDeAnalise(),
                    carteira.getMesReferencia(),
                    analise.sentimentoGeral(),
                    analise.resumo(),
                    analise.diversificacao(),
                    analise.pontosFortes() != null ? analise.pontosFortes() : List.of(),
                    analise.pontosRisco() != null ? analise.pontosRisco() : List.of(),
                    analise.perspectiva(),
                    analise.recomendacaoIA(),
                    analisesPorFundo,
                    itens.size(),
                    totalNoticias,
                    LocalDateTime.now()
            );

        } catch (UnauthorizedException e) {
            log.error("API key da Anthropic inválida ou não configurada.");
            throw new IllegalStateException("Análise por IA indisponível: API key não configurada.");
        } catch (AnthropicException e) {
            log.error("Erro na API Anthropic para carteira {}: {} — {}", carteiraId, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Erro na API de IA: " + e.getMessage());
        } catch (Exception e) {
            log.error("Erro na análise IA para carteira {}: {}", carteiraId, e.getMessage(), e);
            throw new RuntimeException("Erro ao processar análise de IA: " + e.getMessage());
        }
    }

    private String montarPrompt(CarteiraRecomendada carteira,
                                List<CarteiraRecomendadaItem> itens,
                                Map<String, FundoImobiliario> fundosPorCodigo,
                                Map<String, List<NoticiaDTO>> noticiasPorFundo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise a carteira recomendada pela ").append(carteira.getCasaDeAnalise())
          .append(" (referência: ").append(carteira.getMesReferencia()).append(")");

        if (carteira.getObservacoes() != null && !carteira.getObservacoes().isBlank()) {
            sb.append("\nEstratégia da carteira: ").append(carteira.getObservacoes());
        }

        sb.append("\n\nCOMPOSIÇÃO DA CARTEIRA:\n");
        for (CarteiraRecomendadaItem item : itens) {
            FundoImobiliario fundo = fundosPorCodigo.get(item.getCodigoFundo());
            String nome = fundo != null ? fundo.getNome() : item.getCodigoFundo();
            String segmento = fundo != null ? fundo.getSegmento() : "N/D";
            sb.append(String.format("- %s (%s) | Segmento: %s | Peso: %.1f%%",
                    item.getCodigoFundo(), nome, segmento, item.getPeso())).append("\n");
            if (item.getJustificativa() != null && !item.getJustificativa().isBlank()) {
                sb.append("  Justificativa: ").append(item.getJustificativa()).append("\n");
            }
        }

        sb.append("\nNOTÍCIAS RECENTES POR FUNDO:\n");
        for (CarteiraRecomendadaItem item : itens) {
            String codigo = item.getCodigoFundo();
            List<NoticiaDTO> noticias = noticiasPorFundo.getOrDefault(codigo, List.of());
            FundoImobiliario fundo = fundosPorCodigo.get(codigo);
            String nome = fundo != null ? fundo.getNome() : codigo;

            sb.append("\n== ").append(codigo).append(" (").append(nome).append(") ==\n");
            if (noticias.isEmpty()) {
                sb.append("Sem notícias recentes disponíveis.\n");
                continue;
            }
            for (int i = 0; i < noticias.size(); i++) {
                NoticiaDTO n = noticias.get(i);
                sb.append("Notícia ").append(i + 1).append(": ").append(n.titulo()).append("\n");
                if (n.resumo() != null && !n.resumo().isBlank()) {
                    sb.append("  Resumo: ").append(n.resumo()).append("\n");
                }
                if (n.dataPublicacao() != null) {
                    sb.append("  Data: ").append(n.dataPublicacao()).append("\n");
                }
            }
        }

        sb.append("\nCom base nas informações acima, forneça a análise consolidada da carteira em JSON conforme instruído.");
        return sb.toString();
    }

    private List<CarteiraItemAnaliseDTO> construirAnalisesPorFundo(
            List<CarteiraRecomendadaItem> itens,
            Map<String, FundoImobiliario> fundosPorCodigo,
            Map<String, List<NoticiaDTO>> noticiasPorFundo,
            List<ItemAnaliseResponse> respostasIA) {

        Map<String, ItemAnaliseResponse> respostasPorCodigo = respostasIA != null
                ? respostasIA.stream().collect(Collectors.toMap(ItemAnaliseResponse::codigo, r -> r))
                : Map.of();

        return itens.stream().map(item -> {
            FundoImobiliario fundo = fundosPorCodigo.get(item.getCodigoFundo());
            ItemAnaliseResponse ia = respostasPorCodigo.get(item.getCodigoFundo());
            return new CarteiraItemAnaliseDTO(
                    item.getCodigoFundo(),
                    fundo != null ? fundo.getNome() : null,
                    fundo != null ? fundo.getSegmento() : null,
                    item.getPeso(),
                    ia != null ? ia.sentimento() : "NEUTRO",
                    ia != null ? ia.resumo() : "Sem dados suficientes.",
                    noticiasPorFundo.getOrDefault(item.getCodigoFundo(), List.of()).size()
            );
        }).toList();
    }

    private CarteiraAnaliseIADTO semDados(CarteiraRecomendada carteira,
                                          List<CarteiraRecomendadaItem> itens,
                                          Map<String, FundoImobiliario> fundosPorCodigo) {
        List<CarteiraItemAnaliseDTO> analises = itens.stream().map(item -> {
            FundoImobiliario fundo = fundosPorCodigo.get(item.getCodigoFundo());
            return new CarteiraItemAnaliseDTO(
                    item.getCodigoFundo(),
                    fundo != null ? fundo.getNome() : null,
                    fundo != null ? fundo.getSegmento() : null,
                    item.getPeso(),
                    "NEUTRO",
                    "Sem notícias recentes disponíveis.",
                    0
            );
        }).toList();

        return new CarteiraAnaliseIADTO(
                carteira.getId(),
                carteira.getCasaDeAnalise(),
                carteira.getMesReferencia(),
                "NEUTRO",
                "Sem notícias recentes disponíveis para análise desta carteira.",
                "Diversificação não avaliada por falta de dados.",
                List.of(),
                List.of(),
                "Não há informações suficientes para projeção de curto prazo.",
                "Sem dados suficientes para emitir recomendação.",
                analises,
                itens.size(),
                0,
                LocalDateTime.now()
        );
    }

    private String extrairJson(String texto) {
        String s = texto.strip();
        if (s.startsWith("```")) {
            int inicio = s.indexOf('\n');
            int fim = s.lastIndexOf("```");
            if (inicio != -1 && fim > inicio) {
                return s.substring(inicio + 1, fim).strip();
            }
        }
        return s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AnaliseCarteiraResponse(
            String sentimentoGeral,
            String resumo,
            String diversificacao,
            List<String> pontosFortes,
            List<String> pontosRisco,
            String perspectiva,
            String recomendacaoIA,
            List<ItemAnaliseResponse> analisesPorFundo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemAnaliseResponse(
            String codigo,
            String sentimento,
            String resumo
    ) {}
}

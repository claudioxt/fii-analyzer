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
import com.fii.dto.FiiAnaliseIADTO;
import com.fii.dto.NoticiaDTO;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.FundoImobiliarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiiAnaliseIAService {

    private final AnthropicClient anthropicClient;
    private final FiiNoticiasService noticiasService;
    private final FundoImobiliarioRepository fundoRepository;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-haiku-4-5";

    // Prompt estável — elegível para prompt caching (TTL 5 min)
    private static final String SYSTEM_PROMPT = """
            Você é um analista especializado em Fundos de Investimento Imobiliário (FIIs) do mercado brasileiro.

            Sua tarefa é analisar notícias recentes sobre um FII específico e retornar uma análise estruturada.

            INSTRUÇÕES:
            - Analise o sentimento geral das notícias e classifique como: POSITIVO, NEGATIVO ou NEUTRO
            - Identifique até 5 pontos positivos mencionados nas notícias
            - Identifique até 5 pontos negativos ou riscos mencionados
            - Elabore um resumo conciso da situação atual do fundo (2-3 frases)
            - Forneça uma perspectiva de curto prazo baseada apenas nas notícias (1-2 frases)
            - Emita uma recomendação baseada exclusivamente nas notícias analisadas

            FORMATO DE RESPOSTA:
            Retorne SOMENTE um objeto JSON válido, sem markdown, sem explicações adicionais:
            {
              "sentimento": "POSITIVO|NEGATIVO|NEUTRO",
              "resumo": "...",
              "pontosPositivos": ["...", "..."],
              "pontosNegativos": ["...", "..."],
              "perspectiva": "...",
              "recomendacaoIA": "..."
            }

            Baseie sua análise SOMENTE nas notícias fornecidas. Responda em português brasileiro.
            """;

    public FiiAnaliseIADTO analisar(String codigo) {
        log.info("Iniciando análise IA para {}", codigo);

        List<NoticiaDTO> noticias = noticiasService.buscarNoticias(codigo);
        String nomeFundo = fundoRepository.findById(codigo)
                .map(FundoImobiliario::getNome)
                .orElse(codigo);

        if (noticias.isEmpty()) {
            log.warn("Nenhuma notícia encontrada para {}. Análise IA indisponível.", codigo);
            return semDados(codigo, nomeFundo);
        }

        String userPrompt = montarPrompt(codigo, nomeFundo, noticias);

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(MODEL)
                    .maxTokens(1024L)
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

            log.debug("Resposta IA para {}: {}", codigo, jsonResposta);

            String jsonLimpo = extrairJson(jsonResposta);
            AnaliseResponse analise = objectMapper.readValue(jsonLimpo, AnaliseResponse.class);

            log.info("Análise IA concluída para {}. Sentimento: {}, Cache hit: {}",
                    codigo, analise.sentimento(),
                    response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

            return new FiiAnaliseIADTO(
                    codigo,
                    nomeFundo,
                    analise.resumo(),
                    analise.sentimento(),
                    analise.pontosPositivos() != null ? analise.pontosPositivos() : List.of(),
                    analise.pontosNegativos() != null ? analise.pontosNegativos() : List.of(),
                    analise.perspectiva(),
                    analise.recomendacaoIA(),
                    noticias.size(),
                    LocalDateTime.now()
            );

        } catch (UnauthorizedException e) {
            log.error("API key da Anthropic inválida ou não configurada. Defina ANTHROPIC_API_KEY no .env");
            throw new IllegalStateException("Análise por IA indisponível: API key não configurada.");
        } catch (AnthropicException e) {
            log.error("Erro na API Anthropic para {}: {} — {}", codigo, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("Erro na API de IA: " + e.getMessage());
        } catch (Exception e) {
            log.error("Erro na análise IA para {}: {}", codigo, e.getMessage(), e);
            throw new RuntimeException("Erro ao processar análise de IA: " + e.getMessage());
        }
    }

    private String montarPrompt(String codigo, String nomeFundo, List<NoticiaDTO> noticias) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise as seguintes notícias sobre o fundo ").append(nomeFundo)
          .append(" (").append(codigo).append("):\n\n");

        for (int i = 0; i < noticias.size(); i++) {
            NoticiaDTO n = noticias.get(i);
            sb.append("--- Notícia ").append(i + 1).append(" ---\n");
            sb.append("Título: ").append(n.titulo()).append("\n");
            if (n.resumo() != null && !n.resumo().isBlank()) {
                sb.append("Resumo: ").append(n.resumo()).append("\n");
            }
            if (n.fonte() != null && !n.fonte().isBlank()) {
                sb.append("Fonte: ").append(n.fonte()).append("\n");
            }
            if (n.dataPublicacao() != null) {
                sb.append("Data: ").append(n.dataPublicacao()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Com base nessas ").append(noticias.size())
          .append(" notícias, forneça a análise estruturada em JSON conforme instruído.");

        return sb.toString();
    }

    private FiiAnaliseIADTO semDados(String codigo, String nome) {
        return new FiiAnaliseIADTO(
                codigo, nome,
                "Sem notícias recentes disponíveis para análise.",
                "NEUTRO",
                List.of(),
                List.of(),
                "Não há informações suficientes para projeção de curto prazo.",
                "Sem dados suficientes para emitir recomendação.",
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
    private record AnaliseResponse(
            String sentimento,
            String resumo,
            List<String> pontosPositivos,
            List<String> pontosNegativos,
            String perspectiva,
            String recomendacaoIA
    ) {}
}
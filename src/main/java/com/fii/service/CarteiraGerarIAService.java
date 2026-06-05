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
import com.fii.dto.CarteiraItemRequestDTO;
import com.fii.dto.CarteiraRecomendadaRequestDTO;
import com.fii.dto.CarteiraRecomendadaResponseDTO;
import com.fii.dto.DadosMercadoDTO;
import com.fii.dto.FiiEmAltaItemDTO;
import com.fii.dto.FiisEmAltaDTO;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.FundoImobiliarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraGerarIAService {

    private final AnthropicClient anthropicClient;
    private final FundoImobiliarioRepository fundoRepository;
    private final FiiDadosMercadoService dadosMercadoService;
    private final FiisEmAltaService fiisEmAltaService;
    private final CarteiraRecomendadaService carteiraRecomendadaService;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-haiku-4-5";
    private static final String CASA_DE_ANALISE_IA = "Claude AI";

    private static final String SYSTEM_PROMPT = """
            Você é um gestor especializado em carteiras de Fundos de Investimento Imobiliário (FIIs) do mercado brasileiro.

            Sua tarefa é criar uma carteira recomendada diversificada e equilibrada com base nos dados de mercado fornecidos.

            CRITÉRIOS DE SELEÇÃO:
            - Selecione entre 6 e 8 fundos da lista disponível
            - Os pesos devem somar EXATAMENTE 100.00% (use até 2 casas decimais)
            - Diversifique entre os segmentos presentes: Logística, Shoppings, Lajes Corporativas, Recebíveis, Híbrido
            - Prefira fundos com P/VP abaixo de 1.10 (valor justo ou descontado)
            - Prefira fundos com Dividend Yield acima de 7%
            - Dê maior peso aos fundos em alta para analistas
            - Justifique cada escolha em 1-2 frases focando nos fundamentos

            FORMATO DE RESPOSTA:
            Retorne SOMENTE um objeto JSON válido, sem markdown, sem explicações adicionais:
            {
              "observacoes": "Descrição da estratégia e racional da carteira em 2-3 frases",
              "itens": [
                {
                  "codigoFundo": "TICKER11",
                  "peso": 15.00,
                  "justificativa": "Motivo da inclusão na carteira"
                }
              ]
            }

            Use SOMENTE os códigos exatos da lista de fundos fornecida. Responda em português brasileiro.
            """;

    public CarteiraRecomendadaResponseDTO gerarCarteira() {
        log.info("Iniciando geração de carteira recomendada por IA");

        List<FundoImobiliario> fundos = fundoRepository.findAll();
        if (fundos.isEmpty()) {
            throw new IllegalStateException("Nenhum fundo cadastrado. Cadastre fundos antes de gerar uma carteira por IA.");
        }

        Map<String, DadosMercadoDTO> dadosMercado = buscarDadosMercadoEmParalelo(fundos);
        FiisEmAltaDTO fiisEmAlta = carregarFiisEmAlta();

        String userPrompt = montarPrompt(fundos, dadosMercado, fiisEmAlta);

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

            log.debug("Resposta IA (geração de carteira): {}", jsonResposta);

            String jsonLimpo = extrairJson(jsonResposta);
            CarteiraIAResponse resposta = objectMapper.readValue(jsonLimpo, CarteiraIAResponse.class);

            log.info("Claude gerou carteira com {} itens. Cache hit: {}",
                    resposta.itens() != null ? resposta.itens().size() : 0,
                    response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

            Set<String> codigosValidos = fundos.stream()
                    .map(FundoImobiliario::getCodigo)
                    .collect(Collectors.toSet());

            List<CarteiraItemRequestDTO> itensFiltrados = validarEFiltrar(resposta.itens(), codigosValidos);
            List<CarteiraItemRequestDTO> itensNormalizados = normalizarPesos(itensFiltrados);

            CarteiraRecomendadaRequestDTO requestDTO = new CarteiraRecomendadaRequestDTO(
                    CASA_DE_ANALISE_IA,
                    LocalDate.now().withDayOfMonth(1),
                    resposta.observacoes(),
                    itensNormalizados
            );

            CarteiraRecomendadaResponseDTO carteiraCriada = carteiraRecomendadaService.criar(requestDTO);
            log.info("Carteira IA criada com id={}, {} fundos", carteiraCriada.id(), carteiraCriada.totalFundos());
            return carteiraCriada;

        } catch (UnauthorizedException e) {
            log.error("API key da Anthropic inválida ou não configurada.");
            throw new IllegalStateException("Geração de carteira por IA indisponível: API key não configurada.");
        } catch (AnthropicException e) {
            log.error("Erro na API Anthropic ao gerar carteira: {}", e.getMessage());
            throw new RuntimeException("Erro na API de IA: " + e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao gerar carteira por IA: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao gerar carteira por IA: " + e.getMessage());
        }
    }

    // ── Coleta de dados ───────────────────────────────────────────────────────

    private Map<String, DadosMercadoDTO> buscarDadosMercadoEmParalelo(List<FundoImobiliario> fundos) {
        List<CompletableFuture<Map.Entry<String, DadosMercadoDTO>>> futures = fundos.stream()
                .map(f -> CompletableFuture.supplyAsync(() -> {
                    try {
                        DadosMercadoDTO dados = dadosMercadoService.buscarDadosMercado(f.getCodigo());
                        return Map.entry(f.getCodigo(), dados != null ? dados : new DadosMercadoDTO(
                                null, null, null, null, null, null, null, null));
                    } catch (Exception e) {
                        log.warn("Falha ao buscar mercado para {}: {}", f.getCodigo(), e.getMessage());
                        return Map.entry(f.getCodigo(), new DadosMercadoDTO(
                                null, null, null, null, null, null, null, null));
                    }
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private FiisEmAltaDTO carregarFiisEmAlta() {
        try {
            return fiisEmAltaService.buscar();
        } catch (Exception e) {
            log.warn("Falha ao carregar FIIs em alta: {}", e.getMessage());
            return new FiisEmAltaDTO(List.of(), "Dados indisponíveis.", 0, null);
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String montarPrompt(List<FundoImobiliario> fundos,
                                 Map<String, DadosMercadoDTO> dadosMercado,
                                 FiisEmAltaDTO fiisEmAlta) {
        String mesAno = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/yyyy"));
        StringBuilder sb = new StringBuilder();
        sb.append("Crie uma carteira recomendada para ").append(mesAno).append(".\n\n");

        sb.append("FUNDOS DISPONÍVEIS:\n");
        sb.append(String.format("%-10s %-42s %-22s %8s %6s %6s %10s%n",
                "Código", "Nome", "Segmento", "Preço", "P/VP", "DY%", "Var.Mês%"));
        sb.append("-".repeat(110)).append("\n");

        for (FundoImobiliario f : fundos) {
            DadosMercadoDTO dm = dadosMercado.get(f.getCodigo());
            sb.append(String.format("%-10s %-42s %-22s %8s %6s %6s %10s%n",
                    f.getCodigo(),
                    abreviar(f.getNome(), 40),
                    abreviar(f.getSegmento(), 20),
                    dm != null && dm.precoAtual() != null ? "R$" + dm.precoAtual() : "N/D",
                    dm != null && dm.pvp() != null ? dm.pvp() : "N/D",
                    dm != null && dm.dividendYield() != null ? dm.dividendYield() : "N/D",
                    dm != null && dm.variacaoMes() != null ? dm.variacaoMes() + "%" : "N/D"
            ));
        }

        if (fiisEmAlta.totalFundos() > 0) {
            sb.append("\nFIIS EM ALTA PARA ANALISTAS:\n");
            for (var seg : fiisEmAlta.porSegmento()) {
                sb.append("  ").append(seg.segmento()).append(": ");
                sb.append(seg.fundos().stream()
                        .map(FiiEmAltaItemDTO::codigo)
                        .collect(Collectors.joining(", ")));
                sb.append("\n");
                for (var fii : seg.fundos()) {
                    if (fii.motivo() != null && !fii.motivo().isBlank()) {
                        sb.append("    ").append(fii.codigo()).append(": ").append(fii.motivo()).append("\n");
                    }
                }
            }
        }

        sb.append("\nSelecione 6 a 8 fundos e defina os pesos. Os pesos devem somar exatamente 100.00%.");
        return sb.toString();
    }

    // ── Validação e normalização ──────────────────────────────────────────────

    private List<CarteiraItemRequestDTO> validarEFiltrar(List<ItemIAResponse> itens,
                                                          Set<String> codigosValidos) {
        if (itens == null || itens.isEmpty()) {
            throw new RuntimeException("IA não retornou itens para a carteira.");
        }

        List<CarteiraItemRequestDTO> validos = itens.stream()
                .filter(item -> {
                    if (!codigosValidos.contains(item.codigoFundo())) {
                        log.warn("IA sugeriu código desconhecido '{}' — ignorado", item.codigoFundo());
                        return false;
                    }
                    if (item.peso() == null || item.peso().compareTo(BigDecimal.ZERO) <= 0) {
                        log.warn("Peso inválido para '{}' — ignorado", item.codigoFundo());
                        return false;
                    }
                    return true;
                })
                .map(item -> new CarteiraItemRequestDTO(
                        item.codigoFundo(),
                        item.peso().setScale(2, RoundingMode.HALF_UP),
                        item.justificativa()
                ))
                .toList();

        if (validos.size() < 3) {
            throw new RuntimeException(
                    "IA retornou apenas " + validos.size() + " fundo(s) válido(s). Mínimo esperado: 3.");
        }

        return validos;
    }

    private List<CarteiraItemRequestDTO> normalizarPesos(List<CarteiraItemRequestDTO> itens) {
        BigDecimal total = itens.stream()
                .map(CarteiraItemRequestDTO::peso)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cem = BigDecimal.valueOf(100);
        BigDecimal tolerancia = BigDecimal.valueOf(0.5);

        if (total.subtract(cem).abs().compareTo(tolerancia) <= 0 && total.compareTo(cem) == 0) {
            return itens;
        }

        log.info("Normalizando pesos: soma atual = {}%, ajustando para 100%", total);

        // Escalar proporcionalmente
        List<CarteiraItemRequestDTO> normalizados = itens.stream()
                .map(item -> {
                    BigDecimal pesoProporcional = item.peso()
                            .multiply(cem)
                            .divide(total, 2, RoundingMode.DOWN);
                    return new CarteiraItemRequestDTO(
                            item.codigoFundo(), pesoProporcional, item.justificativa());
                })
                .collect(Collectors.toList());

        // Corrigir diferença de arredondamento no maior peso
        BigDecimal somaAjustada = normalizados.stream()
                .map(CarteiraItemRequestDTO::peso)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = cem.subtract(somaAjustada);

        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            int idxMaior = 0;
            for (int i = 1; i < normalizados.size(); i++) {
                if (normalizados.get(i).peso().compareTo(normalizados.get(idxMaior).peso()) > 0) {
                    idxMaior = i;
                }
            }
            CarteiraItemRequestDTO maior = normalizados.get(idxMaior);
            normalizados.set(idxMaior, new CarteiraItemRequestDTO(
                    maior.codigoFundo(),
                    maior.peso().add(diff).setScale(2, RoundingMode.HALF_UP),
                    maior.justificativa()
            ));
        }

        return normalizados;
    }

    // ── Utilitários ───────────────────────────────────────────────────────────

    private String extrairJson(String texto) {
        String s = texto.strip();
        if (s.startsWith("```")) {
            int inicio = s.indexOf('\n');
            int fim = s.lastIndexOf("```");
            if (inicio != -1 && fim > inicio) return s.substring(inicio + 1, fim).strip();
        }
        return s;
    }

    private String abreviar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CarteiraIAResponse(
            String observacoes,
            List<ItemIAResponse> itens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemIAResponse(
            String codigoFundo,
            BigDecimal peso,
            String justificativa
    ) {}
}

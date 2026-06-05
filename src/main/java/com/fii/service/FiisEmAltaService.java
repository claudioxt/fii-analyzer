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
import com.fii.dto.FiiEmAltaItemDTO;
import com.fii.dto.FiiEmAltaPorSegmentoDTO;
import com.fii.dto.FiisEmAltaDTO;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.FundoImobiliarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FiisEmAltaService {

    private final AnthropicClient anthropicClient;
    private final FundoImobiliarioRepository fundoRepository;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-haiku-4-5";

    private static final List<String> QUERIES_NEWS = List.of(
            "FII recomendação comprar analistas casas análise",
            "fundos imobiliários mais recomendados analistas",
            "FII alta recomendação XP BTG Suno Itaú",
            "FII carteira recomendada analistas top pick"
    );

    private static final String SYSTEM_PROMPT = """
            Você é um especialista em Fundos de Investimento Imobiliário (FIIs) do mercado brasileiro.

            Sua tarefa é analisar notícias e textos coletados da internet e identificar quais FIIs estão sendo
            recomendados positivamente por analistas e casas de análise no momento atual.

            INSTRUÇÕES:
            - Identifique apenas FIIs com ticker brasileiro válido (padrão: letras maiúsculas + 11, ex: HGLG11, KNRI11)
            - Foque em recomendações de ANALISTAS — não em variação de cotação ou preço
            - Agrupe os FIIs por segmento (Logística, Shopping Centers, Lajes Corporativas, Recebíveis/CRI,
              Híbrido, Residencial, Hospitalar, Educacional, Agências, Outros)
            - Para cada FII, explique por que está sendo recomendado pelos analistas (1-2 frases)
            - Liste as fontes que mencionam o FII positivamente (casas de análise, portais)
            - Conte quantas menções positivas o FII recebeu
            - Elabore um resumo do momento atual do mercado de FIIs para analistas (2-3 frases)
            - Inclua apenas FIIs com pelo menos uma menção positiva clara de analistas

            FORMATO DE RESPOSTA:
            Retorne SOMENTE um objeto JSON válido, sem markdown, sem explicações adicionais:
            {
              "resumoMercado": "...",
              "porSegmento": [
                {
                  "segmento": "Logística",
                  "fundos": [
                    {
                      "codigo": "HGLG11",
                      "motivo": "...",
                      "fontes": ["XP Investimentos", "BTG Pactual"],
                      "mencoesPositivas": 3
                    }
                  ]
                }
              ]
            }

            Responda em português brasileiro. Se não houver dados suficientes para um segmento, não o inclua.
            """;

    @Cacheable(value = "fiisEmAlta", key = "'todos'")
    public FiisEmAltaDTO buscar() {
        log.info("Buscando FIIs em alta para analistas");

        List<String> trechos = new ArrayList<>();

        for (String query : QUERIES_NEWS) {
            try {
                List<String> noticias = buscarNoticiasPorQuery(query);
                trechos.addAll(noticias);
                log.info("Query '{}': {} resultados", query, noticias.size());
            } catch (Exception e) {
                log.warn("Falha na query '{}': {}", query, e.getMessage());
            }
        }

        try {
            String dadosFundsExplorer = scrapeTopFiis();
            if (!dadosFundsExplorer.isBlank()) {
                trechos.add("=== Funds Explorer — FIIs mais recomendados ===\n" + dadosFundsExplorer);
                log.info("Dados do Funds Explorer coletados");
            }
        } catch (Exception e) {
            log.warn("Falha ao scrape Funds Explorer: {}", e.getMessage());
        }

        if (trechos.isEmpty()) {
            log.warn("Nenhum dado coletado da internet. Retornando resultado vazio.");
            return semDados();
        }

        return analisarComIA(trechos);
    }

    private List<String> buscarNoticiasPorQuery(String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://news.google.com/rss/search?q=" + encoded + "&hl=pt-BR&gl=BR&ceid=BR:pt-419";

        String rssXml = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .ignoreContentType(true)
                .timeout(15000)
                .execute()
                .body();

        if (rssXml == null || rssXml.isBlank() || !rssXml.trim().startsWith("<")) {
            return List.of();
        }

        return parseRssParaTrechos(rssXml, 8);
    }

    private List<String> parseRssParaTrechos(String xml, int limite) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        NodeList items = doc.getElementsByTagName("item");
        List<String> trechos = new ArrayList<>();

        for (int i = 0; i < Math.min(items.getLength(), limite); i++) {
            org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
            String titulo = nodeText(item, "title");
            String descHtml = nodeText(item, "description");
            String resumo = Jsoup.parse(descHtml).text();
            String fonte = extrairFonteHtml(descHtml);

            if (!titulo.isBlank()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Fonte: ").append(fonte).append("\n");
                sb.append("Título: ").append(titulo).append("\n");
                if (!resumo.isBlank()) sb.append("Resumo: ").append(resumo);
                trechos.add(sb.toString());
            }
        }
        return trechos;
    }

    private String scrapeTopFiis() throws Exception {
        Document doc = Jsoup.connect("https://www.fundsexplorer.com.br/ranking")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .timeout(15000)
                .get();

        StringBuilder sb = new StringBuilder();

        // Tabela principal de ranking
        Elements rows = doc.select("table tbody tr, .ranking-table tr");
        int count = 0;
        for (Element row : rows) {
            if (count >= 20) break;
            Elements cols = row.select("td");
            if (cols.size() >= 2) {
                String ticker = cols.get(0).text().trim();
                if (ticker.matches("[A-Z]{4}11")) {
                    sb.append("FII: ").append(ticker);
                    if (cols.size() > 1) sb.append(" | ").append(cols.get(1).text().trim());
                    sb.append("\n");
                    count++;
                }
            }
        }

        // Fallback: buscar qualquer ticker visível na página
        if (sb.isEmpty()) {
            Elements elementos = doc.select(".ticker, .fund-ticker, [class*=ticker], strong");
            for (Element el : elementos) {
                String texto = el.text().trim();
                if (texto.matches("[A-Z]{4}11")) {
                    sb.append("FII mencionado: ").append(texto).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private FiisEmAltaDTO analisarComIA(List<String> trechos) {
        String userPrompt = montarPrompt(trechos);

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

            log.debug("Resposta IA FIIs em alta: {}", jsonResposta);

            String jsonLimpo = extrairJson(jsonResposta);
            EmAltaResponse resposta = objectMapper.readValue(jsonLimpo, EmAltaResponse.class);

            log.info("Análise FIIs em alta concluída. Cache hit: {}",
                    response.usage().cacheReadInputTokens().map(Object::toString).orElse("0"));

            Map<String, String> nomesPorCodigo = carregarNomesDosBanco(resposta);
            List<FiiEmAltaPorSegmentoDTO> porSegmento = construirResposta(resposta, nomesPorCodigo);
            int totalFundos = porSegmento.stream().mapToInt(s -> s.fundos().size()).sum();

            return new FiisEmAltaDTO(porSegmento, resposta.resumoMercado(), totalFundos, LocalDateTime.now());

        } catch (UnauthorizedException e) {
            log.error("API key da Anthropic inválida ou não configurada.");
            throw new IllegalStateException("Análise por IA indisponível: API key não configurada.");
        } catch (AnthropicException e) {
            log.error("Erro na API Anthropic ao buscar FIIs em alta: {}", e.getMessage());
            throw new RuntimeException("Erro na API de IA: " + e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao processar FIIs em alta: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar análise de FIIs em alta: " + e.getMessage());
        }
    }

    private String montarPrompt(List<String> trechos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analise os textos abaixo coletados da internet e identifique quais FIIs estão sendo ")
          .append("positivamente recomendados por analistas no momento atual.\n\n");
        sb.append("TEXTOS COLETADOS (").append(trechos.size()).append(" resultados):\n\n");

        for (int i = 0; i < trechos.size(); i++) {
            sb.append("--- Texto ").append(i + 1).append(" ---\n");
            sb.append(trechos.get(i)).append("\n\n");
        }

        sb.append("Com base nesses textos, identifique os FIIs em alta para analistas, agrupe por segmento e retorne em JSON conforme instruído.");
        return sb.toString();
    }

    private Map<String, String> carregarNomesDosBanco(EmAltaResponse resposta) {
        if (resposta.porSegmento() == null) return Map.of();
        List<String> codigos = resposta.porSegmento().stream()
                .filter(s -> s.fundos() != null)
                .flatMap(s -> s.fundos().stream())
                .map(FundoResponse::codigo)
                .toList();
        return fundoRepository.findAllById(codigos).stream()
                .collect(Collectors.toMap(FundoImobiliario::getCodigo, FundoImobiliario::getNome));
    }

    private List<FiiEmAltaPorSegmentoDTO> construirResposta(EmAltaResponse resposta,
                                                              Map<String, String> nomesPorCodigo) {
        if (resposta.porSegmento() == null) return List.of();
        return resposta.porSegmento().stream()
                .filter(s -> s.fundos() != null && !s.fundos().isEmpty())
                .map(seg -> new FiiEmAltaPorSegmentoDTO(
                        seg.segmento(),
                        seg.fundos().stream().map(f -> new FiiEmAltaItemDTO(
                                f.codigo(),
                                nomesPorCodigo.getOrDefault(f.codigo(), null),
                                f.motivo(),
                                f.fontes() != null ? f.fontes() : List.of(),
                                f.mencoesPositivas()
                        )).toList()
                ))
                .toList();
    }

    private FiisEmAltaDTO semDados() {
        return new FiisEmAltaDTO(
                List.of(),
                "Não foi possível coletar dados da internet no momento. Tente novamente mais tarde.",
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

    private String nodeText(org.w3c.dom.Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    private String extrairFonteHtml(String html) {
        org.jsoup.nodes.Element link = Jsoup.parse(html).selectFirst("a");
        return link != null ? link.text() : "Fonte desconhecida";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmAltaResponse(
            String resumoMercado,
            List<SegmentoResponse> porSegmento
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SegmentoResponse(
            String segmento,
            List<FundoResponse> fundos
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FundoResponse(
            String codigo,
            String motivo,
            List<String> fontes,
            int mencoesPositivas
    ) {}
}

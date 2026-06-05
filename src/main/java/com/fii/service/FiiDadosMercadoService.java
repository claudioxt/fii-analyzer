package com.fii.service;

import com.fii.dto.DadosMercadoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class FiiDadosMercadoService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // Yahoo Finance v8: preço atual + histórico diário (1 ano → variações semana/mês/ano)
    private static final String YAHOO_CHART_URL    = "https://query1.finance.yahoo.com/v8/finance/chart/%s.SA?range=1y&interval=1d&includePrePost=false";
    // Status Invest: P/VP e DY — quoteSummary do Yahoo retorna 404 para FIIs .SA
    private static final String STATUS_INVEST_URL  = "https://statusinvest.com.br/fundos-imobiliarios/%s";

    public record InfoBasicaFundo(String nome, String tipo, String segmento) {}

    /**
     * Tenta obter nome, tipo e segmento de um FII diretamente na internet.
     * Usa Yahoo Finance para confirmar existência e obter o nome,
     * e Status Invest para o segmento.
     * Retorna Optional.empty() se o ticker não existir em nenhuma fonte.
     */
    public Optional<InfoBasicaFundo> buscarInfoBasica(String codigo) {
        String nome = null;
        String segmento = "Outros";

        // 1. Yahoo Finance — confirma existência do ticker e extrai nome
        try {
            String chartJson = restClient.get()
                    .uri(String.format(YAHOO_CHART_URL, codigo))
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            JsonNode result = objectMapper.readTree(chartJson).path("chart").path("result");
            if (result.isArray() && !result.isEmpty()) {
                JsonNode meta = result.get(0).path("meta");
                String longName  = meta.path("longName").asText(null);
                String shortName = meta.path("shortName").asText(null);
                nome = (longName  != null && !longName.isBlank())  ? longName  :
                       (shortName != null && !shortName.isBlank()) ? shortName : null;
            }
        } catch (Exception e) {
            log.warn("Yahoo Finance: ticker {} não encontrado — {}", codigo, e.getMessage());
        }

        if (nome == null) {
            log.info("Ticker {} não confirmado no Yahoo Finance — considerado inexistente", codigo);
            return Optional.empty();
        }

        // 2. Status Invest — segmento e nome mais descritivo (se disponível)
        try {
            String url = String.format(STATUS_INVEST_URL, codigo.toLowerCase());
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12000)
                    .get();

            // Nome mais descritivo do Status Invest (substitui o shortName do Yahoo se encontrar)
            Element nomeEl = doc.selectFirst("h1.lp-name, h1.company-name, h1, .fund-name");
            if (nomeEl != null && !nomeEl.text().isBlank()) {
                nome = nomeEl.text().trim();
            }

            // Segmento / Tipo ANBIMA
            for (Element el : doc.select("span, h3, h4, div, p")) {
                String label = el.ownText().trim();
                if (label.equalsIgnoreCase("Tipo ANBIMA")
                        || label.equalsIgnoreCase("Segmento")
                        || label.equalsIgnoreCase("Tipo")) {
                    Element next = el.nextElementSibling();
                    if (next != null && !next.text().isBlank()) {
                        segmento = next.text().trim();
                        break;
                    }
                    Element pai = el.parent();
                    if (pai != null) {
                        String paiTexto = pai.text().replace(label, "").trim();
                        if (!paiTexto.isBlank() && paiTexto.length() < 80) {
                            segmento = paiTexto;
                            break;
                        }
                    }
                }
            }
            log.info("Status Invest retornou para {}: nome='{}', segmento='{}'", codigo, nome, segmento);
        } catch (Exception e) {
            log.warn("Status Invest indisponível para {}: {}", codigo, e.getMessage());
        }

        return Optional.of(new InfoBasicaFundo(nome, "FII", segmento));
    }

    @Cacheable(value = "dadosMercado", key = "#codigo", unless = "#result == null")
    public DadosMercadoDTO buscarDadosMercado(String codigo) {
        try {
            log.info("Buscando dados de mercado para {}", codigo);

            // Yahoo Finance (preço/histórico) e Status Invest (P/VP/DY) em paralelo
            CompletableFuture<String> chartFuture = CompletableFuture.supplyAsync(() ->
                    restClient.get()
                            .uri(String.format(YAHOO_CHART_URL, codigo))
                            .header("Accept", "application/json")
                            .retrieve()
                            .body(String.class));

            CompletableFuture<BigDecimal[]> siFuture = CompletableFuture.supplyAsync(() ->
                    buscarFundamentaisStatusInvest(codigo));

            String chartJson      = chartFuture.join();
            BigDecimal[] siFund   = siFuture.join();

            DadosMercadoDTO result = parseResposta(codigo, chartJson, siFund);
            if (result != null) {
                log.info("Dados recebidos para {} — preço: {}, P/VP: {}, DY: {}%",
                        codigo, result.precoAtual(), result.pvp(), result.dividendYield());
            }
            return result;

        } catch (Exception e) {
            log.error("Erro ao buscar dados de mercado para {}: {} — {}",
                    codigo, e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    // ── Yahoo Finance chart ────────────────────────────────────────────────────

    private DadosMercadoDTO parseResposta(String codigo, String chartJson, BigDecimal[] siFund) {
        JsonNode chartResult = objectMapper.readTree(chartJson).path("chart").path("result");
        if (!chartResult.isArray() || chartResult.isEmpty()) {
            log.warn("Yahoo Finance não retornou dados para {}", codigo);
            return null;
        }

        JsonNode meta  = chartResult.get(0).path("meta");
        JsonNode quote = chartResult.get(0).path("indicators").path("quote");

        BigDecimal precoAtual  = numDirect(meta, "regularMarketPrice");
        BigDecimal variacaoDia = numDirect(meta, "regularMarketChangePercent");

        List<Double> closes = extrairCloses(quote);
        double current = precoAtual != null ? precoAtual.doubleValue() : 0;

        BigDecimal varSemana = calcVariacao(current, closes, 5);
        BigDecimal varMes    = calcVariacao(current, closes, 21);
        BigDecimal varAno    = closes.size() >= 200
                ? calcVariacao(current, closes, closes.size() - 1)
                : null;

        // P/VP e DY vêm do Status Invest (siFund[0] e siFund[1])
        BigDecimal pvp = siFund[0];
        BigDecimal dy  = siFund[1]; // já em % (ex: 8,30)

        return new DadosMercadoDTO(
                precoAtual, pvp,
                round(variacaoDia), round(varSemana), round(varMes), round(varAno),
                dy, LocalDateTime.now()
        );
    }

    private List<Double> extrairCloses(JsonNode quote) {
        List<Double> closes = new ArrayList<>();
        if (!quote.isArray() || quote.isEmpty()) return closes;
        JsonNode arr = quote.get(0).path("close");
        if (arr.isArray()) {
            for (JsonNode c : arr) {
                if (!c.isNull() && c.isNumber()) closes.add(c.doubleValue());
            }
        }
        return closes;
    }

    private BigDecimal calcVariacao(double atual, List<Double> historico, int diasAtras) {
        if (historico.isEmpty() || atual == 0 || diasAtras <= 0) return null;
        int idx = historico.size() - 1 - diasAtras;
        if (idx < 0) idx = 0;
        double anterior = historico.get(idx);
        if (anterior == 0) return null;
        return round(BigDecimal.valueOf((atual - anterior) / anterior * 100));
    }

    /** Número direto no nó JSON (formato do v8 chart meta). */
    private BigDecimal numDirect(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode() || !v.isNumber()) return null;
        return v.decimalValue();
    }

    // ── Status Invest — P/VP e DY ─────────────────────────────────────────────

    /**
     * Retorna [pvp, dy] em BigDecimal.
     * DY já está em formato percentual (ex: 8.30), não decimal.
     */
    private BigDecimal[] buscarFundamentaisStatusInvest(String codigo) {
        BigDecimal pvp = null;
        BigDecimal dy  = null;
        try {
            String url = String.format(STATUS_INVEST_URL, codigo.toLowerCase());
            log.debug("Buscando P/VP e DY no Status Invest para {}", codigo);

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(12000)
                    .get();

            // Percorre todos os elementos que podem ser labels de indicadores
            for (Element el : doc.select("span, h3, h4, div")) {
                String texto = el.ownText().trim();
                if (pvp == null && texto.equalsIgnoreCase("P/VP")) {
                    pvp = valorDoIndicador(el);
                }
                if (dy == null && (texto.equalsIgnoreCase("DY")
                        || texto.equalsIgnoreCase("D.Y.")
                        || texto.equalsIgnoreCase("Dividend Yield"))) {
                    dy = valorDoIndicador(el);
                }
                if (pvp != null && dy != null) break;
            }

            if (pvp != null || dy != null) {
                log.debug("Status Invest retornou para {} — P/VP: {}, DY: {}%", codigo, pvp, dy);
            } else {
                log.warn("Status Invest não encontrou P/VP/DY para {}", codigo);
            }
        } catch (Exception e) {
            log.warn("Status Invest indisponível para {}: {}", codigo, e.getMessage());
        }
        return new BigDecimal[]{pvp, dy};
    }

    /** Extrai o valor numérico próximo ao label do indicador. */
    private BigDecimal valorDoIndicador(Element labelEl) {
        // Estratégia 1: irmão seguinte
        Element next = labelEl.nextElementSibling();
        if (next != null) {
            BigDecimal v = parseBR(next.ownText());
            if (v != null) return v;
        }
        // Estratégia 2: elemento com .value ou <strong> dentro do pai
        Element pai = labelEl.parent();
        if (pai != null) {
            for (Element candidate : pai.select("[class*='value'], strong, b")) {
                if (candidate.equals(labelEl)) continue;
                BigDecimal v = parseBR(candidate.ownText());
                if (v != null) return v;
            }
            // Estratégia 3: próximo pai (container do indicador)
            Element avo = pai.parent();
            if (avo != null) {
                for (Element candidate : avo.select("[class*='value'], strong")) {
                    BigDecimal v = parseBR(candidate.ownText());
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    /**
     * Converte string no formato brasileiro (ex: "0,92" ou "8,30%") para BigDecimal.
     * Remove %, R$ e separador de milhar antes de parsear.
     */
    private BigDecimal parseBR(String text) {
        if (text == null || text.isBlank()) return null;
        String limpo = text.replace("R$", "").replace("%", "")
                .replace(" ", "").replace(".", "").replace(",", ".").trim();
        if (limpo.isEmpty() || limpo.equals("-") || limpo.equalsIgnoreCase("n/d")) return null;
        try {
            BigDecimal val = new BigDecimal(limpo);
            return val.compareTo(BigDecimal.ZERO) > 0 ? round(val) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal round(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }
}
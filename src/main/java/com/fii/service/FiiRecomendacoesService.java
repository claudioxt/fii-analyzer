package com.fii.service;

import com.fii.dto.NoticiaDTO;
import com.fii.dto.RecomendacaoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class FiiRecomendacoesService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String STATUS_INVEST_URL = "https://statusinvest.com.br/fundos-imobiliarios/";

    @Cacheable(value = "recomendacoes", key = "#codigo", unless = "#result.isEmpty()")
    public List<RecomendacaoDTO> buscarRecomendacoes(String codigo) {
        List<RecomendacaoDTO> recos = new ArrayList<>();

        try {
            recos.addAll(scrapeStatusInvest(codigo));
        } catch (Exception e) {
            log.warn("Status Invest não disponível para {}: {}", codigo, e.getMessage());
        }

        if (recos.isEmpty()) {
            recos.addAll(buscarViaNews(codigo));
        }

        return recos;
    }

    private List<RecomendacaoDTO> scrapeStatusInvest(String codigo) throws Exception {
        log.info("Buscando recomendações no Status Invest para {}", codigo);
        Document doc = Jsoup.connect(STATUS_INVEST_URL + codigo.toLowerCase())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .timeout(12000)
                .get();

        List<RecomendacaoDTO> result = new ArrayList<>();
        result.addAll(extrairDeScriptsJson(doc));
        if (result.isEmpty()) {
            result.addAll(extrairDeHtml(doc));
        }
        return result;
    }

    private List<RecomendacaoDTO> extrairDeScriptsJson(Document doc) {
        List<RecomendacaoDTO> result = new ArrayList<>();
        Elements scripts = doc.select("script:not([src])");
        for (Element script : scripts) {
            String content = script.html();
            if (!content.contains("analyst") && !content.contains("recomend")) continue;
            try {
                int start = content.indexOf('{');
                if (start < 0) continue;
                JsonNode node = objectMapper.readTree(content.substring(start));
                result.addAll(parseAnalystsFromJson(node));
                if (!result.isEmpty()) break;
            } catch (Exception ignored) {}
        }
        return result;
    }

    private List<RecomendacaoDTO> parseAnalystsFromJson(JsonNode root) {
        List<RecomendacaoDTO> result = new ArrayList<>();
        JsonNode analysts = root.path("analysts");
        if (!analysts.isArray()) analysts = root.path("data").path("analysts");
        if (!analysts.isArray()) analysts = root.path("props").path("pageProps").path("analysts");

        if (analysts.isArray()) {
            for (JsonNode a : analysts) {
                String casa = a.path("name").asText(null);
                if (casa == null) casa = a.path("analyst").asText(null);
                String reco = normalizar(a.path("recommendation").asText(""));
                if (reco.isEmpty()) reco = normalizar(a.path("recomendacao").asText(""));
                BigDecimal target = parseBd(a.path("targetPrice").asText(null));
                if (target == null) target = parseBd(a.path("preco_alvo").asText(null));
                String fund = a.path("fundamentacao").asText(null);
                if (casa != null && !reco.isEmpty()) {
                    result.add(new RecomendacaoDTO(casa, reco, target, fund, LocalDate.now()));
                }
            }
        }
        return result;
    }

    private List<RecomendacaoDTO> extrairDeHtml(Document doc) {
        List<RecomendacaoDTO> result = new ArrayList<>();
        String[] selectors = {
                ".analyst-detail-item", ".analyst-item", ".recommendation-item",
                "[data-analyst]", ".recommend-item", ".analista-row"
        };
        for (String sel : selectors) {
            Elements els = doc.select(sel);
            if (els.isEmpty()) continue;
            for (Element el : els) {
                String casa = firstText(el, ".name", ".analyst-name", ".casa", ".broker");
                String reco = normalizar(firstText(el, ".recommend", ".recomendacao", ".recommendation", ".signal"));
                BigDecimal target = parseBd(firstText(el, ".target", ".preco-alvo", ".price-target"));
                if (!casa.isEmpty() && !reco.isEmpty()) {
                    result.add(new RecomendacaoDTO(casa, reco, target, null, LocalDate.now()));
                }
            }
            if (!result.isEmpty()) break;
        }
        return result;
    }

    private List<RecomendacaoDTO> buscarViaNews(String codigo) {
        try {
            String query = URLEncoder.encode(codigo + " recomendação analistas comprar vender preço alvo", StandardCharsets.UTF_8);
            String url = "https://news.google.com/rss/search?q=" + query + "&hl=pt-BR&gl=BR&ceid=BR:pt-419";
            String rssXml = restClient.get().uri(url).retrieve().body(String.class);
            List<NoticiaDTO> noticias = parseRssSimples(rssXml, 10);
            return extrairRecoDeNoticias(noticias);
        } catch (Exception e) {
            log.warn("Erro ao buscar recomendações via notícias para {}: {}", codigo, e.getMessage());
            return List.of();
        }
    }

    private List<NoticiaDTO> parseRssSimples(String xml, int limite) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        NodeList items = doc.getElementsByTagName("item");
        List<NoticiaDTO> lista = new ArrayList<>();
        for (int i = 0; i < Math.min(items.getLength(), limite); i++) {
            org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
            String titulo   = nodeText(item, "title");
            String link     = nodeText(item, "link");
            String descHtml = nodeText(item, "description");
            String fonte    = extrairFonteHtml(descHtml);
            String resumo   = Jsoup.parse(descHtml).text();
            lista.add(new NoticiaDTO(titulo, resumo, fonte, link, null));
        }
        return lista;
    }

    private List<RecomendacaoDTO> extrairRecoDeNoticias(List<NoticiaDTO> noticias) {
        List<RecomendacaoDTO> result = new ArrayList<>();
        for (NoticiaDTO n : noticias) {
            String texto = (n.titulo() + " " + n.resumo()).toUpperCase();
            String reco = detectar(texto);
            if (reco != null) {
                BigDecimal target = extrairPrecoAlvo(texto);
                result.add(new RecomendacaoDTO(
                        n.fonte(), reco, target, n.titulo(),
                        n.dataPublicacao() != null ? n.dataPublicacao().toLocalDate() : LocalDate.now()
                ));
            }
        }
        return result;
    }

    private String detectar(String texto) {
        if (texto.contains("COMPRAR") || texto.contains("COMPRA ") || texto.contains(" BUY ")) return "COMPRAR";
        if (texto.contains("VENDER") || texto.contains("VENDA ") || texto.contains(" SELL ")) return "VENDER";
        if (texto.contains("MANTER") || texto.contains("NEUTRO") || texto.contains("HOLD")) return "MANTER";
        return null;
    }

    private BigDecimal extrairPrecoAlvo(String texto) {
        Pattern p = Pattern.compile("(?:PRE[ÇC]O[- ]?ALVO|TARGET)[^\\d]*(\\d{2,3}[,.]?\\d*)");
        Matcher m = p.matcher(texto);
        if (m.find()) {
            try { return new BigDecimal(m.group(1).replace(",", ".")); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String normalizar(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String upper = raw.toUpperCase().trim();
        if (upper.contains("COMPRAR") || upper.contains("BUY") || upper.contains("OUTPERFORM") || upper.contains("OVERWEIGHT")) return "COMPRAR";
        if (upper.contains("VENDER") || upper.contains("SELL") || upper.contains("UNDERPERFORM") || upper.contains("UNDERWEIGHT")) return "VENDER";
        if (upper.contains("MANTER") || upper.contains("HOLD") || upper.contains("NEUTRO") || upper.contains("NEUTRAL")) return "MANTER";
        return upper;
    }

    private BigDecimal parseBd(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return new BigDecimal(text.replaceAll("[^\\d.]", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstText(Element parent, String... selectors) {
        for (String sel : selectors) {
            Element el = parent.selectFirst(sel);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }

    private String nodeText(org.w3c.dom.Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        return nl.getLength() > 0 ? nl.item(0).getTextContent().trim() : "";
    }

    private String extrairFonteHtml(String html) {
        org.jsoup.nodes.Element link = Jsoup.parse(html).selectFirst("a");
        return link != null ? link.text() : "Fonte desconhecida";
    }
}
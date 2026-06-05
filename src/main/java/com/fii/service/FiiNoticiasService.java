package com.fii.service;

import com.fii.dto.NoticiaDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class FiiNoticiasService {

    @Cacheable(value = "noticias", key = "#codigo", unless = "#result.isEmpty()")
    public List<NoticiaDTO> buscarNoticias(String codigo) {
        String url = "https://news.google.com/rss/search?q=" + codigo
                + "+FII&hl=pt-BR&gl=BR&ceid=BR:pt-419";
        try {
            log.info("Buscando notícias para {} - URL: {}", codigo, url);
            String rssXml = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .ignoreContentType(true)
                    .timeout(15000)
                    .execute()
                    .body();

            if (rssXml == null || rssXml.isBlank()) {
                log.warn("Resposta vazia do Google News para {}", codigo);
                return List.of();
            }
            log.debug("RSS recebido para {} — {} bytes", codigo, rssXml.length());

            if (!rssXml.trim().startsWith("<")) {
                log.warn("Resposta não é XML para {}. Preview: {}", codigo,
                        rssXml.substring(0, Math.min(300, rssXml.length())));
                return List.of();
            }
            List<NoticiaDTO> result = parseRss(rssXml, 6);
            log.info("Notícias encontradas para {}: {}", codigo, result.size());
            return result;
        } catch (Exception e) {
            log.error("Erro ao buscar notícias para {}: {} — {}", codigo, e.getClass().getSimpleName(), e.getMessage(), e);
            return List.of();
        }
    }

    List<NoticiaDTO> parseRss(String xml, int limite) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

        NodeList items = doc.getElementsByTagName("item");
        List<NoticiaDTO> noticias = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String titulo   = getTagText(item, "title");
            String link     = extrairLink(item);
            String pubDate  = getTagText(item, "pubDate");
            String descHtml = getTagText(item, "description");
            String fonte    = extrairFonte(item, descHtml);
            String resumo   = Jsoup.parse(descHtml).text();

            noticias.add(new NoticiaDTO(titulo, resumo, fonte, link, parseData(pubDate)));
        }

        return noticias.stream()
                .sorted(Comparator.comparing(NoticiaDTO::dataPublicacao,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limite)
                .toList();
    }

    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }

    /**
     * O elemento <link> em RSS 2.0 pode ser um text node após o elemento ou um
     * atributo href (Atom). Tenta as duas abordagens.
     */
    private String extrairLink(Element item) {
        // Tentativa 1: <link> como elemento normal
        String direct = getTagText(item, "link");
        if (!direct.isBlank()) return direct;

        // Tentativa 2: <atom:link href="..."> ou <link href="...">
        NodeList links = item.getElementsByTagNameNS("*", "link");
        for (int i = 0; i < links.getLength(); i++) {
            String href = ((Element) links.item(i)).getAttribute("href");
            if (!href.isBlank()) return href;
        }
        return "";
    }

    private String extrairFonte(Element item, String descHtml) {
        NodeList sources = item.getElementsByTagName("source");
        if (sources.getLength() > 0) {
            String texto = sources.item(0).getTextContent().trim();
            if (!texto.isBlank()) return texto;
        }
        org.jsoup.nodes.Element link = Jsoup.parse(descHtml).selectFirst("a");
        return link != null ? link.text() : "Fonte desconhecida";
    }

    private LocalDateTime parseData(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
            Date date = sdf.parse(pubDate);
            return date.toInstant().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDateTime();
        } catch (ParseException e) {
            return null;
        }
    }
}
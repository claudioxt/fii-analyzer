package com.fii.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fii.entity.CarteiraRecomendada;
import com.fii.entity.CarteiraRecomendadaItem;
import com.fii.repository.CarteiraRecomendadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraRecomendadaBuscaService {

    private final AnthropicClient anthropicClient;
    private final CarteiraRecomendadaRepository carteiraRepository;
    private final ObjectMapper objectMapper;

    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String GOOGLE_NEWS_RSS =
            "https://news.google.com/rss/search?q=%s&hl=pt-BR&gl=BR&ceid=BR:pt-419";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Async("analiseExecutor")
    @Transactional
    public void buscarParaMesAsync(LocalDate mes) {
        LocalDate primeiroDia = mes.withDayOfMonth(1);
        log.info("Buscando carteiras recomendadas na web para {}", primeiroDia);
        try {
            // Verifica novamente dentro da thread — pode ter sido populado enquanto esperava
            if (!carteiraRepository.findByMesReferenciaOrderByCasaDeAnaliseAsc(primeiroDia).isEmpty()) {
                log.info("Carteiras para {} já existem. Busca cancelada.", primeiroDia);
                return;
            }

            String textos = coletarTextos(mes);
            if (textos.isBlank()) {
                log.warn("Nenhum texto coletado para {}", primeiroDia);
                return;
            }

            List<CarteiraBuscaDTO> encontradas = extrairComIA(textos, mes);
            if (encontradas.isEmpty()) {
                log.warn("IA não identificou carteiras nos textos coletados para {}", primeiroDia);
                return;
            }

            persistir(encontradas, primeiroDia);
        } catch (Exception e) {
            log.error("Erro na busca web de carteiras para {}: {}", primeiroDia, e.getMessage(), e);
        }
    }

    // ── Coleta de textos via Google News RSS + fetch de artigos ─────────────

    private String coletarTextos(LocalDate mes) {
        String mesNome = mes.format(DateTimeFormatter.ofPattern("MMMM", PT_BR));
        String ano = String.valueOf(mes.getYear());

        List<String> queries = List.of(
                "carteira recomendada FII " + mesNome + " " + ano,
                "portfólio FII recomendado " + mesNome + " " + ano + " XP BTG Genial",
                "melhores FIIs comprar " + mesNome + " " + ano
        );

        StringBuilder sb = new StringBuilder();
        java.util.Set<String> urlsVisitadas = new java.util.HashSet<>();

        for (String query : queries) {
            try {
                String rssUrl = String.format(GOOGLE_NEWS_RSS,
                        URLEncoder.encode(query, StandardCharsets.UTF_8));

                String xml = Jsoup.connect(rssUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/rss+xml, application/xml, text/xml")
                        .timeout(12000)
                        .ignoreContentType(true)
                        .execute()
                        .body();

                var items = Jsoup.parse(xml, "", Parser.xmlParser()).select("item");
                log.info("RSS query '{}': {} artigos encontrados", query, items.size());

                for (var item : items.stream().limit(6).toList()) {
                    String titulo = item.select("title").text();
                    sb.append("TÍTULO: ").append(titulo).append("\n");

                    // Extrai URL real do artigo (o link do RSS é um redirect do Google)
                    String artigoUrl = item.select("link").text();
                    if (artigoUrl.isBlank()) artigoUrl = item.select("guid").text();

                    if (!artigoUrl.isBlank() && urlsVisitadas.add(artigoUrl)) {
                        String conteudo = fetchConteudoArtigo(artigoUrl);
                        if (!conteudo.isBlank()) {
                            sb.append("CONTEÚDO:\n").append(conteudo).append("\n");
                        }
                    }
                    sb.append("---\n");
                }
            } catch (Exception e) {
                log.warn("Falha na query '{}': {}", query, e.getMessage());
            }
        }

        String resultado = sb.toString().trim();
        log.info("Total de texto coletado: {} chars", resultado.length());
        return resultado;
    }

    private String fetchConteudoArtigo(String url) {
        try {
            var response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(10000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() >= 400) {
                log.debug("Artigo retornou {}: {}", response.statusCode(), url);
                return "";
            }

            var doc = response.parse();

            // Remove elementos irrelevantes
            doc.select("script, style, nav, header, footer, .menu, .sidebar, .ad, iframe").remove();

            // Tenta selectors de conteúdo em ordem de especificidade
            for (String selector : List.of(
                    "article", "[itemprop=articleBody]", ".article-body", ".post-content",
                    ".content-body", ".entry-content", ".materia", "main", ".content")) {
                var el = doc.select(selector).first();
                if (el != null && el.text().length() > 300) {
                    String texto = el.text();
                    log.debug("Artigo fetchado ({} chars): {}", texto.length(), url);
                    return texto.length() > 3000 ? texto.substring(0, 3000) : texto;
                }
            }

            // Fallback: body completo
            String body = doc.body().text();
            return body.length() > 3000 ? body.substring(0, 3000) : body;

        } catch (Exception e) {
            log.debug("Não foi possível acessar {}: {}", url, e.getMessage());
            return "";
        }
    }

    // ── Extração estruturada via IA ───────────────────────────────────────────

    private List<CarteiraBuscaDTO> extrairComIA(String textos, LocalDate mes) {
        String mesNome = mes.format(DateTimeFormatter.ofPattern("MMMM", PT_BR));
        String textoLimitado = textos.length() > 12000 ? textos.substring(0, 12000) : textos;

        String prompt = """
                Analise os textos abaixo sobre recomendações de FIIs para %s/%d e extraia carteiras recomendadas.

                TEXTOS:
                ---
                %s
                ---

                Extraia todas as carteiras de FIIs que conseguir identificar. Para cada carteira:
                - Identifique a casa de análise/corretora (ex: "XP Investimentos", "BTG Pactual", "Rico", "Genial", "Suno Research", "Itaú", "Empiricus", etc.)
                - Liste os FIIs com código no formato XXXX11 e peso percentual
                - Se o peso não estiver explícito, distribua igualmente (100 / número de FIIs)
                - Inclua justificativa resumida por FII quando disponível

                Retorne SOMENTE JSON válido, sem markdown:
                [
                  {
                    "casaDeAnalise": "Nome da casa de análise",
                    "observacoes": "Estratégia ou contexto da carteira",
                    "itens": [
                      {"codigoFundo": "HGLG11", "peso": 10.0, "justificativa": "..."}
                    ]
                  }
                ]

                REGRAS:
                - Só inclua FIIs com código válido (letras maiúsculas + número, ex: HGLG11, KNCR11)
                - Mínimo de 3 FIIs por carteira para ser incluída
                - Não invente dados — use apenas o que estiver no texto
                - Se não encontrar nenhuma carteira válida, retorne []
                """.formatted(mesNome, mes.getYear(), textoLimitado);

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(MODEL)
                            .maxTokens(2048L)
                            .addUserMessage(prompt)
                            .build()
            );

            String jsonRaw = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .findFirst()
                    .orElse("[]");

            String json = extrairJsonArray(jsonRaw);
            log.debug("IA retornou {} chars de carteiras", json.length());

            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CarteiraBuscaDTO.class));

        } catch (Exception e) {
            log.error("Erro ao extrair carteiras com IA: {}", e.getMessage(), e);
            return List.of();
        }
    }

    // ── Persistência ──────────────────────────────────────────────────────────

    private void persistir(List<CarteiraBuscaDTO> carteiras, LocalDate primeiroDia) {
        int salvas = 0;

        for (CarteiraBuscaDTO dto : carteiras) {
            if (dto.casaDeAnalise() == null || dto.casaDeAnalise().isBlank()) continue;
            if (dto.itens() == null || dto.itens().size() < 3) continue;

            // Evita duplicata para a mesma casa + mês
            boolean jaExiste = carteiraRepository
                    .findByCasaDeAnaliseIgnoreCaseOrderByMesReferenciaDesc(dto.casaDeAnalise())
                    .stream()
                    .anyMatch(c -> c.getMesReferencia().equals(primeiroDia));
            if (jaExiste) continue;

            // Valida tickers e normaliza pesos
            List<CarteiraBuscaItemDTO> itensValidos = dto.itens().stream()
                    .filter(i -> i.codigoFundo() != null && i.codigoFundo().matches("[A-Z]{3,5}[0-9]{1,2}"))
                    .toList();

            if (itensValidos.size() < 3) continue;

            double totalPeso = itensValidos.stream()
                    .mapToDouble(i -> i.peso() != null ? i.peso() : 0)
                    .sum();

            CarteiraRecomendada entidade = CarteiraRecomendada.builder()
                    .casaDeAnalise(dto.casaDeAnalise())
                    .mesReferencia(primeiroDia)
                    .observacoes(dto.observacoes())
                    .build();

            for (CarteiraBuscaItemDTO item : itensValidos) {
                double peso = item.peso() != null && item.peso() > 0 ? item.peso() : 0;
                if (totalPeso > 0 && Math.abs(totalPeso - 100) > 1) {
                    peso = BigDecimal.valueOf(peso / totalPeso * 100)
                            .setScale(2, RoundingMode.HALF_UP).doubleValue();
                }
                if (peso <= 0) {
                    peso = BigDecimal.valueOf(100.0 / itensValidos.size())
                            .setScale(2, RoundingMode.HALF_UP).doubleValue();
                }

                entidade.getItens().add(CarteiraRecomendadaItem.builder()
                        .carteira(entidade)
                        .codigoFundo(item.codigoFundo())
                        .peso(BigDecimal.valueOf(peso))
                        .justificativa(item.justificativa())
                        .build());
            }

            carteiraRepository.save(entidade);
            salvas++;
            log.info("Carteira salva via web: {} — {}", dto.casaDeAnalise(), primeiroDia);
        }

        log.info("Busca web concluída para {}: {}/{} carteira(s) salva(s)",
                primeiroDia, salvas, carteiras.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extrairJsonArray(String texto) {
        int primeiro = texto.indexOf('[');
        int ultimo = texto.lastIndexOf(']');
        if (primeiro == -1 || ultimo <= primeiro) return "[]";
        return texto.substring(primeiro, ultimo + 1);
    }

    // ── DTOs internos de deserialização ──────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CarteiraBuscaDTO(
            String casaDeAnalise,
            String observacoes,
            List<CarteiraBuscaItemDTO> itens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CarteiraBuscaItemDTO(
            String codigoFundo,
            Double peso,
            String justificativa
    ) {}
}

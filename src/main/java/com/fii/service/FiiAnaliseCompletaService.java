package com.fii.service;

import com.fii.dto.DadosMercadoDTO;
import com.fii.dto.FiiAnaliseCompletaDTO;
import com.fii.dto.NoticiaDTO;
import com.fii.dto.RecomendacaoDTO;
import com.fii.repository.FundoImobiliarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FiiAnaliseCompletaService {

    private final FundoImobiliarioRepository repository;
    private final FiiDadosMercadoService dadosMercadoService;
    private final FiiNoticiasService noticiasService;
    private final FiiRecomendacoesService recomendacoesService;

    public FiiAnaliseCompletaDTO analisar(String codigo) {
        String codigoUpper = codigo.toUpperCase();

        // Nome do fundo (se cadastrado), ou o próprio código
        String nome = repository.findById(codigoUpper)
                .map(f -> f.getNome())
                .orElse(codigoUpper);

        log.info("Iniciando análise completa para {}", codigoUpper);

        // Consultas externas em paralelo para reduzir tempo total de resposta
        CompletableFuture<DadosMercadoDTO> mercadoFuture =
                CompletableFuture.supplyAsync(() -> dadosMercadoService.buscarDadosMercado(codigoUpper));
        CompletableFuture<List<NoticiaDTO>> noticiasFuture =
                CompletableFuture.supplyAsync(() -> noticiasService.buscarNoticias(codigoUpper));
        CompletableFuture<List<RecomendacaoDTO>> recoFuture =
                CompletableFuture.supplyAsync(() -> recomendacoesService.buscarRecomendacoes(codigoUpper));

        DadosMercadoDTO mercado   = mercadoFuture.join();
        List<NoticiaDTO> noticias = noticiasFuture.join();
        List<RecomendacaoDTO> recos = recoFuture.join();

        String consenso = computarConsenso(recos, mercado);

        log.info("Análise finalizada para {} — {} notícias, {} recomendações, consenso: {}",
                codigoUpper, noticias.size(), recos.size(), consenso);

        return new FiiAnaliseCompletaDTO(codigoUpper, nome, mercado, noticias, recos, consenso, LocalDateTime.now());
    }

    private String computarConsenso(List<RecomendacaoDTO> recos, DadosMercadoDTO mercado) {
        if (!recos.isEmpty()) {
            // Consenso pela recomendação mais frequente
            return recos.stream()
                    .collect(Collectors.groupingBy(RecomendacaoDTO::recomendacao, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("NEUTRO");
        }

        // Estimativa baseada em P/VP quando não há cobertura de analistas
        if (mercado != null && mercado.pvp() != null) {
            double pvp = mercado.pvp().doubleValue();
            if (pvp < 0.90) return "POSSÍVEL OPORTUNIDADE (P/VP < 0,90)";
            if (pvp > 1.10) return "ATENÇÃO (P/VP > 1,10)";
            return "NEUTRO";
        }

        return "DADOS INSUFICIENTES";
    }
}
package com.fii.service;

import com.fii.dto.DadosMercadoDTO;
import com.fii.dto.FundoImobiliarioRequestDTO;
import com.fii.dto.FundoImobiliarioResponseDTO;
import com.fii.dto.RecomendacaoDTO;
import com.fii.entity.FundoImobiliario;
import com.fii.repository.FundoImobiliarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FundoImobiliarioService {

    private final FundoImobiliarioRepository repository;
    private final FiiDadosMercadoService dadosMercadoService;
    private final FiiRecomendacoesService recomendacoesService;
    private final FiisEmAltaService fiisEmAltaService;

    @Transactional(readOnly = true)
    public List<FundoImobiliarioResponseDTO> listarTodos() {
        log.info("Listando todos os fundos imobiliários");
        List<FundoImobiliario> fundos = repository.findAll();

        // Buzz: uma única chamada cacheada para todos os FIIs
        Map<String, Integer> buzzPorCodigo = carregarBuzz();

        // Dados de mercado + consenso em paralelo por FII (ambos cacheados por código)
        List<CompletableFuture<FundoImobiliarioResponseDTO>> futures = fundos.stream()
                .map(fundo -> CompletableFuture.supplyAsync(() ->
                        enriquecer(fundo, buzzPorCodigo.getOrDefault(fundo.getCodigo(), null))))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    @Transactional(readOnly = true)
    public FundoImobiliarioResponseDTO buscarPorCodigo(String codigo) {
        log.info("Buscando fundo imobiliário: {}", codigo);
        FundoImobiliario fundo = repository.findById(codigo)
                .orElseGet(() -> buscarNaInternet(codigo));
        Map<String, Integer> buzz = carregarBuzz();
        return enriquecer(fundo, buzz.getOrDefault(codigo, null));
    }

    private FundoImobiliario buscarNaInternet(String codigo) {
        log.info("Fundo {} não está na base — buscando na internet", codigo);
        return dadosMercadoService.buscarInfoBasica(codigo)
                .map(info -> {
                    log.info("Fundo {} encontrado na internet: nome='{}', segmento='{}'",
                            codigo, info.nome(), info.segmento());
                    return FundoImobiliario.builder()
                            .codigo(codigo)
                            .nome(info.nome())
                            .tipo(info.tipo())
                            .segmento(info.segmento())
                            .build();
                })
                .orElseThrow(() -> new EntityNotFoundException(
                        "Fundo imobiliário não encontrado: " + codigo));
    }

    @Transactional
    public FundoImobiliarioResponseDTO criar(FundoImobiliarioRequestDTO dto) {
        log.info("Criando fundo imobiliário: {}", dto.codigo());
        if (repository.existsById(dto.codigo())) {
            throw new IllegalArgumentException("Já existe um fundo com o código: " + dto.codigo());
        }

        String nome = valorOuNull(dto.nome());
        String tipo = valorOuNull(dto.tipo());
        String segmento = valorOuNull(dto.segmento());

        if (nome == null || tipo == null || segmento == null) {
            log.info("Campos ausentes — buscando dados na internet para {}", dto.codigo());
            FiiDadosMercadoService.InfoBasicaFundo info = dadosMercadoService
                    .buscarInfoBasica(dto.codigo())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "FII não encontrado na internet: " + dto.codigo()));
            if (nome == null) nome = info.nome();
            if (tipo == null) tipo = info.tipo();
            if (segmento == null) segmento = info.segmento();
            log.info("Dados obtidos para {}: nome='{}', tipo='{}', segmento='{}'",
                    dto.codigo(), nome, tipo, segmento);
        }

        FundoImobiliario entity = FundoImobiliario.builder()
                .codigo(dto.codigo())
                .nome(nome)
                .tipo(tipo)
                .segmento(segmento)
                .build();
        return toResponseDTO(repository.save(entity));
    }

    @Transactional
    public FundoImobiliarioResponseDTO atualizar(String codigo, FundoImobiliarioRequestDTO dto) {
        log.info("Atualizando fundo imobiliário: {}", codigo);
        FundoImobiliario fundo = repository.findById(codigo)
                .orElseThrow(() -> new EntityNotFoundException("Fundo imobiliário não encontrado: " + codigo));
        if (valorOuNull(dto.nome()) == null || valorOuNull(dto.tipo()) == null || valorOuNull(dto.segmento()) == null) {
            throw new IllegalArgumentException("Nome, tipo e segmento são obrigatórios para atualização");
        }
        fundo.setNome(dto.nome());
        fundo.setTipo(dto.tipo());
        fundo.setSegmento(dto.segmento());
        return toResponseDTO(repository.save(fundo));
    }

    private String valorOuNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    @Transactional
    public void deletar(String codigo) {
        log.info("Deletando fundo imobiliário: {}", codigo);
        if (!repository.existsById(codigo)) {
            throw new EntityNotFoundException("Fundo imobiliário não encontrado: " + codigo);
        }
        repository.deleteById(codigo);
    }

    // ── Enriquecimento ────────────────────────────────────────────────────────

    private FundoImobiliarioResponseDTO enriquecer(FundoImobiliario fundo, Integer mencoes) {
        String codigo = fundo.getCodigo();
        try {
            CompletableFuture<DadosMercadoDTO> mercadoFuture =
                    CompletableFuture.supplyAsync(() -> dadosMercadoService.buscarDadosMercado(codigo));
            CompletableFuture<List<RecomendacaoDTO>> recoFuture =
                    CompletableFuture.supplyAsync(() -> recomendacoesService.buscarRecomendacoes(codigo));

            DadosMercadoDTO mercado = mercadoFuture.join();
            List<RecomendacaoDTO> recos = recoFuture.join();
            String consenso = computarConsenso(recos, mercado);

            return new FundoImobiliarioResponseDTO(
                    fundo.getCodigo(),
                    fundo.getNome(),
                    fundo.getTipo(),
                    fundo.getSegmento(),
                    fundo.getCriadoEm(),
                    fundo.getAtualizadoEm(),
                    mercado != null ? mercado.precoAtual() : null,
                    mercado != null ? mercado.dividendYield() : null,
                    mercado != null ? mercado.pvp() : null,
                    mercado != null ? mercado.variacaoMes() : null,
                    consenso,
                    mencoes
            );
        } catch (Exception e) {
            log.warn("Falha ao enriquecer dados de mercado para {}: {}", codigo, e.getMessage());
            return toResponseDTO(fundo);
        }
    }

    private Map<String, Integer> carregarBuzz() {
        try {
            return fiisEmAltaService.buscar().porSegmento().stream()
                    .flatMap(seg -> seg.fundos().stream())
                    .collect(Collectors.toMap(
                            f -> f.codigo(),
                            f -> f.mencoesPositivas(),
                            (a, b) -> a
                    ));
        } catch (Exception e) {
            log.warn("Falha ao carregar dados de buzz: {}", e.getMessage());
            return Map.of();
        }
    }

    private String computarConsenso(List<RecomendacaoDTO> recos, DadosMercadoDTO mercado) {
        if (recos != null && !recos.isEmpty()) {
            return recos.stream()
                    .collect(Collectors.groupingBy(RecomendacaoDTO::recomendacao, Collectors.counting()))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("NEUTRO");
        }
        if (mercado != null && mercado.pvp() != null) {
            double pvp = mercado.pvp().doubleValue();
            if (pvp < 0.90) return "COMPRAR";
            if (pvp > 1.10) return "MANTER";
            return "NEUTRO";
        }
        return null;
    }

    // ── Conversão básica (sem enriquecimento) ────────────────────────────────

    private FundoImobiliarioResponseDTO toResponseDTO(FundoImobiliario f) {
        return new FundoImobiliarioResponseDTO(
                f.getCodigo(), f.getNome(), f.getTipo(),
                f.getSegmento(), f.getCriadoEm(), f.getAtualizadoEm(),
                null, null, null, null, null, null
        );
    }

}
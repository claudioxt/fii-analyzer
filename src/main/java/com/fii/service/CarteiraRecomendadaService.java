package com.fii.service;

import com.fii.dto.CarteiraItemRequestDTO;
import com.fii.dto.CarteiraItemResponseDTO;
import com.fii.dto.CarteiraRecomendadaRequestDTO;
import com.fii.dto.CarteiraRecomendadaResponseDTO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarteiraRecomendadaService {

    private final CarteiraRecomendadaRepository carteiraRepository;
    private final FundoImobiliarioRepository fundoRepository;
    private final FiiDadosMercadoService dadosMercadoService;
    private final CarteiraRecomendadaBuscaService buscaService;

    @Transactional(readOnly = true)
    public List<CarteiraRecomendadaResponseDTO> listarTodas(String casaDeAnalise, LocalDate mesReferencia) {
        log.info("Listando carteiras recomendadas — casaDeAnalise={}, mesReferencia={}", casaDeAnalise, mesReferencia);

        List<CarteiraRecomendada> carteiras;
        if (casaDeAnalise != null && mesReferencia != null) {
            carteiras = carteiraRepository
                    .findByCasaDeAnaliseIgnoreCaseOrderByMesReferenciaDesc(casaDeAnalise)
                    .stream()
                    .filter(c -> c.getMesReferencia().equals(mesReferencia))
                    .toList();
        } else if (casaDeAnalise != null) {
            carteiras = carteiraRepository.findByCasaDeAnaliseIgnoreCaseOrderByMesReferenciaDesc(casaDeAnalise);
        } else if (mesReferencia != null) {
            carteiras = carteiraRepository.findByMesReferenciaOrderByCasaDeAnaliseAsc(mesReferencia);
        } else {
            LocalDate mesAtual = LocalDate.now().withDayOfMonth(1);
            LocalDate mesMaisRecente = carteiraRepository.findMaxMesReferencia()
                    .orElse(mesAtual);

            // Se o banco está desatualizado, dispara busca em background para o mês atual
            if (mesMaisRecente.isBefore(mesAtual)) {
                log.info("Banco está em {} mas mês atual é {}. Disparando busca web em background.",
                        mesMaisRecente, mesAtual);
                buscaService.buscarParaMesAsync(mesAtual);
            }

            carteiras = carteiraRepository.findByMesReferenciaOrderByCasaDeAnaliseAsc(mesMaisRecente);
            log.info("Nenhum filtro informado — exibindo mês mais recente: {}", mesMaisRecente);
        }

        Map<String, FundoImobiliario> fundosPorCodigo = resolverFundos(carteiras);
        return carteiras.stream().map(c -> toResponseDTO(c, fundosPorCodigo)).toList();
    }

    @Transactional(readOnly = true)
    public CarteiraRecomendadaResponseDTO buscarPorId(Long id) {
        log.info("Buscando carteira recomendada id={}", id);
        CarteiraRecomendada carteira = carteiraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Carteira recomendada não encontrada: " + id));
        Map<String, FundoImobiliario> fundosPorCodigo = resolverFundos(List.of(carteira));
        return toResponseDTO(carteira, fundosPorCodigo);
    }

    @Transactional
    public CarteiraRecomendadaResponseDTO criar(CarteiraRecomendadaRequestDTO dto) {
        log.info("Criando carteira recomendada — casaDeAnalise={}, mes={}", dto.casaDeAnalise(), dto.mesReferencia());
        CarteiraRecomendada carteira = CarteiraRecomendada.builder()
                .casaDeAnalise(dto.casaDeAnalise())
                .mesReferencia(dto.mesReferencia())
                .observacoes(dto.observacoes())
                .build();

        List<CarteiraRecomendadaItem> itens = dto.itens().stream()
                .map(itemDto -> toItemEntity(itemDto, carteira))
                .toList();
        carteira.getItens().addAll(itens);

        CarteiraRecomendada salva = carteiraRepository.save(carteira);
        Map<String, FundoImobiliario> fundosPorCodigo = resolverFundos(List.of(salva));
        return toResponseDTO(salva, fundosPorCodigo);
    }

    @Transactional
    public CarteiraRecomendadaResponseDTO atualizar(Long id, CarteiraRecomendadaRequestDTO dto) {
        log.info("Atualizando carteira recomendada id={}", id);
        CarteiraRecomendada carteira = carteiraRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Carteira recomendada não encontrada: " + id));

        carteira.setCasaDeAnalise(dto.casaDeAnalise());
        carteira.setMesReferencia(dto.mesReferencia());
        carteira.setObservacoes(dto.observacoes());

        carteira.getItens().clear();
        dto.itens().stream()
                .map(itemDto -> toItemEntity(itemDto, carteira))
                .forEach(carteira.getItens()::add);

        CarteiraRecomendada salva = carteiraRepository.save(carteira);
        Map<String, FundoImobiliario> fundosPorCodigo = resolverFundos(List.of(salva));
        return toResponseDTO(salva, fundosPorCodigo);
    }

    @Transactional
    public void deletar(Long id) {
        log.info("Deletando carteira recomendada id={}", id);
        if (!carteiraRepository.existsById(id)) {
            throw new EntityNotFoundException("Carteira recomendada não encontrada: " + id);
        }
        carteiraRepository.deleteById(id);
    }

    private Map<String, FundoImobiliario> resolverFundos(List<CarteiraRecomendada> carteiras) {
        Set<String> codigos = carteiras.stream()
                .flatMap(c -> c.getItens().stream())
                .map(CarteiraRecomendadaItem::getCodigoFundo)
                .collect(Collectors.toSet());
        return fundoRepository.findAllById(codigos).stream()
                .collect(Collectors.toMap(FundoImobiliario::getCodigo, f -> f));
    }

    private CarteiraRecomendadaResponseDTO toResponseDTO(CarteiraRecomendada c,
                                                          Map<String, FundoImobiliario> fundosPorCodigo) {
        // Busca precoAtual de todos os itens em paralelo (resultados cacheados por código)
        Map<String, BigDecimal> precosPorCodigo = buscarPrecosEmParalelo(c.getItens());

        List<CarteiraItemResponseDTO> itensDTO = c.getItens().stream()
                .map(item -> toItemResponseDTO(
                        item,
                        fundosPorCodigo.get(item.getCodigoFundo()),
                        precosPorCodigo.get(item.getCodigoFundo())
                ))
                .toList();

        return new CarteiraRecomendadaResponseDTO(
                c.getId(),
                c.getCasaDeAnalise(),
                c.getMesReferencia(),
                c.getObservacoes(),
                itensDTO,
                itensDTO.size(),
                c.getCriadoEm(),
                c.getAtualizadoEm()
        );
    }

    private Map<String, BigDecimal> buscarPrecosEmParalelo(List<CarteiraRecomendadaItem> itens) {
        List<CompletableFuture<Map.Entry<String, BigDecimal>>> futures = itens.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> {
                    try {
                        var dm = dadosMercadoService.buscarDadosMercado(item.getCodigoFundo());
                        BigDecimal preco = dm != null ? dm.precoAtual() : null;
                        return Map.entry(item.getCodigoFundo(), preco != null ? preco : BigDecimal.ZERO);
                    } catch (Exception e) {
                        log.warn("Falha ao buscar preço para {}: {}", item.getCodigoFundo(), e.getMessage());
                        return Map.entry(item.getCodigoFundo(), BigDecimal.ZERO);
                    }
                }))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    private CarteiraItemResponseDTO toItemResponseDTO(CarteiraRecomendadaItem item,
                                                       FundoImobiliario fundo,
                                                       BigDecimal precoAtual) {
        return new CarteiraItemResponseDTO(
                item.getId(),
                item.getCodigoFundo(),
                fundo != null ? fundo.getNome() : null,
                fundo != null ? fundo.getSegmento() : null,
                item.getPeso(),
                item.getJustificativa(),
                precoAtual
        );
    }

    private CarteiraRecomendadaItem toItemEntity(CarteiraItemRequestDTO dto, CarteiraRecomendada carteira) {
        return CarteiraRecomendadaItem.builder()
                .carteira(carteira)
                .codigoFundo(dto.codigoFundo())
                .peso(dto.peso())
                .justificativa(dto.justificativa())
                .build();
    }
}
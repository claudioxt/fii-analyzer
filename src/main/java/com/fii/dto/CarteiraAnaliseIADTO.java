package com.fii.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CarteiraAnaliseIADTO(
        Long carteiraId,
        String casaDeAnalise,
        LocalDate mesReferencia,
        String sentimentoGeral,
        String resumo,
        String diversificacao,
        List<String> pontosFortes,
        List<String> pontosRisco,
        String perspectiva,
        String recomendacaoIA,
        List<CarteiraItemAnaliseDTO> analisesPorFundo,
        int totalFundosAnalisados,
        int totalNoticiasAnalisadas,
        LocalDateTime geradoEm
) {}

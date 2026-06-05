package com.fii.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CarteiraRecomendadaResponseDTO(
        Long id,
        String casaDeAnalise,
        LocalDate mesReferencia,
        String observacoes,
        List<CarteiraItemResponseDTO> itens,
        int totalFundos,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {}
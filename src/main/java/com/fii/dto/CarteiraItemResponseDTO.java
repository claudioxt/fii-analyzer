package com.fii.dto;

import java.math.BigDecimal;

public record CarteiraItemResponseDTO(
        Long id,
        String codigoFundo,
        String nomeFundo,
        String segmento,
        BigDecimal peso,
        String justificativa,
        BigDecimal precoAtual
) {}
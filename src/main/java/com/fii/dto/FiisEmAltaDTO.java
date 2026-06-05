package com.fii.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FiisEmAltaDTO(
        List<FiiEmAltaPorSegmentoDTO> porSegmento,
        String resumoMercado,
        int totalFundos,
        LocalDateTime geradoEm
) {}

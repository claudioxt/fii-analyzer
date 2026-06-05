package com.fii.dto;

import java.util.List;

public record FiiEmAltaPorSegmentoDTO(
        String segmento,
        List<FiiEmAltaItemDTO> fundos
) {}

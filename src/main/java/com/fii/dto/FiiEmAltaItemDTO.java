package com.fii.dto;

import java.util.List;

public record FiiEmAltaItemDTO(
        String codigo,
        String nome,
        String motivo,
        List<String> fontes,
        int mencoesPositivas
) {}

package com.fii.dto;

import java.math.BigDecimal;

public record CarteiraItemAnaliseDTO(
        String codigo,
        String nome,
        String segmento,
        BigDecimal peso,
        String sentimento,
        String resumo,
        int totalNoticias
) {}

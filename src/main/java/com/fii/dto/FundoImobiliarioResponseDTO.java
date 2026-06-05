package com.fii.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundoImobiliarioResponseDTO(
        String codigo,
        String nome,
        String tipo,
        String segmento,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        BigDecimal precoAtual,
        BigDecimal dividendYield,
        BigDecimal pvp,
        BigDecimal variacaoMes,
        String consenso,
        Integer mencoesPositivas
) {}

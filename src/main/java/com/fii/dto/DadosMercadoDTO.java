package com.fii.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DadosMercadoDTO(
        BigDecimal precoAtual,
        BigDecimal pvp,
        BigDecimal variacaoDia,
        BigDecimal variacaoSemana,
        BigDecimal variacaoMes,
        BigDecimal variacaoAno,
        BigDecimal dividendYield,
        LocalDateTime atualizadoEm
) {}
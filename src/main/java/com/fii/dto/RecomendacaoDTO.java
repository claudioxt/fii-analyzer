package com.fii.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecomendacaoDTO(
        String casaDeAnalise,
        String recomendacao,
        BigDecimal precoAlvo,
        String fundamentacao,
        LocalDate data
) {}
package com.fii.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FiiAnaliseIADTO(
        String codigo,
        String nome,
        String resumo,
        String sentimento,
        List<String> pontosPositivos,
        List<String> pontosNegativos,
        String perspectiva,
        String recomendacaoIA,
        int totalNoticiasAnalisadas,
        LocalDateTime geradoEm
) {}
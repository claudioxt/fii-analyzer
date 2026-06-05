package com.fii.dto;

import java.time.LocalDateTime;

public record CarteiraChatResponseDTO(
        String conversaId,
        String pergunta,
        String resposta,
        LocalDateTime respondidoEm
) {}
package com.fii.dto;

import java.time.LocalDateTime;

public record NoticiaDTO(
        String titulo,
        String resumo,
        String fonte,
        String link,
        LocalDateTime dataPublicacao
) {}
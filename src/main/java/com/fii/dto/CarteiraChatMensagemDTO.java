package com.fii.dto;

import java.time.LocalDateTime;

public record CarteiraChatMensagemDTO(
        String role,
        String conteudo,
        LocalDateTime criadoEm
) {}

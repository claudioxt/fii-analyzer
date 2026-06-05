package com.fii.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CarteiraChatRequestDTO(
        @NotBlank(message = "A pergunta não pode ser vazia")
        String pergunta,
        List<MensagemHistoricoDTO> historico
) {}
package com.fii.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FundoImobiliarioRequestDTO(

        @NotBlank(message = "Código do fundo é obrigatório")
        @Size(max = 10, message = "Código deve ter no máximo 10 caracteres")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Código deve conter apenas letras maiúsculas e números")
        String codigo,

        @Size(max = 255, message = "Nome deve ter no máximo 255 caracteres")
        String nome,

        @Size(max = 100, message = "Tipo deve ter no máximo 100 caracteres")
        String tipo,

        @Size(max = 100, message = "Segmento deve ter no máximo 100 caracteres")
        String segmento
) {}

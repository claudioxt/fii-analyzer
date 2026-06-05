package com.fii.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CarteiraItemRequestDTO(

        @NotBlank(message = "Código do fundo é obrigatório")
        @Size(max = 10, message = "Código deve ter no máximo 10 caracteres")
        @Pattern(regexp = "^[A-Z0-9]+$", message = "Código deve conter apenas letras maiúsculas e números")
        String codigoFundo,

        @NotNull(message = "Peso do fundo na carteira é obrigatório")
        @DecimalMin(value = "0.01", message = "Peso deve ser maior que zero")
        @DecimalMax(value = "100.00", message = "Peso não pode ultrapassar 100%")
        BigDecimal peso,

        @Size(max = 500, message = "Justificativa deve ter no máximo 500 caracteres")
        String justificativa
) {}
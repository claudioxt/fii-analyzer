package com.fii.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CarteiraRecomendadaRequestDTO(

        @NotBlank(message = "Casa de análise é obrigatória")
        @Size(max = 100, message = "Casa de análise deve ter no máximo 100 caracteres")
        String casaDeAnalise,

        @NotNull(message = "Mês de referência é obrigatório")
        LocalDate mesReferencia,

        @Size(max = 2000, message = "Observações devem ter no máximo 2000 caracteres")
        String observacoes,

        @NotEmpty(message = "A carteira deve ter ao menos um fundo")
        @Valid
        List<CarteiraItemRequestDTO> itens
) {}
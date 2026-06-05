package com.fii.dto;

public record AnaliseStatusDTO(
        String jobId,
        String status,
        CarteiraImagemAnaliseDTO resultado,
        String erro
) {}

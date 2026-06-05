package com.fii.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FiiAnaliseCompletaDTO(
        String codigo,
        String nome,
        DadosMercadoDTO dadosMercado,
        List<NoticiaDTO> noticias,
        List<RecomendacaoDTO> recomendacoes,
        String consenso,
        LocalDateTime geradoEm
) {}
package com.fii.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CarteiraImagemAnaliseDTO(
        String conversaId,
        String sentimentoGeral,
        String resumo,
        List<String> pontosPositivos,
        List<String> pontosNegativos,
        List<String> pontosAtencao,
        List<SugestaoAtivoDTO> ativosParaComprar,
        List<SugestaoAtivoDTO> ativosParaVender,
        List<String> proximosAportes,
        String recomendacaoGeral,
        LocalDateTime analisadoEm,
        List<CarteiraChatMensagemDTO> mensagens
) {}

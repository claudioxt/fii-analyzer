package com.fii.service;

import com.fii.dto.SugestaoAtivoDTO;

import java.util.List;

/**
 * Monta o texto da análise de carteira usado como contexto de chat — compartilhado entre
 * a montagem inicial (a partir da resposta da IA) e a reconstrução a partir de dados persistidos.
 */
final class ConversaContextoTextoBuilder {

    private ConversaContextoTextoBuilder() {}

    static String construir(String sentimentoGeral,
                            String resumo,
                            List<String> pontosPositivos,
                            List<String> pontosNegativos,
                            List<String> pontosAtencao,
                            List<SugestaoAtivoDTO> ativosParaComprar,
                            List<SugestaoAtivoDTO> ativosParaVender,
                            List<String> proximosAportes,
                            String recomendacaoGeral) {
        var sb = new StringBuilder();
        sb.append("Sentimento geral: ").append(sentimentoGeral).append("\n");
        sb.append("Resumo: ").append(resumo).append("\n\n");
        appendLista(sb, "Pontos positivos", pontosPositivos);
        appendLista(sb, "Pontos negativos", pontosNegativos);
        appendLista(sb, "Pontos de atenção", pontosAtencao);
        appendAtivos(sb, "Ativos sugeridos para comprar", ativosParaComprar);
        appendAtivos(sb, "Ativos sugeridos para vender", ativosParaVender);
        appendLista(sb, "Próximos aportes", proximosAportes);
        sb.append("Recomendação geral: ").append(recomendacaoGeral);
        return sb.toString();
    }

    private static void appendLista(StringBuilder sb, String titulo, List<String> itens) {
        if (itens == null || itens.isEmpty()) return;
        sb.append(titulo).append(":\n");
        itens.forEach(i -> sb.append("  - ").append(i).append("\n"));
        sb.append("\n");
    }

    private static void appendAtivos(StringBuilder sb, String titulo, List<SugestaoAtivoDTO> ativos) {
        if (ativos == null || ativos.isEmpty()) return;
        sb.append(titulo).append(":\n");
        ativos.forEach(s ->
                sb.append("  - ").append(s.codigo())
                  .append(" [").append(s.tipoAtivo() != null ? s.tipoAtivo() : "OUTRO").append("]")
                  .append(": ").append(s.justificativa()).append("\n"));
        sb.append("\n");
    }
}

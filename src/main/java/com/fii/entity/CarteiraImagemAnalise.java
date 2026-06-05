package com.fii.entity;

import com.fii.dto.SugestaoAtivoDTO;
import com.fii.entity.converter.ListStringConverter;
import com.fii.entity.converter.ListSugestaoFiiConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "carteira_imagem_analises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarteiraImagemAnalise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversa_id", nullable = false, unique = true, length = 36)
    private String conversaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "sentimento_geral", nullable = false, length = 20)
    private String sentimentoGeral;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumo;

    @Convert(converter = ListStringConverter.class)
    @Column(name = "pontos_positivos", columnDefinition = "TEXT")
    private List<String> pontosPositivos;

    @Convert(converter = ListStringConverter.class)
    @Column(name = "pontos_negativos", columnDefinition = "TEXT")
    private List<String> pontosNegativos;

    @Convert(converter = ListStringConverter.class)
    @Column(name = "pontos_atencao", columnDefinition = "TEXT")
    private List<String> pontosAtencao;

    @Convert(converter = ListSugestaoFiiConverter.class)
    @Column(name = "fiis_para_comprar", columnDefinition = "TEXT")
    private List<SugestaoAtivoDTO> ativosParaComprar;

    @Convert(converter = ListSugestaoFiiConverter.class)
    @Column(name = "fiis_para_vender", columnDefinition = "TEXT")
    private List<SugestaoAtivoDTO> ativosParaVender;

    @Convert(converter = ListStringConverter.class)
    @Column(name = "proximos_aportes", columnDefinition = "TEXT")
    private List<String> proximosAportes;

    @Column(name = "recomendacao_geral", columnDefinition = "TEXT")
    private String recomendacaoGeral;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    @Column(name = "analisado_em", nullable = false, updatable = false)
    private LocalDateTime analisadoEm;

    @PrePersist
    void prePersist() {
        if (analisadoEm == null) analisadoEm = LocalDateTime.now();
    }
}

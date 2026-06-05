package com.fii.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "carteiras_recomendadas_itens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarteiraRecomendadaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carteira_id", nullable = false)
    private CarteiraRecomendada carteira;

    @Column(name = "codigo_fundo", nullable = false, length = 10)
    private String codigoFundo;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal peso;

    @Column(length = 500)
    private String justificativa;
}
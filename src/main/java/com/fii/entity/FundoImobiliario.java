package com.fii.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fundos_imobiliarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundoImobiliario {

    @Id
    @Column(length = 10)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false, length = 100)
    private String segmento;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    @PrePersist
    void prePersist() {
        this.criadoEm = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }
}

package com.fii.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "carteira_imagem_dados")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarteiraImagemDados {

    @Id
    @Column(name = "conversa_id", length = 36)
    private String conversaId;

    @Lob
    @Column(name = "imagem_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] imagemBytes;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
    }
}

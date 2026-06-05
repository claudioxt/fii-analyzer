package com.fii.dto;

import com.fii.entity.UsuarioRole;

import java.time.LocalDateTime;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        UsuarioRole role,
        boolean ativo,
        LocalDateTime criadoEm
) {}

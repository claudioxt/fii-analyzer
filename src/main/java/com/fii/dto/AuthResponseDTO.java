package com.fii.dto;

public record AuthResponseDTO(
        String token,
        String tipo,
        long expiresIn,
        UsuarioResponseDTO usuario
) {}

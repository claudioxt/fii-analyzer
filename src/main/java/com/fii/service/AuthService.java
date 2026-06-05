package com.fii.service;

import com.fii.dto.AuthResponseDTO;
import com.fii.dto.LoginRequestDTO;
import com.fii.dto.RegisterRequestDTO;
import com.fii.dto.UsuarioResponseDTO;
import com.fii.entity.Usuario;
import com.fii.entity.UsuarioRole;
import com.fii.repository.UsuarioRepository;
import com.fii.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Transactional
    public UsuarioResponseDTO registrar(RegisterRequestDTO dto) {
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + dto.email());
        }
        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .email(dto.email())
                .senha(passwordEncoder.encode(dto.senha()))
                .role(UsuarioRole.USER)
                .ativo(true)
                .build();
        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Novo usuário registrado: {}", salvo.getEmail());
        return toResponseDTO(salvo);
    }

    public AuthResponseDTO login(LoginRequestDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.senha())
        );
        Usuario usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        String token = jwtService.gerarToken(usuario);
        log.info("Login realizado: {}", usuario.getEmail());
        return new AuthResponseDTO(token, "Bearer", jwtExpiration, toResponseDTO(usuario));
    }

    static UsuarioResponseDTO toResponseDTO(Usuario u) {
        return new UsuarioResponseDTO(u.getId(), u.getNome(), u.getEmail(),
                u.getRole(), u.isAtivo(), u.getCriadoEm());
    }
}

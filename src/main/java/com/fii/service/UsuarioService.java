package com.fii.service;

import com.fii.dto.UsuarioCadastroDTO;
import com.fii.dto.UsuarioResponseDTO;
import com.fii.entity.Usuario;
import com.fii.entity.UsuarioRole;
import com.fii.repository.UsuarioRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioResponseDTO criar(UsuarioCadastroDTO dto) {
        if (dto.role() == UsuarioRole.ADMIN) {
            validarPermissaoAdmin();
        }
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado: " + dto.email());
        }
        Usuario usuario = Usuario.builder()
                .nome(dto.nome())
                .email(dto.email())
                .senha(passwordEncoder.encode(dto.senha()))
                .role(dto.role())
                .ativo(true)
                .build();
        Usuario salvo = usuarioRepository.save(usuario);
        log.info("Usuário criado por admin: {} com role {}", salvo.getEmail(), salvo.getRole());
        return AuthService.toResponseDTO(salvo);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream()
                .map(AuthService::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(AuthService::toResponseDTO)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado: " + id));
    }

    private void validarPermissaoAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new IllegalArgumentException("Apenas administradores podem criar outros administradores");
        }
    }
}

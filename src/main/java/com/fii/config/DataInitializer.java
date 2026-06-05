package com.fii.config;

import com.fii.entity.Usuario;
import com.fii.entity.UsuarioRole;
import com.fii.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() == 0) {
            Usuario admin = Usuario.builder()
                    .nome("Administrador")
                    .email("admin@fii-analyzer.com")
                    .senha(passwordEncoder.encode("admin123"))
                    .role(UsuarioRole.ADMIN)
                    .ativo(true)
                    .build();
            usuarioRepository.save(admin);
            log.info("Usuário admin criado: admin@fii-analyzer.com / admin123");
        }
    }
}

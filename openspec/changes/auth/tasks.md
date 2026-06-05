## 1. Melhorias de segurança

- [x] 1.1 Implementar JwtService com emissão e validação de token
- [x] 1.2 Implementar JwtAuthenticationFilter integrado ao SecurityFilterChain
- [x] 1.3 Configurar SecurityConfig com regras de rotas públicas vs. protegidas
- [x] 1.4 Implementar AuthController (register + login)
- [x] 1.5 Implementar AuthService com BCrypt e emissão de JWT
- [x] 1.6 Criar DataInitializer para admin padrão em produção

## 2. Gestão de usuários

- [x] 2.1 Implementar UsuarioController com CRUD completo
- [x] 2.2 Implementar UsuarioService com validação de e-mail único
- [x] 2.3 Anotar endpoints de escrita com @PreAuthorize("hasRole('ADMIN')")
- [x] 2.4 Garantir que senhas nunca retornam nas respostas (UsuarioResponseDTO)

## 3. Melhorias identificadas (pendentes)

- [ ] 3.1 Documentar política de expiração recomendada do JWT no README
- [ ] 3.2 Adicionar aviso no DataInitializer para troca da senha padrão em produção
- [ ] 3.3 Criar teste de integração para o fluxo register → login → acesso protegido
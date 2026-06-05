## Context

Autenticação stateless com JWT. O token é emitido no login/registro e enviado pelo cliente em todas as requisições protegidas via header `Authorization: Bearer <token>`. Não há sessão servidor-lado (sem HttpSession). Spring Security 7 com `SecurityConfig` define quais rotas são públicas e quais exigem roles específicas.

## Goals / Non-Goals

**Goals:**
- Autenticação stateless via JWT (sem sessão)
- Autorização baseada em roles (USER / ADMIN)
- Admin padrão garantido em produção

**Non-Goals:**
- OAuth2 / SSO / integração com provedores externos
- Refresh token (JWT tem expiração configurável, novo login gera novo token)
- Rate limiting de tentativas de login

## Decisions

**JWT com JJWT 0.12.6**
Biblioteca madura e de baixo boilerplate. O secret é configurado via `jwt.secret` (application.yml). A expiração via `jwt.expiration` em milissegundos. Alternativa considerada (Spring OAuth2 Resource Server) descartada por complexidade desnecessária para o escopo.

**Senha com BCrypt**
`PasswordEncoder` do Spring Security com BCrypt. Senhas nunca são armazenadas em texto plano. Sem alternativa considerada — é o padrão da indústria.

**`@PreAuthorize("hasRole('ADMIN')")`**
Autorização declarativa por anotação nos controllers. Evita lógica de permissão espalhada nos services. A `SecurityConfig` define a regra geral (GETs públicos, escrita protegida) e as anotações refinam por endpoint.

**DataInitializer em produção**
O admin padrão (`admin@fii.com / admin123`) é criado via `CommandLineRunner` somente se não existir nenhum ADMIN. Garante acesso inicial ao sistema sem seed manual. A senha deve ser trocada após o primeiro login (não há enforcement — responsabilidade do operador).

## Risks / Trade-offs

- **[Risco] Secret JWT em texto no yml** → Usar variável de ambiente `JWT_SECRET` em produção, nunca commitar o valor real
- **[Trade-off] Sem refresh token** → Tokens expirados forçam novo login; aceitável para API backend sem frontend persistente
- **[Risco] Admin padrão com senha fraca** → Documentar que `admin123` deve ser trocado imediatamente em produção

## Open Questions

- Definir política de expiração padrão do JWT (atualmente configurável, sem valor recomendado documentado)
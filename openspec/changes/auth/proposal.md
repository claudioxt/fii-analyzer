## Why

O sistema precisa garantir que apenas usuários autenticados acessem recursos protegidos e que operações administrativas sejam restritas ao papel ADMIN. Este change documenta a camada de autenticação e autorização já implementada com JWT e Spring Security 7.

## What Changes

- Endpoint público `POST /api/v1/auth/register` para criação de usuários com role USER
- Endpoint público `POST /api/v1/auth/login` para autenticação e emissão de JWT Bearer token
- Endpoints protegidos `GET|POST|PUT|DELETE /api/v1/usuarios/**` restritos a ADMIN
- Filtro JWT (`JwtAuthenticationFilter`) em todas as requisições protegidas
- Admin padrão criado automaticamente no startup em produção

## Capabilities

### New Capabilities

- `autenticacao`: Registro, login e emissão de JWT para usuários da plataforma
- `gestao-usuarios`: CRUD de usuários com controle de roles, restrito a ADMIN

### Modified Capabilities

## Impact

- `com.fii.security`: JwtService, JwtAuthenticationFilter, SecurityConfig
- `com.fii.entity.Usuario`: implementa UserDetails com roles USER | ADMIN
- `com.fii.service`: AuthService, UsuarioService
- `com.fii.controller`: AuthController, UsuarioController
- `com.fii.config.DataInitializer`: cria admin padrão em produção
- Dependências: Spring Security 7, JJWT 0.12.6
- Banco: tabela `usuarios`
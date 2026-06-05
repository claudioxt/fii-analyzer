## ADDED Requirements

### Requirement: Registro de novo usuário
O sistema SHALL permitir que qualquer visitante crie uma conta fornecendo nome, e-mail e senha. O usuário criado SHALL receber automaticamente a role USER.

#### Scenario: Registro com dados válidos
- **WHEN** um visitante envia `POST /api/v1/auth/register` com nome, e-mail único e senha
- **THEN** o sistema cria o usuário com role USER e retorna HTTP 200 com o JWT

#### Scenario: Registro com e-mail já cadastrado
- **WHEN** um visitante tenta registrar com um e-mail que já existe no banco
- **THEN** o sistema retorna HTTP 409 com mensagem de conflito

#### Scenario: Registro com dados inválidos
- **WHEN** um visitante envia campos obrigatórios ausentes ou inválidos
- **THEN** o sistema retorna HTTP 400 com os erros de validação

---

### Requirement: Login e emissão de JWT
O sistema SHALL autenticar credenciais de e-mail e senha e retornar um JWT Bearer token válido para uso nas requisições subsequentes.

#### Scenario: Login com credenciais válidas
- **WHEN** um usuário envia `POST /api/v1/auth/login` com e-mail e senha corretos
- **THEN** o sistema retorna HTTP 200 com `{ token: "<jwt>", tipo: "Bearer" }` e expiração configurada via `jwt.expiration`

#### Scenario: Login com senha incorreta
- **WHEN** um usuário envia credenciais com senha errada
- **THEN** o sistema retorna HTTP 401

#### Scenario: Login com e-mail inexistente
- **WHEN** um usuário envia um e-mail não cadastrado
- **THEN** o sistema retorna HTTP 401

---

### Requirement: Proteção de rotas via JWT
O sistema SHALL validar o JWT Bearer token em todas as rotas protegidas e rejeitar requisições sem token válido.

#### Scenario: Acesso a rota protegida com token válido
- **WHEN** o cliente envia requisição com `Authorization: Bearer <token_válido>`
- **THEN** o sistema processa a requisição normalmente

#### Scenario: Acesso a rota protegida sem token
- **WHEN** o cliente envia requisição para rota protegida sem header Authorization
- **THEN** o sistema retorna HTTP 401

#### Scenario: Acesso a rota protegida com token expirado
- **WHEN** o cliente envia requisição com JWT cujo `exp` já passou
- **THEN** o sistema retorna HTTP 401

#### Scenario: Rotas públicas acessíveis sem autenticação
- **WHEN** o cliente acessa `POST /api/v1/auth/login` ou `POST /api/v1/auth/register` ou qualquer `GET` de FII sem token
- **THEN** o sistema responde normalmente sem exigir autenticação

---

### Requirement: Admin padrão em produção
O sistema SHALL criar automaticamente um usuário administrador padrão no startup quando nenhum usuário ADMIN existir no banco, para garantir acesso inicial ao sistema.

#### Scenario: Startup sem nenhum ADMIN cadastrado
- **WHEN** a aplicação inicia e não existe nenhum usuário com role ADMIN
- **THEN** o sistema cria `admin@fii.com` com senha `admin123` e role ADMIN

#### Scenario: Startup com ADMIN já existente
- **WHEN** a aplicação inicia e já existe pelo menos um usuário ADMIN
- **THEN** o sistema NÃO cria novo usuário padrão
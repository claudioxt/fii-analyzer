## ADDED Requirements

### Requirement: Listagem de usuários
O sistema SHALL permitir que apenas usuários com role ADMIN listem todos os usuários cadastrados.

#### Scenario: ADMIN lista usuários
- **WHEN** um usuário ADMIN envia `GET /api/v1/usuarios`
- **THEN** o sistema retorna HTTP 200 com lista de todos os usuários (sem senha)

#### Scenario: Usuário comum tenta listar
- **WHEN** um usuário com role USER envia `GET /api/v1/usuarios`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Criação de usuário por ADMIN
O sistema SHALL permitir que ADMIN crie usuários com qualquer role, incluindo ADMIN.

#### Scenario: ADMIN cria usuário com role USER
- **WHEN** um ADMIN envia `POST /api/v1/usuarios` com role USER
- **THEN** o sistema cria o usuário e retorna HTTP 201

#### Scenario: ADMIN cria outro ADMIN
- **WHEN** um ADMIN envia `POST /api/v1/usuarios` com role ADMIN
- **THEN** o sistema cria o usuário ADMIN e retorna HTTP 201

#### Scenario: Tentativa de criar usuário com e-mail duplicado
- **WHEN** um ADMIN tenta criar usuário com e-mail já existente
- **THEN** o sistema retorna HTTP 409

---

### Requirement: Atualização de usuário
O sistema SHALL permitir que apenas ADMIN atualize dados de qualquer usuário, incluindo sua role.

#### Scenario: ADMIN atualiza dados de usuário
- **WHEN** um ADMIN envia `PUT /api/v1/usuarios/{id}` com dados válidos
- **THEN** o sistema atualiza e retorna HTTP 200 com os dados atualizados

#### Scenario: Tentativa de atualização por não-ADMIN
- **WHEN** um usuário USER envia `PUT /api/v1/usuarios/{id}`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Exclusão de usuário
O sistema SHALL permitir que apenas ADMIN exclua usuários do sistema.

#### Scenario: ADMIN exclui usuário
- **WHEN** um ADMIN envia `DELETE /api/v1/usuarios/{id}`
- **THEN** o sistema remove o usuário e retorna HTTP 204

#### Scenario: Tentativa de excluir usuário inexistente
- **WHEN** um ADMIN envia `DELETE /api/v1/usuarios/{id}` com ID que não existe
- **THEN** o sistema retorna HTTP 404

#### Scenario: Tentativa de exclusão por não-ADMIN
- **WHEN** um usuário USER envia `DELETE /api/v1/usuarios/{id}`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Roles disponíveis
O sistema SHALL suportar exatamente dois níveis de acesso: USER (acesso padrão, leitura) e ADMIN (acesso total, incluindo escrita e gerenciamento).

#### Scenario: Atribuição de role válida
- **WHEN** um ADMIN cria ou atualiza usuário com role USER ou ADMIN
- **THEN** o sistema aceita e persiste a role

#### Scenario: Tentativa de role inválida
- **WHEN** qualquer requisição contém uma role não reconhecida pelo sistema
- **THEN** o sistema retorna HTTP 400
## ADDED Requirements

### Requirement: Listagem de FIIs cadastrados
O sistema SHALL retornar todos os fundos imobiliários cadastrados, ordenados alfabeticamente pelo código.

#### Scenario: Listagem com registros existentes
- **WHEN** qualquer cliente envia `GET /api/v1/fundos-imobiliarios`
- **THEN** o sistema retorna HTTP 200 com lista de FundoImobiliarioResponseDTO (codigo, nome, tipo, segmento)

#### Scenario: Listagem com catálogo vazio
- **WHEN** nenhum FII está cadastrado e o cliente envia `GET /api/v1/fundos-imobiliarios`
- **THEN** o sistema retorna HTTP 200 com lista vazia `[]`

---

### Requirement: Busca de FII por código
O sistema SHALL permitir buscar um FII específico pelo seu código de bolsa (ex: HGLG11).

#### Scenario: Código existente
- **WHEN** qualquer cliente envia `GET /api/v1/fundos-imobiliarios/{codigo}` com código cadastrado
- **THEN** o sistema retorna HTTP 200 com os dados do FII

#### Scenario: Código inexistente
- **WHEN** o cliente envia `GET /api/v1/fundos-imobiliarios/{codigo}` com código não cadastrado
- **THEN** o sistema retorna HTTP 404

---

### Requirement: Cadastro de FII com enriquecimento automático
O sistema SHALL permitir que ADMIN cadastre um novo FII informando apenas o código. O sistema SHALL buscar automaticamente nome, tipo e segmento em fontes externas (Yahoo Finance e Status Invest).

#### Scenario: Cadastro de FII válido
- **WHEN** um ADMIN envia `POST /api/v1/fundos-imobiliarios` com `{ "codigo": "HGLG11" }`
- **THEN** o sistema busca os dados nas fontes externas, persiste o FII completo e retorna HTTP 201

#### Scenario: Código já cadastrado
- **WHEN** um ADMIN tenta cadastrar código que já existe
- **THEN** o sistema retorna HTTP 409

#### Scenario: Código não encontrado nas fontes externas
- **WHEN** o código informado não existe no Yahoo Finance nem no Status Invest
- **THEN** o sistema retorna HTTP 422 indicando que não foi possível obter os dados do FII

#### Scenario: Tentativa de cadastro por não-ADMIN
- **WHEN** um usuário USER envia `POST /api/v1/fundos-imobiliarios`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Atualização de FII
O sistema SHALL permitir que ADMIN atualize os dados de um FII existente.

#### Scenario: Atualização com dados válidos
- **WHEN** um ADMIN envia `PUT /api/v1/fundos-imobiliarios/{codigo}` com dados válidos
- **THEN** o sistema atualiza e retorna HTTP 200 com os dados atualizados

#### Scenario: Atualização de FII inexistente
- **WHEN** um ADMIN envia `PUT /api/v1/fundos-imobiliarios/{codigo}` com código não cadastrado
- **THEN** o sistema retorna HTTP 404

#### Scenario: Tentativa de atualização por não-ADMIN
- **WHEN** um usuário USER envia `PUT /api/v1/fundos-imobiliarios/{codigo}`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Remoção de FII
O sistema SHALL permitir que ADMIN remova um FII do catálogo.

#### Scenario: Remoção de FII existente
- **WHEN** um ADMIN envia `DELETE /api/v1/fundos-imobiliarios/{codigo}`
- **THEN** o sistema remove o registro e retorna HTTP 204

#### Scenario: Remoção de FII inexistente
- **WHEN** um ADMIN envia `DELETE /api/v1/fundos-imobiliarios/{codigo}` com código não cadastrado
- **THEN** o sistema retorna HTTP 404

#### Scenario: Tentativa de remoção por não-ADMIN
- **WHEN** um usuário USER envia `DELETE /api/v1/fundos-imobiliarios/{codigo}`
- **THEN** o sistema retorna HTTP 403

---

### Requirement: Código de FII como identificador único
O sistema SHALL usar o código de bolsa (ex: HGLG11) como chave primária lógica, sem distinguir maiúsculas de minúsculas. O código SHALL ser normalizado para maiúsculas antes de persistir.

#### Scenario: Código em minúsculas
- **WHEN** um ADMIN cadastra o código `hglg11`
- **THEN** o sistema persiste como `HGLG11`

#### Scenario: Busca case-insensitive
- **WHEN** o cliente busca `GET /api/v1/fundos-imobiliarios/hglg11`
- **THEN** o sistema retorna o FII `HGLG11` normalmente

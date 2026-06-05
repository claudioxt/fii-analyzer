## 1. Entidade e repositório

- [x] 1.1 Criar entidade JPA FundoImobiliario com campos: codigo (PK), nome, tipo, segmento
- [x] 1.2 Criar FundoImobiliarioRepository com Spring Data JPA
- [x] 1.3 Criar migration Flyway para tabela fundos_imobiliarios

## 2. Serviços de enriquecimento

- [x] 2.1 Implementar FiiDadosMercadoService com scraping Yahoo Finance (preço, P/VP, variações)
- [x] 2.2 Implementar enriquecimento de nome/tipo/segmento via Status Invest no FundoImobiliarioService
- [x] 2.3 Implementar fallback entre Yahoo Finance e Status Invest para enriquecimento

## 3. CRUD e controller

- [x] 3.1 Implementar FundoImobiliarioService com criar, atualizar, deletar, buscarPorCodigo, listarTodos
- [x] 3.2 Implementar FundoImobiliarioController com endpoints GET (público), POST/PUT/DELETE (ADMIN)
- [x] 3.3 Criar FundoImobiliarioRequestDTO (aceita apenas codigo) e FundoImobiliarioResponseDTO

## 4. Melhorias identificadas (pendentes)

- [ ] 4.1 Normalizar código para maiúsculas antes de persistir e buscar
- [ ] 4.2 Adicionar validação de formato de código de FII (regex: [A-Z]{4}[0-9]{2})
- [ ] 4.3 Paralelizar chamadas de enriquecimento com CompletableFuture para reduzir latência

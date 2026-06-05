## 1. Entidades e repositórios

- [x] 1.1 Criar entidade CarteiraRecomendada (casaDeAnalise, mesReferencia, itens)
- [x] 1.2 Criar entidade CarteiraRecomendadaItem (codigo FII, percentual) com cascata da carteira
- [x] 1.3 Criar CarteiraRecomendadaRepository com Spring Data JPA
- [x] 1.4 Criar migrations Flyway para tabelas carteiras_recomendadas e carteira_recomendada_itens

## 2. CRUD de carteiras

- [x] 2.1 Implementar CarteiraRecomendadaService com criar, atualizar (replace-all), deletar, buscarPorId, listarTodas
- [x] 2.2 Implementar validação de soma de percentuais = 100% no service
- [x] 2.3 Implementar CarteiraRecomendadaBuscaService com filtros por casaDeAnalise e mesReferencia
- [x] 2.4 Criar DTOs: CarteiraRecomendadaRequestDTO, CarteiraRecomendadaResponseDTO, CarteiraItemRequestDTO, CarteiraItemResponseDTO

## 3. Análise e geração por IA

- [x] 3.1 Implementar CarteiraAnaliseIAService com prompt crítico injetando dados de mercado dos FIIs
- [x] 3.2 Implementar CarteiraGerarIAService com geração de carteira via Claude validando FIIs contra catálogo
- [x] 3.3 Criar CarteiraAnaliseIADTO e CarteiraItemAnaliseDTO

## 4. Controller

- [x] 4.1 Implementar CarteiraRecomendadaController com todos os endpoints

## 5. Melhorias identificadas (pendentes)

- [ ] 5.1 Definir controle de acesso para criação de carteiras (atualmente qualquer autenticado pode criar)
- [ ] 5.2 Tratar falha do Claude na geração de carteira (retry ou retorno 422 claro)
- [ ] 5.3 Validar que todos os códigos de FII nos itens existem no catálogo antes de persistir

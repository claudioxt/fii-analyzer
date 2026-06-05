## Context

Carteiras recomendadas são conjuntos de FIIs com percentuais de alocação publicados por casas de análise. O sistema armazena essas carteiras para análise histórica e comparativa. O Claude pode analisar criticamente uma carteira ou gerar uma nova com base no catálogo.

## Goals / Non-Goals

**Goals:**
- CRUD de carteiras recomendadas com filtros por casa de análise e mês
- Análise crítica de carteira via Claude
- Geração automática de carteira via Claude

**Non-Goals:**
- Tracking de performance da carteira ao longo do tempo
- Comparação lado a lado de múltiplas carteiras
- Notificações de atualização de carteiras

## Decisions

**Relação 1:N entre CarteiraRecomendada e CarteiraRecomendadaItem**
A carteira tem metadados (casaDeAnalise, mesReferencia) e uma lista de itens (codigo do FII + percentual). Cascata de delete: remover carteira remove todos os itens. Sem tabela de junção separada — item não existe sem a carteira.

**Soma de 100% validada na camada de serviço**
A validação de que os percentuais somam 100% ocorre em `CarteiraRecomendadaService.criar/atualizar`. Não há constraint no banco para isso — é regra de negócio, não integridade referencial.

**Claude via CarteiraAnaliseIAService e CarteiraGerarIAService separados**
Dois casos de uso distintos com prompts diferentes: um analisa criticamente uma carteira existente, o outro gera do zero. Separação em services evita prompt condicional complexo em um único service.

**Carteiras geradas por IA com casaDeAnalise="IA"**
Convenção simples para identificar origem. O `mesReferencia` recebe o primeiro dia do mês corrente. Sem campo booleano especial — casa de análise "IA" é o discriminador.

## Risks / Trade-offs

- **[Risco] Claude pode gerar FII inválido** → `CarteiraGerarIAService` deve validar os códigos gerados contra o catálogo e retentar ou retornar 422
- **[Trade-off] PUT substitui tudo** → Sem patch parcial; cliente deve enviar carteira completa na atualização
- **[Risco] Dados de mercado podem estar desatualizados no contexto da análise** → Aceita-se para a versão atual; cache Caffeine pode retornar dados de alguns minutos atrás

## Open Questions

- Deve existir controle de acesso diferenciado para criação de carteiras (atualmente qualquer autenticado pode criar)?

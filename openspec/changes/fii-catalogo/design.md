## Context

O catálogo de FIIs é a entidade central do sistema. Todos os outros módulos (análise, carteira) referenciam FIIs pelo código. O cadastro é simplificado: o admin informa apenas o código e o sistema enriquece automaticamente os dados via scraping.

## Goals / Non-Goals

**Goals:**
- CRUD completo de FIIs com enriquecimento automático no cadastro
- Busca por código como identificador único (case-insensitive)
- GETs públicos, escrita restrita a ADMIN

**Non-Goals:**
- Sincronização automática periódica de dados (cadastro é um evento único)
- Importação em lote de FIIs
- Histórico de alterações de dados do FII

## Decisions

**Código como chave natural**
O campo `codigo` é a chave de negócio (ex: HGLG11). Não há ID numérico exposto nas URLs — o código é o identificador nos endpoints. Isso torna as URLs semânticas e previsíveis.

**Enriquecimento automático no POST**
O `POST /fundos-imobiliarios` aceita apenas `{ codigo }`. O `FundoImobiliarioService` chama Yahoo Finance e Status Invest via Jsoup para obter nome, tipo e segmento. Evita que o admin precise conhecer os metadados do FII.

**Jsoup para scraping**
Status Invest não possui API pública. Jsoup 1.18.3 faz scraping do HTML. Fragilidade conhecida: mudanças no layout do site podem quebrar o scraper — aceita-se esse risco para a versão atual.

**Fallback entre fontes**
Se Yahoo Finance falhar, tenta Status Invest. Se ambas falharem, retorna 422. Não persiste FII com dados incompletos.

## Risks / Trade-offs

- **[Risco] Scraping frágil** → Mudanças no layout do Status Invest quebram o enriquecimento; monitorar erros 422 frequentes
- **[Trade-off] Sem sincronização automática** → Nome/segmento do FII podem ficar desatualizados; admin pode usar PUT para corrigir
- **[Risco] Rate limiting** → Requisições frequentes ao Yahoo Finance podem ser bloqueadas; Caffeine cache mitiga para leituras repetidas

## Open Questions

- Deve-se validar se o código segue o padrão brasileiro de FII (4 letras + 2 dígitos, ex: HGLG11)?

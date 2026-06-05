CREATE TABLE IF NOT EXISTS fundos_imobiliarios (
    codigo        VARCHAR(10)  NOT NULL PRIMARY KEY,
    nome          VARCHAR(255) NOT NULL,
    tipo          VARCHAR(100) NOT NULL,
    segmento      VARCHAR(100) NOT NULL,
    criado_em     TIMESTAMP    NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fundos_imobiliarios_segmento ON fundos_imobiliarios (segmento);

CREATE TABLE IF NOT EXISTS carteiras_recomendadas (
    id              BIGSERIAL    PRIMARY KEY,
    casa_de_analise VARCHAR(100) NOT NULL,
    mes_referencia  DATE         NOT NULL,
    observacoes     TEXT,
    criado_em       TIMESTAMP    NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMP,
    CONSTRAINT uq_carteira_casa_mes UNIQUE (casa_de_analise, mes_referencia)
);

CREATE INDEX IF NOT EXISTS idx_carteiras_casa_de_analise ON carteiras_recomendadas (casa_de_analise);
CREATE INDEX IF NOT EXISTS idx_carteiras_mes_referencia  ON carteiras_recomendadas (mes_referencia);

CREATE TABLE IF NOT EXISTS carteiras_recomendadas_itens (
    id            BIGSERIAL    PRIMARY KEY,
    carteira_id   BIGINT       NOT NULL REFERENCES carteiras_recomendadas(id) ON DELETE CASCADE,
    codigo_fundo  VARCHAR(10)  NOT NULL,
    peso          NUMERIC(5,2) NOT NULL,
    justificativa VARCHAR(500),
    CONSTRAINT chk_peso          CHECK (peso > 0 AND peso <= 100),
    CONSTRAINT uq_item_carteira_fundo UNIQUE (carteira_id, codigo_fundo)
);

CREATE INDEX IF NOT EXISTS idx_carteiras_itens_carteira_id  ON carteiras_recomendadas_itens (carteira_id);
CREATE INDEX IF NOT EXISTS idx_carteiras_itens_codigo_fundo ON carteiras_recomendadas_itens (codigo_fundo);

CREATE TABLE IF NOT EXISTS usuarios (
    id        BIGSERIAL    PRIMARY KEY,
    nome      VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL UNIQUE,
    senha     VARCHAR(255) NOT NULL,
    role      VARCHAR(20)  NOT NULL DEFAULT 'USER',
    ativo     BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios (email);

CREATE TABLE IF NOT EXISTS carteira_imagem_analises (
    id                BIGSERIAL    PRIMARY KEY,
    conversa_id       VARCHAR(36)  NOT NULL UNIQUE,
    usuario_id        BIGINT       REFERENCES usuarios(id),
    sentimento_geral  VARCHAR(20)  NOT NULL,
    resumo            TEXT         NOT NULL,
    pontos_positivos  TEXT,
    pontos_negativos  TEXT,
    pontos_atencao    TEXT,
    fiis_para_comprar TEXT,
    fiis_para_vender  TEXT,
    proximos_aportes  TEXT,
    recomendacao_geral TEXT,
    nome_arquivo      VARCHAR(255),
    analisado_em      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analises_usuario_id   ON carteira_imagem_analises (usuario_id);
CREATE INDEX IF NOT EXISTS idx_analises_analisado_em ON carteira_imagem_analises (analisado_em DESC);

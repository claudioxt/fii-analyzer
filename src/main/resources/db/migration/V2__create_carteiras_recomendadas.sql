CREATE TABLE IF NOT EXISTS carteiras_recomendadas (
    id              BIGSERIAL    PRIMARY KEY,
    casa_de_analise VARCHAR(100) NOT NULL,
    mes_referencia  DATE         NOT NULL,
    observacoes     TEXT,
    criado_em       TIMESTAMP    NOT NULL DEFAULT NOW(),
    atualizado_em   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_carteiras_casa_de_analise ON carteiras_recomendadas (casa_de_analise);
CREATE INDEX IF NOT EXISTS idx_carteiras_mes_referencia  ON carteiras_recomendadas (mes_referencia);

CREATE TABLE IF NOT EXISTS carteiras_recomendadas_itens (
    id            BIGSERIAL    PRIMARY KEY,
    carteira_id   BIGINT       NOT NULL REFERENCES carteiras_recomendadas(id) ON DELETE CASCADE,
    codigo_fundo  VARCHAR(10)  NOT NULL,
    peso          NUMERIC(5,2) NOT NULL,
    justificativa VARCHAR(500),
    CONSTRAINT chk_peso CHECK (peso > 0 AND peso <= 100)
);

CREATE INDEX IF NOT EXISTS idx_carteiras_itens_carteira_id ON carteiras_recomendadas_itens (carteira_id);
CREATE INDEX IF NOT EXISTS idx_carteiras_itens_codigo_fundo ON carteiras_recomendadas_itens (codigo_fundo);

-- ============================================================
-- Seed: carteiras recomendadas de abril/2026
-- ============================================================

-- XP Investimentos
INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('XP Investimentos', '2026-04-01',
     'Carteira focada em diversificação entre tijolo e papel com viés defensivo. Preferência por fundos com alta liquidez e gestão ativa comprovada.');

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa) VALUES
    (1, 'KNCR11', 20.00, 'Fundo de recebíveis com carteira atrelada ao CDI, proteção em cenário de juros altos'),
    (1, 'HGLG11', 15.00, 'Portfólio logístico premium com contratos longos e baixa vacância'),
    (1, 'KNRI11', 15.00, 'Fundo híbrido robusto com histórico consistente de distribuições'),
    (1, 'BTLG11', 15.00, 'Boa diversificação de inquilinos no segmento logístico e yield atrativo'),
    (1, 'XPML11', 15.00, 'Shoppings de alta renda com vendas acima do pré-pandemia e DY crescente'),
    (1, 'MXRF11', 10.00, 'Alta distribuição mensal com carteira de CRIs diversificada'),
    (1, 'HGBS11', 10.00, 'Shoppings regionais com gestão ativa e portfólio em expansão');

-- BTG Pactual
INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('BTG Pactual', '2026-04-01',
     'Carteira concentrada nos melhores ativos de cada segmento com foco em crescimento de FFO. Maior exposição a logística dado o cenário macroeconômico favorável ao e-commerce.');

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa) VALUES
    (2, 'BTLG11', 20.00, 'Fundo âncora da casa com excelente track record e pipeline de aquisições'),
    (2, 'KNCR11', 20.00, 'Proteção contra volatilidade da taxa de juros com spread atrativo sobre CDI'),
    (2, 'KNRI11', 20.00, 'Melhor relação risco-retorno no segmento híbrido com baixo P/VP'),
    (2, 'HGLG11', 15.00, 'Ativos classe A com contratos BTS e inquilinos triple net'),
    (2, 'VILG11', 15.00, 'Gestão Vinci com foco em galpões AAA e expansão para o interior'),
    (2, 'HGRE11', 10.00, 'Lajes corporativas em regiões premium de SP com upside de valorização');

-- Itaú BBA
INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('Itaú BBA', '2026-04-01',
     'Alocação conservadora com maior peso em recebíveis imobiliários dado o patamar elevado de juros. Seletividade em fundos de tijolo com baixo P/VP e gestores de primeira linha.');

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa) VALUES
    (3, 'KNCR11', 25.00, 'Melhor veículo de proteção em cenário de Selic elevada com rendimento acima do CDI'),
    (3, 'MXRF11', 20.00, 'Carteira diversificada de CRIs com histórico de dividendos acima da média do setor'),
    (3, 'KNRI11', 20.00, 'Fundo híbrido de alta qualidade com desconto relevante sobre o valor patrimonial'),
    (3, 'HGLG11', 15.00, 'Logística como melhor segmento de tijolo no atual ciclo econômico'),
    (3, 'XPML11', 10.00, 'Shoppings premium com fluxo de visitantes em máximas históricas'),
    (3, 'BTLG11', 10.00, 'Complemento logístico com liquidez elevada e gestão profissional');

-- Genial Investimentos
INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('Genial Investimentos', '2026-04-01',
     'Carteira balanceada com viés de valor. Selecionamos fundos negociados com desconto sobre o NAV e com catalistas claros de crescimento para os próximos trimestres.');

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa) VALUES
    (4, 'HGLG11', 20.00, 'P/VP abaixo de 1.0 representa oportunidade rara para um ativo de qualidade'),
    (4, 'KNCR11', 20.00, 'Yield real positivo com risco baixo e liquidez diária elevada'),
    (4, 'GARE11', 15.00, 'Renda urbana com contratos atípicos e low risk de vacância'),
    (4, 'TRXF11', 15.00, 'Portfólio sale & leaseback com inquilinos de primeira linha'),
    (4, 'KNRI11', 15.00, 'Gestora Kinea com histórico de entrega consistente e portfólio diversificado'),
    (4, 'VILG11', 15.00, 'Galpões de alto padrão com expansão prevista e contratos longos');

-- Suno Research
INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('Suno Research', '2026-04-01',
     'Carteira focada em renda passiva e crescimento sustentável de dividendos. Priorizamos fundos com histórico mínimo de 3 anos, liquidez acima de R$ 1M/dia e P/VP abaixo de 1.10.');

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa) VALUES
    (5, 'MXRF11', 20.00, 'Maior DY do portfólio com pagamentos mensais consistentes há mais de 5 anos'),
    (5, 'KNCR11', 20.00, 'Âncora de renda com correlação negativa à volatilidade dos fundos de tijolo'),
    (5, 'HGLG11', 15.00, 'Dividendos crescentes sustentados por reajustes contratuais de IPCA'),
    (5, 'KNRI11', 15.00, 'Fundo all-in-one com exposição equilibrada a tijolo e papel'),
    (5, 'HGBS11', 15.00, 'Shoppings com participação nos lucros das lojas gerando upside nos proventos'),
    (5, 'RBRX11', 15.00, 'Fundo híbrido com estratégia multiestratégia e retorno ajustado ao risco elevado');
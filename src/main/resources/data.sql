INSERT INTO fundos_imobiliarios (codigo, nome, tipo, segmento) VALUES
    ('HGLG11', 'CSHG Logística FII',                'Fundo de Tijolo', 'Logística'),
    ('HGBS11', 'CSHG Brasil Shopping FII',           'Fundo de Tijolo', 'Shoppings'),
    ('TRXF11', 'TRX Real Estate Logística FII',      'Fundo de Tijolo', 'Renda Urbana'),
    ('GARE11', 'Guardian Real Estate FII',           'Fundo de Tijolo', 'Renda Urbana'),
    ('VILG11', 'Vinci Logística FII',                'Fundo de Tijolo', 'Logística'),
    ('XPML11', 'XP Malls FII',                      'Fundo de Tijolo', 'Shoppings'),
    ('RBRX11', 'RBR Alpha Multiestratégia FII',      'Fundo Híbrido',   'Híbrido'),
    ('BTLG11', 'BTG Pactual Logística FII',          'Fundo de Tijolo', 'Logística'),
    ('KNCR11', 'Kinea Rendimentos Imobiliários FII', 'Fundo de Papel',  'Recebíveis'),
    ('HGRE11', 'CSHG Real Estate FII',               'Fundo de Tijolo', 'Lajes Corporativas'),
    ('KNRI11', 'Kinea Renda Imobiliária FII',        'Fundo de Tijolo', 'Híbrido'),
    ('MXRF11', 'Maxi Renda FII',                     'Fundo de Papel',  'Recebíveis')
ON CONFLICT (codigo) DO NOTHING;

-- ============================================================
-- Carteiras recomendadas — abril/2026
-- ON CONFLICT garante idempotência a cada reinício da aplicação
-- ============================================================

INSERT INTO carteiras_recomendadas (casa_de_analise, mes_referencia, observacoes) VALUES
    ('XP Investimentos',   '2026-04-01', 'Carteira focada em diversificação entre tijolo e papel com viés defensivo. Preferência por fundos com alta liquidez e gestão ativa comprovada.'),
    ('BTG Pactual',        '2026-04-01', 'Carteira concentrada nos melhores ativos de cada segmento com foco em crescimento de FFO. Maior exposição a logística dado o cenário macroeconômico favorável ao e-commerce.'),
    ('Itaú BBA',           '2026-04-01', 'Alocação conservadora com maior peso em recebíveis imobiliários dado o patamar elevado de juros. Seletividade em fundos de tijolo com baixo P/VP e gestores de primeira linha.'),
    ('Genial Investimentos','2026-04-01', 'Carteira balanceada com viés de valor. Selecionamos fundos negociados com desconto sobre o NAV e com catalisadores claros de crescimento para os próximos trimestres.'),
    ('Suno Research',      '2026-04-01', 'Carteira focada em renda passiva e crescimento sustentável de dividendos. Priorizamos fundos com histórico mínimo de 3 anos, liquidez acima de R$ 1M/dia e P/VP abaixo de 1.10.')
ON CONFLICT (casa_de_analise, mes_referencia) DO NOTHING;

INSERT INTO carteiras_recomendadas_itens (carteira_id, codigo_fundo, peso, justificativa)
SELECT c.id, v.codigo_fundo, v.peso, v.justificativa
FROM carteiras_recomendadas c
JOIN (VALUES
    ('XP Investimentos',    '2026-04-01'::date, 'KNCR11', 20.00::numeric, 'Fundo de recebíveis com carteira atrelada ao CDI, proteção em cenário de juros altos'),
    ('XP Investimentos',    '2026-04-01'::date, 'HGLG11', 15.00::numeric, 'Portfólio logístico premium com contratos longos e baixa vacância'),
    ('XP Investimentos',    '2026-04-01'::date, 'KNRI11', 15.00::numeric, 'Fundo híbrido robusto com histórico consistente de distribuições'),
    ('XP Investimentos',    '2026-04-01'::date, 'BTLG11', 15.00::numeric, 'Boa diversificação de inquilinos no segmento logístico e yield atrativo'),
    ('XP Investimentos',    '2026-04-01'::date, 'XPML11', 15.00::numeric, 'Shoppings de alta renda com vendas acima do pré-pandemia e DY crescente'),
    ('XP Investimentos',    '2026-04-01'::date, 'MXRF11', 10.00::numeric, 'Alta distribuição mensal com carteira de CRIs diversificada'),
    ('XP Investimentos',    '2026-04-01'::date, 'HGBS11', 10.00::numeric, 'Shoppings regionais com gestão ativa e portfólio em expansão'),
    ('BTG Pactual',         '2026-04-01'::date, 'BTLG11', 20.00::numeric, 'Fundo âncora da casa com excelente track record e pipeline de aquisições'),
    ('BTG Pactual',         '2026-04-01'::date, 'KNCR11', 20.00::numeric, 'Proteção contra volatilidade da taxa de juros com spread atrativo sobre CDI'),
    ('BTG Pactual',         '2026-04-01'::date, 'KNRI11', 20.00::numeric, 'Melhor relação risco-retorno no segmento híbrido com baixo P/VP'),
    ('BTG Pactual',         '2026-04-01'::date, 'HGLG11', 15.00::numeric, 'Ativos classe A com contratos BTS e inquilinos triple net'),
    ('BTG Pactual',         '2026-04-01'::date, 'VILG11', 15.00::numeric, 'Gestão Vinci com foco em galpões AAA e expansão para o interior'),
    ('BTG Pactual',         '2026-04-01'::date, 'HGRE11', 10.00::numeric, 'Lajes corporativas em regiões premium de SP com upside de valorização'),
    ('Itaú BBA',            '2026-04-01'::date, 'KNCR11', 25.00::numeric, 'Melhor veículo de proteção em cenário de Selic elevada com rendimento acima do CDI'),
    ('Itaú BBA',            '2026-04-01'::date, 'MXRF11', 20.00::numeric, 'Carteira diversificada de CRIs com histórico de dividendos acima da média do setor'),
    ('Itaú BBA',            '2026-04-01'::date, 'KNRI11', 20.00::numeric, 'Fundo híbrido de alta qualidade com desconto relevante sobre o valor patrimonial'),
    ('Itaú BBA',            '2026-04-01'::date, 'HGLG11', 15.00::numeric, 'Logística como melhor segmento de tijolo no atual ciclo econômico'),
    ('Itaú BBA',            '2026-04-01'::date, 'XPML11', 10.00::numeric, 'Shoppings premium com fluxo de visitantes em máximas históricas'),
    ('Itaú BBA',            '2026-04-01'::date, 'BTLG11', 10.00::numeric, 'Complemento logístico com liquidez elevada e gestão profissional'),
    ('Genial Investimentos', '2026-04-01'::date, 'HGLG11', 20.00::numeric, 'P/VP abaixo de 1.0 representa oportunidade rara para um ativo de qualidade'),
    ('Genial Investimentos', '2026-04-01'::date, 'KNCR11', 20.00::numeric, 'Yield real positivo com risco baixo e liquidez diária elevada'),
    ('Genial Investimentos', '2026-04-01'::date, 'GARE11', 15.00::numeric, 'Renda urbana com contratos atípicos e low risk de vacância'),
    ('Genial Investimentos', '2026-04-01'::date, 'TRXF11', 15.00::numeric, 'Portfólio sale & leaseback com inquilinos de primeira linha'),
    ('Genial Investimentos', '2026-04-01'::date, 'KNRI11', 15.00::numeric, 'Gestora Kinea com histórico de entrega consistente e portfólio diversificado'),
    ('Genial Investimentos', '2026-04-01'::date, 'VILG11', 15.00::numeric, 'Galpões de alto padrão com expansão prevista e contratos longos'),
    ('Suno Research',        '2026-04-01'::date, 'MXRF11', 20.00::numeric, 'Maior DY do portfólio com pagamentos mensais consistentes há mais de 5 anos'),
    ('Suno Research',        '2026-04-01'::date, 'KNCR11', 20.00::numeric, 'Âncora de renda com correlação negativa à volatilidade dos fundos de tijolo'),
    ('Suno Research',        '2026-04-01'::date, 'HGLG11', 15.00::numeric, 'Dividendos crescentes sustentados por reajustes contratuais de IPCA'),
    ('Suno Research',        '2026-04-01'::date, 'KNRI11', 15.00::numeric, 'Fundo all-in-one com exposição equilibrada a tijolo e papel'),
    ('Suno Research',        '2026-04-01'::date, 'HGBS11', 15.00::numeric, 'Shoppings com participação nos lucros das lojas gerando upside nos proventos'),
    ('Suno Research',        '2026-04-01'::date, 'RBRX11', 15.00::numeric, 'Fundo híbrido com estratégia multiestratégia e retorno ajustado ao risco elevado')
) AS v(casa_de_analise, mes_referencia, codigo_fundo, peso, justificativa)
  ON c.casa_de_analise = v.casa_de_analise AND c.mes_referencia = v.mes_referencia
ON CONFLICT (carteira_id, codigo_fundo) DO NOTHING;

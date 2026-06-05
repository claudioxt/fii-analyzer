CREATE TABLE IF NOT EXISTS fundos_imobiliarios (
    codigo        VARCHAR(10)  NOT NULL PRIMARY KEY,
    nome          VARCHAR(255) NOT NULL,
    tipo          VARCHAR(100) NOT NULL,
    segmento      VARCHAR(100) NOT NULL,
    criado_em     TIMESTAMP    NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fundos_imobiliarios_segmento ON fundos_imobiliarios (segmento);

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
    ('MXRF11', 'Maxi Renda FII',                     'Fundo de Papel',  'Recebíveis');

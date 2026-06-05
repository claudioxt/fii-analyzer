-- Remove todas as análises de carteira por imagem
-- Execute com cuidado: operação irreversível

BEGIN;

DO $$
DECLARE
    total INTEGER;
BEGIN
    SELECT COUNT(*) INTO total FROM carteira_imagem_analises;
    RAISE NOTICE 'Registros encontrados antes da remoção: %', total;
END $$;

TRUNCATE TABLE carteira_imagem_analises RESTART IDENTITY;

DO $$
BEGIN
    RAISE NOTICE 'Tabela carteira_imagem_analises limpa com sucesso.';
END $$;

COMMIT;

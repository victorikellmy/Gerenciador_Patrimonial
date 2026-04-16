-- =============================================================================
-- V2 — Dados de referência extraídos da aba "val est cons" da planilha original.
-- =============================================================================

-- VUT (Vida Útil Padrão) por categoria.
-- Valores adotados no sistema atual; ajustáveis futuramente via tela admin.
INSERT INTO vida_util_categoria (categoria, vut_anos) VALUES
    ('COMPUTADOR',  5),
    ('IMPRESSORA',  5),
    ('EQUIPAMENTO', 10),
    ('MOVEIS',      10);

-- Percentual VUD por estado de conservação (tabela "val est cons" da planilha).
INSERT INTO percentual_conservacao (conservacao, percentual_vud, descricao) VALUES
    ('NOVO',          0.0500, 'Sem uso ou em estado de novo.'),
    ('OTIMO',         0.1500, 'Pouco uso. Sem defeitos aparentes.'),
    ('BOM',           0.3000, 'Uso regular. Desgaste normal. Funcionamento pleno.'),
    ('BOM_REGULAR',   0.4000, 'Transição entre bom e regular.'),
    ('REGULAR',       0.5000, 'Uso intenso. Desgaste físico e funcional perceptível.'),
    ('REGULAR_RUIM',  0.7000, 'Transição entre regular e ruim.'),
    ('RUIM',          0.8000, 'Alto desgaste físico e funcional. Uso comprometido.'),
    ('INSERVIVEL',    0.9500, 'Não funciona ou não pode ser utilizado.');

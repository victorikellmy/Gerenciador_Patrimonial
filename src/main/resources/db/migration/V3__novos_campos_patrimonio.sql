-- =============================================================================
-- V3 — Campos exigidos pelo novo padrão "Planilha de Reconstituição de Dados".
-- Os derivados (VUD, VUR, depreciação acumulada, VCL, depreciação anual,
-- perda por impairment) seguem sendo calculados em runtime pelo
-- DepreciacaoService — apenas o que é insumo "puro" é persistido.
-- =============================================================================
ALTER TABLE patrimonio ADD COLUMN valor_recuperavel    NUMERIC(19, 2);
ALTER TABLE patrimonio ADD COLUMN conclusao_impairment VARCHAR(255);
ALTER TABLE patrimonio ADD COLUMN observacao           VARCHAR(1000);
ALTER TABLE patrimonio ADD COLUMN link_referencia      VARCHAR(2000);

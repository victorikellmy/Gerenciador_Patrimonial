-- =============================================================================
-- V4 — Subcategoria de patrimônio (refinamento abaixo de "categoria").
-- Por enquanto a lista é mantida hardcoded no frontend (controller); quando
-- a tabela de domínio oficial existir, podemos converter para FK sem alterar
-- a coluna em si (mesmo nome, mesmo tipo).
-- =============================================================================
ALTER TABLE patrimonio ADD COLUMN subcategoria VARCHAR(60);
CREATE INDEX idx_patrimonio_subcategoria ON patrimonio(subcategoria);

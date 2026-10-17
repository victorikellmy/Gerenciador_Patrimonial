-- =============================================================================
-- V11 — Troca de senha obrigatória no primeiro acesso.
-- Usuário criado (ou com senha redefinida) pelo administrador entra com
-- senha_provisoria = TRUE e só navega depois de definir a própria senha.
-- =============================================================================
ALTER TABLE usuario ADD COLUMN senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE;

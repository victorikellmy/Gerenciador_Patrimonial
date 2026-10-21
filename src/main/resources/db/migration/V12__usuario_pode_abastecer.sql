-- =============================================================================
-- V12 — Permissão extra por usuário: registrar abastecimentos da frota.
-- Para quem não é motorista nem gestor da frota (ex.: um GERENTE) poder lançar
-- abastecimentos com nota fiscal obrigatória, sem ganhar o resto da frota.
-- =============================================================================
ALTER TABLE usuario ADD COLUMN pode_abastecer BOOLEAN NOT NULL DEFAULT FALSE;

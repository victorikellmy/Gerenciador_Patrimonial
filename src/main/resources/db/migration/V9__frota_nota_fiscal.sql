-- =============================================================================
-- V9 — Nota fiscal (PDF ou foto) anexada a abastecimentos e manutenções.
-- Obrigatória quando o lançamento é feito por um MOTORISTA; opcional para
-- ADMINISTRADOR/FISCAL (registros antigos ou importados do Transportes).
-- O arquivo fica no StorageService (frota/{veiculoId}/...); aqui só o caminho.
-- =============================================================================
ALTER TABLE frota_abastecimento ADD COLUMN nf_nome_original VARCHAR(255);
ALTER TABLE frota_abastecimento ADD COLUMN nf_content_type  VARCHAR(100);
ALTER TABLE frota_abastecimento ADD COLUMN nf_tamanho_bytes BIGINT;
ALTER TABLE frota_abastecimento ADD COLUMN nf_caminho       VARCHAR(500);

ALTER TABLE frota_manutencao ADD COLUMN nf_nome_original VARCHAR(255);
ALTER TABLE frota_manutencao ADD COLUMN nf_content_type  VARCHAR(100);
ALTER TABLE frota_manutencao ADD COLUMN nf_tamanho_bytes BIGINT;
ALTER TABLE frota_manutencao ADD COLUMN nf_caminho       VARCHAR(500);

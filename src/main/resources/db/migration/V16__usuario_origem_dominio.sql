-- =============================================================================
-- V16 — Login pela rede da Fundação (Active Directory FPTO.LOCAL).
-- LOCAL  = senha guardada no sistema (como até agora; admin inicial, integração).
-- DOMINIO = senha conferida no AD a cada login e perfil vindo dos grupos
--           "Patrimonio-*" do AD; senha_hash guarda só uma marca sem uso.
-- O perfil novo USUARIO (usuário da rede ainda sem grupo) cabe no VARCHAR(30) atual.
-- =============================================================================
ALTER TABLE usuario ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

-- =============================================================================
-- V5 — Trilha de auditoria de ações de usuário (CREATE / UPDATE / DELETE / etc).
-- Distinta da auditoria do JPA (criado_por/atualizado_por), que registra apenas
-- o estado mais recente. Esta tabela mantém a sequência completa.
-- =============================================================================
CREATE TABLE auditoria_acao (
    id            BIGSERIAL    PRIMARY KEY,
    usuario       VARCHAR(80)  NOT NULL,
    acao          VARCHAR(20)  NOT NULL,
    entidade      VARCHAR(60)  NOT NULL,
    entidade_id   BIGINT,
    descricao     VARCHAR(2000),
    ip_origem     VARCHAR(45),
    data_hora     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auditoria_usuario   ON auditoria_acao(usuario);
CREATE INDEX idx_auditoria_acao      ON auditoria_acao(acao);
CREATE INDEX idx_auditoria_entidade  ON auditoria_acao(entidade, entidade_id);
CREATE INDEX idx_auditoria_data_hora ON auditoria_acao(data_hora DESC);

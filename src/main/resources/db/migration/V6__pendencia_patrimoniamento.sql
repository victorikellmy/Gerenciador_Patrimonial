-- =============================================================================
-- V6 — Pendências de patrimoniamento recebidas de sistemas externos
-- (hoje: Almoxarifado, compras PATRIMONIAL/DIRETA patrimoniadas).
-- Cada recebimento gera UMA pendência; (origem, compra_id_origem) é a chave de
-- idempotência — reenvios da fila do Almoxarifado não duplicam registros.
-- =============================================================================
CREATE TABLE pendencia_patrimoniamento (
    id                      BIGSERIAL     PRIMARY KEY,
    origem                  VARCHAR(30)   NOT NULL,                     -- ALMOXARIFADO
    compra_id_origem        BIGINT        NOT NULL,                     -- compraId do almoxarifado
    status                  VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',  -- PENDENTE | CONCLUIDA | DESCARTADA
    numero_documento        VARCHAR(30),
    numero_sgd              VARCHAR(40),
    assunto                 VARCHAR(255),
    solicitante_documento   VARCHAR(255),
    fornecedor              VARCHAR(150),
    numero_nota_fiscal      VARCHAR(60),
    data_recebimento        TIMESTAMP,
    data_compra             DATE,
    valor_total             NUMERIC(19,2),
    retirado_por            VARCHAR(120),
    setor_destino           VARCHAR(150),
    setor_destino_cc        VARCHAR(30),
    registrado_por          VARCHAR(120),
    observacao              VARCHAR(1000),
    itens_json              VARCHAR(10000),                             -- lista de itens como recebida (JSON)
    quantidade_total        INTEGER       NOT NULL DEFAULT 1,           -- soma das quantidades dos itens
    quantidade_patrimoniada INTEGER       NOT NULL DEFAULT 0,           -- bens já cadastrados a partir dela
    nf_nome_original        VARCHAR(255),
    nf_content_type         VARCHAR(100),
    nf_tamanho_bytes        BIGINT,
    nf_caminho              VARCHAR(500),                               -- PDF salvo pelo StorageService
    patrimonio_id           BIGINT        REFERENCES patrimonio(id),    -- último bem criado ao patrimoniar
    concluida_por           VARCHAR(80),
    concluida_em            TIMESTAMP,
    criado_em               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pendencia_origem UNIQUE (origem, compra_id_origem)
);
CREATE INDEX idx_pendencia_status    ON pendencia_patrimoniamento(status);
CREATE INDEX idx_pendencia_criado_em ON pendencia_patrimoniamento(criado_em);

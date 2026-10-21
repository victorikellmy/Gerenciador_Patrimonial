-- =============================================================================
-- V13 — Seguro dos veículos da frota.
-- frota_seguro: apólices (uma por vigência; renovação = nova apólice), com o
-- arquivo da apólice (PDF/foto) no StorageService (frota/{veiculoId}/seguros).
-- frota_sinistro: ocorrências acionadas no seguro (batida, roubo, vidro...).
-- =============================================================================
CREATE TABLE frota_seguro (
    id                     BIGSERIAL      PRIMARY KEY,
    veiculo_id             BIGINT         NOT NULL REFERENCES veiculo(id),
    seguradora             VARCHAR(120)   NOT NULL,
    numero_apolice         VARCHAR(60)    NOT NULL,
    corretor               VARCHAR(120),
    inicio_vigencia        DATE           NOT NULL,
    fim_vigencia           DATE           NOT NULL,
    valor_premio           NUMERIC(12,2),
    franquia               NUMERIC(12,2),
    coberturas             VARCHAR(1000),
    telefone_assistencia   VARCHAR(40),             -- assistência 24h / guincho
    observacao             VARCHAR(500),
    apolice_nome_original  VARCHAR(255),
    apolice_content_type   VARCHAR(100),
    apolice_tamanho_bytes  BIGINT,
    apolice_caminho        VARCHAR(500),
    registrado_por         VARCHAR(80),
    criado_em              TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_seguro_veiculo ON frota_seguro(veiculo_id);
CREATE INDEX idx_seguro_fim     ON frota_seguro(fim_vigencia);

CREATE TABLE frota_sinistro (
    id                BIGSERIAL      PRIMARY KEY,
    veiculo_id        BIGINT         NOT NULL REFERENCES veiculo(id),
    seguro_id         BIGINT         REFERENCES frota_seguro(id),
    data_ocorrencia   DATE           NOT NULL,
    numero_sinistro   VARCHAR(60),                  -- protocolo da seguradora
    descricao         VARCHAR(1000)  NOT NULL,
    local_ocorrencia  VARCHAR(160),
    condutor          VARCHAR(160),
    valor_prejuizo    NUMERIC(12,2),
    valor_indenizado  NUMERIC(12,2),
    situacao          VARCHAR(20)    NOT NULL,
    observacao        VARCHAR(500),
    registrado_por    VARCHAR(80),
    criado_em         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sinistro_veiculo ON frota_sinistro(veiculo_id);
CREATE INDEX idx_sinistro_seguro  ON frota_sinistro(seguro_id);

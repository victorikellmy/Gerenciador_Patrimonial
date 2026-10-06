-- =============================================================================
-- V7 — Levantamento patrimonial (inventário físico anual).
-- Ao abrir um levantamento, todos os bens não baixados viram itens de
-- conferência, cada um "congelado" na lotação em que estava naquele momento
-- (lotacao_id). O operador percorre o guia local a local e registra se o bem
-- está OK, avariado ou não foi localizado, podendo atualizar a conservação e
-- a foto. Bens achados em local diferente do esperado ficam em
-- lotacao_encontrada_id para posterior movimentação.
-- =============================================================================
CREATE TABLE levantamento_patrimonial (
    id             BIGSERIAL     PRIMARY KEY,
    ano            INTEGER       NOT NULL,
    descricao      VARCHAR(150)  NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ABERTO',   -- ABERTO | CONCLUIDO
    observacao     VARCHAR(1000),
    total_itens    INTEGER       NOT NULL DEFAULT 0,          -- bens incluídos na abertura
    aberto_por     VARCHAR(80),
    aberto_em      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluido_por  VARCHAR(80),
    concluido_em   TIMESTAMP
);
CREATE INDEX idx_levantamento_status ON levantamento_patrimonial(status);
CREATE INDEX idx_levantamento_ano    ON levantamento_patrimonial(ano);

CREATE TABLE item_levantamento (
    id                      BIGSERIAL     PRIMARY KEY,
    levantamento_id         BIGINT        NOT NULL REFERENCES levantamento_patrimonial(id) ON DELETE CASCADE,
    patrimonio_id           BIGINT        NOT NULL REFERENCES patrimonio(id) ON DELETE CASCADE,
    lotacao_id              BIGINT        NOT NULL REFERENCES lotacao(id),         -- local esperado (snapshot)
    lotacao_encontrada_id   BIGINT        REFERENCES lotacao(id),                  -- onde o bem foi achado, se diferente
    resultado               VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',             -- PENDENTE | OK | AVARIADO | NAO_LOCALIZADO
    conservacao_anterior    VARCHAR(30),                                           -- conservação do bem na abertura
    conservacao_verificada  VARCHAR(30),                                           -- conservação informada na conferência
    descricao_avaria        VARCHAR(1000),
    observacao              VARCHAR(1000),
    foto_id                 BIGINT        REFERENCES arquivo_anexo(id) ON DELETE SET NULL, -- foto tirada na conferência
    verificado_por          VARCHAR(80),
    verificado_em           TIMESTAMP,
    CONSTRAINT uk_item_levantamento UNIQUE (levantamento_id, patrimonio_id)
);
CREATE INDEX idx_item_lev_levantamento ON item_levantamento(levantamento_id);
CREATE INDEX idx_item_lev_lotacao      ON item_levantamento(levantamento_id, lotacao_id);
CREATE INDEX idx_item_lev_encontrada   ON item_levantamento(levantamento_id, lotacao_encontrada_id);
CREATE INDEX idx_item_lev_resultado    ON item_levantamento(levantamento_id, resultado);

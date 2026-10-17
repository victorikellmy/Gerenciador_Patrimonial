-- =============================================================================
-- V8 — Frota: veículos como patrimônio + histórico operacional.
--
-- Um veículo é SEMPRE um patrimônio (patrimonio_id único). A tabela veiculo
-- guarda só o que é específico de automóvel (placa, chassi, km...). Os três
-- tipos de registro (abastecimento, manutenção/revisão e deslocamento) ficam
-- vinculados ao veículo e formam o histórico dele.
--
-- As colunas id_transportes guardam a chave do sistema "Transportes FPTO"
-- (banco transportes no servidor .25) para a importação ser idempotente.
-- =============================================================================
CREATE TABLE veiculo (
    id                  BIGSERIAL     PRIMARY KEY,
    patrimonio_id       BIGINT        NOT NULL REFERENCES patrimonio(id),
    placa               VARCHAR(20)   NOT NULL,
    modelo              VARCHAR(60)   NOT NULL,
    marca               VARCHAR(60),
    chassi              VARCHAR(50),
    renavam             VARCHAR(20),
    ano                 VARCHAR(10),
    cidade              VARCHAR(60),
    combustivel_padrao  VARCHAR(20),                       -- GASOLINA | ETANOL | DIESEL | GNV
    km_atual            INTEGER,                           -- maior odômetro já registrado
    ativo               BOOLEAN       NOT NULL DEFAULT TRUE,
    id_transportes      INTEGER,
    criado_em           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em       TIMESTAMP,
    CONSTRAINT uk_veiculo_patrimonio  UNIQUE (patrimonio_id),
    CONSTRAINT uk_veiculo_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_veiculo_placa ON veiculo(placa);
CREATE INDEX idx_veiculo_ativo ON veiculo(ativo);

CREATE TABLE frota_abastecimento (
    id                 BIGSERIAL      PRIMARY KEY,
    veiculo_id         BIGINT         NOT NULL REFERENCES veiculo(id),
    data               DATE           NOT NULL,
    km                 INTEGER,
    combustivel        VARCHAR(20)    NOT NULL,
    quantidade_litros  NUMERIC(10,3),
    valor              NUMERIC(12,2)  NOT NULL,
    posto_cidade       VARCHAR(160),
    motorista          VARCHAR(160)   NOT NULL,
    observacao         VARCHAR(500),
    registrado_por     VARCHAR(80),
    criado_em          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_transportes     INTEGER,
    CONSTRAINT uk_abastecimento_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_abastecimento_veiculo ON frota_abastecimento(veiculo_id);
CREATE INDEX idx_abastecimento_data    ON frota_abastecimento(data);

CREATE TABLE frota_manutencao (
    id                   BIGSERIAL      PRIMARY KEY,
    veiculo_id           BIGINT         NOT NULL REFERENCES veiculo(id),
    data                 DATE           NOT NULL,
    servico              VARCHAR(30)    NOT NULL,          -- CONSERTO | REVISAO | TROCA_OLEO | ...
    km                   INTEGER,
    descricao            VARCHAR(1000),
    valor                NUMERIC(12,2)  NOT NULL DEFAULT 0,
    fornecedor           VARCHAR(160),
    nota_fiscal          VARCHAR(60),
    proxima_revisao_km   INTEGER,
    proxima_revisao_data DATE,
    motorista            VARCHAR(160)   NOT NULL,
    registrado_por       VARCHAR(80),
    criado_em            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_transportes       INTEGER,
    CONSTRAINT uk_manutencao_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_manutencao_veiculo ON frota_manutencao(veiculo_id);
CREATE INDEX idx_manutencao_data    ON frota_manutencao(data);

CREATE TABLE frota_deslocamento (
    id              BIGSERIAL      PRIMARY KEY,
    veiculo_id      BIGINT         NOT NULL REFERENCES veiculo(id),
    data_viagem     DATE           NOT NULL,
    destino         VARCHAR(160)   NOT NULL,
    motivo          VARCHAR(60)    NOT NULL,
    titular         VARCHAR(160),
    assistido       VARCHAR(160),
    hora_saida      TIME,
    km_inicial      INTEGER,
    hora_chegada    TIME,
    km_final        INTEGER,                              -- NULL = veículo ainda na rua
    motorista       VARCHAR(160)   NOT NULL,
    lotacao         VARCHAR(60),
    observacao      VARCHAR(500),
    registrado_por  VARCHAR(80),
    criado_em       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_transportes  INTEGER,
    CONSTRAINT uk_deslocamento_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_deslocamento_veiculo ON frota_deslocamento(veiculo_id);
CREATE INDEX idx_deslocamento_data    ON frota_deslocamento(data_viagem);
CREATE INDEX idx_deslocamento_aberto  ON frota_deslocamento(km_final);

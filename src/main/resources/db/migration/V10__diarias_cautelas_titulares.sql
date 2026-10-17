-- =============================================================================
-- V10 — Restante do sistema Transportes FPTO absorvido pelo Patrimônio:
--   * diárias de viagem (Portaria 004/2018 GAB. Pres FPTO) com tabela de valores
--     por nível funcional, profissionais e histórico de aprovação;
--   * cautelas de veículo (entrega a condutor externo com termo de responsabilidade);
--   * base de titulares (militares/pensionistas) para autocomplete.
-- As colunas id_transportes guardam a chave do banco do Transportes (importação idempotente).
-- =============================================================================
CREATE TABLE nivel_diaria (
    id                           BIGSERIAL      PRIMARY KEY,
    descricao                    VARCHAR(160)   NOT NULL,
    valor_capitais               NUMERIC(12,2)  NOT NULL,
    valor_interior_estado        NUMERIC(12,2)  NOT NULL,
    valor_interior_outro_estado  NUMERIC(12,2)  NOT NULL,
    ativo                        BOOLEAN        NOT NULL DEFAULT TRUE,
    id_transportes               INTEGER,
    CONSTRAINT uk_nivel_diaria_transportes UNIQUE (id_transportes)
);

CREATE TABLE profissional (
    id               BIGSERIAL     PRIMARY KEY,
    matricula        VARCHAR(30),
    nome             VARCHAR(160)  NOT NULL,
    lotacao          VARCHAR(60),
    nivel_diaria_id  BIGINT        NOT NULL REFERENCES nivel_diaria(id),
    cpf              VARCHAR(14)   NOT NULL,
    rg               VARCHAR(30),
    orgao_expedidor  VARCHAR(30),
    dados_bancarios  VARCHAR(160),
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    cadastrado_por   VARCHAR(160),
    id_transportes   INTEGER,
    CONSTRAINT uk_profissional_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_profissional_cpf  ON profissional(cpf);
CREATE INDEX idx_profissional_nome ON profissional(nome);

CREATE TABLE diaria (
    id                    BIGSERIAL      PRIMARY KEY,
    data_solicitacao      DATE           NOT NULL,
    profissional_id       BIGINT         NOT NULL REFERENCES profissional(id),
    itinerario            VARCHAR(200)   NOT NULL,
    hora_saida            TIME,
    meio_transporte       VARCHAR(20)    NOT NULL,   -- VEICULO_OFICIAL | AEREO | RODOVIARIO
    partida               DATE           NOT NULL,
    retorno               DATE           NOT NULL,
    hora_chegada          TIME,
    recurso               VARCHAR(20)    NOT NULL,   -- PROPRIO | CONVENIO
    numero_diarias        NUMERIC(6,1)   NOT NULL,
    valor_unitario        NUMERIC(12,2)  NOT NULL,
    valor_total           NUMERIC(12,2)  NOT NULL,
    finalidade            VARCHAR(2000),
    solicitante           VARCHAR(160)   NOT NULL,
    status                VARCHAR(20)    NOT NULL,   -- SOLICITADO | CONFIRMADO | DEFERIDO | INDEFERIDO | PAGO
    data_pagamento        DATE,
    motivo_indeferimento  VARCHAR(500),
    criado_em             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em         TIMESTAMP,
    id_transportes        INTEGER,
    CONSTRAINT uk_diaria_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_diaria_status    ON diaria(status);
CREATE INDEX idx_diaria_pagamento ON diaria(data_pagamento);
CREATE INDEX idx_diaria_partida   ON diaria(partida);

CREATE TABLE diaria_historico (
    id           BIGSERIAL     PRIMARY KEY,
    diaria_id    BIGINT        NOT NULL REFERENCES diaria(id) ON DELETE CASCADE,
    de_status    VARCHAR(20),
    para_status  VARCHAR(20)   NOT NULL,
    usuario      VARCHAR(160)  NOT NULL,
    instante     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacao   VARCHAR(500)
);
CREATE INDEX idx_diaria_historico_diaria ON diaria_historico(diaria_id);

CREATE TABLE frota_cautela (
    id              BIGSERIAL      PRIMARY KEY,
    veiculo_id      BIGINT         NOT NULL REFERENCES veiculo(id),
    data_viagem     DATE           NOT NULL,
    destino         VARCHAR(160)   NOT NULL,
    motivo          VARCHAR(60)    NOT NULL,
    responsavel     VARCHAR(160),
    assistido       VARCHAR(160),
    hora_saida      TIME,
    km_inicial      INTEGER,
    hora_chegada    TIME,
    km_final        INTEGER,                      -- NULL = cautela em aberto
    motorista_nome  VARCHAR(160)   NOT NULL,      -- condutor que recebe o veículo
    rg              VARCHAR(30),
    cpf             VARCHAR(14),
    fone            VARCHAR(25),
    lotacao         VARCHAR(60),
    observacao      VARCHAR(500),
    registrado_por  VARCHAR(80),
    criado_em       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_transportes  INTEGER,
    CONSTRAINT uk_cautela_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_cautela_veiculo ON frota_cautela(veiculo_id);
CREATE INDEX idx_cautela_data    ON frota_cautela(data_viagem);
CREATE INDEX idx_cautela_aberta  ON frota_cautela(km_final);

CREATE TABLE titular (
    id               BIGSERIAL     PRIMARY KEY,
    posto_graduacao  INTEGER,
    tipo_cadastro    VARCHAR(1),                  -- M = militar, P = pensionista
    matricula        VARCHAR(20),
    vinculo          VARCHAR(2),
    nome             VARCHAR(120)  NOT NULL,
    cpf              VARCHAR(15),
    id_transportes   INTEGER,
    CONSTRAINT uk_titular_transportes UNIQUE (id_transportes)
);
CREATE INDEX idx_titular_nome ON titular(nome);

-- Tabela de valores (Portaria 004/2018 GAB. Pres FPTO), mesma do Transportes.
INSERT INTO nivel_diaria (descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado, id_transportes) VALUES
 ('Presidente',                                                    582.74, 367.38, 481.40, 1),
 ('Diretores Administrativo e Financeiro; Gerente de Projetos',    471.26, 324.31, 410.45, 2),
 ('Gerente FA-Saude e Capitão',                                    443.39, 296.44, 380.05, 3),
 ('Analista; 1º e 2º Tenente; Aspirante e ST',                     397.79, 266.04, 342.05, 4),
 ('Assistente; 1º, 2º e 3º Sgt',                                   354.71, 243.23, 311.64, 5),
 ('Auxiliar; Cabo e Soldado',                                      286.30, 190.03, 245.77, 6),
 ('Aluno-Soldado',                                                 228.03, 152.02, 200.16, 7);

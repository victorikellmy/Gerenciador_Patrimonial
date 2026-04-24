-- =============================================================================
-- V1 — Schema inicial do Gerenciador Patrimonial
-- Compatível com H2 (modo PostgreSQL) e PostgreSQL real.
-- =============================================================================

-- Lotação (UPM + sala; chave única composta)
CREATE TABLE lotacao (
    id                    BIGSERIAL PRIMARY KEY,
    upm                   VARCHAR(120) NOT NULL,
    nome                  VARCHAR(120) NOT NULL,
    cidade                VARCHAR(80),
    tipo_local            VARCHAR(20)  NOT NULL DEFAULT 'INTERNO',
    responsavel_atual_id  BIGINT,
    criado_em             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em         TIMESTAMP,
    CONSTRAINT uk_lotacao_upm_nome UNIQUE (upm, nome)
);

-- Responsável (pessoa física que assina pelos bens)
CREATE TABLE responsavel (
    id            BIGSERIAL PRIMARY KEY,
    nome_completo VARCHAR(150) NOT NULL,
    matricula     VARCHAR(50),
    cidade        VARCHAR(80),
    lotacao_id    BIGINT REFERENCES lotacao(id),
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP,
    CONSTRAINT uk_responsavel_matricula UNIQUE (matricula)
);

-- FK recíproca: lotação -> responsável atual
ALTER TABLE lotacao
    ADD CONSTRAINT fk_lotacao_responsavel
    FOREIGN KEY (responsavel_atual_id) REFERENCES responsavel(id);

-- Tabela de referência: vida útil padrão por categoria
CREATE TABLE vida_util_categoria (
    id         BIGSERIAL PRIMARY KEY,
    categoria  VARCHAR(60) NOT NULL UNIQUE,
    vut_anos   INTEGER     NOT NULL
);

-- Tabela de referência: percentual VUD por conservação
CREATE TABLE percentual_conservacao (
    id                BIGSERIAL PRIMARY KEY,
    conservacao       VARCHAR(30)    NOT NULL UNIQUE,
    percentual_vud    NUMERIC(5, 4)  NOT NULL,
    descricao         VARCHAR(255)
);

-- Patrimônio (tabela principal)
CREATE TABLE patrimonio (
    id               BIGSERIAL PRIMARY KEY,
    numero_tombo     VARCHAR(30),
    descricao        VARCHAR(255) NOT NULL,
    categoria        VARCHAR(60),
    data_compra      DATE,
    valor_compra     NUMERIC(19, 2),
    conservacao      VARCHAR(30),
    situacao         VARCHAR(30)  NOT NULL DEFAULT 'ATIVO',
    nota_fiscal      VARCHAR(60),
    data_baixa       DATE,
    motivo_baixa     VARCHAR(255),
    lotacao_id       BIGINT       NOT NULL REFERENCES lotacao(id),
    responsavel_id   BIGINT       NOT NULL REFERENCES responsavel(id),
    criado_em        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em    TIMESTAMP,
    criado_por       VARCHAR(80),
    atualizado_por   VARCHAR(80),
    CONSTRAINT uk_patrimonio_tombo UNIQUE (numero_tombo)
);
CREATE INDEX idx_patrimonio_situacao ON patrimonio(situacao);
CREATE INDEX idx_patrimonio_lotacao  ON patrimonio(lotacao_id);
CREATE INDEX idx_patrimonio_resp     ON patrimonio(responsavel_id);

-- Anexos (nota fiscal, fotos, manuais) — caminho em disco
CREATE TABLE arquivo_anexo (
    id                     BIGSERIAL PRIMARY KEY,
    patrimonio_id          BIGINT       NOT NULL REFERENCES patrimonio(id) ON DELETE CASCADE,
    nome_original          VARCHAR(255) NOT NULL,
    caminho_armazenamento  VARCHAR(500) NOT NULL,
    content_type           VARCHAR(100),
    tamanho_bytes          BIGINT,
    tipo                   VARCHAR(30)  NOT NULL,
    criado_em              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_anexo_patrimonio ON arquivo_anexo(patrimonio_id);

-- Histórico de movimentações (lotação/responsável)
CREATE TABLE movimentacao (
    id                      BIGSERIAL PRIMARY KEY,
    patrimonio_id           BIGINT    NOT NULL REFERENCES patrimonio(id),
    lotacao_origem_id       BIGINT REFERENCES lotacao(id),
    lotacao_destino_id      BIGINT REFERENCES lotacao(id),
    responsavel_origem_id   BIGINT REFERENCES responsavel(id),
    responsavel_destino_id  BIGINT REFERENCES responsavel(id),
    data_movimentacao       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacao              VARCHAR(500),
    executado_por           VARCHAR(80)
);
CREATE INDEX idx_mov_patrimonio ON movimentacao(patrimonio_id);

-- Usuário do sistema (operador)
CREATE TABLE usuario (
    id             BIGSERIAL PRIMARY KEY,
    nome_completo  VARCHAR(150) NOT NULL,
    login          VARCHAR(60)  NOT NULL UNIQUE,
    senha_hash     VARCHAR(255) NOT NULL,
    perfil         VARCHAR(30)  NOT NULL,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em  TIMESTAMP
);

-- =============================================================================
-- V14 — Requerimento de diárias (Anexo II da Portaria 001/2019 GAB. PRES).
--   * profissional: cargo/posto/função em texto, dados bancários estruturados
--     (banco, agência, conta, PIX) e nível da tabela de valores opcional — o
--     cadastro passa a ser alimentado pelo próprio requerimento de diária;
--   * diaria: número do requerimento, origem, tipo de diária e a "fotografia"
--     dos dados do profissional e da conta no momento do pedido (o documento
--     não muda se o cadastro for alterado depois).
-- Colunas novas são anuláveis: as diárias antigas continuam válidas.
-- =============================================================================
ALTER TABLE profissional ALTER COLUMN nivel_diaria_id DROP NOT NULL;
ALTER TABLE profissional ADD COLUMN cargo           VARCHAR(120);
ALTER TABLE profissional ADD COLUMN banco           VARCHAR(3);
ALTER TABLE profissional ADD COLUMN agencia         VARCHAR(15);
ALTER TABLE profissional ADD COLUMN conta_corrente  VARCHAR(20);
ALTER TABLE profissional ADD COLUMN pix             VARCHAR(120);
CREATE INDEX idx_profissional_matricula ON profissional(matricula);

ALTER TABLE diaria ADD COLUMN numero_requerimento  VARCHAR(20);
ALTER TABLE diaria ADD COLUMN origem               VARCHAR(120);
ALTER TABLE diaria ADD COLUMN tipo_diaria          VARCHAR(30);   -- CAPITAL | INTERIOR_ESTADO | INTERIOR_OUTRO_ESTADO
ALTER TABLE diaria ADD COLUMN prof_nome            VARCHAR(160);
ALTER TABLE diaria ADD COLUMN prof_matricula       VARCHAR(30);
ALTER TABLE diaria ADD COLUMN prof_cargo           VARCHAR(160);
ALTER TABLE diaria ADD COLUMN prof_lotacao         VARCHAR(60);
ALTER TABLE diaria ADD COLUMN prof_cpf             VARCHAR(14);
ALTER TABLE diaria ADD COLUMN prof_rg              VARCHAR(30);
ALTER TABLE diaria ADD COLUMN prof_orgao_expedidor VARCHAR(30);
ALTER TABLE diaria ADD COLUMN banco                VARCHAR(3);
ALTER TABLE diaria ADD COLUMN agencia              VARCHAR(15);
ALTER TABLE diaria ADD COLUMN conta_corrente       VARCHAR(20);
ALTER TABLE diaria ADD COLUMN pix                  VARCHAR(120);
ALTER TABLE diaria ADD COLUMN dados_bancarios      VARCHAR(160);  -- texto livre do cadastro antigo

-- Diárias já existentes: copia os dados do cadastro como estavam e numera por id/ano.
UPDATE diaria SET
    prof_nome            = (SELECT p.nome            FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_matricula       = (SELECT p.matricula       FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_lotacao         = (SELECT p.lotacao         FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_cpf             = (SELECT p.cpf             FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_rg              = (SELECT p.rg              FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_orgao_expedidor = (SELECT p.orgao_expedidor FROM profissional p WHERE p.id = diaria.profissional_id),
    dados_bancarios      = (SELECT p.dados_bancarios FROM profissional p WHERE p.id = diaria.profissional_id),
    prof_cargo           = (SELECT n.descricao FROM profissional p JOIN nivel_diaria n ON n.id = p.nivel_diaria_id
                             WHERE p.id = diaria.profissional_id),
    numero_requerimento  = CAST(id AS VARCHAR(12)) || '/' || CAST(EXTRACT(YEAR FROM data_solicitacao) AS VARCHAR(4));

CREATE UNIQUE INDEX uk_diaria_numero_requerimento ON diaria(numero_requerimento);

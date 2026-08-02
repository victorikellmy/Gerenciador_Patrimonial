-- =============================================================================
-- V6 — Índices para busca textual e filtros (somente PostgreSQL).
--
-- Motivação: os filtros de pesquisa usam upper(coluna) LIKE '%termo%', que
-- sem índice funcional/trigram força seq scan. A tabela de auditoria é
-- append-only e só cresce, então o custo aumenta com o tempo.
--
-- Requer a extensão pg_trgm. CREATE EXTENSION exige privilégio no database;
-- se o usuário da aplicação não tiver, peça ao DBA para rodar:
--   CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- patrimonio: filtros da tela de pesquisa ----------

-- Busca por descrição: LIKE '%termo%' → índice trigram (GIN).
CREATE INDEX IF NOT EXISTS idx_patrimonio_descricao_trgm
    ON patrimonio USING gin (upper(descricao) gin_trgm_ops);

-- Filtro por categoria: igualdade sobre upper(categoria) → índice funcional B-tree.
CREATE INDEX IF NOT EXISTS idx_patrimonio_categoria_upper
    ON patrimonio (upper(categoria));

-- ---------- lotacao: filtro por UPM ----------

CREATE INDEX IF NOT EXISTS idx_lotacao_upm_upper
    ON lotacao (upper(upm));

-- ---------- auditoria_acao: filtro por usuário (LIKE '%x%') ----------

CREATE INDEX IF NOT EXISTS idx_auditoria_usuario_trgm
    ON auditoria_acao USING gin (lower(usuario) gin_trgm_ops);

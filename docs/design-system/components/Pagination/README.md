Navegação de listagens: filtro rápido por `btn-group` de `btn-sm` (o ativo em `btn-primary`, os demais em `btn-secondary`, contagem opcional em `badge text-bg-light`) no `page-header`, e paginação `pagination-sm` abaixo do card da tabela.

**Paginação.** Três itens: anterior, "página / total" como item desabilitado, próxima. Centralizada abaixo do card (`justify-content-center`), só aparece quando há mais de uma página. Os links preservam os filtros ativos na query string.

**Filtro por botões.** Para situações mutuamente exclusivas (Pendentes / Concluídas / Todas; Pendentes / Todos no checklist). Para filtros combináveis use a toolbar com selects e os `filter-chip`.

**Regras.** Tamanho de página padrão 20 (25 na API); nunca mais de 50 por página em telas. Não use paginação numérica longa (1 2 3 … 40): a busca por filtro substitui o pulo de página.

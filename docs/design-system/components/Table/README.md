Listagem padrão: `.table.table-hover.align-middle.mb-0` dentro de um `.card-panel` com `overflow:hidden`, cabeçalho em `table-header` (11px caixa alta) sobre `bg-muted`, linhas separadas por `border`, hover em `bg-hover`.

**Colunas, nesta ordem quando existirem.** Tombo/ID como `.chip-tombo`; descrição em `.cell-stack` (`cell-primary` + `cell-secondary` com categoria, responsável ou ID); lotação em `.cell-stack` (sala em cima, UPM embaixo); valores à direita com `.tabular-nums` e formato `1.234,56`; datas `dd/MM/yyyy`; situação como badge; ações à direita em `d-inline-flex gap-1` com `btn-sm btn-secondary btn-icon` (uma ação principal pode ser `btn-sm btn-primary` com texto, como "Patrimoniar").

**Regras.**
- `table-responsive` sempre; em telas < 768px o CSS reorganiza cada linha como cartão (`.table-card-mobile`, seção 21 do app.css) quando a tabela tem `data-label` nas células.
- Vazio: substitua a tabela pelo `empty-state` do ui-kit; carregando: `skeleton-rows(cols, rows)`.
- Nada de zebra, nem bordas verticais, nem texto centralizado fora de ícones.
- Filtros acima da tabela em `.toolbar` com os chips ativos em `.filter-chips`; paginação abaixo do card.
- Em relatórios use `container-fluid` para a tabela respirar.

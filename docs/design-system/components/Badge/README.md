Marcadores de estado e de dados: badges de situação e de resultado, chip de tombo e chip de filtro ativo. Todos em 12px/500 com `badge-plain` (sem fundo gradiente do Bootstrap).

**Pares de estado.** Cada badge usa um par `*-bg` / `*-fg` do mesmo nome, nunca cores soltas:
- `badge-sit` (situação do bem): `ATIVO` → success; `MANUTENCAO` → warning; `CAUTELADO` → info; `BAIXADO` → `slate-200`/`slate-700`; `EM_APURACAO` → warning.
- `badge-res` (resultado da conferência): `PENDENTE` → slate; `OK` → success; `AVARIADO` → warning; `NAO_LOCALIZADO` → danger.
- Pendências e campanhas: `text-bg-warning` pendente, `text-bg-success` concluída, `text-bg-info` em andamento, `text-bg-secondary` descartada.
- A classe do estado é o próprio nome do enum (`th:classappend="${p.situacao.name()}"`): o texto da badge é sempre o rótulo legível.

**Chip de tombo (`.chip-tombo`).** Estilo `tombo` (mono 13px/600) sobre `bg-muted`, `radius-xs`; usado para tombo, ID, ano e qualquer código. Sem tombo, mostre `—` ou `#id`.

**Contagem.** `badge rounded-pill text-bg-danger badge-plain` ao lado do item do menu e no sino (máximo "99+").

**Filtro ativo (`.filter-chip`).** `chip-label` com o nome do filtro, valor em negrito e `bi-x-lg` para remover; agrupados em `.filter-chips` abaixo da toolbar.

**Regra.** Estado nunca se comunica só por cor: o texto da badge diz o estado, e a mesma informação aparece na coluna/tabela quando exportada.

Botões do Bootstrap 5 reestilizados: `body-medium` (14px, peso 500), `radius-md`, ícone `bi-*` à esquerda com `space-2` de gap, foco com `shadow-focus`.

**Variantes e quando usar.**
- `btn-primary` (`primary-600`, hover `primary-700`): a única ação principal da tela: salvar, patrimoniar, registrar, concluir.
- `btn-secondary` (`bg-surface` + `border`): ações de apoio: exportar, voltar, filtrar, ver detalhes.
- `btn-outline-secondary`: reverter algo (desfazer, reabrir).
- `btn-outline-danger` / `btn-danger`: destrutivas (excluir, descartar, dar baixa); sempre com confirmação via `data-confirm` no `<form>`.
- `btn-warning` só para reprovar; `btn-info` para "continuar" algo em andamento.

**Tamanhos.** Padrão em headers e formulários; `btn-sm` em linhas de tabela e cards; `btn-xs` só em chips/filtros. `btn-icon` (quadrado 36px, `btn-sm` 30px) para ícone sozinho, sempre com `title`.

**Grupo de decisão.** No checklist do levantamento as três respostas são `input.btn-check` + `label.btn-outline-*` dentro de `.btn-group.w-100.lev-form`; a opção marcada ganha o fundo cheio do seu estado (`success-fg`, `warning-fg`, `danger-fg`) com texto branco.

**Regras.** Rótulo no infinitivo, sem ponto final. Estado desabilitado durante submissão com o texto em andamento ("Salvando…"). Botões de uma mesma linha ficam em `d-flex gap-2`; o primário sempre por último (à direita).

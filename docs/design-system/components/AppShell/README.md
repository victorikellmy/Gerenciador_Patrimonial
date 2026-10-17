Casca de toda página autenticada: sidebar fixa à esquerda com a marca, grupos de navegação e o usuário, mais uma topbar fina com breadcrumb, sino de pendências, alternância de tema e menu do usuário. Renderização estática do `fragments/layout.html` do Gerenciador Patrimonial.

**Quando usar.** Em todas as telas internas. A tela de login fica fora do shell.

**O consumidor fornece.** O nome do módulo no brand (`.sidebar-brand-title`: "Patrimônio", "Almoxarifado", "Financeiro"), os grupos e links de navegação (ícone `bi-*` + rótulo; badge opcional com `badge-plain` à direita), o usuário autenticado (inicial no `.nav-avatar`, login e perfil) e, no `<head>`, o snippet que aplica `data-theme` e `sidebar-collapsed` a partir do `localStorage` antes do primeiro paint.

**Regras.**
- Larguras: `sidebar-w` 260px, recolhida `sidebar-w-collapsed` 76px, `topbar-h` 60px. Abaixo de 992px a sidebar vira drawer com backdrop e fecha com Esc.
- Item ativo recebe `.active` (fundo `primary-50`, texto `primary-600`; no escuro, ícone `primary-300`). O script do layout marca o ativo pelo `data-nav-match` mais longo que casa com a rota.
- Logo colorido no claro, branco no escuro: os dois `<img>` ficam no HTML e o CSS mostra um por tema.
- O breadcrumb é derivado automaticamente do link ativo (grupo › página): não escreva breadcrumb à mão.
- Grupos de navegação em `section-label` (caixa alta, 0.08em); no máximo 4 grupos e 7 links por grupo.

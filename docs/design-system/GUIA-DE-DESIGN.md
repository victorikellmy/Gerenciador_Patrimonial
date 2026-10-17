# FPTO Design System — Guia de design

Gerado em 14/10/2026 a partir do Gerenciador Patrimonial (app.css, layout.html, ui-kit.html). Versão navegável com previews: https://claude.ai/artifact/8gySx69BGPvacdtYciRDq8

Conteúdo desta pasta: `tokens.json` (tokens), `tokens-e-componentes.app.css` (CSS pronto para copiar), `components/` (guia + preview HTML de cada componente), `assets/Logos/` (logos oficiais).

Sistema de design dos sistemas internos da **Fundação Pró-Tocantins (FPTO)**: Gerenciador Patrimonial, Almoxarifado/Compras, Financeiro FA-SAUDE e os apps móveis que os acompanham. A referência é o CSS do Gerenciador Patrimonial (`static/css/app.css`): os mesmos nomes de token funcionam em qualquer projeto que copie o `tokens.css` deste sistema.

## Princípios

- **Ferramenta de trabalho, não vitrine.** As telas são operadas o dia todo por fiscais, almoxarifes e o financeiro. Densidade média, 14px de corpo, hierarquia clara, nada de hero.
- **Um azul.** `primary-600` é a única cor de marca na interface. Verde, amarelo, vermelho e ciano são cores de estado (`success`, `warning`, `danger`, `info`) e nunca decoração.
- **Superfícies por borda, não por sombra.** Cards usam `border` + `shadow-1`; a sombra só cresce em hover (`shadow-2`) e em elementos flutuantes (`shadow-3`).
- **Claro e escuro com o mesmo cuidado.** Todo componente é escrito com tokens; o tema é trocado por `data-theme="dark"` no `<html>` e lembrado em `localStorage` (`gp-theme`), com fallback em `prefers-color-scheme`.

## Conteúdo e tom

- Português do Brasil, formal e direto, sem gíria e sem emoji. Trate o usuário por "você" só quando inevitável; prefira frases sem sujeito: "Nenhuma pendência nesta situação."
- Use a terminologia do domínio exatamente como nos sistemas: **patrimônio**, **tombo**, **lotação** (UPM + sala), **responsável**, **baixa**, **movimentação**, **levantamento**, **conferência**, **pendência de patrimoniamento**, **solicitação de pagamento**, **plano de contas**, **centro de custo**, **rateio**, **favorecido**.
- Títulos em frase (só a primeira maiúscula): "Pendências de patrimoniamento", nunca "Pendências De Patrimoniamento". Siglas ficam em caixa alta (UPM, BPM, CIPM, NF, SGD, FA-SAUDE).
- Botões dizem a ação no infinitivo: "Patrimoniar", "Registrar", "Concluir", "Mover". A confirmação repete a consequência: "Concluir o levantamento? Depois disso as conferências não poderão ser alteradas."
- Datas `dd/MM/yyyy` e `dd/MM/yyyy HH:mm`; moeda `R$ 8.367,20` (vírgula decimal, ponto de milhar); IDs e contagens com `.tabular-nums`.
- Mensagens de erro dizem o que aconteceu e o que fazer: "Descreva a avaria encontrada no bem." Toast de sucesso leva título "Sucesso" e some em 4 s; o de erro leva "Atenção" e some em 6 s.

## Cor

- Fundo do app: `bg-page`; cards, sidebar, topbar e inputs: `bg-surface`; áreas rebaixadas (thead, chips, miniaturas vazias): `bg-muted`; hover de linhas e itens: `bg-hover`.
- Texto: `text-primary` para títulos e corpo, `text-secondary` para subtítulos e descrições, `text-muted` para legendas e rótulos de KPI. Sobre `primary-600`/`primary-700`/`primary-900` use `text-inverse`.
- Ação primária: fundo `primary-600`, hover `primary-700`, pressed `primary-800`, texto `text-inverse`. Link: `primary-600`, hover `primary-700`.
- Estados de registro, sempre como badge com fundo `-bg` e texto `-fg` do mesmo par: ATIVO/OK → `success`; MANUTENCAO/AVARIADO/PENDENTE → `warning`; NAO_LOCALIZADO/erro → `danger`; CAUTELADO/em andamento → `info`; BAIXADO/concluído → `slate-200` + `slate-700`. Estados nunca se distinguem só pela cor: cada badge traz o texto do estado.
- Foco: `border-focus` + `shadow-focus` em todo input e botão.
- No tema escuro os pares `-bg` viram transparências de 15% e os `-fg` clareiam; não invente cores novas por componente.

## Tipografia

- Família `sans` (Inter, hospedada no Google Fonts) para tudo; `mono` (JetBrains Mono) só para o estilo `tombo` (tombos, códigos, anos em chip).
- Escala: `page-title` (um por página), `card-title` para cards e seções, `body` 14px como base, `small` 13px para apoio, `caption` 12px para segunda linha e validações.
- Rótulos em caixa alta sempre com espaçamento de letras: `table-header` (0.06em) e `section-label` (0.08em). Não use caixa alta fora deles.
- Títulos têm `letter-spacing` negativo (-0.01em em h1–h6, -0.02em em `page-title`); nunca centralize títulos de página.

## Layout

- **App shell**: sidebar fixa de `sidebar-w` (260px) com brand, grupos de navegação ("Visão geral", "Operacional", "Análises", "Administração") e rodapé com o usuário; topbar de `topbar-h` (60px) com breadcrumb automático, sino de pendências, alternância de tema e menu do usuário. Em < 992px a sidebar vira drawer com backdrop; em desktop pode recolher para `sidebar-w-collapsed` (76px).
- O conteúdo começa com `.page-header` (título + subtítulo à esquerda, ações à direita, quebrando em coluna no celular) e segue em `.card-panel`. Listagens em `container-fluid`; formulários e detalhes em `container`.
- Espaçamento por `gap`, não por margem solta: `space-2` entre botões, `space-3`/`space-4` dentro de cards, `space-6` entre seções de formulário (`.form-section`, separadas por `border`), `space-7` entre blocos independentes.
- Formulários longos: seções com `.form-section-header` (ícone em `bg-muted`, título `card-title`, descrição `small`), botões de ação em barra sticky no rodapé, resumo lateral em `col-lg-4`.
- Fluxos de campo (conferência, checklist): lista vertical de itens com miniatura à esquerda, botões grandes de decisão em `btn-group` e formulário inline, pensados para celular (toque ≥ 44px, `capture="environment"` para câmera).

## Ícones

- **Bootstrap Icons 1.11.3** (fonte `bi bi-*`), sempre acompanhando um texto ou com `title`/`aria-label`. Ícones de significado fixo: `bi-speedometer2` dashboard, `bi-inbox` pendências, `bi-grid-3x3-gap` patrimônios, `bi-building` lotações/UPM, `bi-person-badge` responsáveis, `bi-clipboard2-check` levantamento, `bi-file-earmark-bar-graph` relatórios, `bi-upload` importação, `bi-people` usuários, `bi-box-seam` patrimoniar/bem, `bi-arrow-left-right` movimentar, `bi-camera` foto, `bi-check-circle` OK, `bi-exclamation-triangle` avaria, `bi-question-circle` não localizado, `bi-geo-alt` local divergente.
- Ícone de seção dentro de um quadrado `bg-muted` com `radius-md` e cor `primary-600`; ícone de KPI dentro do `kpi-icon-*` do mesmo par de estado.
- Nada de emoji na interface.

## Logos

- O logo institucional (coração em `primary-900`, família em verde, "PRÓ" em azul) está em `assets/Logos`. Versão colorida horizontal na sidebar clara e na tela de login; versão branca na sidebar escura (o shell troca automaticamente por `[data-theme="dark"]`); versão vertical como favicon e em telas estreitas; versão preta só em impressos monocromáticos.
- Altura do logo na sidebar: 36px; na tela de login: 56px. Não recolorir, não girar, não aplicar sombra, não colocar sobre `primary-600` (use a versão branca sobre `primary-900`).
- Com a sidebar recolhida o brand vira a marca textual "Patrimônio" ao lado de `bi-box-seam`; cada sistema usa o nome do seu módulo ali ("Almoxarifado", "Financeiro").

## Tabelas e listas

- `.table.table-hover.align-middle` dentro de `.card-panel` com `overflow: hidden`; cabeçalho em `table-header` sobre `bg-muted`; tombo na primeira coluna como `.chip-tombo`; descrição com `.cell-stack` (`cell-primary` + `cell-secondary`); valores à direita com `.tabular-nums`; situação como badge; ações à direita em `btn-sm` (`btn-icon` para ícone só).
- Vazio: `fragments/ui-kit :: empty-state(icon, title, desc, ctaHref, ctaLabel)`. Carregando: `skeleton-rows`.
- Paginação compacta (`pagination-sm`) centralizada abaixo do card, com "página / total".
- Exportação: toda listagem oferece Excel e PDF (CSV quando fizer sentido) como `btn-secondary`, o PDF como `btn-primary` quando é o principal.

## Movimento

- Transição única `transition` (150ms) em cor, fundo, borda, sombra e largura da sidebar. Toasts entram da direita e saem com opacidade. Nenhuma animação decorativa; respeitar `prefers-reduced-motion`.

## Como usar em outro projeto

1. Copie `tokens.css` (gerado por este sistema) para o projeto e carregue antes do CSS próprio; carregue Inter e JetBrains Mono do Google Fonts.
2. Use Bootstrap 5.3 + Bootstrap Icons 1.11 como base e sobreponha `components/bundle.css` (ou o `app.css` do Gerenciador Patrimonial, que é a mesma coisa) para os componentes.
3. Reaproveite o shell (`fragments/layout.html`) e o ui-kit (`fragments/ui-kit.html`) do Gerenciador Patrimonial; troque apenas o nome do módulo, os grupos de navegação e o favicon.
4. Aplique o tema com o snippet de `<head>` que lê `gp-theme` do `localStorage` antes do primeiro paint, para não piscar.

---

# Tokens

Arquivo completo: `tokens.json`.

## Cores

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `primary-50` | #eaf3fa | #eaf3fa | Fundo de item ativo do menu, ícone de KPI primário, destaque de linha (lev-item.is-target). |
| `primary-100` | #d4e6f2 | #d4e6f2 | Fundos de apoio da cor institucional (hover de chips, tags). |
| `primary-200` | #a9cde5 | #a9cde5 | Bordas e divisores em destaque. |
| `primary-300` | #7eb4d7 | #7eb4d7 | Ícone do item ativo do menu no tema escuro. |
| `primary-400` | #4593c9 | #4593c9 | Foco (border-focus) no tema escuro; links no escuro. |
| `primary-500` | #2776ad | #2776ad | Anel de foco (border-focus) no tema claro; progress-bar. |
| `primary-600` | #175a8a | #175a8a | AZUL INSTITUCIONAL. Botão primário, links, ícones de seção e de página, item ativo do menu. |
| `primary-700` | #0f4269 | #0f4269 | Hover do botão primário e dos links. |
| `primary-800` | #0a2e4a | #0a2e4a | Active/pressed do botão primário. |
| `primary-900` | #071c2e | #071c2e | Fundo da sidebar institucional e de cabeçalhos escuros (também o navy do logo). |
| `slate-50` | #f8fafc | #f8fafc | Fundo de página no tema claro (via bg-page). |
| `slate-100` | #f1f5f9 | #f1f5f9 | Superfície rebaixada e hover no tema claro (bg-muted, bg-hover). |
| `slate-200` | #e2e8f0 | #e2e8f0 | Bordas padrão no claro; fundo da badge BAIXADO. |
| `slate-300` | #cbd5e1 | #cbd5e1 | Borda em hover; texto secundário no escuro. |
| `slate-400` | #94a3b8 | #94a3b8 | Texto muted no escuro; ícones inativos. |
| `slate-500` | #64748b | #64748b | Texto muted no claro (legendas, cell-secondary). |
| `slate-600` | #475569 | #475569 | Texto secundário no claro (subtítulos, descrições de seção). |
| `slate-700` | #334155 | #334155 | Texto da badge BAIXADO; títulos de seção da sidebar. |
| `slate-800` | #1e293b | #1e293b | Fundo de tooltips e do modal escuro. |
| `slate-900` | #0f172a | #0f172a | Texto principal no claro; base das sombras. |
| `success` | #10b981 | #10b981 | Ícone/indicador de sucesso; barra de progresso concluída (bg-success). |
| `success-bg` | #d1fae5 | rgba(16, 185, 129, 0.15) | Fundo das badges ATIVO e OK, do ícone kpi-icon-success e do toast de sucesso. |
| `success-fg` | #065f46 | #6ee7b7 | Texto sobre success-bg (badges ATIVO/OK). 7.9:1 no claro, 9.6:1 no escuro. |
| `warning` | #f59e0b | #f59e0b | Ícone de alerta; botão de aviso. |
| `warning-bg` | #fef3c7 | rgba(245, 158, 11, 0.15) | Fundo das badges MANUTENCAO, AVARIADO e PENDENTE (pendências), ícone kpi-icon-warning. |
| `warning-fg` | #92400e | #fcd34d | Texto sobre warning-bg; descrição de avaria (text-warning-emphasis). |
| `danger` | #ef4444 | #ef4444 | Botão/ação destrutiva (excluir, descartar), badge de contagem no sino. |
| `danger-bg` | #fee2e2 | rgba(239, 68, 68, 0.15) | Fundo da badge NAO_LOCALIZADO, ícone kpi-icon-danger, toast de erro. |
| `danger-fg` | #991b1b | #fca5a5 | Texto sobre danger-bg. |
| `info` | #0ea5e9 | #0ea5e9 | Progresso informativo (levantamento em andamento), botão btn-info. |
| `info-bg` | #e0f2fe | rgba(14, 165, 233, 0.15) | Fundo da badge CAUTELADO, aviso 'encontrado em outro local', kpi-icon-info. |
| `info-fg` | #075985 | #7dd3fc | Texto sobre info-bg. |
| `bg-page` | {slate-50} | #0b1220 | Fundo do body (toda página com shell e a tela de login). |
| `bg-surface` | #ffffff | #111a2e | Cards (card-panel), sidebar, topbar, modal, toasts, inputs. |
| `bg-muted` | {slate-100} | #1a2540 | Fundo de chip-tombo, miniatura sem foto (lev-thumb), thead da tabela, ícone de seção. |
| `bg-hover` | {slate-100} | #1e2a47 | Hover de linhas de tabela, itens de menu e dropdown. |
| `border` | {slate-200} | #233150 | Toda borda padrão: cards, inputs, divisores de seção (form-section), linhas de tabela, itens do checklist. |
| `border-hover` | {slate-300} | #334165 | Borda de input e card em hover. |
| `border-focus` | {primary-500} | {primary-400} | Borda de input focado, junto com shadow-focus. |
| `text-primary` | {slate-900} | #f1f5f9 | Texto principal: títulos, corpo, cell-primary. Sobre bg-page e bg-surface (≥ 15:1). |
| `text-secondary` | {slate-600} | #cbd5e1 | Subtítulos de página, descrição de seção, chip-tombo. Sobre bg-page/bg-surface (7.6:1 claro, 12:1 escuro). |
| `text-muted` | {slate-500} | #94a3b8 | Legendas, cell-secondary, rótulos de KPI, placeholders. Sobre bg-page/bg-surface (4.8:1 claro, 7:1 escuro). |
| `text-inverse` | #ffffff | #ffffff | Texto sobre primary-600/700 (botão primário, item ativo) e sobre primary-900 (sidebar). |

## Tipografia

Famílias: sans = 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; mono = 'JetBrains Mono', 'Courier New', Consolas, monospace

| Estilo | Tamanho | Peso | Uso |
|---|---|---|---|
| `page-title` (sans) | 24px | 700 | H1 único por página, dentro de .page-header. |
| `card-title` (sans) | 15.2px | 600 | Título de card (.card-title) e de seção de formulário (.form-section-title). |
| `empty-state-title` (sans) | 16px | 600 | Título do estado vazio ilustrado. |
| `kpi-value` (sans) | 24px | 700 | Número grande dos cards de KPI do dashboard; sempre com .tabular-nums. |
| `body` (sans) | 14px | 400 | Corpo padrão (body { font-size: 14px }). Tabelas, botões, inputs e dropdowns usam 0.875rem = 14px. |
| `body-medium` (sans) | 14px | 500 | Texto principal de célula (.cell-primary), botões (.btn), item do menu (.sidebar-link). |
| `page-subtitle` (sans) | 14px | 400 | Linha abaixo do título da página, em text-secondary. |
| `small` (sans) | 13px | 400 | Texto de apoio: .toast-desc, .btn-sm, .form-control-sm, paginação, breadcrumb (0.8125rem). |
| `caption` (sans) | 12px | 400 | Segunda linha de célula (.cell-secondary), mensagens de validação, .btn-xs (0.75rem). |
| `form-label` (sans) | 13px | 500 | Rótulo de campo (.form-label). |
| `table-header` (sans) | 11.2px | 600 | Cabeçalho de tabela (.table thead th) e rótulo de resumo (.summary-label): caixa alta. |
| `section-label` (sans) | 10.9px | 700 | Título de grupo do menu lateral (.sidebar-section-title) e perfil do usuário: caixa alta. |
| `kpi-label` (sans) | 12px | 500 | Rótulo acima do número do KPI, em text-muted. |
| `badge` (sans) | 12px | 500 | Badges de situação, resultado e filtros (.badge, .filter-chip). |
| `tombo` (mono) | 13px | 600 | Número de tombo, código, ano, ID: sempre no chip .chip-tombo (bg-muted / text-secondary). |

## spacing

| Token | Valor | Uso |
|---|---|---|
| `space-1` | 4px | Gap entre ícone e texto em botões pequenos; padding de .btn-icon.btn-sm. |
| `space-2` | 8px | Gap padrão entre botões de uma mesma linha; padding horizontal de badges. |
| `space-3` | 12px | Gap entre colunas de um item de lista (lev-item); padding interno de inputs. |
| `space-4` | 16px | Padding horizontal de cards e seções; gutter mínimo lateral em qualquer largura. |
| `space-5` | 20px | Padding superior do main abaixo da topbar. |
| `space-6` | 24px | Padding vertical de uma .form-section; espaço entre page-header e conteúdo. |
| `space-7` | 32px | Separação entre blocos independentes (grupos de UPM no guia). |
| `space-8` | 48px | Padding vertical do estado vazio; respiro no fim da página (pb-5). |

## radius

| Token | Valor | Uso |
|---|---|---|
| `radius-xs` | 4px | Chips de tombo e badges retangulares. |
| `radius-sm` | 6px | Inputs, botões pequenos, itens de dropdown. |
| `radius-md` | 10px | Botões padrão, miniaturas de foto (lev-thumb), ícones de KPI. |
| `radius-lg` | 14px | Cards (.card-panel), modais, toasts. |
| `radius-xl` | 20px | Cartão de login e painéis de destaque. |
| `radius-full` | 9999px | Avatares (.nav-avatar), badges pill, botões de ícone da topbar. |

## shadow

| Token | Valor | Uso |
|---|---|---|
| `shadow-1` | 0 1px 2px rgba(15, 23, 42, 0.04), 0 1px 3px rgba(15, 23, 42, 0.06) | Cards em repouso (.card-panel). |
| `shadow-2` | 0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -2px rgba(15, 23, 42, 0.06) | Card em hover (.card-hover), dropdowns, botão primário em hover. |
| `shadow-3` | 0 10px 15px -3px rgba(15, 23, 42, 0.10), 0 4px 6px -4px rgba(15, 23, 42, 0.08) | Modais, toasts, drawer da sidebar no mobile. |
| `shadow-focus` | 0 0 0 3px rgba(23, 90, 138, 0.18) | Anel de foco de inputs e botões (junto com border-focus). |

## layout

| Token | Valor | Uso |
|---|---|---|
| `sidebar-w` | 260px | Largura da sidebar expandida. |
| `sidebar-w-collapsed` | 76px | Largura da sidebar recolhida (só ícones); estado salvo em localStorage 'gp-sidebar'. |
| `topbar-h` | 60px | Altura da topbar (breadcrumb, sino, tema, usuário). |

## effects

| Token | Valor | Uso |
|---|---|---|
| `transition` | 150ms cubic-bezier(0.4, 0, 0.2, 1) | transition: <propriedade> var(--transition). Respeitar prefers-reduced-motion. |

---

# Componentes

## AppShell

Casca de toda página autenticada: sidebar fixa à esquerda com a marca, grupos de navegação e o usuário, mais uma topbar fina com breadcrumb, sino de pendências, alternância de tema e menu do usuário. Renderização estática do `fragments/layout.html` do Gerenciador Patrimonial.

**Quando usar.** Em todas as telas internas. A tela de login fica fora do shell.

**O consumidor fornece.** O nome do módulo no brand (`.sidebar-brand-title`: "Patrimônio", "Almoxarifado", "Financeiro"), os grupos e links de navegação (ícone `bi-*` + rótulo; badge opcional com `badge-plain` à direita), o usuário autenticado (inicial no `.nav-avatar`, login e perfil) e, no `<head>`, o snippet que aplica `data-theme` e `sidebar-collapsed` a partir do `localStorage` antes do primeiro paint.

**Regras.**
- Larguras: `sidebar-w` 260px, recolhida `sidebar-w-collapsed` 76px, `topbar-h` 60px. Abaixo de 992px a sidebar vira drawer com backdrop e fecha com Esc.
- Item ativo recebe `.active` (fundo `primary-50`, texto `primary-600`; no escuro, ícone `primary-300`). O script do layout marca o ativo pelo `data-nav-match` mais longo que casa com a rota.
- Logo colorido no claro, branco no escuro: os dois `<img>` ficam no HTML e o CSS mostra um por tema.
- O breadcrumb é derivado automaticamente do link ativo (grupo › página): não escreva breadcrumb à mão.
- Grupos de navegação em `section-label` (caixa alta, 0.08em); no máximo 4 grupos e 7 links por grupo.


## PageHeader

Cabeçalho de página: um `page-title` (único h1), um `page-subtitle` em `text-secondary` e as ações à direita. Em telas estreitas as duas metades empilham e as ações quebram linha.

**O consumidor fornece.** Título em frase, subtítulo com os números que importam (contagem, datas, autor), ações como `.btn` (a principal em `btn-primary`, exportações em `btn-secondary`), opcionalmente o botão de voltar (`btn-secondary btn-icon` com `bi-arrow-left`) e uma badge de situação ao lado do título (`badge-plain` + par de estado).

**Regras.**
- No máximo uma ação `btn-primary`; destrutivas ficam fora do header (no card de ações ou em dropdown).
- O subtítulo descreve o conjunto da página ("1.284 bens ativos em 16 unidades"), não repete o título.
- Ícone no título só em relatórios (`bi-list-ul` em `primary-600`); nas demais páginas o ícone fica no menu.


## Card

Superfície padrão de conteúdo: `.card.card-panel` com fundo `bg-surface`, borda `border`, `radius-lg` e `shadow-1`. Dentro, o conteúdo se organiza em `.form-section` (padding `space-6` vertical, `space-4` horizontal, divididas por `border`), cada uma aberta por um `.form-section-header` com ícone em `bg-muted`.

**O consumidor fornece.** As seções com ícone `bi-*`, título (`card-title`) e descrição opcional (`small` em `text-secondary`); o conteúdo (dl, tabela, formulário). Use `fragments/ui-kit :: section-header(icon, title, desc)` no Thymeleaf.

**Variações.** `card-hover` para cards clicáveis (sobe para `shadow-2` e `border-hover`); `style="overflow:hidden"` quando a última coisa dentro é uma tabela, para a borda arredondada cortar a tabela.

**Regras.**
- Cards nunca ficam dentro de cards. Para uma coluna lateral (resumo, andamento, ações) use `col-lg-4` com outro `card-panel`.
- Não adicione barra colorida à esquerda nem sombra forte: hierarquia vem do título e do espaçamento.
- Listas de itens com miniatura (checklist do levantamento) usam `.lev-item` dentro do card, separadas por `border`, sem card individual por item.


## Button

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


## FormField

Campos de formulário do Bootstrap 5 com o tema FPTO: `form-label` 13px/500, `form-control`/`form-select` 14px com `radius-sm`, borda `border` (hover `border-hover`), foco `border-focus` + `shadow-focus`, fundo `bg-surface`.

**O consumidor fornece.** `label` ligado por `for`/`id` em todo campo; obrigatório marcado com `<span class="text-danger">*</span>`; mensagens de validação em `.invalid-feedback` (12px) com a classe `is-invalid` no campo; `placeholder` só como exemplo ("Ex.: ...").

**Regras.**
- Grade `row g-3`; campos curtos em `col-sm-4`, texto livre em `col-12`. Botões do formulário à direita em `d-flex justify-content-end gap-2`, Cancelar antes de Salvar.
- Valores monetários em `<input type="number" step="0.01">` com ponto decimal (padrão dos projetos; a formatação `R$ 8.367,20` é só na exibição). Datas em `<input type="date">`.
- Foto em campo: `<input type="file" accept="image/*" capture="environment">` abre a câmera traseira no celular.
- `form-control-sm`/`form-select-sm` (13px) só em formulários inline dentro de listas (checklist, filtros).
- Select com opção neutra explícita ("Manter como está", "Todas") em vez de vazio sem texto.
- Em formulários longos, agrupe em `.form-section` (ver Card) e deixe as ações numa barra sticky no rodapé (`.sticky-actions`).


## Badge

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


## Table

Listagem padrão: `.table.table-hover.align-middle.mb-0` dentro de um `.card-panel` com `overflow:hidden`, cabeçalho em `table-header` (11px caixa alta) sobre `bg-muted`, linhas separadas por `border`, hover em `bg-hover`.

**Colunas, nesta ordem quando existirem.** Tombo/ID como `.chip-tombo`; descrição em `.cell-stack` (`cell-primary` + `cell-secondary` com categoria, responsável ou ID); lotação em `.cell-stack` (sala em cima, UPM embaixo); valores à direita com `.tabular-nums` e formato `1.234,56`; datas `dd/MM/yyyy`; situação como badge; ações à direita em `d-inline-flex gap-1` com `btn-sm btn-secondary btn-icon` (uma ação principal pode ser `btn-sm btn-primary` com texto, como "Patrimoniar").

**Regras.**
- `table-responsive` sempre; em telas < 768px o CSS reorganiza cada linha como cartão (`.table-card-mobile`, seção 21 do app.css) quando a tabela tem `data-label` nas células.
- Vazio: substitua a tabela pelo `empty-state` do ui-kit; carregando: `skeleton-rows(cols, rows)`.
- Nada de zebra, nem bordas verticais, nem texto centralizado fora de ícones.
- Filtros acima da tabela em `.toolbar` com os chips ativos em `.filter-chips`; paginação abaixo do card.
- Em relatórios use `container-fluid` para a tabela respirar.


## EmptyState

Estado vazio ilustrado: ícone `bi-*` grande em `text-muted` dentro de um círculo `bg-muted`, título `empty-state-title`, descrição `small` e, opcionalmente, uma ação `btn-primary`. No Thymeleaf: `~{fragments/ui-kit :: empty-state(icon, title, desc, ctaHref, ctaLabel)}`.

**O consumidor fornece.** O ícone do assunto (o mesmo do menu: `bi-inbox` pendências, `bi-clipboard2-check` levantamento, `bi-grid-3x3-gap` patrimônios), um título curto em frase, uma descrição que diga por que está vazio e o que fazer, e a ação só quando o usuário pode criar o primeiro registro dali.

**Regras.**
- Fica dentro do mesmo `card-panel` que a tabela ocuparia, nunca solto na página.
- Texto diferente para "sem filtro" e "com filtro": "Nenhum levantamento registrado" versus "Não há pendências nesta situação".
- Sem ilustração, mascote ou emoji: só o ícone.


## Toast

Feedback de ação: toasts no canto superior direito para o resultado de um POST (flash `sucesso` / `erro`) e alertas de contexto no topo da página para situações persistentes (levantamento em andamento, bens a patrimoniar).

**Toast (`.toast-modern`).** Fundo `bg-surface`, `radius-lg`, `shadow-3`, ícone num círculo do par de estado (`toast-success` → `bi-check-lg` em success; `toast-danger` → `bi-exclamation-triangle` em danger). Título fixo: "Sucesso" ou "Atenção"; descrição com a frase do serviço. Some sozinho em 4 s (sucesso) ou 6 s (erro) e tem botão fechar. No Thymeleaf: `~{fragments/layout :: messages}` lê `sucesso`/`erro` dos flash attributes.

**Alerta de contexto (`.alert`).** `alert-info` para algo em andamento, `alert-warning` para algo que exige ação (pendências). Ícone à esquerda, texto com os números, barra de progresso fina quando há andamento e um `btn-sm` da mesma cor à direita. Só no dashboard e na página do assunto; nunca mais de dois alertas por página.

**Regras.**
- Um toast por ação; a mensagem diz o que aconteceu e o número do registro ("Patrimônio criado (ID 42). Pendência #7 concluída.").
- Erros de validação de campo ficam no campo (`invalid-feedback`), não em toast.
- Confirmações usam o modal `showConfirm(title, msg, variant)` do layout, acionado por `data-confirm` no `<form>`; `confirm()` nativo não é usado.


## KpiCard

Card de indicador: ícone num quadrado `kpi-icon-*` (par de estado: primary, success, warning, info, danger), rótulo `kpi-label` em `text-muted`, número `kpi-value` (24px/700, `.tabular-nums`) e uma linha `kpi-delta` com badges ou texto de apoio. Usado no dashboard e no topo do guia de levantamento.

**O consumidor fornece.** Ícone do assunto, rótulo curto, o número já formatado (`1.312`, `R$ 2.418.930,55` com o `R$` em `.kpi-currency`), e a linha de apoio: badges de situação, variação, ou uma `progress` fina (6px) quando o indicador é um andamento.

**Regras.**
- Quatro cards por linha em `col-xl-3 col-md-6`; no guia, um card largo de progresso (`col-xl-4`) seguido de quatro estreitos (`col-xl-2 col-6`).
- A cor do ícone segue o significado: primary para totais, success para OK/valores, warning para avarias/manutenção, danger para não localizados/erros, info para "em outro local"/em andamento.
- O número é a única coisa grande no card; nada de ícones gigantes nem gradientes.
- `card-hover` só quando o card leva a uma listagem filtrada.


## Pagination

Navegação de listagens: filtro rápido por `btn-group` de `btn-sm` (o ativo em `btn-primary`, os demais em `btn-secondary`, contagem opcional em `badge text-bg-light`) no `page-header`, e paginação `pagination-sm` abaixo do card da tabela.

**Paginação.** Três itens: anterior, "página / total" como item desabilitado, próxima. Centralizada abaixo do card (`justify-content-center`), só aparece quando há mais de uma página. Os links preservam os filtros ativos na query string.

**Filtro por botões.** Para situações mutuamente exclusivas (Pendentes / Concluídas / Todas; Pendentes / Todos no checklist). Para filtros combináveis use a toolbar com selects e os `filter-chip`.

**Regras.** Tamanho de página padrão 20 (25 na API); nunca mais de 50 por página em telas. Não use paginação numérica longa (1 2 3 … 40): a busca por filtro substitui o pulo de página.

---

# Logos


Arquivos PNG com fundo transparente, copiados de `static/images/` do Gerenciador Patrimonial.

| Arquivo | Uso |
|---|---|
| `logo-color.png` (1926×956) | Horizontal colorido: sidebar no tema claro, tela de login, cabeçalho de PDFs e termos. |
| `logo-white.png` (2759×1370) | Horizontal branco: sidebar no tema escuro e qualquer fundo `primary-900`/escuro. |
| `logo-black.png` (2759×1370) | Horizontal preto: impressos monocromáticos e carimbos. |
| `logo-color-vert.png` (1082×1809) | Vertical colorido: favicon, apple-touch-icon, ícone do app Android, telas estreitas. |

Cores do mark: coração e "TOCANTINS" em navy (próximo de `primary-900`), família em verde institucional, "PRÓ" em azul (próximo de `primary-500`). Mínimo de 24px de altura; área de respiro de metade da altura do coração em volta. Não recolorir, não aplicar sombra, não esticar.

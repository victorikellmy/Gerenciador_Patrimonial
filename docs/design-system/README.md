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

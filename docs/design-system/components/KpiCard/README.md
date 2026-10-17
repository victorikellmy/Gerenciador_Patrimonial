Card de indicador: ícone num quadrado `kpi-icon-*` (par de estado: primary, success, warning, info, danger), rótulo `kpi-label` em `text-muted`, número `kpi-value` (24px/700, `.tabular-nums`) e uma linha `kpi-delta` com badges ou texto de apoio. Usado no dashboard e no topo do guia de levantamento.

**O consumidor fornece.** Ícone do assunto, rótulo curto, o número já formatado (`1.312`, `R$ 2.418.930,55` com o `R$` em `.kpi-currency`), e a linha de apoio: badges de situação, variação, ou uma `progress` fina (6px) quando o indicador é um andamento.

**Regras.**
- Quatro cards por linha em `col-xl-3 col-md-6`; no guia, um card largo de progresso (`col-xl-4`) seguido de quatro estreitos (`col-xl-2 col-6`).
- A cor do ícone segue o significado: primary para totais, success para OK/valores, warning para avarias/manutenção, danger para não localizados/erros, info para "em outro local"/em andamento.
- O número é a única coisa grande no card; nada de ícones gigantes nem gradientes.
- `card-hover` só quando o card leva a uma listagem filtrada.

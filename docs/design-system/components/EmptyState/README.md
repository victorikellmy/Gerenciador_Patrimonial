Estado vazio ilustrado: ícone `bi-*` grande em `text-muted` dentro de um círculo `bg-muted`, título `empty-state-title`, descrição `small` e, opcionalmente, uma ação `btn-primary`. No Thymeleaf: `~{fragments/ui-kit :: empty-state(icon, title, desc, ctaHref, ctaLabel)}`.

**O consumidor fornece.** O ícone do assunto (o mesmo do menu: `bi-inbox` pendências, `bi-clipboard2-check` levantamento, `bi-grid-3x3-gap` patrimônios), um título curto em frase, uma descrição que diga por que está vazio e o que fazer, e a ação só quando o usuário pode criar o primeiro registro dali.

**Regras.**
- Fica dentro do mesmo `card-panel` que a tabela ocuparia, nunca solto na página.
- Texto diferente para "sem filtro" e "com filtro": "Nenhum levantamento registrado" versus "Não há pendências nesta situação".
- Sem ilustração, mascote ou emoji: só o ícone.

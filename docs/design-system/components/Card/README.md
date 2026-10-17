Superfície padrão de conteúdo: `.card.card-panel` com fundo `bg-surface`, borda `border`, `radius-lg` e `shadow-1`. Dentro, o conteúdo se organiza em `.form-section` (padding `space-6` vertical, `space-4` horizontal, divididas por `border`), cada uma aberta por um `.form-section-header` com ícone em `bg-muted`.

**O consumidor fornece.** As seções com ícone `bi-*`, título (`card-title`) e descrição opcional (`small` em `text-secondary`); o conteúdo (dl, tabela, formulário). Use `fragments/ui-kit :: section-header(icon, title, desc)` no Thymeleaf.

**Variações.** `card-hover` para cards clicáveis (sobe para `shadow-2` e `border-hover`); `style="overflow:hidden"` quando a última coisa dentro é uma tabela, para a borda arredondada cortar a tabela.

**Regras.**
- Cards nunca ficam dentro de cards. Para uma coluna lateral (resumo, andamento, ações) use `col-lg-4` com outro `card-panel`.
- Não adicione barra colorida à esquerda nem sombra forte: hierarquia vem do título e do espaçamento.
- Listas de itens com miniatura (checklist do levantamento) usam `.lev-item` dentro do card, separadas por `border`, sem card individual por item.

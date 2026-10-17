Cabeçalho de página: um `page-title` (único h1), um `page-subtitle` em `text-secondary` e as ações à direita. Em telas estreitas as duas metades empilham e as ações quebram linha.

**O consumidor fornece.** Título em frase, subtítulo com os números que importam (contagem, datas, autor), ações como `.btn` (a principal em `btn-primary`, exportações em `btn-secondary`), opcionalmente o botão de voltar (`btn-secondary btn-icon` com `bi-arrow-left`) e uma badge de situação ao lado do título (`badge-plain` + par de estado).

**Regras.**
- No máximo uma ação `btn-primary`; destrutivas ficam fora do header (no card de ações ou em dropdown).
- O subtítulo descreve o conjunto da página ("1.284 bens ativos em 16 unidades"), não repete o título.
- Ícone no título só em relatórios (`bi-list-ul` em `primary-600`); nas demais páginas o ícone fica no menu.

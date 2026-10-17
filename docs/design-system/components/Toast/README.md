Feedback de ação: toasts no canto superior direito para o resultado de um POST (flash `sucesso` / `erro`) e alertas de contexto no topo da página para situações persistentes (levantamento em andamento, bens a patrimoniar).

**Toast (`.toast-modern`).** Fundo `bg-surface`, `radius-lg`, `shadow-3`, ícone num círculo do par de estado (`toast-success` → `bi-check-lg` em success; `toast-danger` → `bi-exclamation-triangle` em danger). Título fixo: "Sucesso" ou "Atenção"; descrição com a frase do serviço. Some sozinho em 4 s (sucesso) ou 6 s (erro) e tem botão fechar. No Thymeleaf: `~{fragments/layout :: messages}` lê `sucesso`/`erro` dos flash attributes.

**Alerta de contexto (`.alert`).** `alert-info` para algo em andamento, `alert-warning` para algo que exige ação (pendências). Ícone à esquerda, texto com os números, barra de progresso fina quando há andamento e um `btn-sm` da mesma cor à direita. Só no dashboard e na página do assunto; nunca mais de dois alertas por página.

**Regras.**
- Um toast por ação; a mensagem diz o que aconteceu e o número do registro ("Patrimônio criado (ID 42). Pendência #7 concluída.").
- Erros de validação de campo ficam no campo (`invalid-feedback`), não em toast.
- Confirmações usam o modal `showConfirm(title, msg, variant)` do layout, acionado por `data-confirm` no `<form>`; `confirm()` nativo não é usado.

Campos de formulário do Bootstrap 5 com o tema FPTO: `form-label` 13px/500, `form-control`/`form-select` 14px com `radius-sm`, borda `border` (hover `border-hover`), foco `border-focus` + `shadow-focus`, fundo `bg-surface`.

**O consumidor fornece.** `label` ligado por `for`/`id` em todo campo; obrigatório marcado com `<span class="text-danger">*</span>`; mensagens de validação em `.invalid-feedback` (12px) com a classe `is-invalid` no campo; `placeholder` só como exemplo ("Ex.: ...").

**Regras.**
- Grade `row g-3`; campos curtos em `col-sm-4`, texto livre em `col-12`. Botões do formulário à direita em `d-flex justify-content-end gap-2`, Cancelar antes de Salvar.
- Valores monetários em `<input type="number" step="0.01">` com ponto decimal (padrão dos projetos; a formatação `R$ 8.367,20` é só na exibição). Datas em `<input type="date">`.
- Foto em campo: `<input type="file" accept="image/*" capture="environment">` abre a câmera traseira no celular.
- `form-control-sm`/`form-select-sm` (13px) só em formulários inline dentro de listas (checklist, filtros).
- Select com opção neutra explícita ("Manter como está", "Todas") em vez de vazio sem texto.
- Em formulários longos, agrupe em `.form-section` (ver Card) e deixe as ações numa barra sticky no rodapé (`.sticky-actions`).

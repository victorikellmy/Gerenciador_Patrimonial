package com.fundacao.gerenciador_patrimonial.domain.diff;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Resultado da comparação entre o estado anterior e o novo de uma entidade.
 *
 * <p>Usado pela trilha de auditoria para registrar apenas o que mudou —
 * substitui a antiga concatenação manual de {@link StringBuilder}.</p>
 *
 * <p><b>PostgreSQL:</b> a comparação de {@link BigDecimal} usa {@code compareTo}
 * em vez de {@code equals} de propósito. Colunas {@code NUMERIC(19,2)} sempre
 * retornam valores com {@code scale=2}; o request JSON pode vir com {@code scale=0}.
 * {@code equals} considera {@code 100} ≠ {@code 100.00} (falso positivo no diff).</p>
 */
public final class DiffPatrimonio {

    private static final String VAZIO = "—";

    private final List<String> mudancas;

    private DiffPatrimonio(List<String> mudancas) {
        this.mudancas = mudancas;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isVazio() {
        return mudancas.isEmpty();
    }

    /** Texto pronto para gravar no log de auditoria. */
    public String descricao() {
        return mudancas.isEmpty()
                ? "Salvar sem alterações detectadas"
                : String.join("; ", mudancas);
    }

    public static final class Builder {

        private final List<String> mudancas = new ArrayList<>();

        public Builder compara(String campo, Object antes, Object depois) {
            if (!iguais(antes, depois)) {
                mudancas.add("%s: %s → %s".formatted(campo, format(antes), format(depois)));
            }
            return this;
        }

        public DiffPatrimonio build() {
            return new DiffPatrimonio(List.copyOf(mudancas));
        }

        private static boolean iguais(Object a, Object b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a instanceof BigDecimal ba && b instanceof BigDecimal bb) {
                return ba.compareTo(bb) == 0;
            }
            return a.equals(b);
        }

        private static String format(Object v) {
            return v == null ? VAZIO : v.toString();
        }
    }
}

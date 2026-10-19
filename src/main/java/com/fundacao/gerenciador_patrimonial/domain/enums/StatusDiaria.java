package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.util.Set;

/**
 * Fluxo da diária (herdado do Transportes FPTO):
 * <pre>
 *   SOLICITADO  → (ADMINISTRADOR confirma)             → CONFIRMADO
 *   CONFIRMADO  → (PRESIDENTE defere / indefere)        → DEFERIDO | INDEFERIDO
 *   DEFERIDO    → (FINANCEIRO registra o pagamento)     → PAGO
 * </pre>
 * PAGO é terminal: não se edita nem se exclui uma diária paga.
 */
public enum StatusDiaria {
    SOLICITADO("Solicitada", "text-bg-secondary"),
    CONFIRMADO("Confirmada", "text-bg-warning"),
    DEFERIDO("Deferida", "text-bg-info"),
    INDEFERIDO("Indeferida", "text-bg-dark"),
    PAGO("Paga", "text-bg-success");

    private final String rotulo;
    private final String classeBadge;

    StatusDiaria(String rotulo, String classeBadge) {
        this.rotulo = rotulo;
        this.classeBadge = classeBadge;
    }

    public String getRotulo() { return rotulo; }
    public String getClasseBadge() { return classeBadge; }

    public Set<StatusDiaria> proximos() {
        return switch (this) {
            case SOLICITADO -> Set.of(CONFIRMADO);
            case CONFIRMADO -> Set.of(DEFERIDO, INDEFERIDO);
            case DEFERIDO -> Set.of(PAGO);
            case INDEFERIDO, PAGO -> Set.of();
        };
    }

    public boolean podeIr(StatusDiaria destino) { return proximos().contains(destino); }

    /**
     * Pode ser alterada enquanto não foi paga. Alterar uma diária que já passou da solicitação
     * (confirmada, deferida ou indeferida) a devolve para SOLICITADO (ver DiariaService.atualizar).
     */
    public boolean isEditavel() { return this != PAGO; }

    /** Texto do banco antigo ("SOLICITADO", "PAGO", ...). */
    public static StatusDiaria de(String texto) {
        if (texto == null) return SOLICITADO;
        String t = texto.trim().toUpperCase();
        for (StatusDiaria s : values()) if (s.name().equals(t)) return s;
        return SOLICITADO;
    }
}

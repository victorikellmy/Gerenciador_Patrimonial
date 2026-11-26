package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.util.Set;

/**
 * Fluxo da diária:
 * <pre>
 *   SOLICITADO  → (PRESIDENTE defere / indefere)        → DEFERIDO | INDEFERIDO
 *   DEFERIDO    → (FINANCEIRO registra o pagamento)     → PAGO
 * </pre>
 * PAGO é terminal: não se edita nem se exclui uma diária paga.
 *
 * <p>CONFIRMADO é a antiga etapa de confirmação pelo administrador, extinta em 26/11/2026: nenhuma
 * transição leva a ela e a migration V17 devolveu as diárias que estavam nela para SOLICITADO.
 * Continua no enum porque o histórico (diaria_historico) e o banco do Transportes ainda a citam
 * (o importador converte a situação atual para SOLICITADO); se alguma aparecer, o Presidente
 * decide como se fosse solicitada.</p>
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
            case SOLICITADO, CONFIRMADO -> Set.of(DEFERIDO, INDEFERIDO);
            case DEFERIDO -> Set.of(PAGO);
            case INDEFERIDO, PAGO -> Set.of();
        };
    }

    public boolean podeIr(StatusDiaria destino) { return proximos().contains(destino); }

    /**
     * Pode ser alterada enquanto não foi paga. Alterar uma diária que já passou da solicitação
     * (deferida ou indeferida) a devolve para SOLICITADO (ver DiariaService.atualizar).
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

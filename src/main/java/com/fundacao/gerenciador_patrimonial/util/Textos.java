package com.fundacao.gerenciador_patrimonial.util;

/**
 * Utilitários de String — fonte única de {@code nullIfBlank}/{@code truncar},
 * antes duplicados em services, entidades e controllers.
 */
public final class Textos {

    private Textos() {}

    /** Trim; retorna {@code null} para nulo/vazio/apenas espaços. */
    public static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Trunca ao limite da coluna preservando {@code null}. */
    public static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

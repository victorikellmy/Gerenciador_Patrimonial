package com.fundacao.gerenciador_patrimonial.util;

/**
 * Limites de paginação das telas/API.
 *
 * <p>O tamanho de página é configurável pelo usuário ({@code ?size=}), mas
 * precisa de teto: sem clamp, {@code ?size=100000} materializaria a tabela
 * inteira em uma única página (memória + tempo de render).</p>
 */
public final class Paginacao {

    public static final int MIN = 5;
    public static final int MAX = 200;

    private Paginacao() {}

    /** Restringe o tamanho de página ao intervalo [{@value MIN}, {@value MAX}]. */
    public static int clampSize(int size) {
        return Math.max(MIN, Math.min(size, MAX));
    }
}

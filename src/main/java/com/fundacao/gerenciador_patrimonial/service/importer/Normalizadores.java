package com.fundacao.gerenciador_patrimonial.service.importer;

import java.util.regex.Pattern;

/**
 * Funções utilitárias para limpar dados vindos da planilha original.
 *
 * <p>Baseado nos problemas de qualidade identificados:
 * <ul>
 *   <li>UPMs com formatação inconsistente ("1BPM" vs "2º BPM ARAGUAINA")</li>
 *   <li>Salas com células corrompidas ("CON+B85:D92S. ODONTO 2")</li>
 *   <li>Typos (AMOX → ALMOX)</li>
 * </ul>
 */
public final class Normalizadores {

    // Patterns pré-compilados: replaceAll/matches de String recompilam a regex
    // a cada chamada, e estes métodos rodam várias vezes por linha importada.
    private static final Pattern ESPACOS            = Pattern.compile("\\s+");
    private static final Pattern NUMERO_SIGLA       = Pattern.compile("^(\\d+)\\s*(BPM|CIPM)");
    private static final Pattern CELULA_CORROMPIDA  = Pattern.compile("CON\\+[A-Z0-9:]+S\\.");
    private static final Pattern SO_ZEROS           = Pattern.compile("0+");

    private Normalizadores() {}

    /**
     * Normaliza UPM:
     * <ul>
     *   <li>Remove ordinais (º, °)</li>
     *   <li>Padroniza espaços</li>
     *   <li>Insere espaço entre número e sigla ("1BPM" → "1 BPM")</li>
     * </ul>
     */
    public static String normalizarUpm(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase()
                .replace("º", "")
                .replace("°", "");
        s = ESPACOS.matcher(s).replaceAll(" ");
        // Insere espaço antes de BPM/CIPM quando colado ao número
        s = NUMERO_SIGLA.matcher(s).replaceAll("$1 $2");
        return s.isEmpty() ? null : s;
    }

    /**
     * Normaliza o nome da sala:
     * <ul>
     *   <li>Corrige células corrompidas por fórmulas do Excel</li>
     *   <li>Corrige typos conhecidos</li>
     *   <li>Uppercase, espaços uniformes</li>
     * </ul>
     */
    public static String normalizarSala(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase();

        // Célula quebrada: "CON+B85:D92S. ODONTO 2" → "CONS. ODONTO 2"
        s = CELULA_CORROMPIDA.matcher(s).replaceAll("CONS.");

        // Typos conhecidos
        if (s.equals("AMOX")) s = "ALMOX";

        s = ESPACOS.matcher(s).replaceAll(" ");
        return s.isEmpty() ? null : s;
    }

    /** Uppercase + trim, preservando conteúdo. */
    public static String normalizarNome(String raw) {
        if (raw == null) return null;
        String s = ESPACOS.matcher(raw.trim()).replaceAll(" ");
        return s.isEmpty() ? null : s;
    }

    /**
     * Normaliza número de tombo:
     * <ul>
     *   <li>Trim e remoção de espaços internos</li>
     *   <li>Trata {@code "0"}, {@code "00"}, {@code "-"} e vazios como {@code null}
     *       — são placeholders da planilha, não tombos reais</li>
     * </ul>
     * Evita colisões com a constraint UNIQUE de {@code numero_tombo}.
     */
    public static String normalizarTombo(String raw) {
        if (raw == null) return null;
        String s = ESPACOS.matcher(raw.trim()).replaceAll("");
        if (s.isEmpty() || s.equals("-") || SO_ZEROS.matcher(s).matches()) return null;
        return s;
    }
}

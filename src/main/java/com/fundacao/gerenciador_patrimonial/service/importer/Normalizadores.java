package com.fundacao.gerenciador_patrimonial.service.importer;

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
                .replace("°", "")
                .replaceAll("\\s+", " ");
        // Insere espaço antes de BPM/CIPM quando colado ao número
        s = s.replaceAll("^(\\d+)\\s*(BPM|CIPM)", "$1 $2");
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
        s = s.replaceAll("CON\\+[A-Z0-9:]+S\\.", "CONS.");

        // Typos conhecidos
        if (s.equals("AMOX")) s = "ALMOX";

        s = s.replaceAll("\\s+", " ");
        return s.isEmpty() ? null : s;
    }

    /** Uppercase + trim, preservando conteúdo. */
    public static String normalizarNome(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
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
        String s = raw.trim().replaceAll("\\s+", "");
        if (s.isEmpty() || s.equals("-") || s.matches("0+")) return null;
        return s;
    }
}

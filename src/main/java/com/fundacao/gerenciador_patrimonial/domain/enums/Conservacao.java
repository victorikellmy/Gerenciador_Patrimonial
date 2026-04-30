package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Estado físico de conservação do bem.
 *
 * <p>Os valores seguem a tabela de referência da Fundação
 * (ver migração {@code V2__seed_reference_data.sql}), que associa
 * cada estado a um percentual VUD (Vida Útil Decorrida).</p>
 *
 * <p>Valores da planilha original como {@code CAUTELADO} e {@code TECNICO}
 * <b>não</b> são estados de conservação e são mapeados para
 * {@link SituacaoPatrimonio#CAUTELADO} ou
 * {@link SituacaoPatrimonio#EM_APURACAO}.</p>
 */
public enum Conservacao {

    NOVO,
    OTIMO,
    BOM,
    BOM_REGULAR,
    REGULAR,
    REGULAR_RUIM,
    RUIM,
    INSERVIVEL;

    /**
     * Normaliza valores brutos vindos da planilha Excel para uma instância
     * desta enumeração.
     *
     * <p>Lida com variações: acentos, barras, espaços, e os valores "NOVO",
     * "ÓTIMO", "BOM / REGULAR" etc. Retorna {@code null} para valores que
     * <b>não representam conservação</b> (CAUTELADO, TECNICO, em branco),
     * deixando a decisão de situação a cargo do chamador.</p>
     *
     * @param raw texto da célula da planilha (pode ser {@code null})
     * @return a enumeração correspondente ou {@code null} se não for um estado de conservação
     */
    public static Conservacao fromPlanilha(String raw) {
        if (raw == null || raw.isBlank()) return null;

        // Normaliza: upper, remove acentos, padroniza separador "/" com espaços ao redor.
        // Aceita "BOM/REGULAR", "BOM / REGULAR", "REGULAR/RUIM", "REGULAR / RUIM", etc.
        String s = raw.trim().toUpperCase()
                .replace('Ó', 'O').replace('Ã', 'A').replace('Ç', 'C').replace('Í', 'I')
                .replaceAll("\\s*/\\s*", " / ")
                .replaceAll("\\s+", " ");

        return switch (s) {
            case "NOVO"            -> NOVO;
            case "OTIMO"           -> OTIMO;
            case "BOM"             -> BOM;
            case "BOM / REGULAR"   -> BOM_REGULAR;
            case "REGULAR"         -> REGULAR;
            case "REGULAR / RUIM"  -> REGULAR_RUIM;
            case "RUIM"            -> RUIM;
            case "INSERVIVEL"      -> INSERVIVEL;
            default                -> null; // CAUTELADO, TECNICO, etc.
        };
    }
}

package com.fundacao.gerenciador_patrimonial.dto.response;

/**
 * Andamento da conferência em um local (lotação) dentro de um levantamento.
 *
 * @param encontradosAqui bens de OUTROS locais que foram achados neste
 */
public record LocalLevantamentoResumo(
        Long lotacaoId,
        String upm,
        String nome,
        long total,
        long pendentes,
        long ok,
        long avariados,
        long naoLocalizados,
        long encontradosAqui
) {
    public long conferidos() { return total - pendentes; }

    public int percentual() {
        return total == 0 ? 0 : (int) Math.round(100.0 * conferidos() / total);
    }

    public boolean concluido() { return total > 0 && pendentes == 0; }

    public boolean possuiOcorrencias() { return avariados > 0 || naoLocalizados > 0 || encontradosAqui > 0; }
}

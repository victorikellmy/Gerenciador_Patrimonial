package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Situação (ciclo de vida) de um bem patrimonial.
 *
 * <p>Diferente de {@link Conservacao}, que representa estado físico,
 * esta enumeração representa o estado administrativo do bem.</p>
 */
public enum SituacaoPatrimonio {

    /** Em uso regular. */
    ATIVO,

    /** Bem deu baixa (descarte, perda, venda). Não some do DB — soft delete. */
    BAIXADO,

    /** Fora de operação temporariamente (em manutenção). */
    MANUTENCAO,

    /** Emprestado/cautelado a terceiros. */
    CAUTELADO,

    /** Registro oriundo da planilha com dados inconsistentes — requer revisão humana. */
    EM_APURACAO
}

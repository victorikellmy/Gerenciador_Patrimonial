package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Tipo de ação registrada na trilha de auditoria.
 * Mapeia o nome literal do enum para a coluna {@code acao} (máx. 20 caracteres).
 */
public enum AcaoAuditoria {
    CREATE,
    UPDATE,
    DELETE,
    MOVIMENTAR,
    BAIXAR,
    ANEXAR,
    REMOVER_ANEXO,
    LOGIN,
    /** Recebimento de pendência vinda de sistema externo (Almoxarifado). */
    RECEBER_INTEGRACAO,
    /** Bem cadastrado a partir de uma pendência de patrimoniamento. */
    PATRIMONIAR,
    /** Conferência física registrada em um levantamento patrimonial. */
    LEVANTAMENTO
}

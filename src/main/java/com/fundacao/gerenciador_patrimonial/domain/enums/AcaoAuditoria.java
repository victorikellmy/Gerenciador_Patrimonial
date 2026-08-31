package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Tipo de ação registrada na trilha de auditoria.
 * Mapeia o nome literal do enum para a coluna {@code acao}.
 */
public enum AcaoAuditoria {
    CREATE,
    UPDATE,
    DELETE,
    MOVIMENTAR,
    BAIXAR,
    ANEXAR,
    REMOVER_ANEXO,
    LOGIN
}

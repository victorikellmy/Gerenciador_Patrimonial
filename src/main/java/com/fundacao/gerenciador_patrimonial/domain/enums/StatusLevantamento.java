package com.fundacao.gerenciador_patrimonial.domain.enums;

/** Ciclo de vida de um levantamento patrimonial (inventário físico). */
public enum StatusLevantamento {
    /** Em andamento — itens podem ser conferidos. Só um levantamento fica aberto por vez. */
    ABERTO,
    /** Encerrado — itens não conferidos permanecem como PENDENTE para registro histórico. */
    CONCLUIDO
}

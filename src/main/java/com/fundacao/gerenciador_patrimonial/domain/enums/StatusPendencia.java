package com.fundacao.gerenciador_patrimonial.domain.enums;

/** Ciclo de vida de uma pendência de patrimoniamento recebida de sistema externo. */
public enum StatusPendencia {
    /** Recebida e aguardando o cadastro do(s) bem(ns). */
    PENDENTE,
    /** Todos os bens cadastrados (ou concluída manualmente pelo operador). */
    CONCLUIDA,
    /** Descartada por um administrador — não gera patrimônio. */
    DESCARTADA
}

package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Perfis de acesso (RBAC). Serão consumidos pelo Spring Security
 * no formato {@code ROLE_ADMINISTRADOR} / {@code ROLE_FISCAL}.
 */
public enum Perfil {
    /** Acesso total: cadastrar, editar, movimentar, dar baixa, excluir, gerir usuários. */
    ADMINISTRADOR,

    /** Acesso restrito: leitura, auditoria e movimentação; NÃO pode excluir ou gerir usuários. */
    FISCAL
}

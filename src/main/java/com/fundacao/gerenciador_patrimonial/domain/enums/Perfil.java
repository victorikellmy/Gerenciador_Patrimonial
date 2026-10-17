package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Perfis de acesso (RBAC). Consumidos pelo Spring Security no formato
 * {@code ROLE_ADMINISTRADOR} / {@code ROLE_FISCAL} / {@code ROLE_MOTORISTA} / {@code ROLE_INTEGRACAO}.
 */
public enum Perfil {
    /** Acesso total: cadastrar, editar, movimentar, dar baixa, excluir, gerir usuários. */
    ADMINISTRADOR,

    /** Acesso restrito: leitura, auditoria e movimentação; NÃO pode excluir ou gerir usuários. */
    FISCAL,

    /**
     * Motorista da frota: só enxerga o módulo Frota ({@code /frota/**}) para
     * registrar abastecimentos, manutenções e deslocamentos dos veículos.
     */
    MOTORISTA,

    /**
     * Usuário técnico de sistema externo (ex.: Almoxarifado). Só acessa
     * {@code /api/integracao/**} via HTTP Basic; sem acesso à interface web.
     */
    INTEGRACAO
}

package com.fundacao.gerenciador_patrimonial.domain.enums;

/**
 * Perfis de acesso (RBAC). Consumidos pelo Spring Security no formato
 * {@code ROLE_<PERFIL>}.
 */
public enum Perfil {
    /** Acesso total: cadastrar, editar, movimentar, dar baixa, excluir, gerir usuários, confirmar diárias. */
    ADMINISTRADOR,

    /** Operação (coordenação): leitura, movimentação, frota, abre e edita diárias; NÃO exclui nem gere usuários. */
    FISCAL,

    /**
     * Gerente: somente leitura. Painel gerencial da frota (gastos, quem está com
     * qual viatura, manutenções, revisões), dashboard e relatórios patrimoniais.
     */
    GERENTE,

    /** Presidente da FPTO: vê as diárias e DEFERE / INDEFERE as confirmadas; relatórios. */
    PRESIDENTE,

    /** Financeiro: vê as diárias deferidas e REGISTRA O PAGAMENTO; relatório de diárias pagas. */
    FINANCEIRO,

    /**
     * Motorista da frota: só enxerga o módulo Frota ({@code /frota/**}) para
     * registrar abastecimentos, manutenções, saídas e cautelas dos veículos.
     */
    MOTORISTA,

    /**
     * Usuário técnico de sistema externo (ex.: Almoxarifado). Só acessa
     * {@code /api/integracao/**} via HTTP Basic; sem acesso à interface web.
     */
    INTEGRACAO
}

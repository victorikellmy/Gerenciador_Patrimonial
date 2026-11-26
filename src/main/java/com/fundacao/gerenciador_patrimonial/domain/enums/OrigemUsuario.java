package com.fundacao.gerenciador_patrimonial.domain.enums;

/** De onde vem a senha (e o perfil) do usuário. */
public enum OrigemUsuario {
    /** Conta do próprio sistema: senha BCrypt na tabela e perfil escolhido no cadastro. */
    LOCAL,
    /** Conta da rede da Fundação (AD FPTO.LOCAL): senha e grupos ficam no AD. */
    DOMINIO
}

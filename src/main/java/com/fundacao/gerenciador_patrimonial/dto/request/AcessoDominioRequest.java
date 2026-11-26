package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;

/**
 * Acesso de uma conta da rede: o sistema grava nos grupos do Patrimônio no AD.
 *
 * @param perfil        nulo ou USUARIO = usuário padrão (tira de todos os grupos de perfil)
 * @param podeAbastecer grupo extra Patrimonio-Abastecer
 */
public record AcessoDominioRequest(Perfil perfil, Boolean podeAbastecer) {}

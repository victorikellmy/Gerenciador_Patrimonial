package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;

import java.time.LocalDateTime;

/**
 * Projeção de usuário para exibição — NUNCA inclui o hash da senha.
 *
 * @param senhaProvisoria true quando o usuário ainda precisa trocar a senha
 *                        definida pelo administrador (primeiro acesso)
 * @param podeAbastecer   permissão extra para registrar abastecimentos da frota
 */
public record UsuarioResponse(
        Long id,
        String nomeCompleto,
        String login,
        Perfil perfil,
        boolean ativo,
        boolean senhaProvisoria,
        boolean podeAbastecer,
        LocalDateTime criadoEm
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(), u.getNomeCompleto(), u.getLogin(),
                u.getPerfil(), u.isAtivo(), u.isSenhaProvisoria(), u.isPodeAbastecer(), u.getCriadoEm());
    }
}

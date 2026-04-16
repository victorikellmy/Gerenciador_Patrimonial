package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;

import java.time.LocalDateTime;

/**
 * Projeção de usuário para exibição — NUNCA inclui o hash da senha.
 */
public record UsuarioResponse(
        Long id,
        String nomeCompleto,
        String login,
        Perfil perfil,
        boolean ativo,
        LocalDateTime criadoEm
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(), u.getNomeCompleto(), u.getLogin(),
                u.getPerfil(), u.isAtivo(), u.getCriadoEm());
    }
}

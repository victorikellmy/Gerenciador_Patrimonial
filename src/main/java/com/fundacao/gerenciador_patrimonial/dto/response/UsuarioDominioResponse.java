package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.enums.OrigemUsuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;

/**
 * Conta da rede (AD) com o acesso que ela tem no Patrimônio.
 *
 * @param perfil         perfil dado pelos grupos do AD (USUARIO = nenhum grupo)
 * @param usuarioId      cadastro no sistema; nulo se a pessoa nunca entrou nem teve grupo definido
 * @param ativoNoSistema falso quando o administrador desativou a pessoa só no Patrimônio
 * @param origem         origem do cadastro no sistema (LOCAL até o primeiro login pela rede)
 */
public record UsuarioDominioResponse(
        String login,
        String nome,
        String email,
        boolean ativoNoDominio,
        Perfil perfil,
        boolean podeAbastecer,
        Long usuarioId,
        Boolean ativoNoSistema,
        OrigemUsuario origem
) {}

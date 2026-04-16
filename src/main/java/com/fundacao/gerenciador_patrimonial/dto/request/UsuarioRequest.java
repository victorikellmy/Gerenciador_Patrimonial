package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação/edição de usuário.
 *
 * <p>A senha é opcional na edição (mantém a atual se vier vazia).
 * Em criação, o controller/service valida que foi informada.</p>
 */
public record UsuarioRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150)
        String nomeCompleto,

        @NotBlank(message = "Login é obrigatório")
        @Size(min = 3, max = 60)
        String login,

        /** Obrigatório apenas na criação. */
        @Size(min = 6, max = 60, message = "Senha deve ter entre 6 e 60 caracteres")
        String senha,

        @NotNull(message = "Perfil é obrigatório")
        Perfil perfil,

        Boolean ativo
) {}

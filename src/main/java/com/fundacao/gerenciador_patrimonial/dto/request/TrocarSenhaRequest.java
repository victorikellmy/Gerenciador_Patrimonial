package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para troca de senha do próprio usuário autenticado.
 */
public record TrocarSenhaRequest(
        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 6, max = 60, message = "Nova senha deve ter entre 6 e 60 caracteres")
        String novaSenha,

        @NotBlank(message = "Confirmação é obrigatória")
        String confirmacao
) {}

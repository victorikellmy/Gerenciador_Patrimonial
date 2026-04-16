package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para dar baixa em um patrimônio.
 * O motivo é obrigatório por questão de rastreabilidade.
 */
public record BaixaRequest(
        @NotBlank(message = "Motivo da baixa é obrigatório")
        @Size(max = 255)
        String motivo
) {}

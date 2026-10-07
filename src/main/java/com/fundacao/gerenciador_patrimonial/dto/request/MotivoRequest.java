package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo textual de uma ação (ex.: descarte de pendência) na API. */
public record MotivoRequest(
        @NotBlank(message = "Informe o motivo") @Size(max = 200) String motivo
) {}

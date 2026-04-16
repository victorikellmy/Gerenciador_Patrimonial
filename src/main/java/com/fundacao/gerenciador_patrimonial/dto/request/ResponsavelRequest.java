package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponsavelRequest(
        @NotBlank(message = "Nome completo é obrigatório")
        @Size(max = 150)
        String nomeCompleto,

        @Size(max = 50)
        String matricula,

        @Size(max = 80)
        String cidade,

        Long lotacaoId
) {}

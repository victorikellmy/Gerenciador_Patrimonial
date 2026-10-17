package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record NivelDiariaRequest(
        @NotBlank(message = "Descrição é obrigatória") @Size(max = 160) String descricao,
        @NotNull(message = "Informe o valor para capitais") @PositiveOrZero BigDecimal valorCapitais,
        @NotNull(message = "Informe o valor para interior do Estado") @PositiveOrZero BigDecimal valorInteriorEstado,
        @NotNull(message = "Informe o valor para interior de outro Estado") @PositiveOrZero BigDecimal valorInteriorOutroEstado
) {}

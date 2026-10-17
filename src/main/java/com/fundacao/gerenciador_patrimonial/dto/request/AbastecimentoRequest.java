package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AbastecimentoRequest(
        Long veiculoId,

        @NotNull(message = "Data é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate data,

        @PositiveOrZero(message = "Km não pode ser negativo") Integer km,

        @NotNull(message = "Combustível é obrigatório") Combustivel combustivel,

        @Positive(message = "Litros deve ser maior que zero") BigDecimal quantidadeLitros,

        @NotNull(message = "Valor é obrigatório")
        @Positive(message = "Valor deve ser maior que zero")
        BigDecimal valor,

        @Size(max = 160) String postoCidade,

        @NotBlank(message = "Motorista é obrigatório") @Size(max = 160) String motorista,

        @Size(max = 500) String observacao
) {}

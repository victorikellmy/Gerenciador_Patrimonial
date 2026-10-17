package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManutencaoVeiculoRequest(
        Long veiculoId,

        @NotNull(message = "Data é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate data,

        @NotNull(message = "Tipo de serviço é obrigatório") TipoServicoVeiculo servico,

        @PositiveOrZero(message = "Km não pode ser negativo") Integer km,

        @Size(max = 1000) String descricao,

        @PositiveOrZero(message = "Valor não pode ser negativo") BigDecimal valor,

        @Size(max = 160) String fornecedor,

        @Size(max = 60) String notaFiscal,

        @PositiveOrZero(message = "Km da próxima revisão não pode ser negativo") Integer proximaRevisaoKm,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate proximaRevisaoData,

        @NotBlank(message = "Responsável pelo registro é obrigatório") @Size(max = 160) String motorista
) {}

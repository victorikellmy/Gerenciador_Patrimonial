package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeguroVeiculoRequest(
        @NotNull(message = "Selecione o veículo") Long veiculoId,
        @NotBlank(message = "Seguradora é obrigatória") @Size(max = 120) String seguradora,
        @NotBlank(message = "Número da apólice é obrigatório") @Size(max = 60) String numeroApolice,
        @Size(max = 120) String corretor,
        @NotNull(message = "Início da vigência é obrigatório")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicioVigencia,
        @NotNull(message = "Fim da vigência é obrigatório")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fimVigencia,
        @PositiveOrZero(message = "Valor do prêmio não pode ser negativo") BigDecimal valorPremio,
        @PositiveOrZero(message = "Franquia não pode ser negativa") BigDecimal franquia,
        @Size(max = 1000) String coberturas,
        @Size(max = 40) String telefoneAssistencia,
        @Size(max = 500) String observacao
) {}

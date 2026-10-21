package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoSinistro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code seguroId} nulo: o serviço usa a apólice que cobria a data da ocorrência, se houver. */
public record SinistroVeiculoRequest(
        @NotNull(message = "Selecione o veículo") Long veiculoId,
        Long seguroId,
        @NotNull(message = "Data da ocorrência é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataOcorrencia,
        @Size(max = 60) String numeroSinistro,
        @NotBlank(message = "Descreva o que aconteceu") @Size(max = 1000) String descricao,
        @Size(max = 160) String localOcorrencia,
        @Size(max = 160) String condutor,
        @PositiveOrZero(message = "Prejuízo não pode ser negativo") BigDecimal valorPrejuizo,
        @PositiveOrZero(message = "Valor indenizado não pode ser negativo") BigDecimal valorIndenizado,
        SituacaoSinistro situacao,
        @Size(max = 500) String observacao
) {}

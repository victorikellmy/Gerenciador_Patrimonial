package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record DiariaRequest(
        @NotNull(message = "Selecione o profissional") Long profissionalId,

        @NotBlank(message = "Itinerário é obrigatório") @Size(max = 200) String itinerario,

        @NotNull(message = "Data de partida é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate partida,

        @DateTimeFormat(pattern = "HH:mm") LocalTime horaSaida,

        @NotNull(message = "Data de retorno é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate retorno,

        @DateTimeFormat(pattern = "HH:mm") LocalTime horaChegada,

        @NotNull(message = "Selecione o meio de transporte") MeioTransporte meioTransporte,

        @NotNull(message = "Selecione o recurso") RecursoDiaria recurso,

        @NotNull(message = "Informe o número de diárias")
        @Positive(message = "Número de diárias deve ser maior que zero") BigDecimal numeroDiarias,

        @NotNull(message = "Informe o valor unitário")
        @Positive(message = "Valor unitário deve ser maior que zero") BigDecimal valorUnitario,

        @Size(max = 2000) String finalidade
) {}

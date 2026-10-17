package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record DeslocamentoRequest(
        Long veiculoId,

        @NotNull(message = "Data é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataViagem,

        @NotBlank(message = "Destino é obrigatório") @Size(max = 160) String destino,

        @NotBlank(message = "Motivo é obrigatório") @Size(max = 60) String motivo,

        @Size(max = 160) String titular,
        @Size(max = 160) String assistido,

        @DateTimeFormat(pattern = "HH:mm") LocalTime horaSaida,
        @PositiveOrZero(message = "Km inicial não pode ser negativo") Integer kmInicial,
        @DateTimeFormat(pattern = "HH:mm") LocalTime horaChegada,
        @PositiveOrZero(message = "Km final não pode ser negativo") Integer kmFinal,

        @NotBlank(message = "Motorista é obrigatório") @Size(max = 160) String motorista,
        @Size(max = 60) String lotacao,
        @Size(max = 500) String observacao
) {}

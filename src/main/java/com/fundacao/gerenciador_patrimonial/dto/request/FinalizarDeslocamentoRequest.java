package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;

/** Chegada de um deslocamento em andamento. */
public record FinalizarDeslocamentoRequest(
        @DateTimeFormat(pattern = "HH:mm") LocalTime horaChegada,

        @NotNull(message = "Km final é obrigatório")
        @PositiveOrZero(message = "Km final não pode ser negativo")
        Integer kmFinal
) {}

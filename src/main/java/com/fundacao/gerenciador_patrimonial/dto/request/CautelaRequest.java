package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record CautelaRequest(
        Long veiculoId,

        @NotNull(message = "Data é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataViagem,

        @NotBlank(message = "Destino é obrigatório") @Size(max = 160) String destino,
        @NotBlank(message = "Motivo é obrigatório") @Size(max = 60) String motivo,
        @Size(max = 160) String responsavel,
        @Size(max = 160) String assistido,

        @DateTimeFormat(pattern = "HH:mm") LocalTime horaSaida,
        @PositiveOrZero(message = "Km inicial não pode ser negativo") Integer kmInicial,
        @DateTimeFormat(pattern = "HH:mm") LocalTime horaChegada,
        @PositiveOrZero(message = "Km final não pode ser negativo") Integer kmFinal,

        // ---- condutor que recebe o veículo ----
        @NotBlank(message = "Nome do condutor é obrigatório") @Size(max = 160) String motoristaNome,
        @NotBlank(message = "RG é obrigatório") @Size(max = 30) String rg,
        @NotBlank(message = "CPF é obrigatório") @Size(max = 14) String cpf,
        @NotBlank(message = "Telefone é obrigatório") @Size(max = 25) String fone,
        @NotBlank(message = "Lotação é obrigatória") @Size(max = 60) String lotacao,
        @Size(max = 500) String observacao
) {}

package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Abertura de um levantamento patrimonial pela API. */
public record AbrirLevantamentoRequest(
        @NotNull(message = "Ano é obrigatório") Integer ano,
        @Size(max = 150) String descricao,
        @Size(max = 1000) String observacao
) {}

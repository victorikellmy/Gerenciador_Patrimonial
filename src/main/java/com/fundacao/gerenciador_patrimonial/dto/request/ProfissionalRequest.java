package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank(message = "Nome é obrigatório") @Size(max = 160) String nome,
        @Size(max = 30) String matricula,
        @Size(max = 60) String lotacao,
        @NotNull(message = "Selecione o cargo / posto / graduação") Long nivelId,
        @NotBlank(message = "CPF é obrigatório") @Size(max = 14) String cpf,
        @Size(max = 30) String rg,
        @Size(max = 30) String orgaoExpedidor,
        @Size(max = 160) String dadosBancarios
) {}

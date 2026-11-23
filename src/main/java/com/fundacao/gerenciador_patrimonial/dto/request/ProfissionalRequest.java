package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank(message = "Nome é obrigatório") @Size(max = 160) String nome,
        @Size(max = 30) String matricula,
        @Size(max = 60) String lotacao,
        /** Linha da tabela de valores; opcional (o valor unitário pode ser digitado no requerimento). */
        Long nivelId,
        @NotBlank(message = "CPF é obrigatório") @Size(max = 14) String cpf,
        @Size(max = 30) String rg,
        @Size(max = 30) String orgaoExpedidor,
        /** Texto livre do cadastro antigo; os campos banco/agência/conta/PIX o substituem. */
        @Size(max = 160) String dadosBancarios,
        @Size(max = 120) String cargo,
        @Size(max = 3) String banco,
        @Size(max = 15) String agencia,
        @Size(max = 20) String contaCorrente,
        @Size(max = 120) String pix
) {}

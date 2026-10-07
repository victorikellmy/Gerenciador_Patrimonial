package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Identificação de um bem pelo número de tombo (ou ID) na API. */
public record TomboRequest(
        @NotBlank(message = "Informe o número de tombo") String tombo
) {}

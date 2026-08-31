package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação/atualização de Lotação.
 */
public record LotacaoRequest(
        @NotBlank(message = "UPM é obrigatória")
        @Size(max = 120)
        String upm,

        @NotBlank(message = "Nome do setor/sala é obrigatório")
        @Size(max = 120)
        String nome,

        @Size(max = 80)
        String cidade,

        @NotNull(message = "Tipo de local é obrigatório")
        TipoLocal tipoLocal,

        Long responsavelAtualId
) {}

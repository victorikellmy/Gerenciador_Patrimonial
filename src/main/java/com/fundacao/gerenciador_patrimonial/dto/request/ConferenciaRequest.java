package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro da conferência física de um item do levantamento.
 *
 * @param resultado             OK, AVARIADO ou NAO_LOCALIZADO
 * @param conservacaoVerificada conservação observada (opcional — atualiza o bem)
 * @param descricaoAvaria       obrigatória quando AVARIADO
 * @param observacao            texto livre
 * @param lotacaoContextoId     lotação cujo checklist estava aberto; quando difere do
 *                              local esperado, vira "local encontrado" (divergência)
 */
public record ConferenciaRequest(
        @NotNull(message = "Informe o resultado da conferência.")
        ResultadoConferencia resultado,
        Conservacao conservacaoVerificada,
        @Size(max = 1000) String descricaoAvaria,
        @Size(max = 1000) String observacao,
        Long lotacaoContextoId
) {}

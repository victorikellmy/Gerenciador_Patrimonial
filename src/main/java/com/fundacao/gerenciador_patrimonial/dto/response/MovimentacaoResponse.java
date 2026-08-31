package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;

import java.time.LocalDateTime;

/**
 * Projeção de uma movimentação para o relatório de histórico.
 * Materializa nomes (não IDs) para evitar lazy-loading nas views.
 */
public record MovimentacaoResponse(
        Long id,
        Long patrimonioId,
        String lotacaoOrigemNome,
        String lotacaoOrigemUpm,
        String lotacaoDestinoNome,
        String lotacaoDestinoUpm,
        String responsavelOrigemNome,
        String responsavelDestinoNome,
        LocalDateTime dataMovimentacao,
        String observacao,
        String executadoPor
) {
    public static MovimentacaoResponse from(Movimentacao m) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getPatrimonio() != null ? m.getPatrimonio().getId() : null,
                m.getLotacaoOrigem()  != null ? m.getLotacaoOrigem().getNome() : null,
                m.getLotacaoOrigem()  != null ? m.getLotacaoOrigem().getUpm()  : null,
                m.getLotacaoDestino() != null ? m.getLotacaoDestino().getNome() : null,
                m.getLotacaoDestino() != null ? m.getLotacaoDestino().getUpm()  : null,
                m.getResponsavelOrigem()  != null ? m.getResponsavelOrigem().getNomeCompleto()  : null,
                m.getResponsavelDestino() != null ? m.getResponsavelDestino().getNomeCompleto() : null,
                m.getDataMovimentacao(),
                m.getObservacao(),
                m.getExecutadoPor()
        );
    }
}

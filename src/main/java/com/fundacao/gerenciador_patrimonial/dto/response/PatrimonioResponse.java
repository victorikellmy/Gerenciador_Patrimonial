package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projeção completa de Patrimônio para o frontend,
 * incluindo campos derivados calculados pelo {@code DepreciacaoService}.
 */
public record PatrimonioResponse(
        Long id,
        String numeroTombo,
        String descricao,
        String categoria,
        LocalDate dataCompra,
        BigDecimal valorCompra,
        Conservacao conservacao,
        SituacaoPatrimonio situacao,
        String notaFiscal,

        // --- Lotação/Responsável ---
        Long lotacaoId,
        String lotacaoNome,
        String lotacaoUpm,
        Long responsavelId,
        String responsavelNome,

        // --- Baixa (quando aplicável) ---
        LocalDate dataBaixa,
        String motivoBaixa,

        // --- Métricas derivadas ---
        Integer vutAnos,
        BigDecimal depreciacaoAcumulada,
        BigDecimal valorContabilLiquido,
        BigDecimal depreciacaoAnual
) {
    public static PatrimonioResponse from(Patrimonio p, CalculoDepreciacao calc) {
        return new PatrimonioResponse(
                p.getId(),
                p.getNumeroTombo(),
                p.getDescricao(),
                p.getCategoria(),
                p.getDataCompra(),
                p.getValorCompra(),
                p.getConservacao(),
                p.getSituacao(),
                p.getNotaFiscal(),
                p.getLotacao() != null ? p.getLotacao().getId() : null,
                p.getLotacao() != null ? p.getLotacao().getNome() : null,
                p.getLotacao() != null ? p.getLotacao().getUpm()  : null,
                p.getResponsavel() != null ? p.getResponsavel().getId() : null,
                p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null,
                p.getDataBaixa(),
                p.getMotivoBaixa(),
                calc.vutAnos(),
                calc.depreciacaoAcumulada(),
                calc.valorContabilLiquido(),
                calc.depreciacaoAnual()
        );
    }
}

package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Projeção completa de Patrimônio para o frontend,
 * incluindo campos derivados calculados pelo {@code DepreciacaoService}.
 */
public record PatrimonioResponse(
        Long id,
        String numeroTombo,
        String descricao,
        String categoria,
        String subcategoria,
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

        // --- Métricas derivadas (calculadas em tempo real a cada leitura) ---
        Integer vutAnos,
        BigDecimal percentualVud,
        BigDecimal vudAnos,
        BigDecimal vurAnos,
        BigDecimal depreciacaoAcumulada,
        BigDecimal valorContabilLiquido,
        BigDecimal valorRecuperavel,
        BigDecimal perdaImpairment,
        BigDecimal depreciacaoAnual,
        LocalDate  dataReferencia,
        boolean    calculoLegado,

        // --- Campos do laudo de impairment ---
        String conclusaoImpairment,
        String observacao,
        String linkReferencia,

        // --- Auditoria (Spring Data JPA Auditing) ---
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        String criadoPor,
        String atualizadoPor
) {
    public static PatrimonioResponse from(Patrimonio p, CalculoDepreciacao calc) {
        return new PatrimonioResponse(
                p.getId(),
                p.getNumeroTombo(),
                p.getDescricao(),
                p.getCategoria(),
                p.getSubcategoria(),
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
                calc.percentualVud(),
                calc.vudAnos(),
                calc.vurAnos(),
                calc.depreciacaoAcumulada(),
                calc.valorContabilLiquido(),
                calc.valorRecuperavel(),
                calc.perdaImpairment(),
                calc.depreciacaoAnual(),
                calc.dataReferencia(),
                calc.calculoLegado(),
                p.getConclusaoImpairment(),
                p.getObservacao(),
                p.getLinkReferencia(),
                p.getCriadoEm(),
                p.getAtualizadoEm(),
                p.getCriadoPor(),
                p.getAtualizadoPor()
        );
    }
}

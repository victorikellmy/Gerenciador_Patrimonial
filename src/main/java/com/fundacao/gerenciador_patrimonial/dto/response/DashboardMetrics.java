package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Snapshot consolidado do patrimônio para exibição em dashboard.
 *
 * @param totalPatrimonios total geral (inclui baixados)
 * @param contagemPorSituacao mapa Situação → quantidade
 * @param totalLotacoes número de lotações cadastradas
 * @param totalResponsaveis número de responsáveis ativos
 * @param valorTotalAtivos soma do valor de compra dos bens ativos
 * @param depreciacaoAcumuladaTotal soma da depreciação acumulada dos ativos
 * @param valorContabilLiquidoTotal soma do VCL dos ativos
 * @param porCategoria agrupamento por categoria
 * @param porConservacao agrupamento por conservação
 * @param topUpms top-N UPMs por volume
 * @param ultimasMovimentacoes últimas N movimentações
 */
public record DashboardMetrics(
        long totalPatrimonios,
        Map<String, Long> contagemPorSituacao,
        long totalLotacoes,
        long totalResponsaveis,

        BigDecimal valorTotalAtivos,
        BigDecimal depreciacaoAcumuladaTotal,
        BigDecimal valorContabilLiquidoTotal,

        List<AgrupamentoResponse> porCategoria,
        List<AgrupamentoResponse> porConservacao,
        List<AgrupamentoResponse> topUpms,

        List<MovimentacaoResumo> ultimasMovimentacoes
) {
    /** Resumo leve de movimentação para dashboard — evita serializar entidade inteira. */
    public record MovimentacaoResumo(
            Long id,
            String patrimonioDescricao,
            String patrimonioTombo,
            String deLotacao,
            String paraLotacao,
            String deResponsavel,
            String paraResponsavel,
            String dataMovimentacao
    ) {}
}

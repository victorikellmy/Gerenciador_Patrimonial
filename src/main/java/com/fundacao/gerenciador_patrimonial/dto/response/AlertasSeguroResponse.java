package com.fundacao.gerenciador_patrimonial.dto.response;

import java.util.List;

/**
 * Pendências de seguro da frota ativa: apólices vencidas (sem renovação), vencendo
 * nos próximos dias, veículos sem nenhuma apólice em vigor e sinistros em andamento.
 */
public record AlertasSeguroResponse(
        int diasAviso,
        List<SeguroVeiculoResponse> vencidos,
        List<SeguroVeiculoResponse> vencendo,
        List<VeiculoSemSeguro> semSeguro,
        List<SinistroVeiculoResponse> sinistrosEmAndamento
) {
    public record VeiculoSemSeguro(Long veiculoId, String placa, String modelo) {}

    public int total() { return vencidos.size() + vencendo.size() + semSeguro.size(); }
}

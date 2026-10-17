package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.ManutencaoVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManutencaoVeiculoResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        LocalDate data,
        TipoServicoVeiculo servico,
        Integer km,
        String descricao,
        BigDecimal valor,
        String fornecedor,
        String notaFiscal,
        Integer proximaRevisaoKm,
        LocalDate proximaRevisaoData,
        String motorista,
        String registradoPor
) {
    public static ManutencaoVeiculoResponse from(ManutencaoVeiculo m) {
        return new ManutencaoVeiculoResponse(m.getId(), m.getVeiculo().getId(), m.getVeiculo().getPlaca(),
                m.getVeiculo().getModelo(), m.getData(), m.getServico(), m.getKm(), m.getDescricao(), m.getValor(),
                m.getFornecedor(), m.getNotaFiscal(), m.getProximaRevisaoKm(), m.getProximaRevisaoData(),
                m.getMotorista(), m.getRegistradoPor());
    }
}

package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Abastecimento;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AbastecimentoResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        LocalDate data,
        Integer km,
        Combustivel combustivel,
        BigDecimal quantidadeLitros,
        BigDecimal valor,
        BigDecimal precoLitro,
        String postoCidade,
        String motorista,
        String observacao,
        String registradoPor,
        boolean possuiNotaFiscal,
        String nfNomeOriginal
) {
    public static AbastecimentoResponse from(Abastecimento a) {
        return new AbastecimentoResponse(a.getId(), a.getVeiculo().getId(), a.getVeiculo().getPlaca(),
                a.getVeiculo().getModelo(), a.getData(), a.getKm(), a.getCombustivel(), a.getQuantidadeLitros(),
                a.getValor(), a.getPrecoLitro(), a.getPostoCidade(), a.getMotorista(), a.getObservacao(),
                a.getRegistradoPor(), a.possuiNotaFiscal(), a.getNfNomeOriginal());
    }
}

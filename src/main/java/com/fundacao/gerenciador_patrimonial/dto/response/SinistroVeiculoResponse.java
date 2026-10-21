package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.SinistroVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoSinistro;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SinistroVeiculoResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        Long seguroId,
        String seguradora,
        String numeroApolice,
        LocalDate dataOcorrencia,
        String numeroSinistro,
        String descricao,
        String localOcorrencia,
        String condutor,
        BigDecimal valorPrejuizo,
        BigDecimal valorIndenizado,
        SituacaoSinistro situacao,
        boolean emAndamento,
        String observacao,
        String registradoPor
) {
    public static SinistroVeiculoResponse from(SinistroVeiculo s) {
        var v = s.getVeiculo();
        var seg = s.getSeguro();
        return new SinistroVeiculoResponse(s.getId(), v.getId(), v.getPlaca(), v.getModelo(),
                seg == null ? null : seg.getId(), seg == null ? null : seg.getSeguradora(),
                seg == null ? null : seg.getNumeroApolice(), s.getDataOcorrencia(), s.getNumeroSinistro(),
                s.getDescricao(), s.getLocalOcorrencia(), s.getCondutor(), s.getValorPrejuizo(),
                s.getValorIndenizado(), s.getSituacao(), s.getSituacao().emAndamento(), s.getObservacao(),
                s.getRegistradoPor());
    }
}

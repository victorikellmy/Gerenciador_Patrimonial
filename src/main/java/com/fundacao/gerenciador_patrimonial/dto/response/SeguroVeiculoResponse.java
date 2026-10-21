package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.SeguroVeiculo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Apólice com a situação calculada na data de hoje e os dias até o fim da vigência. */
public record SeguroVeiculoResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        String seguradora,
        String numeroApolice,
        String corretor,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        SeguroVeiculo.Situacao situacao,
        long diasParaVencer,
        BigDecimal valorPremio,
        BigDecimal franquia,
        String coberturas,
        String telefoneAssistencia,
        String observacao,
        boolean possuiApolice,
        String apoliceNomeOriginal,
        String registradoPor
) {
    public static SeguroVeiculoResponse from(SeguroVeiculo s) {
        LocalDate hoje = LocalDate.now();
        var v = s.getVeiculo();
        return new SeguroVeiculoResponse(s.getId(), v.getId(), v.getPlaca(), v.getModelo(), s.getSeguradora(),
                s.getNumeroApolice(), s.getCorretor(), s.getInicioVigencia(), s.getFimVigencia(), s.situacao(hoje),
                ChronoUnit.DAYS.between(hoje, s.getFimVigencia()), s.getValorPremio(), s.getFranquia(),
                s.getCoberturas(), s.getTelefoneAssistencia(), s.getObservacao(), s.possuiApolice(),
                s.getApoliceNomeOriginal(), s.getRegistradoPor());
    }
}

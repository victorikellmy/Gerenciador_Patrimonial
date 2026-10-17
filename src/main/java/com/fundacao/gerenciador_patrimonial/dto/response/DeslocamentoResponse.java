package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Deslocamento;

import java.time.LocalDate;
import java.time.LocalTime;

public record DeslocamentoResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        LocalDate dataViagem,
        String destino,
        String motivo,
        String titular,
        String assistido,
        LocalTime horaSaida,
        Integer kmInicial,
        LocalTime horaChegada,
        Integer kmFinal,
        Integer kmRodados,
        boolean emAndamento,
        String motorista,
        String lotacao,
        String observacao,
        String registradoPor
) {
    public static DeslocamentoResponse from(Deslocamento d) {
        return new DeslocamentoResponse(d.getId(), d.getVeiculo().getId(), d.getVeiculo().getPlaca(),
                d.getVeiculo().getModelo(), d.getDataViagem(), d.getDestino(), d.getMotivo(), d.getTitular(),
                d.getAssistido(), d.getHoraSaida(), d.getKmInicial(), d.getHoraChegada(), d.getKmFinal(),
                d.getKmRodados(), d.isEmAndamento(), d.getMotorista(), d.getLotacao(), d.getObservacao(),
                d.getRegistradoPor());
    }
}

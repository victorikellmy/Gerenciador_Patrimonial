package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Cautela;

import java.time.LocalDate;
import java.time.LocalTime;

public record CautelaResponse(
        Long id,
        Long veiculoId,
        String placa,
        String modelo,
        String chassi,
        String ano,
        String cidadeVeiculo,
        LocalDate dataViagem,
        String destino,
        String motivo,
        String responsavel,
        String assistido,
        LocalTime horaSaida,
        Integer kmInicial,
        LocalTime horaChegada,
        Integer kmFinal,
        Integer kmRodados,
        boolean emAndamento,
        String motoristaNome,
        String rg,
        String cpf,
        String fone,
        String lotacao,
        String observacao,
        String registradoPor
) {
    public static CautelaResponse from(Cautela c) {
        var v = c.getVeiculo();
        return new CautelaResponse(c.getId(), v.getId(), v.getPlaca(), v.getModelo(), v.getChassi(), v.getAno(),
                v.getCidade(), c.getDataViagem(), c.getDestino(), c.getMotivo(), c.getResponsavel(), c.getAssistido(),
                c.getHoraSaida(), c.getKmInicial(), c.getHoraChegada(), c.getKmFinal(), c.getKmRodados(),
                c.isEmAndamento(), c.getMotoristaNome(), c.getRg(), c.getCpf(), c.getFone(), c.getLotacao(),
                c.getObservacao(), c.getRegistradoPor());
    }
}

package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Diaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DiariaResponse(
        Long id,
        LocalDate dataSolicitacao,
        Long profissionalId,
        String profissionalNome,
        String profissionalMatricula,
        String profissionalLotacao,
        String profissionalNivel,
        String profissionalCpf,
        String profissionalRg,
        String profissionalOrgaoExpedidor,
        String profissionalDadosBancarios,
        String itinerario,
        LocalTime horaSaida,
        MeioTransporte meioTransporte,
        LocalDate partida,
        LocalDate retorno,
        LocalTime horaChegada,
        RecursoDiaria recurso,
        BigDecimal numeroDiarias,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        String finalidade,
        String solicitante,
        StatusDiaria status,
        LocalDate dataPagamento,
        String motivoIndeferimento,
        LocalDateTime criadoEm,
        boolean editavel
) {
    public static DiariaResponse from(Diaria d) {
        var p = d.getProfissional();
        return new DiariaResponse(d.getId(), d.getDataSolicitacao(), p.getId(), p.getNome(), p.getMatricula(),
                p.getLotacao(), p.getNivel() != null ? p.getNivel().getDescricao() : null, p.getCpf(), p.getRg(),
                p.getOrgaoExpedidor(), p.getDadosBancarios(), d.getItinerario(), d.getHoraSaida(), d.getMeioTransporte(),
                d.getPartida(), d.getRetorno(), d.getHoraChegada(), d.getRecurso(), d.getNumeroDiarias(),
                d.getValorUnitario(), d.getValorTotal(), d.getFinalidade(), d.getSolicitante(), d.getStatus(),
                d.getDataPagamento(), d.getMotivoIndeferimento(), d.getCriadoEm(), d.isEditavel());
    }
}

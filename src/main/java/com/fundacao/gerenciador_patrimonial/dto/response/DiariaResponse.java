package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Diaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoDiaria;
import com.fundacao.gerenciador_patrimonial.service.CalculoDiarias;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Diária como a API devolve. Os campos {@code profissional*} e os bancários são os gravados no
 * próprio requerimento; nas diárias antigas (sem essa fotografia) vêm do cadastro atual.
 */
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
        boolean editavel,
        // --- requerimento (Anexo II) ---
        String numeroRequerimento,
        String origem,
        TipoDiaria tipoDiaria,
        String profissionalCargo,
        Long profissionalNivelId,
        String banco,
        String agencia,
        String contaCorrente,
        String pix,
        /** Memória de cálculo; null nas diárias antigas sem horário de saída ou de chegada. */
        CalculoDiarias.Resultado calculo
) {
    public static DiariaResponse from(Diaria d) {
        var p = d.getProfissional();
        var nivel = p.getNivel();
        String nivelDescricao = nivel != null ? nivel.getDescricao() : null;
        return new DiariaResponse(d.getId(), d.getDataSolicitacao(), p.getId(),
                ou(d.getProfNome(), p.getNome()), ou(d.getProfMatricula(), p.getMatricula()),
                ou(d.getProfLotacao(), p.getLotacao()), nivelDescricao, ou(d.getProfCpf(), p.getCpf()),
                ou(d.getProfRg(), p.getRg()), ou(d.getProfOrgaoExpedidor(), p.getOrgaoExpedidor()),
                dadosBancarios(d, p.getDadosBancarios()),
                d.getItinerario(), d.getHoraSaida(), d.getMeioTransporte(),
                d.getPartida(), d.getRetorno(), d.getHoraChegada(), d.getRecurso(), d.getNumeroDiarias(),
                d.getValorUnitario(), d.getValorTotal(), d.getFinalidade(), d.getSolicitante(), d.getStatus(),
                d.getDataPagamento(), d.getMotivoIndeferimento(), d.getCriadoEm(), d.isEditavel(),
                d.getNumeroRequerimento(), d.getOrigem(), d.getTipoDiaria(),
                ou(d.getProfCargo(), ou(p.getCargo(), nivelDescricao)), nivel != null ? nivel.getId() : null,
                d.getBanco(), d.getAgencia(), d.getContaCorrente(), d.getPix(), calculo(d));
    }

    /** Uma linha com os dados bancários, para listagens e para clientes antigos da API. */
    private static String dadosBancarios(Diaria d, String doCadastro) {
        if (d.getBanco() == null && d.getAgencia() == null && d.getContaCorrente() == null && d.getPix() == null) {
            return ou(d.getDadosBancarios(), doCadastro);
        }
        StringBuilder sb = new StringBuilder();
        juntar(sb, "Banco ", d.getBanco());
        juntar(sb, "Ag. ", d.getAgencia());
        juntar(sb, "C/C ", d.getContaCorrente());
        juntar(sb, "PIX ", d.getPix());
        return sb.toString();
    }

    private static void juntar(StringBuilder sb, String rotulo, String valor) {
        if (valor == null || valor.isBlank()) return;
        if (!sb.isEmpty()) sb.append(" · ");
        sb.append(rotulo).append(valor);
    }

    private static CalculoDiarias.Resultado calculo(Diaria d) {
        if (d.getPartida() == null || d.getRetorno() == null || d.getHoraSaida() == null || d.getHoraChegada() == null) return null;
        LocalDateTime saida = d.getPartida().atTime(d.getHoraSaida());
        LocalDateTime retorno = d.getRetorno().atTime(d.getHoraChegada());
        return retorno.isAfter(saida) ? CalculoDiarias.calcular(saida, retorno) : null;
    }

    private static String ou(String a, String b) { return a != null && !a.isBlank() ? a : b; }
}

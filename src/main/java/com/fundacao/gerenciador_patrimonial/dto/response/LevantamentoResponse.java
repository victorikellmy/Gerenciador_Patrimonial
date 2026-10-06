package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.LevantamentoPatrimonial;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Levantamento patrimonial com os totais de andamento.
 */
public record LevantamentoResponse(
        Long id,
        int ano,
        String descricao,
        StatusLevantamento status,
        String observacao,
        String abertoPor,
        LocalDateTime abertoEm,
        String concluidoPor,
        LocalDateTime concluidoEm,

        long total,
        long pendentes,
        long ok,
        long avariados,
        long naoLocalizados,
        long divergenciasLocal
) {
    public static LevantamentoResponse from(LevantamentoPatrimonial l,
                                            Map<ResultadoConferencia, Long> porResultado,
                                            long divergenciasLocal) {
        long pend = porResultado.getOrDefault(ResultadoConferencia.PENDENTE, 0L);
        long ok   = porResultado.getOrDefault(ResultadoConferencia.OK, 0L);
        long av   = porResultado.getOrDefault(ResultadoConferencia.AVARIADO, 0L);
        long nl   = porResultado.getOrDefault(ResultadoConferencia.NAO_LOCALIZADO, 0L);
        return new LevantamentoResponse(
                l.getId(), l.getAno(), l.getDescricao(), l.getStatus(), l.getObservacao(),
                l.getAbertoPor(), l.getAbertoEm(), l.getConcluidoPor(), l.getConcluidoEm(),
                pend + ok + av + nl, pend, ok, av, nl, divergenciasLocal);
    }

    public boolean aberto() { return status == StatusLevantamento.ABERTO; }

    public long conferidos() { return total - pendentes; }

    /** 0–100, inteiro, para barras de progresso. */
    public int percentual() {
        return total == 0 ? 0 : (int) Math.round(100.0 * conferidos() / total);
    }

    public long ocorrencias() { return avariados + naoLocalizados + divergenciasLocal; }
}

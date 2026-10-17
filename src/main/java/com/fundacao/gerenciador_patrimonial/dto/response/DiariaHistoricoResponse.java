package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.DiariaHistorico;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;

import java.time.LocalDateTime;

public record DiariaHistoricoResponse(Long id, StatusDiaria deStatus, StatusDiaria paraStatus, String usuario,
                                      LocalDateTime instante, String observacao) {
    public static DiariaHistoricoResponse from(DiariaHistorico h) {
        return new DiariaHistoricoResponse(h.getId(), h.getDeStatus(), h.getParaStatus(), h.getUsuario(),
                h.getInstante(), h.getObservacao());
    }
}

package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;

import java.time.LocalDateTime;

public record AuditoriaResponse(
        Long id,
        String usuario,
        AcaoAuditoria acao,
        String entidade,
        Long entidadeId,
        String descricao,
        String ipOrigem,
        LocalDateTime dataHora
) {
    public static AuditoriaResponse from(AuditoriaAcao a) {
        return new AuditoriaResponse(
                a.getId(), a.getUsuario(), a.getAcao(),
                a.getEntidade(), a.getEntidadeId(),
                a.getDescricao(), a.getIpOrigem(), a.getDataHora());
    }
}

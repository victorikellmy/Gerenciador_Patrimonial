package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.PendenciaPatrimoniamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;

/** Resposta mínima do endpoint de integração: {@code { "id": 17, "status": "PENDENTE" }}. */
public record PendenciaIntegracaoResponse(Long id, StatusPendencia status) {
    public static PendenciaIntegracaoResponse from(PendenciaPatrimoniamento p) {
        return new PendenciaIntegracaoResponse(p.getId(), p.getStatus());
    }
}

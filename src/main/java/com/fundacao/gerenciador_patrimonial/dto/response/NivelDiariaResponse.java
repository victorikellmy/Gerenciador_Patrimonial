package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria;

import java.math.BigDecimal;

public record NivelDiariaResponse(Long id, String descricao, BigDecimal valorCapitais, BigDecimal valorInteriorEstado,
                                  BigDecimal valorInteriorOutroEstado, boolean ativo) {
    public static NivelDiariaResponse from(NivelDiaria n) {
        return new NivelDiariaResponse(n.getId(), n.getDescricao(), n.getValorCapitais(), n.getValorInteriorEstado(),
                n.getValorInteriorOutroEstado(), n.isAtivo());
    }
}

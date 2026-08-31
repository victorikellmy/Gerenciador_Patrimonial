package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;

public record ResponsavelResponse(
        Long id,
        String nomeCompleto,
        String matricula,
        String cidade,
        Long lotacaoId,
        String lotacaoNome,
        boolean ativo
) {
    public static ResponsavelResponse from(Responsavel r) {
        return new ResponsavelResponse(
                r.getId(),
                r.getNomeCompleto(),
                r.getMatricula(),
                r.getCidade(),
                r.getLotacao() != null ? r.getLotacao().getId() : null,
                r.getLotacao() != null ? r.getLotacao().getNome() : null,
                r.isAtivo()
        );
    }
}

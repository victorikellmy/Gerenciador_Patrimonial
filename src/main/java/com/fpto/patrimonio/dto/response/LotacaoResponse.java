package com.fpto.patrimonio.dto.response;

import com.fpto.patrimonio.domain.entity.Lotacao;
import com.fpto.patrimonio.domain.enums.TipoLocal;

/**
 * Projeção pública de Lotação.
 * Não expõe a lista de patrimônios (evita payload gigantesco).
 */
public record LotacaoResponse(
        Long id,
        String upm,
        String nome,
        String cidade,
        TipoLocal tipoLocal,
        Long responsavelAtualId,
        String responsavelAtualNome
) {
    public static LotacaoResponse from(Lotacao l) {
        return new LotacaoResponse(
                l.getId(),
                l.getUpm(),
                l.getNome(),
                l.getCidade(),
                l.getTipoLocal(),
                l.getResponsavelAtual() != null ? l.getResponsavelAtual().getId() : null,
                l.getResponsavelAtual() != null ? l.getResponsavelAtual().getNomeCompleto() : null
        );
    }
}

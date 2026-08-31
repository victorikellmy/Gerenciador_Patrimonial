package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;

/**
 * Critérios de busca dinâmica de patrimônio.
 * Todos os campos opcionais — qualquer combinação é válida.
 * Usado como {@code @ModelAttribute} pelo Spring MVC (GET com query params).
 */
public record FiltroPatrimonio(
        String descricao,
        String numeroTombo,
        Long lotacaoId,
        Long responsavelId,
        String upm,
        String categoria,
        SituacaoPatrimonio situacao,
        Conservacao conservacao
) {
    /** Cópia com a situação trocada — usado pelos atalhos /ativos e /baixados. */
    public FiltroPatrimonio comSituacao(SituacaoPatrimonio novaSituacao) {
        return new FiltroPatrimonio(
                descricao, numeroTombo, lotacaoId, responsavelId,
                upm, categoria, novaSituacao, conservacao);
    }
}

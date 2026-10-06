package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.ItemLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;

import java.time.LocalDateTime;

/**
 * Item do checklist de levantamento pronto para a tela.
 *
 * @param fotoId      foto tirada NESTA conferência (pode ser nula)
 * @param fotoAtualId foto mais recente anexada ao bem (para mostrar como está hoje)
 */
public record ItemLevantamentoResponse(
        Long id,
        Long patrimonioId,
        String numeroTombo,
        String descricao,
        String categoria,
        String subcategoria,
        SituacaoPatrimonio situacao,
        Conservacao conservacaoAtual,
        String responsavelNome,

        Long lotacaoId,
        String lotacaoUpm,
        String lotacaoNome,
        Long lotacaoEncontradaId,
        String lotacaoEncontradaUpm,
        String lotacaoEncontradaNome,

        ResultadoConferencia resultado,
        Conservacao conservacaoAnterior,
        Conservacao conservacaoVerificada,
        String descricaoAvaria,
        String observacao,
        Long fotoId,
        Long fotoAtualId,
        String verificadoPor,
        LocalDateTime verificadoEm
) {
    public static ItemLevantamentoResponse from(ItemLevantamento i, Long fotoAtualId) {
        Patrimonio p = i.getPatrimonio();
        Lotacao l = i.getLotacao();
        Lotacao e = i.getLotacaoEncontrada();
        return new ItemLevantamentoResponse(
                i.getId(),
                p.getId(), p.getNumeroTombo(), p.getDescricao(), p.getCategoria(), p.getSubcategoria(),
                p.getSituacao(), p.getConservacao(),
                p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null,
                l.getId(), l.getUpm(), l.getNome(),
                e != null ? e.getId() : null,
                e != null ? e.getUpm() : null,
                e != null ? e.getNome() : null,
                i.getResultado(), i.getConservacaoAnterior(), i.getConservacaoVerificada(),
                i.getDescricaoAvaria(), i.getObservacao(),
                i.getFoto() != null ? i.getFoto().getId() : null,
                fotoAtualId,
                i.getVerificadoPor(), i.getVerificadoEm());
    }

    public boolean pendente() { return resultado == ResultadoConferencia.PENDENTE; }

    public boolean localDivergente() {
        return lotacaoEncontradaId != null && !lotacaoEncontradaId.equals(lotacaoId);
    }

    /** Verdadeiro quando o bem pertence a outro local mas foi achado no local {@code lotacaoContexto}. */
    public boolean encontradoFora(Long lotacaoContexto) {
        return localDivergente() && lotacaoEncontradaId.equals(lotacaoContexto);
    }

    /** Verdadeiro quando o bem era esperado em {@code lotacaoContexto} mas foi achado em outro local. */
    public boolean levadoParaOutroLocal(Long lotacaoContexto) {
        return localDivergente() && lotacaoId.equals(lotacaoContexto);
    }

    public boolean possuiOcorrencia() {
        return resultado == ResultadoConferencia.AVARIADO
                || resultado == ResultadoConferencia.NAO_LOCALIZADO
                || localDivergente();
    }
}

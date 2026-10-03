package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.PendenciaPatrimoniamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Visão de uma pendência para as telas web (itens já desserializados). */
public record PendenciaResponse(
        Long id,
        String origem,
        Long compraIdOrigem,
        StatusPendencia status,
        String numeroDocumento,
        String numeroSgd,
        String assunto,
        String solicitanteDocumento,
        String fornecedor,
        String numeroNotaFiscal,
        LocalDateTime dataRecebimento,
        LocalDate dataCompra,
        BigDecimal valorTotal,
        String retiradoPor,
        String setorDestino,
        String setorDestinoCentroCusto,
        String registradoPor,
        String observacao,
        List<ItemPendencia> itens,
        int quantidadeTotal,
        int quantidadePatrimoniada,
        int quantidadeRestante,
        boolean possuiNotaFiscal,
        String nfNomeOriginal,
        Long patrimonioId,
        String concluidaPor,
        LocalDateTime concluidaEm,
        LocalDateTime criadoEm
) {
    public record ItemPendencia(String descricao, String codigoSku, Integer quantidade, BigDecimal valorUnitario) {}

    public static PendenciaResponse from(PendenciaPatrimoniamento p, List<ItemPendencia> itens) {
        return new PendenciaResponse(
                p.getId(), p.getOrigem(), p.getCompraIdOrigem(), p.getStatus(),
                p.getNumeroDocumento(), p.getNumeroSgd(), p.getAssunto(), p.getSolicitanteDocumento(),
                p.getFornecedor(), p.getNumeroNotaFiscal(), p.getDataRecebimento(), p.getDataCompra(),
                p.getValorTotal(), p.getRetiradoPor(), p.getSetorDestino(), p.getSetorDestinoCentroCusto(),
                p.getRegistradoPor(), p.getObservacao(), itens,
                p.getQuantidadeTotal(), p.getQuantidadePatrimoniada(), p.getQuantidadeRestante(),
                p.possuiNotaFiscal(), p.getNfNomeOriginal(),
                p.getPatrimonio() != null ? p.getPatrimonio().getId() : null,
                p.getConcluidaPor(), p.getConcluidaEm(), p.getCriadoEm());
    }

    public boolean pendente() { return status == StatusPendencia.PENDENTE; }
}

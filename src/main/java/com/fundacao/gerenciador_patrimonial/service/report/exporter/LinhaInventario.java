package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Linha "achatada" do relatório de inventário — modelo comum dos três
 * exporters (CSV, XLSX, PDF).
 *
 * <p>Centraliza os null-checks de lotação/responsável e o cálculo de
 * depreciação (feito UMA vez por item, no mapeamento), que antes eram
 * repetidos em cada exporter. Coluna nova = mexer aqui + na renderização
 * do(s) formato(s) que a exibem.</p>
 */
public record LinhaInventario(
        Long id,
        String tombo,
        String descricao,
        String categoria,
        LocalDate dataCompra,
        BigDecimal valorCompra,
        String conservacao,
        String situacao,
        String notaFiscal,
        String upm,
        String lotacaoNome,
        String responsavelNome,
        Integer vutAnos,
        BigDecimal depreciacaoAcumulada,
        BigDecimal valorContabilLiquido,
        BigDecimal depreciacaoAnual,
        LocalDate dataBaixa,
        String motivoBaixa
) {
    /** Cabeçalho das 18 colunas — fonte única para CSV e XLSX. */
    public static final String[] CABECALHO = {
            "ID", "Tombo", "Descrição", "Categoria", "Data Compra", "Valor Compra",
            "Conservação", "Situação", "Nota Fiscal",
            "UPM", "Lotação", "Responsável",
            "VUT (anos)", "Depreciação Acumulada", "VCL", "Depreciação Anual",
            "Data Baixa", "Motivo Baixa"
    };

    public static LinhaInventario de(Patrimonio p, CalculoDepreciacao c) {
        return new LinhaInventario(
                p.getId(),
                p.getNumeroTombo(),
                p.getDescricao(),
                p.getCategoria(),
                p.getDataCompra(),
                p.getValorCompra(),
                p.getConservacao() != null ? p.getConservacao().name() : null,
                p.getSituacao() != null ? p.getSituacao().name() : null,
                p.getNotaFiscal(),
                p.getLotacao() != null ? p.getLotacao().getUpm() : null,
                p.getLotacao() != null ? p.getLotacao().getNome() : null,
                p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null,
                c.vutAnos(),
                c.depreciacaoAcumulada(),
                c.valorContabilLiquido(),
                c.depreciacaoAnual(),
                p.getDataBaixa(),
                p.getMotivoBaixa()
        );
    }
}

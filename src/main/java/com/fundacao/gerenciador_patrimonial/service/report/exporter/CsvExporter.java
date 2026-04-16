package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporta inventário em CSV (separador {@code ;}) — compatível com Excel em pt-BR.
 *
 * <p>Escreve BOM UTF-8 no início para que o Excel reconheça acentos automaticamente.</p>
 */
@Component
@RequiredArgsConstructor
public class CsvExporter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEP = ";";

    private final DepreciacaoService depreciacaoService;

    public void exportarInventario(List<Patrimonio> lista, OutputStream out) {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // BOM para Excel reconhecer UTF-8
        w.write('\ufeff');

        // Cabeçalho
        w.println(String.join(SEP,
                "ID", "Tombo", "Descrição", "Categoria", "Data Compra", "Valor Compra",
                "Conservação", "Situação", "Nota Fiscal",
                "UPM", "Lotação", "Responsável",
                "VUT (anos)", "Depreciação Acumulada", "VCL", "Depreciação Anual",
                "Data Baixa", "Motivo Baixa"));

        for (Patrimonio p : lista) {
            CalculoDepreciacao c = depreciacaoService.calcular(p);
            w.println(String.join(SEP,
                    str(p.getId()),
                    esc(p.getNumeroTombo()),
                    esc(p.getDescricao()),
                    esc(p.getCategoria()),
                    p.getDataCompra() != null ? p.getDataCompra().format(DATA) : "",
                    bd(p.getValorCompra()),
                    p.getConservacao() != null ? p.getConservacao().name() : "",
                    p.getSituacao() != null ? p.getSituacao().name() : "",
                    esc(p.getNotaFiscal()),
                    esc(p.getLotacao() != null ? p.getLotacao().getUpm() : null),
                    esc(p.getLotacao() != null ? p.getLotacao().getNome() : null),
                    esc(p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null),
                    c.vutAnos() != null ? c.vutAnos().toString() : "",
                    bd(c.depreciacaoAcumulada()),
                    bd(c.valorContabilLiquido()),
                    bd(c.depreciacaoAnual()),
                    p.getDataBaixa() != null ? p.getDataBaixa().format(DATA) : "",
                    esc(p.getMotivoBaixa())
            ));
        }
        w.flush();
    }

    // ---- helpers de escape ----

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    /** Escapa aspas e envolve em aspas se houver ; ou aspas ou quebra de linha. */
    private static String esc(String s) {
        if (s == null) return "";
        boolean precisa = s.contains(SEP) || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String limpo = s.replace("\"", "\"\"");
        return precisa ? "\"" + limpo + "\"" : limpo;
    }

    /** Formata BigDecimal com vírgula decimal (pt-BR). */
    private static String bd(BigDecimal v) {
        if (v == null) return "";
        return v.toPlainString().replace('.', ',');
    }
}

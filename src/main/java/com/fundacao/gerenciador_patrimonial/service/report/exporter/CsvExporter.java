package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporta inventário em CSV (separador {@code ;}) — compatível com Excel em pt-BR.
 *
 * <p>Escreve BOM UTF-8 no início para que o Excel reconheça acentos automaticamente.</p>
 *
 * <p>Usa {@link Writer} (e não PrintWriter, que engole IOException): se o
 * cliente abortar o download, a exceção propaga e o loop para em vez de
 * formatar a base inteira num stream morto.</p>
 */
@Component
public class CsvExporter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEP = ";";
    private static final String EOL = "\r\n";

    public void exportarInventario(List<LinhaInventario> linhas, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // BOM para Excel reconhecer UTF-8
        w.write('\ufeff');

        w.write(String.join(SEP, LinhaInventario.CABECALHO));
        w.write(EOL);

        for (LinhaInventario l : linhas) {
            w.write(String.join(SEP,
                    str(l.id()),
                    esc(l.tombo()),
                    esc(l.descricao()),
                    esc(l.categoria()),
                    data(l.dataCompra()),
                    bd(l.valorCompra()),
                    str(l.conservacao()),
                    str(l.situacao()),
                    esc(l.notaFiscal()),
                    esc(l.upm()),
                    esc(l.lotacaoNome()),
                    esc(l.responsavelNome()),
                    l.vutAnos() != null ? l.vutAnos().toString() : "",
                    bd(l.depreciacaoAcumulada()),
                    bd(l.valorContabilLiquido()),
                    bd(l.depreciacaoAnual()),
                    data(l.dataBaixa()),
                    esc(l.motivoBaixa())
            ));
            w.write(EOL);
        }
        w.flush();
    }

    // ---- helpers de escape/formatação ----

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String data(LocalDate d) { return d == null ? "" : d.format(DATA); }

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

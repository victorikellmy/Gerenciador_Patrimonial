package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.dto.response.ItemLevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporta o resultado de um levantamento patrimonial em XLSX (uma linha por bem)
 * e PDF (agrupado por local, com resumo no topo).
 */
@Component
public class LevantamentoExporter {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA      = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font FONT_TITULO  = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font FONT_SECAO   = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font FONT_NORMAL  = new Font(Font.HELVETICA, 8);
    private static final Font FONT_PEQUENO = new Font(Font.HELVETICA, 7);
    private static final Font FONT_HEADER  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

    // =========================================================================
    // XLSX
    // =========================================================================

    public void xlsx(LevantamentoResponse lev, List<ItemLevantamentoResponse> itens, OutputStream out) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sh = wb.createSheet("Levantamento " + lev.ano());

            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(f);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            String[] cols = {
                    "UPM (esperada)", "Local (esperado)", "Tombo", "Descrição", "Categoria", "Responsável",
                    "Resultado", "Conservação anterior", "Conservação verificada", "Avaria", "Observação",
                    "Encontrado em (UPM)", "Encontrado em (local)", "Foto atualizada", "Conferido por", "Conferido em"
            };
            Row h = sh.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            int r = 1;
            for (ItemLevantamentoResponse it : itens) {
                Row row = sh.createRow(r++);
                int c = 0;
                set(row, c++, it.lotacaoUpm());
                set(row, c++, it.lotacaoNome());
                set(row, c++, it.numeroTombo());
                set(row, c++, it.descricao());
                set(row, c++, it.categoria());
                set(row, c++, it.responsavelNome());
                set(row, c++, it.resultado().getRotulo());
                set(row, c++, nome(it.conservacaoAnterior()));
                set(row, c++, nome(it.conservacaoVerificada()));
                set(row, c++, it.descricaoAvaria());
                set(row, c++, it.observacao());
                set(row, c++, it.localDivergente() ? it.lotacaoEncontradaUpm() : null);
                set(row, c++, it.localDivergente() ? it.lotacaoEncontradaNome() : null);
                set(row, c++, it.fotoId() != null ? "Sim" : "Não");
                set(row, c++, it.verificadoPor());
                set(row, c,   it.verificadoEm() != null ? it.verificadoEm().format(DATA_HORA) : null);
            }

            sh.createFreezePane(0, 1);
            int[] larguras = {18, 28, 12, 40, 14, 28, 14, 18, 20, 30, 30, 18, 28, 10, 16, 16};
            for (int i = 0; i < larguras.length; i++) sh.setColumnWidth(i, larguras[i] * 256);

            wb.write(out);
        }
    }

    // =========================================================================
    // PDF
    // =========================================================================

    public void pdf(LevantamentoResponse lev, List<ItemLevantamentoResponse> itens, OutputStream out) {
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph(lev.descricao(), FONT_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph sub = new Paragraph(
                    "Fundação Pró-Tocantins · %s · gerado em %s".formatted(
                            lev.aberto() ? "em andamento" : "concluído em " +
                                    (lev.concluidoEm() != null ? lev.concluidoEm().format(DATA) : "—"),
                            LocalDate.now().format(DATA)),
                    FONT_PEQUENO);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(8f);
            doc.add(sub);

            Paragraph resumo = new Paragraph(
                    "Bens: %d · Conferidos: %d (%d%%) · OK: %d · Avariados: %d · Não localizados: %d · Em local divergente: %d · Pendentes: %d"
                            .formatted(lev.total(), lev.conferidos(), lev.percentual(), lev.ok(), lev.avariados(),
                                       lev.naoLocalizados(), lev.divergenciasLocal(), lev.pendentes()),
                    FONT_NORMAL);
            resumo.setAlignment(Element.ALIGN_CENTER);
            resumo.setSpacingAfter(12f);
            doc.add(resumo);

            String localAtual = null;
            PdfPTable tabela = null;
            for (ItemLevantamentoResponse it : itens) {
                String chave = it.lotacaoUpm() + " / " + it.lotacaoNome();
                if (!chave.equals(localAtual)) {
                    if (tabela != null) doc.add(tabela);
                    localAtual = chave;
                    Paragraph secao = new Paragraph(chave, FONT_SECAO);
                    secao.setSpacingBefore(10f);
                    secao.setSpacingAfter(4f);
                    doc.add(secao);
                    tabela = novaTabela();
                }
                linha(tabela, it);
            }
            if (tabela != null) doc.add(tabela);

            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF do levantamento", e);
        }
    }

    private PdfPTable novaTabela() {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 3.2f, 1.6f, 1.2f, 1.4f, 2.6f, 2.0f, 1.4f, 1.3f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : new String[]{"Tombo", "Descrição", "Responsável", "Resultado", "Conservação",
                                     "Avaria / observação", "Encontrado em", "Conferido por", "Em"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, FONT_HEADER));
            c.setBackgroundColor(new Color(31, 56, 100));
            c.setPadding(4f);
            t.addCell(c);
        }
        return t;
    }

    private void linha(PdfPTable t, ItemLevantamentoResponse it) {
        cel(t, it.numeroTombo());
        cel(t, it.descricao());
        cel(t, it.responsavelNome());
        cel(t, it.resultado().getRotulo());
        String cons = it.conservacaoVerificada() != null
                ? nome(it.conservacaoAnterior()) + " → " + nome(it.conservacaoVerificada())
                : nome(it.conservacaoAnterior());
        cel(t, cons);
        String obs = join(it.descricaoAvaria(), it.observacao());
        cel(t, obs);
        cel(t, it.localDivergente() ? it.lotacaoEncontradaUpm() + " / " + it.lotacaoEncontradaNome() : "");
        cel(t, it.verificadoPor());
        cel(t, it.verificadoEm() != null ? it.verificadoEm().format(DATA) : "");
    }

    private void cel(PdfPTable t, String v) {
        PdfPCell c = new PdfPCell(new Phrase(v != null ? v : "—", FONT_NORMAL));
        c.setPadding(3f);
        t.addCell(c);
    }

    // ----- utilitários -----

    private static void set(Row r, int col, String v) {
        Cell c = r.createCell(col);
        if (v != null) c.setCellValue(v);
    }

    private static String nome(Enum<?> e) {
        return e != null ? e.name().replace('_', '/') : "";
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + " — " + b;
    }
}

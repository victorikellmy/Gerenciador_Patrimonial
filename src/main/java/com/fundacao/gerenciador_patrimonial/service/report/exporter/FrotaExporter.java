package com.fundacao.gerenciador_patrimonial.service.report.exporter;

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
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Exporta as listagens da frota (abastecimentos, manutenções, deslocamentos e
 * histórico de um veículo) como tabela simples em XLSX ou PDF.
 */
@Component
public class FrotaExporter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DecimalFormat MOEDA =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));

    private static final Font FONT_TITULO  = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font FONT_NORMAL  = new Font(Font.HELVETICA, 8);
    private static final Font FONT_PEQUENO = new Font(Font.HELVETICA, 7);
    private static final Font FONT_HEADER  = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);

    /** Tabela genérica: cabeçalhos + linhas de células (String, número, data, hora ou null). */
    public record Tabela(String titulo, String subtitulo, List<String> cabecalhos, List<List<Object>> linhas) {}

    public void xlsx(Tabela t, OutputStream out) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            Sheet sh = wb.createSheet(t.titulo().length() > 30 ? t.titulo().substring(0, 30) : t.titulo());

            CellStyle header = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(f);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle data = wb.createCellStyle();
            data.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle moeda = wb.createCellStyle();
            moeda.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int r = 0;
            Row titulo = sh.createRow(r++);
            titulo.createCell(0).setCellValue(t.titulo());
            if (t.subtitulo() != null) sh.createRow(r++).createCell(0).setCellValue(t.subtitulo());
            r++;

            Row cab = sh.createRow(r++);
            for (int i = 0; i < t.cabecalhos().size(); i++) {
                Cell c = cab.createCell(i);
                c.setCellValue(t.cabecalhos().get(i));
                c.setCellStyle(header);
            }
            for (List<Object> linha : t.linhas()) {
                Row row = sh.createRow(r++);
                for (int i = 0; i < linha.size(); i++) {
                    Object v = linha.get(i);
                    Cell c = row.createCell(i);
                    if (v == null) continue;
                    if (v instanceof LocalDate d) {
                        c.setCellValue(d);
                        c.setCellStyle(data);
                    } else if (v instanceof LocalTime h) {
                        c.setCellValue(h.format(HORA));
                    } else if (v instanceof BigDecimal b) {
                        c.setCellValue(b.doubleValue());
                        c.setCellStyle(moeda);
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(v.toString());
                    }
                }
            }
            for (int i = 0; i < t.cabecalhos().size(); i++) sh.setColumnWidth(i, 18 * 256);
            wb.write(out);
        }
    }

    public void pdf(Tabela t, OutputStream out) {
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph(t.titulo(), FONT_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph sub = new Paragraph(
                    "Fundação Pró-Tocantins · " + (t.subtitulo() != null ? t.subtitulo() + " · " : "")
                            + "gerado em " + LocalDate.now().format(DATA), FONT_PEQUENO);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(10f);
            doc.add(sub);

            PdfPTable tabela = new PdfPTable(t.cabecalhos().size());
            tabela.setWidthPercentage(100);
            tabela.setHeaderRows(1);
            for (String h : t.cabecalhos()) {
                PdfPCell c = new PdfPCell(new Phrase(h, FONT_HEADER));
                c.setBackgroundColor(new Color(31, 56, 100));
                c.setPadding(4f);
                tabela.addCell(c);
            }
            for (List<Object> linha : t.linhas()) {
                for (Object v : linha) {
                    PdfPCell c = new PdfPCell(new Phrase(formatar(v), FONT_NORMAL));
                    c.setPadding(3f);
                    if (v instanceof Number) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    tabela.addCell(c);
                }
            }
            doc.add(tabela);
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF da frota", e);
        }
    }

    private static String formatar(Object v) {
        if (v == null) return "";
        if (v instanceof LocalDate d) return d.format(DATA);
        if (v instanceof LocalTime h) return h.format(HORA);
        if (v instanceof BigDecimal b) return MOEDA.format(b);
        return v.toString();
    }
}

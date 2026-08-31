package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera PDFs institucionais via OpenPDF (fork do iText 4, LGPL).
 *
 * <p>Dois documentos:</p>
 * <ol>
 *   <li>{@link #gerarInventario(List, OutputStream)} — listagem geral em paisagem</li>
 *   <li>{@link #gerarTermoResponsabilidade(Responsavel, List, OutputStream)} —
 *       termo individual para um responsável, com assinatura</li>
 * </ol>
 */
@Component
public class PdfExporter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font FONT_TITULO  = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font FONT_SUBT    = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font FONT_NORMAL  = new Font(Font.HELVETICA, 10);
    private static final Font FONT_PEQUENO = new Font(Font.HELVETICA, 8);
    private static final Font FONT_HEADER  = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);

    // =========================================================================
    // Inventário geral
    // =========================================================================

    public void gerarInventario(List<Patrimonio> lista, OutputStream out) {
        // Paisagem (A4 girado) para caber mais colunas.
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 36, 28);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph("Inventário Patrimonial", FONT_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph rodape = new Paragraph(
                    "Gerado em " + LocalDate.now().format(DATA)
                            + " · " + lista.size() + " registros",
                    FONT_PEQUENO);
            rodape.setAlignment(Element.ALIGN_CENTER);
            rodape.setSpacingAfter(12f);
            doc.add(rodape);

            PdfPTable tabela = new PdfPTable(new float[]{5, 8, 25, 12, 14, 18, 10, 10});
            tabela.setWidthPercentage(100);
            tabela.setHeaderRows(1);

            for (String h : new String[]{"ID","Tombo","Descrição","Categoria","Lotação","Responsável","Valor","Situação"}) {
                tabela.addCell(headerCell(h));
            }

            for (Patrimonio p : lista) {
                tabela.addCell(corpoCell(str(p.getId())));
                tabela.addCell(corpoCell(str(p.getNumeroTombo())));
                tabela.addCell(corpoCell(str(p.getDescricao())));
                tabela.addCell(corpoCell(str(p.getCategoria())));
                tabela.addCell(corpoCell(p.getLotacao() != null
                        ? p.getLotacao().getUpm() + " / " + p.getLotacao().getNome() : ""));
                tabela.addCell(corpoCell(p.getResponsavel() != null
                        ? p.getResponsavel().getNomeCompleto() : ""));
                tabela.addCell(corpoCellRight(formatarValor(p.getValorCompra())));
                tabela.addCell(corpoCell(p.getSituacao() != null ? p.getSituacao().name() : ""));
            }

            doc.add(tabela);
        } finally {
            doc.close();
        }
    }

    // =========================================================================
    // Termo de Responsabilidade — por responsável
    // =========================================================================

    public void gerarTermoResponsabilidade(Responsavel responsavel,
                                           List<Patrimonio> bens,
                                           OutputStream out) {
        Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // --- Cabeçalho ---
            Paragraph org = new Paragraph("Fundação Pró-Tocantins (Fasaúde)", FONT_SUBT);
            org.setAlignment(Element.ALIGN_CENTER);
            doc.add(org);

            Paragraph titulo = new Paragraph("Termo de Responsabilidade de Patrimônio", FONT_TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(20f);
            doc.add(titulo);

            // --- Identificação ---
            PdfPTable ident = new PdfPTable(new float[]{1, 3});
            ident.setWidthPercentage(100);
            ident.addCell(rotuloCell("Nome:"));
            ident.addCell(valorCell(responsavel.getNomeCompleto()));
            ident.addCell(rotuloCell("Matrícula:"));
            ident.addCell(valorCell(str(responsavel.getMatricula())));
            ident.addCell(rotuloCell("Cidade:"));
            ident.addCell(valorCell(str(responsavel.getCidade())));
            ident.addCell(rotuloCell("Lotação:"));
            ident.addCell(valorCell(responsavel.getLotacao() != null
                    ? responsavel.getLotacao().getUpm() + " / " + responsavel.getLotacao().getNome()
                    : "—"));
            ident.setSpacingAfter(14f);
            doc.add(ident);

            // --- Texto legal ---
            Paragraph texto = new Paragraph(
                    "Declaro, para os devidos fins, que recebi, neste ato, os bens patrimoniais " +
                    "relacionados na tabela abaixo, comprometendo-me a zelar pela sua guarda, " +
                    "conservação e uso adequado, de acordo com as normas internas da instituição. " +
                    "Comprometo-me ainda a comunicar formalmente qualquer movimentação, perda, " +
                    "extravio ou dano dos referidos bens ao setor responsável pelo patrimônio.",
                    FONT_NORMAL);
            texto.setAlignment(Element.ALIGN_JUSTIFIED);
            texto.setSpacingAfter(12f);
            doc.add(texto);

            // --- Tabela de bens ---
            PdfPTable bensTab = new PdfPTable(new float[]{10, 40, 15, 10, 15});
            bensTab.setWidthPercentage(100);
            bensTab.setHeaderRows(1);
            for (String h : new String[]{"Tombo", "Descrição", "Categoria", "Conservação", "Valor de compra"}) {
                bensTab.addCell(headerCell(h));
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Patrimonio p : bens) {
                bensTab.addCell(corpoCell(str(p.getNumeroTombo())));
                bensTab.addCell(corpoCell(str(p.getDescricao())));
                bensTab.addCell(corpoCell(str(p.getCategoria())));
                bensTab.addCell(corpoCell(p.getConservacao() != null ? p.getConservacao().name() : "—"));
                bensTab.addCell(corpoCellRight(formatarValor(p.getValorCompra())));
                if (p.getValorCompra() != null) total = total.add(p.getValorCompra());
            }
            // rodapé de total
            PdfPCell totLbl = new PdfPCell(new Phrase("Total", FONT_SUBT));
            totLbl.setColspan(4);
            totLbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totLbl.setPadding(5f);
            bensTab.addCell(totLbl);
            PdfPCell totVal = new PdfPCell(new Phrase(formatarValor(total), FONT_SUBT));
            totVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totVal.setPadding(5f);
            bensTab.addCell(totVal);

            bensTab.setSpacingAfter(30f);
            doc.add(bensTab);

            // --- Assinatura ---
            Paragraph dataLocal = new Paragraph(
                    "Palmas/TO, ____ de _______________ de "
                            + LocalDate.now().getYear() + ".",
                    FONT_NORMAL);
            dataLocal.setAlignment(Element.ALIGN_RIGHT);
            dataLocal.setSpacingAfter(40f);
            doc.add(dataLocal);

            PdfPTable ass = new PdfPTable(2);
            ass.setWidthPercentage(100);
            ass.addCell(assinaturaCell("Responsável pelo bem\n" + responsavel.getNomeCompleto()));
            ass.addCell(assinaturaCell("Setor de Patrimônio"));
            doc.add(ass);
        } finally {
            doc.close();
        }
    }

    // =========================================================================
    // Helpers de células
    // =========================================================================

    private PdfPCell headerCell(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FONT_HEADER));
        c.setBackgroundColor(new Color(33, 66, 120));
        c.setPadding(5f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        return c;
    }

    private PdfPCell corpoCell(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        c.setPadding(4f);
        return c;
    }

    private PdfPCell corpoCellRight(String texto) {
        PdfPCell c = corpoCell(texto);
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return c;
    }

    private PdfPCell rotuloCell(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FONT_SUBT));
        c.setBorder(0);
        c.setPadding(3f);
        return c;
    }

    private PdfPCell valorCell(String texto) {
        PdfPCell c = new PdfPCell(new Phrase(texto, FONT_NORMAL));
        c.setBorder(0);
        c.setPadding(3f);
        return c;
    }

    private PdfPCell assinaturaCell(String descricao) {
        PdfPCell c = new PdfPCell();
        c.setBorder(0);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.addElement(linhaAssinatura());
        Paragraph txt = new Paragraph(descricao, FONT_PEQUENO);
        txt.setAlignment(Element.ALIGN_CENTER);
        c.addElement(txt);
        return c;
    }

    private Paragraph linhaAssinatura() {
        Paragraph p = new Paragraph("_________________________________", FONT_NORMAL);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static String formatarValor(BigDecimal v) {
        if (v == null) return "—";
        return "R$ " + v.toPlainString().replace('.', ',');
    }
}

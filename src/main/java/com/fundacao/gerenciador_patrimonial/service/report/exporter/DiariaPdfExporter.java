package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.response.CautelaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaHistoricoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Documentos do antigo Transportes FPTO: formulário de solicitação de diária
 * (com campos para assinatura), relatório de diárias pagas e termo de cautela.
 */
@Component
public class DiariaPdfExporter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DecimalFormat MOEDA = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.of("pt", "BR")));

    private static final Color AZUL = new Color(0x1d, 0x4e, 0x89);
    private static final Color AZUL_CLARO = new Color(0xe7, 0xed, 0xf6);
    private static final Color CINZA = new Color(0x5d, 0x6b, 0x82);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, AZUL);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 8, CINZA);
    private static final Font CABECALHO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
    private static final Font CELULA = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
    private static final Font CELULA_NEGRITO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f);
    private static final Font ROTULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, CINZA);
    private static final Font VALOR = FontFactory.getFont(FontFactory.HELVETICA, 9.5f);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.HELVETICA, 8, CINZA);

    /** Formulário de solicitação de diária, com campos para assinatura. */
    public byte[] solicitacaoDiaria(DiariaResponse d, List<DiariaHistoricoResponse> historico) {
        return gerar(PageSize.A4, "Solicitação de Pagamento de Diária nº " + d.id(),
                "Portaria 004/2018 GAB. Pres FPTO | Situação: " + d.status().getRotulo(), doc -> {
            doc.add(secao("Dados do profissional"));
            PdfPTable p = grade(2);
            campo(p, "Nome", d.profissionalNome());
            campo(p, "Matrícula", d.profissionalMatricula());
            campo(p, "Cargo / Posto / Graduação", d.profissionalNivel());
            campo(p, "Lotação", d.profissionalLotacao());
            campo(p, "CPF", d.profissionalCpf());
            campo(p, "RG / Órgão", juntar(d.profissionalRg(), d.profissionalOrgaoExpedidor()));
            campo(p, "Banco / Agência / Conta", d.profissionalDadosBancarios());
            campo(p, "Data da solicitação", data(d.dataSolicitacao()));
            doc.add(p);

            doc.add(secao("Dados da viagem"));
            PdfPTable v = grade(2);
            campo(v, "Itinerário", d.itinerario());
            campo(v, "Meio de transporte", d.meioTransporte().getRotulo());
            campo(v, "Partida", data(d.partida()) + (d.horaSaida() == null ? "" : " às " + hora(d.horaSaida())));
            campo(v, "Retorno", data(d.retorno()) + (d.horaChegada() == null ? "" : " às " + hora(d.horaChegada())));
            campo(v, "Recurso", d.recurso().getRotulo());
            campo(v, "Nº de diárias", d.numeroDiarias().stripTrailingZeros().toPlainString().replace('.', ','));
            campo(v, "Valor unitário", "R$ " + MOEDA.format(d.valorUnitario()));
            campo(v, "Valor total", "R$ " + MOEDA.format(d.valorTotal()));
            doc.add(v);

            doc.add(secao("Finalidade da viagem"));
            Paragraph fin = new Paragraph(texto(d.finalidade()), VALOR);
            fin.setSpacingAfter(10);
            doc.add(fin);

            if (d.status() == StatusDiaria.INDEFERIDO && d.motivoIndeferimento() != null) {
                doc.add(secao("Motivo do indeferimento"));
                doc.add(new Paragraph(d.motivoIndeferimento(), VALOR));
            }
            if (d.status() == StatusDiaria.PAGO) {
                doc.add(secao("Pagamento"));
                doc.add(new Paragraph("Pago em " + data(d.dataPagamento()), VALOR));
            }
            if (!historico.isEmpty()) {
                doc.add(secao("Histórico"));
                PdfPTable h = tabela(new float[]{20, 25, 25, 30}, "Quando", "Situação", "Usuário", "Observação");
                for (DiariaHistoricoResponse item : historico) {
                    celula(h, dataHora(item.instante()), Element.ALIGN_LEFT);
                    celula(h, item.paraStatus().getRotulo(), Element.ALIGN_LEFT);
                    celula(h, item.usuario(), Element.ALIGN_LEFT);
                    celula(h, texto(item.observacao()), Element.ALIGN_LEFT);
                }
                doc.add(h);
            }
            doc.add(assinaturas("Solicitante: " + d.solicitante(), "Presidente da FPTO", "Financeiro"));
        });
    }

    /** Relatório de diárias pagas por data de pagamento. */
    public byte[] diariasPagas(List<DiariaResponse> lista, LocalDate de, LocalDate ate, String usuario) {
        return gerar(PageSize.A4.rotate(), "Relatório de Diárias Pagas",
                "Por data de pagamento: " + data(de) + " a " + data(ate) + " | Usuário: " + usuario, doc -> {
            PdfPTable t = tabela(new float[]{3, 5, 20, 12, 22, 8, 8, 6, 9, 8},
                    "Item", "Nº", "Profissional", "Lotação", "Itinerário", "Partida", "Retorno", "Diárias", "Valor pago", "Dt. pgto");
            int i = 1;
            BigDecimal total = BigDecimal.ZERO;
            for (DiariaResponse d : lista) {
                celula(t, String.valueOf(i++), Element.ALIGN_CENTER);
                celula(t, String.valueOf(d.id()), Element.ALIGN_CENTER);
                celula(t, d.profissionalNome(), Element.ALIGN_LEFT);
                celula(t, texto(d.profissionalLotacao()), Element.ALIGN_LEFT);
                celula(t, d.itinerario(), Element.ALIGN_LEFT);
                celula(t, data(d.partida()), Element.ALIGN_CENTER);
                celula(t, data(d.retorno()), Element.ALIGN_CENTER);
                celula(t, d.numeroDiarias().stripTrailingZeros().toPlainString().replace('.', ','), Element.ALIGN_CENTER);
                celula(t, MOEDA.format(d.valorTotal()), Element.ALIGN_RIGHT);
                celula(t, data(d.dataPagamento()), Element.ALIGN_CENTER);
                total = total.add(d.valorTotal());
            }
            if (lista.isEmpty()) {
                PdfPCell c = new PdfPCell(new Phrase("Nenhum registro no período.", CELULA));
                c.setColspan(10);
                c.setPadding(8);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                t.addCell(c);
            }
            doc.add(t);
            Paragraph tot = new Paragraph("Registros: " + lista.size() + "     Total pago: R$ " + MOEDA.format(total), CELULA_NEGRITO);
            tot.setSpacingBefore(8);
            tot.setAlignment(Element.ALIGN_RIGHT);
            doc.add(tot);
        });
    }

    /** Termo de cautela de veículo, para assinatura do condutor. */
    public byte[] termoCautela(CautelaResponse c) {
        return gerar(PageSize.A4, "Termo de Cautela de Veículo nº " + c.id(),
                "Emitido em " + dataHora(LocalDateTime.now()), doc -> {
            doc.add(secao("Veículo"));
            PdfPTable v = grade(2);
            campo(v, "Placa / Modelo", c.placa() + " / " + c.modelo());
            campo(v, "Chassi", c.chassi());
            campo(v, "Ano", c.ano());
            campo(v, "Cidade de origem", c.cidadeVeiculo());
            doc.add(v);

            doc.add(secao("Deslocamento"));
            PdfPTable d = grade(2);
            campo(d, "Data", data(c.dataViagem()));
            campo(d, "Destino", c.destino());
            campo(d, "Motivo", c.motivo());
            campo(d, "Responsável / Assistido", juntar(c.responsavel(), c.assistido()));
            campo(d, "Saída", juntar(hora(c.horaSaida()), c.kmInicial() == null ? null : c.kmInicial() + " km"));
            campo(d, "Chegada", c.kmFinal() == null ? "Em aberto" : juntar(hora(c.horaChegada()), c.kmFinal() + " km"));
            doc.add(d);

            doc.add(secao("Condutor responsável"));
            PdfPTable m = grade(2);
            campo(m, "Nome", c.motoristaNome());
            campo(m, "Lotação", c.lotacao());
            campo(m, "RG", c.rg());
            campo(m, "CPF", c.cpf());
            campo(m, "Telefone", c.fone());
            if (c.observacao() != null) campo(m, "Observação", c.observacao());
            doc.add(m);

            Paragraph termo = new Paragraph(
                    "Declaro ter recebido o veículo acima descrito em perfeitas condições de uso e "
                    + "conservação, comprometendo-me a utilizá-lo exclusivamente para o fim indicado, a "
                    + "zelar por sua guarda e a devolvê-lo nas mesmas condições, respondendo por danos, "
                    + "multas e infrações ocorridos durante o período da cautela.", VALOR);
            termo.setSpacingBefore(14);
            termo.setAlignment(Element.ALIGN_JUSTIFIED);
            doc.add(termo);

            doc.add(assinaturas("Condutor: " + c.motoristaNome(), "Responsável pela entrega - FPTO"));
        });
    }

    // ------------------------------------------------------------------ infra

    @FunctionalInterface
    private interface Conteudo { void escrever(Document doc) throws Exception; }

    private static byte[] gerar(Rectangle tamanho, String titulo, String subtitulo, Conteudo conteudo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(tamanho, 36, 36, 40, 36);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Paragraph cab = new Paragraph("FUNDAÇÃO PRÓ-TOCANTINS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, CINZA));
            cab.setAlignment(Element.ALIGN_CENTER);
            doc.add(cab);
            Paragraph t = new Paragraph(titulo, TITULO);
            t.setAlignment(Element.ALIGN_CENTER);
            doc.add(t);
            Paragraph s = new Paragraph(subtitulo, SUBTITULO);
            s.setAlignment(Element.ALIGN_CENTER);
            s.setSpacingAfter(8);
            doc.add(s);
            conteudo.escrever(doc);
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF: " + titulo, e);
        }
        return out.toByteArray();
    }

    private static PdfPTable tabela(float[] larguras, String... cabecalhos) {
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : cabecalhos) {
            PdfPCell c = new PdfPCell(new Phrase(h, CABECALHO));
            c.setBackgroundColor(AZUL);
            c.setPadding(4);
            t.addCell(c);
        }
        return t;
    }

    private static void celula(PdfPTable t, String valor, int alinhamento) {
        PdfPCell c = new PdfPCell(new Phrase(valor == null ? "" : valor, CELULA));
        c.setPadding(3);
        c.setHorizontalAlignment(alinhamento);
        t.addCell(c);
    }

    private static Paragraph secao(String titulo) {
        Paragraph p = new Paragraph(titulo.toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, AZUL));
        p.setSpacingBefore(10);
        p.setSpacingAfter(4);
        return p;
    }

    private static PdfPTable grade(int colunas) {
        PdfPTable t = new PdfPTable(colunas);
        t.setWidthPercentage(100);
        return t;
    }

    private static void campo(PdfPTable t, String rotulo, String valor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(AZUL_CLARO);
        cell.setBorderColor(Color.WHITE);
        cell.setBorderWidth(2);
        cell.setPadding(6);
        cell.addElement(new Phrase(rotulo.toUpperCase(), ROTULO));
        cell.addElement(new Phrase(valor == null || valor.isBlank() ? "—" : valor, VALOR));
        t.addCell(cell);
    }

    private static PdfPTable assinaturas(String... rotulos) {
        PdfPTable t = new PdfPTable(rotulos.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(56);
        for (String r : rotulos) {
            PdfPCell cell = new PdfPCell(new Phrase(r, PEQUENA));
            cell.setBorder(Rectangle.TOP);
            cell.setBorderColor(Color.BLACK);
            cell.setPaddingTop(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(cell);
        }
        return t;
    }

    private static String juntar(String a, String b) {
        boolean ta = a != null && !a.isBlank();
        boolean tb = b != null && !b.isBlank();
        if (ta && tb) return a + " / " + b;
        return ta ? a : (tb ? b : null);
    }

    private static String texto(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String data(LocalDate d) { return d == null ? "—" : d.format(DATA); }
    private static String dataHora(LocalDateTime d) { return d == null ? "—" : d.format(DATA_HORA); }
    private static String hora(LocalTime h) { return h == null ? null : h.format(HORA); }
}

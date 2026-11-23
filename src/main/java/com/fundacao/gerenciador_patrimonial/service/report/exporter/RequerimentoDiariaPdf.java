package com.fundacao.gerenciador_patrimonial.service.report.exporter;

import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaResponse;
import com.fundacao.gerenciador_patrimonial.service.CalculoDiarias;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Requerimento de Diárias preenchido, no layout do Anexo II da Portaria 001/2019 GAB. PRES
 * (modelo DIARIA.docx): é o documento que o solicitante salva e envia pelo SGD para o parecer do
 * Diretor Presidente. Abaixo do formulário sai a memória de cálculo da quantidade de diárias.
 */
@Component
@Slf4j
public class RequerimentoDiariaPdf {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DecimalFormat MOEDA = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
    private static final DecimalFormat QUANTIDADE = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(PT_BR));

    private static final Font TITULO = FontFactory.getFont(FontFactory.TIMES_BOLD, 11, Font.UNDERLINE);
    private static final Font NOME_DOC = FontFactory.getFont(FontFactory.TIMES_BOLD, 11);
    private static final Font ROTULO = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10);
    private static final Font VALOR = FontFactory.getFont(FontFactory.TIMES_BOLD, 11);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10.5f);
    private static final Font PEQUENA = FontFactory.getFont(FontFactory.TIMES_ROMAN, 8.5f, new Color(0x40, 0x40, 0x40));
    private static final Font PEQUENA_NEGRITO = FontFactory.getFont(FontFactory.TIMES_BOLD, 8.5f, new Color(0x40, 0x40, 0x40));

    private static final int COLUNAS = 12;
    private static final float BORDA = 0.8f;

    /** Nome e posto de quem assina o parecer (rodapé do Anexo II). */
    @Value("${app.diarias.autoridade-nome:Luiz Cláudio Gonçalves Benício – CEL PM R/R}")
    private String autoridadeNome = "Luiz Cláudio Gonçalves Benício – CEL PM R/R";

    @Value("${app.diarias.autoridade-cargo:DIRETOR PRESIDENTE DA FUNDAÇÃO PRÓ-TOCANTINS}")
    private String autoridadeCargo = "DIRETOR PRESIDENTE DA FUNDAÇÃO PRÓ-TOCANTINS";

    public byte[] gerar(DiariaResponse d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 34, 30);
        try {
            PdfWriter.getInstance(doc, out);
            doc.addTitle("Requerimento de Diárias nº " + numero(d));
            doc.open();

            Paragraph anexo = new Paragraph("ANEXO II – PORTARIA Nº 001/2019 – GAB. PRES.", TITULO);
            anexo.setAlignment(Element.ALIGN_CENTER);
            anexo.setSpacingAfter(12);
            doc.add(anexo);

            PdfPTable t = new PdfPTable(COLUNAS);
            t.setWidthPercentage(100);

            // Cabeçalho: logomarca + nome do documento (ocupa duas linhas), data, número e matrícula.
            t.addCell(cabecalho());
            t.addCell(campo("1. DATA", data(d.dataSolicitacao()), 2));
            t.addCell(campo("Diária nº", numero(d), 2));
            t.addCell(campo("2. MATRÍCULA FUNCIONAL", d.profissionalMatricula(), 4));

            t.addCell(campo("3. PROFISSIONAL", d.profissionalNome(), 12));
            t.addCell(campo("4. LOTAÇÃO", d.profissionalLotacao(), 5));
            t.addCell(campo("5. CARGO/POSTO/FUNÇÃO", d.profissionalCargo(), 7));

            t.addCell(campo("6. CPF/RG", juntar(" / ", d.profissionalCpf(), d.profissionalRg()), 4));
            t.addCell(campo("7. ÓRGÃO EXPEDIDOR", d.profissionalOrgaoExpedidor(), 4));
            t.addCell(campo("8. ITINERÁRIO", juntar(" – ", d.origem(), d.itinerario()), 4));

            t.addCell(campo("9. PERÍODO DA VIAGEM", data(d.partida()) + " a " + data(d.retorno()), 4));
            t.addCell(campo("10. HORÁRIO DE SAÍDA", hora(d.horaSaida()), 4));
            t.addCell(campo("11. HORÁRIO DE CHEGADA", hora(d.horaChegada()), 4));

            t.addCell(opcoes("12. MEIO DE TRANSPORTE", meios(d.meioTransporte())));
            t.addCell(opcoes("13. RECURSO", recursos(d.recurso())));
            t.addCell(opcoes("14. DADOS BANCÁRIOS", bancarios(d)));

            t.addCell(campo("15. NÚMERO DE DIÁRIAS", quantidade(d.numeroDiarias()), 4));
            t.addCell(campo("16. VALOR UNITÁRIO", "R$: " + MOEDA.format(d.valorUnitario())
                    + (d.tipoDiaria() != null ? "\n(" + d.tipoDiaria().getRotulo() + ")" : ""), 4));
            t.addCell(campo("17. VALOR TOTAL", "R$: " + MOEDA.format(d.valorTotal()), 4));

            t.addCell(finalidade(d));
            t.addCell(parecer(d));
            doc.add(t);

            memoriaDeCalculo(doc, d);

            Paragraph rodape = new Paragraph(limpar("Emitido pelo sistema de Patrimônio da FPTO em "
                    + LocalDateTime.now().format(DATA_HORA) + ". Solicitação registrada por " + d.solicitante()
                    + ". Situação no sistema: " + d.status().getRotulo() + "."), PEQUENA);
            rodape.setSpacingBefore(6);
            doc.add(rodape);
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o requerimento de diária " + d.id(), e);
        }
        return out.toByteArray();
    }

    /** Nome de arquivo para download: Requerimento_Diaria_12-2026_FULANO.pdf */
    public static String nomeArquivo(DiariaResponse d) {
        String nome = d.profissionalNome() == null ? "" : java.text.Normalizer
                .normalize(d.profissionalNome(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (nome.length() > 40) nome = nome.substring(0, 40);
        return "Requerimento_Diaria_" + numero(d).replace('/', '-') + (nome.isEmpty() ? "" : "_" + nome) + ".pdf";
    }

    // ------------------------------------------------------------------ células

    private PdfPCell cabecalho() {
        PdfPCell c = new PdfPCell();
        c.setColspan(8);
        c.setRowspan(2);
        c.setBorderWidth(BORDA);
        c.setPadding(6);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = logo();
        if (logo != null) {
            logo.scaleToFit(150, 62);
            logo.setAlignment(Element.ALIGN_CENTER);
            c.addElement(logo);
        } else {
            Paragraph nome = new Paragraph("FUNDAÇÃO PRÓ-TOCANTINS", NOME_DOC);
            nome.setAlignment(Element.ALIGN_CENTER);
            c.addElement(nome);
        }
        Paragraph p = new Paragraph("REQUERIMENTO DE DIÁRIAS", NOME_DOC);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(4);
        c.addElement(p);
        return c;
    }

    private static PdfPCell campo(String rotulo, String valor, int colspan) {
        PdfPCell c = celula(colspan, 40);
        c.addElement(linha(rotulo, ROTULO, 11));
        Paragraph v = linha(texto(valor), VALOR, 13);
        v.setSpacingBefore(2);
        c.addElement(v);
        return c;
    }

    private static PdfPCell opcoes(String rotulo, List<String> linhas) {
        PdfPCell c = celula(4, 62);
        c.addElement(linha(rotulo, ROTULO, 11));
        for (String l : linhas) c.addElement(linha(l, TEXTO, 14));
        return c;
    }

    private static PdfPCell finalidade(DiariaResponse d) {
        PdfPCell c = celula(COLUNAS, 104);
        c.addElement(linha("18. FINALIDADE DA VIAGEM", ROTULO, 11));
        Paragraph f = linha(texto(d.finalidade()), TEXTO, 13);
        f.setAlignment(Element.ALIGN_JUSTIFIED);
        f.setSpacingBefore(2);
        c.addElement(f);
        Paragraph tracejado = linha("______________________________________", ROTULO, 12);
        tracejado.setAlignment(Element.ALIGN_RIGHT);
        tracejado.setSpacingBefore(26);
        c.addElement(tracejado);
        Paragraph assinatura = linha("ASSINATURA DO SOLICITANTE", ROTULO, 12);
        assinatura.setAlignment(Element.ALIGN_RIGHT);
        c.addElement(assinatura);
        return c;
    }

    private PdfPCell parecer(DiariaResponse d) {
        boolean autorizado = d.status() == StatusDiaria.DEFERIDO || d.status() == StatusDiaria.PAGO;
        boolean negado = d.status() == StatusDiaria.INDEFERIDO;
        PdfPCell c = celula(COLUNAS, 120);
        Paragraph titulo = linha("PARECER DO DIRETOR PRESIDENTE", ROTULO, 12);
        titulo.setAlignment(Element.ALIGN_CENTER);
        c.addElement(titulo);
        Paragraph base = linha("No uso das atribuições legais e conforme o disposto no artigo 1° e 4°, inciso I da "
                + "Portaria 001/2019 - Gab. Pres. da Fundação Pró-Tocantins, resolve:", ROTULO, 13);
        base.setFirstLineIndent(28);
        base.setAlignment(Element.ALIGN_JUSTIFIED);
        base.setSpacingBefore(8);
        c.addElement(base);
        Paragraph escolha = linha(marca(autorizado) + " AUTORIZAR          " + marca(negado) + " NÃO AUTORIZAR", ROTULO, 14);
        escolha.setAlignment(Element.ALIGN_CENTER);
        c.addElement(escolha);
        if (negado && d.motivoIndeferimento() != null && !d.motivoIndeferimento().isBlank()) {
            Paragraph motivo = linha("Motivo: " + d.motivoIndeferimento(), ROTULO, 12);
            motivo.setSpacingBefore(4);
            c.addElement(motivo);
        }
        Paragraph nome = linha(autoridadeNome, ROTULO, 12);
        nome.setAlignment(Element.ALIGN_CENTER);
        nome.setSpacingBefore(30);
        c.addElement(nome);
        Paragraph cargo = linha(autoridadeCargo, ROTULO, 12);
        cargo.setAlignment(Element.ALIGN_CENTER);
        c.addElement(cargo);
        return c;
    }

    private static void memoriaDeCalculo(Document doc, DiariaResponse d) throws Exception {
        CalculoDiarias.Resultado r = d.calculo();
        if (r == null) return;
        Paragraph titulo = new Paragraph("MEMÓRIA DE CÁLCULO", PEQUENA_NEGRITO);
        titulo.setSpacingBefore(8);
        doc.add(titulo);
        LocalDateTime retorno = d.retorno().atTime(d.horaChegada());
        List<String> linhas = new ArrayList<>();
        linhas.add("Saída em " + d.partida().atTime(d.horaSaida()).format(DATA_HORA) + ", retorno em " + retorno.format(DATA_HORA)
                + ". Duração total: " + duracao(r.duracaoMin()) + ".");
        linhas.add("Períodos completos de 24 horas: " + r.integrais() + " período(s) × 1 diária = " + QUANTIDADE.format(r.integrais()) + ".");
        if (r.restoMin() == 0) {
            linhas.add("Não há intervalo restante: nenhuma parcela adicional é computada.");
        } else if (r.refeicoes().isEmpty()) {
            linhas.add("Intervalo restante de " + r.inicioResto().format(DATA_HORA) + " até " + retorno.format(DATA_HORA)
                    + ": sem coincidência com 12h ou 18h = 0,0.");
        } else {
            linhas.add("Intervalo restante de " + r.inicioResto().format(DATA_HORA) + " até " + retorno.format(DATA_HORA)
                    + ": alimentação em " + r.refeicoes().stream().map(x -> x.format(DATA_HORA)).collect(Collectors.joining("; "))
                    + " (limite de 0,5) = " + QUANTIDADE.format(r.alimentacao()) + ".");
        }
        linhas.add("Quantidade: " + quantidade(d.numeroDiarias()) + " diária(s) × R$ " + MOEDA.format(d.valorUnitario())
                + (d.tipoDiaria() != null ? " (" + d.tipoDiaria().getRotulo() + ")" : "") + " = R$ " + MOEDA.format(d.valorTotal()) + ".");
        for (String l : linhas) doc.add(new Paragraph(limpar(l), PEQUENA));
    }

    // ------------------------------------------------------------------ conteúdo

    private static List<String> meios(MeioTransporte m) {
        List<String> l = new ArrayList<>(List.of(
                marca(m == MeioTransporte.AEREO) + " AÉREO",
                marca(m == MeioTransporte.RODOVIARIO) + " RODOVIÁRIO",
                marca(m == MeioTransporte.VEICULO_OFICIAL) + " VEÍCULO OFICIAL"));
        if (m == MeioTransporte.OUTRO) l.add(marca(true) + " OUTRO");
        return l;
    }

    private static List<String> recursos(RecursoDiaria r) {
        List<String> l = new ArrayList<>(List.of(
                marca(r == RecursoDiaria.PROPRIO) + " PRÓPRIO",
                marca(r == RecursoDiaria.CONVENIO) + " CONVÊNIO"));
        if (r == RecursoDiaria.OUTRA) l.add(marca(true) + " OUTRA");
        return l;
    }

    private static List<String> bancarios(DiariaResponse d) {
        if (d.banco() == null && d.agencia() == null && d.contaCorrente() == null && d.pix() == null) {
            return List.of(texto(d.profissionalDadosBancarios()));
        }
        List<String> l = new ArrayList<>(List.of("BANCO: " + texto(d.banco()), "AGÊNCIA: " + texto(d.agencia()),
                "CONTA: " + texto(d.contaCorrente())));
        if (d.pix() != null && !d.pix().isBlank()) l.add("PIX: " + d.pix());
        return l;
    }

    private static String marca(boolean marcado) { return marcado ? "( X )" : "(    )"; }

    private static String numero(DiariaResponse d) {
        return d.numeroRequerimento() != null && !d.numeroRequerimento().isBlank() ? d.numeroRequerimento() : String.valueOf(d.id());
    }

    private static String quantidade(BigDecimal n) { return n == null ? "" : QUANTIDADE.format(n); }

    private static String duracao(long minutos) { return (minutos / 60) + "h " + (minutos % 60) + "min"; }

    private static String juntar(String separador, String a, String b) {
        boolean ta = a != null && !a.isBlank();
        boolean tb = b != null && !b.isBlank();
        if (ta && tb) return a + separador + b;
        return ta ? a : (tb ? b : "");
    }

    private static String data(LocalDate d) { return d == null ? "" : d.format(DATA); }
    private static String hora(LocalTime h) { return h == null ? "" : h.format(HORA); }
    private static String texto(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ infra

    private static PdfPCell celula(int colspan, float alturaMinima) {
        PdfPCell c = new PdfPCell();
        c.setColspan(colspan);
        c.setBorderWidth(BORDA);
        c.setPaddingLeft(5);
        c.setPaddingRight(5);
        c.setPaddingTop(2);
        c.setPaddingBottom(6);
        c.setMinimumHeight(alturaMinima);
        return c;
    }

    private static Paragraph linha(String texto, Font fonte, float entrelinha) {
        Paragraph p = new Paragraph(limpar(texto), fonte);
        p.setLeading(entrelinha);
        return p;
    }

    /** As fontes padrão do PDF só têm os caracteres do Windows-1252: troca setas e remove o resto. */
    private static String limpar(String s) {
        if (s == null) return "";
        String t = s.replace("→", "–").replace("➔", "–").replace("⇒", "–");
        CharsetEncoder enc = Charset.forName("windows-1252").newEncoder();
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            sb.append(ch == '\n' || enc.canEncode(ch) ? ch : ' ');
        }
        return sb.toString();
    }

    private static volatile byte[] logoBytes;

    private static Image logo() {
        try {
            byte[] bytes = logoBytes;
            if (bytes == null) {
                try (InputStream in = new ClassPathResource("static/images/logo-color.png").getInputStream()) {
                    bytes = in.readAllBytes();
                }
                logoBytes = bytes;
            }
            return Image.getInstance(bytes);
        } catch (Exception e) {
            log.warn("Logomarca do requerimento de diárias indisponível: {}", e.getMessage());
            return null;
        }
    }
}
